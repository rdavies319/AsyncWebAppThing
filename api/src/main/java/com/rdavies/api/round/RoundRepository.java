package com.rdavies.api.round;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RoundRepository extends CrudRepository<Round, Long> {
    @Query("SELECT * FROM rounds WHERE status = 'OPEN' ORDER BY round_id DESC LIMIT 1")
    Optional<Round> findCurrentOpenRound();

    /** Same as {@link #findCurrentOpenRound()} but row-locks it until the caller's transaction ends. */
    @Query("SELECT * FROM rounds WHERE status = 'OPEN' ORDER BY round_id DESC LIMIT 1 FOR UPDATE")
    Optional<Round> lockCurrentOpenRound();

    @Query("SELECT * FROM rounds WHERE status <> 'OPEN' ORDER BY round_id DESC LIMIT 1")
    Optional<Round> findLastFinishedRound();

    /**
     * Moves an OPEN round to a final status. Returns 0 if the round was not OPEN any more,
     * in which case the caller must not pay or refund anything.
     */
    @Modifying
    @Query("UPDATE rounds SET status = :status, current_roll = :roll, end_time = :end "
            + "WHERE round_id = :id AND status = 'OPEN'")
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("roll") Integer roll, @Param("end") Instant end);
}
