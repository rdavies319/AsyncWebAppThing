package com.rdavies.arena.bet;

import com.rdavies.arena.bet.records.Bet;
import org.springframework.data.domain.Limit;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Package-private: bets change only through the bet feature's services. */
interface BetRepository extends CrudRepository<Bet, Long> {

    boolean existsByUserIdAndFightId(long userId, long fightId);

    Optional<Bet> findByUserIdAndFightId(long userId, long fightId);

    List<Bet> findByFightIdOrderById(long fightId);

    List<Bet> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Limit limit);

    /**
     * Settles every pending bet on the fight in one statement (spec section 5, Rules). A win pays
     * floor(stake × multiplier_bps / 10000), stake included, at most {@code maxPayout}; a draw
     * refunds the stake (push); a loss pays 0, the stake having been taken when the bet was placed.
     *
     * @param winner the fight's Winner name: RED, BLUE or DRAW
     * @return how many bets were settled
     */
    @Modifying
    @Query("""
            UPDATE bets SET
                status = CASE WHEN :winner = 'DRAW' THEN 'PUSH' WHEN corner = :winner THEN 'WON' ELSE 'LOST' END,
                payout = CASE WHEN :winner = 'DRAW' THEN stake
                              WHEN corner = :winner THEN LEAST(stake * multiplier_bps / 10000, :maxPayout)
                              ELSE 0 END,
                settled_at = :now
            WHERE fight_id = :fightId AND status = 'PENDING'""")
    int settleFight(@Param("fightId") long fightId, @Param("winner") String winner,
                    @Param("maxPayout") long maxPayout, @Param("now") Instant now);
}
