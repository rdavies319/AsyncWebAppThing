package com.rdavies.api.service;

import com.rdavies.api.domain.*;
import com.rdavies.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Rolls and settles one round per minute. Each round owns a minute slot: it opens on the minute
 * (start_time) and is rolled at the next minute boundary, using the seed it committed to on opening.
 *
 * <p>Safe to run on several instances and to trigger more than once per minute:
 * <ul>
 *   <li>a transaction-scoped Postgres advisory lock makes every other instance skip the tick;</li>
 *   <li>the open round is row-locked, and a round is only rolled once its roll time has passed,
 *       so a repeat tick finds nothing due;</li>
 *   <li>the round moves out of OPEN with a conditional UPDATE before any points move;</li>
 *   <li>rounds.start_time is unique, so the same slot can never be opened twice.</li>
 * </ul>
 *
 * <p>Catch-up: if the engine reaches a round {@link #ROLL_GRACE} or more after its roll time
 * (the app was down), that round is voided and its stakes refunded, and a fresh round opens
 * on the current minute. No rounds are created for the minutes in between.
 */
@Service
public class RoundEngine {
    private static final Logger log = LoggerFactory.getLogger(RoundEngine.class);

    /** How late a roll may be before the round counts as missed and is voided instead. */
    static final Duration ROLL_GRACE = Duration.ofSeconds(30);
    /** Arbitrary app-wide key for pg_try_advisory_xact_lock. */
    public static final long ENGINE_LOCK_KEY = 0x486967684C6F77L; // "HighLow"

    private final SecureRandom random = new SecureRandom();

    private final RoundRepository roundRepository;
    private final PredictionRepository predictionRepository;
    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public RoundEngine(RoundRepository roundRepository, PredictionRepository predictionRepository,
                       WalletRepository walletRepository, LedgerRepository ledgerRepository,
                       JdbcTemplate jdbc, Clock clock, ApplicationEventPublisher events) {
        this.roundRepository = roundRepository;
        this.predictionRepository = predictionRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    // Annotated here rather than on a helper: Spring's @Transactional only applies to calls
    // that come through the proxy, so a this.helper() call would run without a transaction.
    @Scheduled(cron = "${game.engine.cron:0 * * * * *}")
    @Transactional
    public void tick() {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ENGINE_LOCK_KEY))) {
            log.info("Round engine is running on another instance; skipping tick");
            return;
        }

        Instant now = clock.instant();
        Instant currentSlot = now.truncatedTo(ChronoUnit.MINUTES);
        Optional<Round> openRoundOpt = roundRepository.lockCurrentOpenRound();

        if (openRoundOpt.isEmpty()) {
            // Initial state: the very first round needs a starting number to predict against
            openRound(currentSlot, random.nextInt(100) + 1);
            return;
        }

        Round current = openRoundOpt.get();
        Instant rollAt = rollTime(current);
        if (now.isBefore(rollAt)) {
            log.debug("Round {} rolls at {}; nothing due", current.roundId(), rollAt);
            return;
        }

        if (now.isBefore(rollAt.plus(ROLL_GRACE))) {
            int roll = rollFor(current);
            if (finish(current, RoundStatus.SETTLED, roll, now)) {
                settle(current, roll);
                openRound(rollAt, roll);
            }
        } else {
            log.warn("Round {} missed its roll at {} (now {}); voiding and refunding stakes",
                    current.roundId(), rollAt, now);
            if (finish(current, RoundStatus.VOIDED, null, now)) {
                refundAll(current);
                // Nothing was rolled, so the next round predicts against the same number
                openRound(currentSlot, current.previousRoll());
            }
        }
    }

    private static Instant rollTime(Round round) {
        return round.startTime().truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(1));
    }

    private int rollFor(Round round) {
        if (round.seed() == null) {
            // Rounds opened before commit-reveal existed have no seed to roll from
            log.warn("Round {} has no committed seed; rolling without one", round.roundId());
            return random.nextInt(100) + 1;
        }
        return ProvablyFair.roll(round.seed());
    }

    private boolean finish(Round round, RoundStatus status, Integer roll, Instant now) {
        // The row lock should make this impossible, but never pay out on a round that has already left OPEN
        if (roundRepository.finish(round.roundId(), status.name(), roll, now) == 0) {
            log.warn("Round {} was already finished; skipping", round.roundId());
            return false;
        }
        return true;
    }

    private void settle(Round round, int roll) {
        Long roundId = round.roundId();
        int prevRoll = round.previousRoll();
        log.info("Settling round {} with roll {}", roundId, roll);

        List<Prediction> predictions = predictionRepository.findByRoundId(roundId);

        for (Prediction p : predictions) {
            if (roll == prevRoll) {
                // Push: Refund stake
                credit(p.userId(), p.stake(), roundId, LedgerEntry.LedgerType.REFUND);
            } else if ((p.prediction() == PredictionType.HIGHER && roll > prevRoll) ||
                       (p.prediction() == PredictionType.LOWER && roll < prevRoll)) {
                // Win: Calculate payout
                long payout = Odds.payout(prevRoll, p.prediction(), p.stake());
                credit(p.userId(), payout, roundId, LedgerEntry.LedgerType.PAYOUT);
            }
            // Loss: stake is already deducted, do nothing
        }
    }

    private void refundAll(Round round) {
        for (Prediction p : predictionRepository.findByRoundId(round.roundId())) {
            credit(p.userId(), p.stake(), round.roundId(), LedgerEntry.LedgerType.REFUND);
        }
    }

    private void openRound(Instant slot, int previousRoll) {
        String seed = ProvablyFair.newSeed(random);
        Round next = new Round(null, previousRoll, null, RoundStatus.OPEN, slot, null, clock.instant(),
                ProvablyFair.commit(seed), seed);
        Round saved = roundRepository.save(next);
        log.info("Round {} opened at {} against {}, seed commit {}",
                saved.roundId(), slot, previousRoll, saved.seedCommitHash());
        // Delivered to listeners only once this tick's transaction commits
        events.publishEvent(new RoundOpened(saved.roundId()));
    }

    private void credit(Long userId, long amount, Long roundId, LedgerEntry.LedgerType type) {
        walletRepository.addFunds(userId, amount);
        ledgerRepository.save(new LedgerEntry(null, userId, roundId, amount, type, clock.instant()));
    }
}
