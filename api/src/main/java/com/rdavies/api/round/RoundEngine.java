package com.rdavies.api.round;

import com.rdavies.api.prediction.PredictionRepository;
import com.rdavies.api.prediction.PredictionSettled;
import com.rdavies.api.wallet.LedgerRepository;
import com.rdavies.api.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rolls and settles one round per minute (spec section 6). Round N opens on its minute with its roll
 * already made; round N+1 waits as SCHEDULED with its seed committed, so players can see the
 * commitment for the roll they are predicting. At round N's starts_at + 1 minute, the tick rolls
 * N+1 from its committed seed, settles N against that roll, opens N+1 and schedules N+2.
 *
 * <p>Safe to run on several instances and to trigger more than once per minute:
 * <ul>
 *   <li>a transaction-scoped Postgres advisory lock makes every other instance skip the tick;</li>
 *   <li>the open and scheduled rounds are row-locked, and nothing is due until a minute has passed,
 *       so a repeat tick does nothing;</li>
 *   <li>a round moves OPEN to SETTLED with a conditional UPDATE before any points move;</li>
 *   <li>the schema allows one OPEN and one SCHEDULED round, and one round per starts_at.</li>
 * </ul>
 *
 * <p>Catch-up: after downtime the same tick settles the pending round with the next round's
 * already-committed seed (still fair: it was fixed before anyone bet), and the next round takes the
 * current minute slot. Missed minutes get no rounds; the gap is logged as a warning.
 */
@Service
public class RoundEngine {
    private static final Logger log = LoggerFactory.getLogger(RoundEngine.class);

    private static final Duration ROUND_LENGTH = Duration.ofMinutes(1);
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

    /** Catch up straight away rather than waiting for the next minute. Off when the schedule is ("-"). */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup(ApplicationReadyEvent event) {
        if (!"-".equals(event.getApplicationContext().getEnvironment().getProperty("game.engine.cron"))) {
            event.getApplicationContext().getBean(RoundEngine.class).tick();
        }
    }

    // Annotated here rather than on a helper: Spring's @Transactional only applies to calls
    // that come through the proxy, so a this.helper() call would run without a transaction.
    @Scheduled(cron = "${game.engine.cron:0 * * * * *}", zone = "UTC")
    @Transactional
    public void tick() {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ENGINE_LOCK_KEY))) {
            log.info("Round engine is running on another instance; skipping tick");
            return;
        }

        Instant now = clock.instant();
        Instant currentSlot = now.truncatedTo(ChronoUnit.MINUTES);
        var openRound = roundRepository.lockOpenRound();
        if (openRound.isEmpty()) {
            bootstrap(currentSlot);
            return;
        }

        Round current = openRound.get();
        Instant settleAt = current.startsAt().plus(ROUND_LENGTH);
        if (now.isBefore(settleAt)) {
            log.debug("Round {} settles at {}; nothing due", current.id(), settleAt);
            return;
        }

        Round next = roundRepository.lockScheduledRound().orElseThrow(() -> new IllegalStateException(
                "Round " + current.id() + " is open but no round is scheduled after it"));
        int roll = ProvablyFair.roll(next.seed(), next.id());

        // Never pay out on a round that has already left OPEN (the row lock should make this impossible)
        if (roundRepository.markSettled(current.id(), now) == 0) {
            log.warn("Round {} was already settled; skipping", current.id());
            return;
        }
        int settled = predictionRepository.settleRound(current.id(), current.rollValue(), roll, now);
        walletRepository.creditRoundPayouts(current.id(), now);
        ledgerRepository.recordRoundPayouts(current.id(), now);
        log.info("Round {} settled: {} -> {}, {} predictions", current.id(), current.rollValue(), roll, settled);
        // Delivered to listeners only once this tick's transaction commits
        events.publishEvent(new RoundSettled(current.id(), next.id(), roll, next.seed()));
        events.publishEvent(new PredictionSettled.Batch(jdbc.query("""
                SELECT p.user_id, p.id AS prediction_id, p.round_id, p.status, p.payout, w.balance
                FROM predictions p JOIN wallets w ON w.user_id = p.user_id
                WHERE p.round_id = ?""", DataClassRowMapper.newInstance(PredictionSettled.class), current.id())));

        Instant nextSlot = next.startsAt();
        if (currentSlot.isAfter(nextSlot)) {
            log.warn("Engine missed {} minute(s): round {} was due at {}; opening it at {} instead",
                    Duration.between(nextSlot, currentSlot).toMinutes(), next.id(), nextSlot, currentSlot);
            nextSlot = currentSlot;
        }
        open(next, roll, nextSlot);
        schedule(nextSlot.plus(ROUND_LENGTH));
    }

    /** First ever tick: round 1 opens on the current minute, already rolled, with round 2 scheduled. */
    private void bootstrap(Instant slot) {
        Round first = schedule(slot);
        open(first, ProvablyFair.roll(first.seed(), first.id()), slot);
        schedule(slot.plus(ROUND_LENGTH));
    }

    private void open(Round round, int roll, Instant slot) {
        if (roundRepository.open(round.id(), roll, slot) != 1) {
            throw new IllegalStateException("Round " + round.id() + " was not scheduled");
        }
        log.info("Round {} opened at {} with roll {}", round.id(), slot, roll);
        // Delivered to listeners only once this tick's transaction commits
        events.publishEvent(new RoundOpened(round.id()));
    }

    private Round schedule(Instant slot) {
        String seed = ProvablyFair.newSeed(random);
        Round saved = roundRepository.save(new Round(null, slot, null, ProvablyFair.commit(seed), seed,
                RoundStatus.SCHEDULED, null));
        log.info("Round {} scheduled for {}, seed commit {}", saved.id(), slot, saved.seedCommitHash());
        return saved;
    }
}
