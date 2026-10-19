package com.rdavies.api.round;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RoundRepository extends CrudRepository<Round, Long> {
    @Query("SELECT * FROM rounds WHERE status = 'OPEN'")
    Optional<Round> findOpenRound();

    @Query("SELECT * FROM rounds WHERE status = 'SCHEDULED'")
    Optional<Round> findScheduledRound();

    /** Row-locks the open round until the caller's transaction ends. */
    @Query("SELECT * FROM rounds WHERE status = 'OPEN' FOR UPDATE")
    Optional<Round> lockOpenRound();

    @Query("SELECT * FROM rounds WHERE status = 'SCHEDULED' FOR UPDATE")
    Optional<Round> lockScheduledRound();

    /** Shares the row lock with other predictions, but blocks the engine from settling meanwhile. */
    @Query("SELECT * FROM rounds WHERE id = :id FOR SHARE")
    Optional<Round> lockForPrediction(@Param("id") Long id);

    @Query("SELECT * FROM rounds WHERE status = 'SETTLED' ORDER BY id DESC LIMIT 1")
    Optional<Round> findLastSettledRound();

    /** The round after this one, whose roll this round's predictions settle against. */
    @Query("SELECT * FROM rounds WHERE id > :id ORDER BY id LIMIT 1")
    Optional<Round> findRoundAfter(@Param("id") Long id);

    /** SCHEDULED to OPEN with its roll. Returns 0 if the round was not SCHEDULED any more. */
    @Modifying
    @Query("UPDATE rounds SET status = 'OPEN', roll_value = :roll, starts_at = :startsAt "
            + "WHERE id = :id AND status = 'SCHEDULED'")
    int open(@Param("id") Long id, @Param("roll") int roll, @Param("startsAt") Instant startsAt);

    /** OPEN to SETTLED. Returns 0 if the round was not OPEN any more, in which case nothing may be paid. */
    @Modifying
    @Query("UPDATE rounds SET status = 'SETTLED', settled_at = :now WHERE id = :id AND status = 'OPEN'")
    int markSettled(@Param("id") Long id, @Param("now") Instant now);
}
