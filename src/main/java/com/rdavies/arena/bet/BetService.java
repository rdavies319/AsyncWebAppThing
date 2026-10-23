package com.rdavies.arena.bet;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.GameRuleException;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.wallet.WalletService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Bets on the announced fight (spec section 5): one final bet per player per fight, at the odds of the moment. */
@Service
public class BetService {
    private static final String CLOSED = "Betting on this fight has closed";
    private static final String ALREADY_BET = "You have already bet on this fight";

    private final BetRepository bets;
    private final FightService fights;
    private final WalletService wallets;
    private final GameProperties game;
    private final Clock clock;

    BetService(BetRepository bets, FightService fights, WalletService wallets, GameProperties game, Clock clock) {
        this.bets = bets;
        this.fights = fights;
        this.wallets = wallets;
        this.game = game;
        this.clock = clock;
    }

    /** The player's bet on the fight, if they placed one. */
    public Optional<Bet> bet(long userId, long fightId) {
        return bets.findByUserIdAndFightId(userId, fightId);
    }

    /**
     * Places a bet on {@code corner} of the announced fight, copying that corner's multiplier onto
     * it and taking the stake, with its ledger row. The stake must be within game.min-stake and the
     * corner's max stake, which the payout cap may lower below game.max-stake.
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
        Instant now = clock.instant();
        AnnouncedFight fight = fights.lockForBetting(fightId)
                .filter(f -> now.isBefore(f.cutoffAt()))
                .orElseThrow(() -> GameRuleException.conflict(CLOSED));
        AnnouncedFight.Fighter fighter = fight.fighter(corner);
        if (stake < game.minStake() || stake > fighter.maxStake()) {
            throw GameRuleException.invalid("Stake must be between " + game.minStake() + " and "
                    + fighter.maxStake() + " pts on " + fighter.name());
        }
        if (bets.existsByUserIdAndFightId(userId, fightId)) {
            throw GameRuleException.conflict(ALREADY_BET);
        }
        wallets.requireBalance(userId, stake);
        Bet bet;
        try {
            bet = bets.save(new Bet(null, userId, fightId, corner, stake,
                    fighter.multiplierBps(), BetStatus.PENDING, null, now, null));
        } catch (DuplicateKeyException e) { // a concurrent bet slipped past the check above
            throw GameRuleException.conflict(ALREADY_BET, e);
        }
        wallets.debitStake(userId, stake, bet.id(), now);
        return bet;
    }
}
