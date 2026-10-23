package com.rdavies.arena.board;

import com.rdavies.arena.GameProperties;
import com.rdavies.arena.bet.BetService;
import com.rdavies.arena.bet.records.Bet;
import com.rdavies.arena.board.records.Board;
import com.rdavies.arena.fight.FightService;
import com.rdavies.arena.fight.enums.Corner;
import com.rdavies.arena.fight.enums.Winner;
import com.rdavies.arena.fight.records.AnnouncedFight;
import com.rdavies.arena.fight.records.CurrentFights;
import com.rdavies.arena.fight.records.RoundView;
import com.rdavies.arena.fight.records.LiveFight;
import com.rdavies.arena.fight.records.SettledFight;
import com.rdavies.arena.user.records.GamePrincipal;
import com.rdavies.arena.wallet.WalletService;
import com.rdavies.arena.wallet.records.Wallet;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Builds the game page's board: anyone can watch, and a logged-in player also sees their own part. */
@Service
public class BoardService {
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
                fight.rounds().reversed().stream().map(e -> logLine(e, red, blue)).toList());
    }

    private static Board.Corner corner(LiveFight.Fighter fighter) {
        return new Board.Corner(fighter.name(), fighter.epithet(), fighter.startingHp(), fighter.currentHp());
    }

    /** "2. Brutus hits Vex for 14 · Brutus dodges": red's attack on blue, then blue's on red. */
    private static String logLine(RoundView e, String red, String blue) {
        String redAttack = e.redHit() ? red + " hits " + blue + " for " + e.redDamage() : blue + " dodges";
        String blueAttack = e.blueHit() ? blue + " hits " + red + " for " + e.blueDamage() : red + " dodges";
        return e.n() + ". " + redAttack + " · " + blueAttack;
    }

    private static Board.Result result(SettledFight fight) {
        String red = fight.red().name();
        String blue = fight.blue().name();
        String summary = switch (fight.endedBy()) {
            case DOUBLE_KO -> "Draw: " + red + " and " + blue + " knocked each other out";
            case KO -> winner(fight, red, blue) + " by KO";
            case DECISION -> fight.winner() == Winner.DRAW
                    ? "Draw: level on HP after " + fight.roundsDone() + " rounds"
                    : winner(fight, red, blue) + " on HP after " + fight.roundsDone() + " rounds";
        };
        return new Board.Result(fight.fightId(), summary);
    }

    /** "Brutus beat Vex", for a fight with a winner. */
    private static String winner(SettledFight fight, String red, String blue) {
        return fight.winner() == Winner.RED ? red + " beat " + blue : blue + " beat " + red;
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
        }
        return new Board.PlayerBoard(viewer.displayName(), wallet.balance(), topUpTarget, betForm, nextBet, fightBet);
    }

    /** The stake options this balance covers, up to the corner's max stake. */
    private List<Long> affordable(long balance, AnnouncedFight.Fighter fighter) {
        long most = Math.min(balance, fighter.maxStake());
        return game.stakeOptions().stream().filter(stake -> stake <= most).toList();
    }

    /** "Your bet: 100 on Vex at 1.96x" */
    private static String pendingBet(Bet bet, String gladiator) {
        return "Your bet: " + bet.stake() + " on " + gladiator + " at " + Board.odds(bet.multiplierBps());
    }
}
