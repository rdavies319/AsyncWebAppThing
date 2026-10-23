package com.rdavies.arena.board;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.bet.records.BetRecord;
import com.rdavies.arena.board.records.Board;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.SettledFight;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.WalletService;
import com.rdavies.arena.wallet.records.Wallet;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Builds the game page's board: anyone can watch, and a logged-in player also sees their own part. */
@Service
public class BoardService {
    private static final int RECENT_BETS = 5;

    private final FightService fightService;
    private final WalletService walletService;
    private final BetService betService;
    private final GameProperties game;
    private final boolean devProfile;

    public BoardService(FightService fightService, WalletService walletService, BetService betService,
                        GameProperties game, Environment environment) {
        this.fightService = fightService;
        this.walletService = walletService;
        this.betService = betService;
        this.game = game;
        this.devProfile = environment.matchesProfiles("dev");
    }

    /** The board as {@code viewer} sees it now; {@code viewer} is empty for an anonymous viewer. */
    public Board board(Optional<GamePrincipal> viewer) {
        CurrentFights current = fightService.current();
        Optional<SettledFight> lastSettled = current.live() == null ? fightService.lastSettled() : Optional.empty();
        Board.Next next = current.announced() == null ? null : next(current.announced(), current.serverTime());
        return new Board(current.serverTime(),
                current.live() == null ? null : live(current.live()),
                lastSettled.map(BoardService::result).orElse(null), // between fights
                next,
                viewer.map(v -> playerBoard(v, current, lastSettled, next)).orElse(null),
                devProfile && viewer.isPresent());
    }

    private static Board.Live live(LiveFight fight) {
        String red = fight.red().name();
        String blue = fight.blue().name();
        return new Board.Live(fight.fightId(), corner(fight.red()), corner(fight.blue()), fight.nextRoundAt(),
                fight.rounds().reversed().stream().map(e -> Wording.logLine(e, red, blue)).toList());
    }

    private static Board.Corner corner(LiveFight.Fighter fighter) {
        return new Board.Corner(fighter.name(), fighter.epithet(), fighter.startingHp(), fighter.currentHp());
    }

    private static Board.Result result(SettledFight fight) {
        return new Board.Result(fight.fightId(), Wording.result(fight.red().name(), fight.blue().name(),
                fight.winner(), fight.endedBy(), fight.roundsDone()));
    }

    private static Board.Next next(AnnouncedFight fight, Instant now) {
        return new Board.Next(fight.fightId(), contender(fight.red()), contender(fight.blue()), fight.startsAt(),
                fight.cutoffAt(), now.isBefore(fight.cutoffAt()));
    }

    private static Board.Contender contender(AnnouncedFight.Fighter fighter) {
        return new Board.Contender(fighter.name(), fighter.epithet(), fighter.elo(), fighter.multiplierBps(), fighter.maxStake());
    }

    private Board.PlayerBoard playerBoard(GamePrincipal viewer, CurrentFights current,
                                          Optional<SettledFight> lastSettled, Board.Next next) {
        Wallet wallet = walletService.getWallet(viewer.id());
        Long topUpTarget = walletService.canTopUp(wallet) ? game.topUpTarget() : null;

        Board.BetForm betForm = null;
        String nextBet = null;
        if (next != null) {
            AnnouncedFight announced = current.announced();
            Optional<Bet> bet = betService.bet(viewer.id(), next.fightId());
            nextBet = bet.map(b -> pendingBet(b, announced.fighter(b.corner()).name())).orElse(null);
            if (bet.isEmpty() && next.bettingOpen()) {
                betForm = new Board.BetForm(next.fightId(),
                        affordable(wallet.balance(), announced.red()), affordable(wallet.balance(), announced.blue()));
            }
        }

        String fightBet = null;
        if (current.live() != null) {
            LiveFight live = current.live();
            fightBet = betService.bet(viewer.id(), live.fightId())
                    .map(b -> pendingBet(b, (b.corner() == Corner.RED ? live.red() : live.blue()).name()))
                    .orElse(null);
        } else if (lastSettled.isPresent()) {
            SettledFight settled = lastSettled.get();
            fightBet = betService.bet(viewer.id(), settled.fightId())
                    .map(b -> settledBet(b, (b.corner() == Corner.RED ? settled.red() : settled.blue()).name()))
                    .orElse(null);
        }
        List<Board.PastBet> recentBets = betService.history(viewer.id(), Limit.of(RECENT_BETS)).stream()
                .map(bet -> new Board.PastBet(bet.fightId(), pastBet(bet)))
                .toList();
        return new Board.PlayerBoard(viewer.displayName(), wallet.balance(), topUpTarget, betForm, nextBet, fightBet,
                recentBets);
    }

    /** The stake options this balance covers, up to the corner's max stake. */
    private List<Long> affordable(long balance, AnnouncedFight.Fighter fighter) {
        long most = Math.min(balance, fighter.maxStake());
        return game.stakeOptions().stream().filter(stake -> stake <= most).toList();
    }

    /** "You won 196 on Vex", "You lost 100 on Vex" or "Draw: your 100 on Vex is refunded" */
    private static String settledBet(Bet bet, String gladiator) {
        return switch (bet.status()) {
            case WON -> "You won " + bet.payout() + " on " + gladiator;
            case LOST -> "You lost " + bet.stake() + " on " + gladiator;
            case PUSH -> "Draw: your " + bet.stake() + " on " + gladiator + " is refunded";
            case PENDING -> throw new IllegalStateException("Bet " + bet.id() + " on a settled fight is still pending");
        };
    }

    /** "Brutus v Vex: 100 on Brutus at 1.96x · won 196", then lost, refunded 100 or pending. */
    private static String pastBet(BetRecord bet) {
        String outcome = switch (bet.status()) {
            case PENDING -> "pending";
            case WON -> "won " + bet.payout();
            case LOST -> "lost";
            case PUSH -> "refunded " + bet.payout();
        };
        return bet.red() + " v " + bet.blue() + ": " + bet.stake() + " on " + bet.backed() + " at "
                + Board.odds(bet.multiplierBps()) + " · " + outcome;
    }

    /** "Your bet: 100 on Vex at 1.96x" */
    private static String pendingBet(Bet bet, String gladiator) {
        return "Your bet: " + bet.stake() + " on " + gladiator + " at " + Board.odds(bet.multiplierBps());
    }
}
