package com.rdavies.arena.bet;

import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.bet.records.BetPosition;
import com.rdavies.arena.bet.records.BetRecord;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.FightRecord;
import com.rdavies.arena.wallet.WalletService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Bets on the announced fight (spec section 5): one final bet per player per fight, at the odds of the moment. */
@Service
public class BetService {
    private static final String ALREADY_BET = "You have already bet on this fight";

    private final BetRepository bets;
    private final FightService fights;
    private final WalletService wallets;
    private final Clock clock;

    BetService(BetRepository bets, FightService fights, WalletService wallets, Clock clock) {
        this.bets = bets;
        this.fights = fights;
        this.wallets = wallets;
        this.clock = clock;
    }

    /** The player's bet on the fight, if they placed one. */
    public Optional<Bet> bet(long userId, long fightId) {
        return bets.findByUserIdAndFightId(userId, fightId);
    }

    /**
     * What the player can do on {@code fight}, as it stood when it was read: their bet if they placed
     * one, else the stakes open to them on each corner while betting is open, else nothing.
     */
    @Transactional(readOnly = true)
    public BetPosition position(long userId, AnnouncedFight fight) {
        Optional<Bet> bet = bet(userId, fight.fightId());
        if (bet.isPresent()) {
            return new BetPosition.Placed(bet.get());
        }
        if (!fight.bettingOpen()) {
            return new BetPosition.Closed();
        }
        long balance = wallets.getWallet(userId).balance();
        return new BetPosition.Open(affordable(fight.red(), balance), affordable(fight.blue(), balance));
    }

    private static List<Long> affordable(AnnouncedFight.Fighter fighter, long balance) {
        return fighter.stakes().stream().filter(stake -> stake <= balance).toList();
    }

    /** The player's bets, newest first, each with its fight's matchup; all of them if {@code limit} is unlimited. */
    public List<BetRecord> history(long userId, Limit limit) {
        List<Bet> mine = bets.findByUserIdOrderByCreatedAtDescIdDesc(userId, limit);
        Map<Long, FightRecord> fightsById = fights.records(mine.stream().map(Bet::fightId).toList());
        return mine.stream().map(bet -> {
            FightRecord fight = fightsById.get(bet.fightId());
            return new BetRecord(bet.id(), bet.fightId(), fight.startsAt(), fight.red().name(), fight.blue().name(),
                    bet.corner(), bet.stake(), bet.multiplierBps(), bet.status(), bet.payout(), bet.createdAt());
        }).toList();
    }

    /**
     * Places a bet on {@code corner} of the announced fight, copying that corner's multiplier onto
     * it and taking the stake, with its ledger row. It accepts exactly what the player's
     * {@link #position} offers: one of the corner's stakes that their balance covers, with no bet
     * placed yet. The debit still checks the balance atomically, since it can change in between.
     */
    @Transactional
    public Bet place(long userId, Long fightId, Corner corner, Long stake) {
        if (fightId == null) {
            throw GameRuleException.invalid("Choose a fight");
        }
        if (corner == null) {
            throw GameRuleException.invalid("Choose a corner");
        }
        if (stake == null) {
            throw GameRuleException.invalid("Enter a stake");
        }
        AnnouncedFight fight = fights.lockForBetting(fightId);
        Instant now = clock.instant();
        AnnouncedFight.Fighter fighter = fight.fighter(corner);
        BetPosition.Open open = switch (position(userId, fight)) {
            case BetPosition.Placed placed -> throw GameRuleException.conflict(ALREADY_BET);
            case BetPosition.Closed closed -> throw new IllegalStateException("lockForBetting returned a closed fight");
            case BetPosition.Open o -> o;
        };
        if (!fighter.stakes().contains(stake)) {
            throw GameRuleException.invalid("Stake must be one of " + fighter.stakes().stream()
                    .map(String::valueOf).collect(Collectors.joining(", ")) + " pts on " + fighter.name());
        }
        if (!open.stakes(corner).contains(stake)) {
            throw GameRuleException.conflict(WalletService.INSUFFICIENT);
        }
        Bet bet;
        try {
            bet = bets.save(new Bet(null, userId, fightId, corner, stake,
                    fighter.multiplierBps(), BetStatus.PENDING, null, now, null));
        } catch (DuplicateKeyException e) { // a concurrent bet slipped past the position
            throw GameRuleException.conflict(ALREADY_BET, e);
        }
        wallets.debitStake(userId, stake, bet.id(), now);
        return bet;
    }
}
