package com.rdavies.arena.bet;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.bet.enums.BetStatus;
import com.rdavies.arena.bet.events.BetSettled;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.fight.events.FightSettled;
import com.rdavies.arena.wallet.WalletService;
import com.rdavies.arena.wallet.records.Credit;
import com.rdavies.arena.wallet.records.LedgerEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Settles a fight's bets in the engine's settle step (spec section 7, step 4). A plain
 * {@code @EventListener}, so it runs inside the tick's transaction as the engine publishes
 * FightSettled: the bets, credits and fight result commit together, and the fight feature never
 * calls the bet feature. Set-based, so 1,000 bets settle in a few statements.
 */
@Component
class BetSettlement {
    private final BetRepository bets;
    private final WalletService wallets;
    private final ApplicationEventPublisher events;
    private final GameProperties game;
    private final Clock clock;

    BetSettlement(BetRepository bets, WalletService wallets, ApplicationEventPublisher events,
                  GameProperties game, Clock clock) {
        this.bets = bets;
        this.wallets = wallets;
        this.events = events;
        this.game = game;
        this.clock = clock;
    }

    /**
     * Marks every pending bet won, lost or push, pays wins and refunds draws through the wallet's
     * batch credit, and publishes each bet's BetSettled, delivered after commit. A fight with
     * nothing pending changes nothing, so a repeat never pays twice.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(FightSettled fight) {
        Instant now = clock.instant();
        if (bets.settleFight(fight.fightId(), fight.winner().name(), game.maxPayout(), now) == 0) {
            return;
        }
        List<Bet> settled = bets.findByFightIdOrderById(fight.fightId());
        wallets.creditAll(settled.stream()
                .filter(bet -> bet.payout() > 0)
                .map(bet -> new Credit(bet.userId(), bet.payout(),
                        bet.status() == BetStatus.WON ? LedgerEntry.Reason.PAYOUT : LedgerEntry.Reason.REFUND, bet.id()))
                .toList());
        Map<Long, Long> balances = wallets.balances(settled.stream().map(Bet::userId).toList());
        for (Bet bet : settled) {
            events.publishEvent(new BetSettled(bet.userId(), bet.id(), bet.fightId(), bet.status(), bet.payout(),
                    balances.get(bet.userId())));
        }
    }
}
