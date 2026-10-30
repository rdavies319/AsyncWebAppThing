package com.rdavies.arena;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Game settings, overridable as game.* properties.
 *
 * @param cutoff       betting on a fight closes this long before it starts
 * @param maxPayout    most one bet can pay out, stake included
 * @param signupBonus  points credited to a new wallet
 * @param topUpTarget  the daily top-up refills a wallet to this balance
 * @param stakeOptions the only stakes a bet may have, ascending: the game page's buttons, and all the API accepts
 * @param fightCron        when fights start, as a Spring cron expression in UTC (spec section 4)
 * @param roundInterval the time between a fight's rounds; round n is due at starts_at + n × this
 * @param maxRounds     the round cap, after which a fight is decided on remaining HP share
 * @param rosterFile       where the roster loads from on first boot, as a Spring resource location
 * @param engine           when the fight engine ticks
 */
@ConfigurationProperties("game")
public record GameProperties(
    @DefaultValue("10s") Duration cutoff,
    @DefaultValue("10000") long maxPayout,
    @DefaultValue("1000") long signupBonus,
    @DefaultValue("1000") long topUpTarget,
    @DefaultValue({"10", "100", "500"}) List<Long> stakeOptions,
    @DefaultValue("0 0 * * * *") String fightCron,
    @DefaultValue("1m") Duration roundInterval,
    @DefaultValue("30") int maxRounds,
    @DefaultValue("classpath:roster-dev.json") String rosterFile,
    @DefaultValue Engine engine
) {
    /**
     * @param cron when the engine ticks, as a Spring cron expression; "-" turns the schedule off,
     *             along with the tick at startup, so tests drive every tick themselves
     */
    public record Engine(@DefaultValue("0 * * * * *") String cron) {
        public boolean scheduled() {
            return !"-".equals(cron);
        }
    }

    /**
     * Checked at startup, so the stake buttons show in order, and a capped fight always settles before
     * the next one's slot (spec section 7).
     */
    public GameProperties {
        if (stakeOptions.isEmpty()) {
            throw new IllegalArgumentException("game.stake-options must list at least one stake");
        }
        for (int i = 0; i < stakeOptions.size(); i++) {
            if (stakeOptions.get(i) <= (i == 0 ? 0 : stakeOptions.get(i - 1))) {
                throw new IllegalArgumentException("game.stake-options must be positive and ascending: " + stakeOptions);
            }
        }
        stakeOptions = List.copyOf(stakeOptions);
        Duration cappedFight = roundInterval.multipliedBy(maxRounds);
        Duration gap = shortestGap(CronExpression.parse(fightCron));
        if (cappedFight.compareTo(gap) >= 0) {
            throw new IllegalArgumentException("game.max-rounds × game.round-interval (" + cappedFight
                    + ") must be shorter than the gap between two game.fight-cron slots (" + gap + ")");
        }
    }

    /** How many consecutive fight slots {@link #shortestGap} compares: plenty for any real schedule. */
    private static final int SLOTS_CHECKED = 10_000;

    /**
     * The shortest time between two consecutive slots of {@code cron}, over a leap year's worth of
     * slots or {@link #SLOTS_CHECKED} of them, whichever comes first. A schedule's gaps can vary
     * (09:00 and 09:20, then the next morning), and a capped fight must fit in the shortest.
     */
    private static Duration shortestGap(CronExpression cron) {
        ZonedDateTime start = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime end = start.plusYears(1);
        ZonedDateTime slot = cron.next(start);
        Duration shortest = null;
        for (int i = 0; slot != null && slot.isBefore(end) && i < SLOTS_CHECKED; i++) {
            ZonedDateTime next = cron.next(slot);
            if (next == null) {
                break;
            }
            Duration gap = Duration.between(slot, next);
            shortest = shortest == null || gap.compareTo(shortest) < 0 ? gap : shortest;
            slot = next;
        }
        if (shortest == null) {
            throw new IllegalArgumentException("game.fight-cron (" + cron + ") must have at least two slots a year");
        }
        return shortest;
    }
}
