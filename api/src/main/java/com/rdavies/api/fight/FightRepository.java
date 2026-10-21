package com.rdavies.api.fight;

import com.rdavies.api.fight.records.Fight;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

/** Package-private: fight rows carry the seed, so only the fight feature reads them. */
interface FightRepository extends CrudRepository<Fight, Long> {

    /** Row-locks the live fight until the engine's transaction ends. */
    @Query("SELECT * FROM fights WHERE status = 'LIVE' FOR UPDATE")
    Optional<Fight> lockLive();

    /** Row-locks the announced fight until the engine's transaction ends. */
    @Query("SELECT * FROM fights WHERE status = 'ANNOUNCED' FOR UPDATE")
    Optional<Fight> lockAnnounced();

    @Query("SELECT * FROM fights WHERE status = 'LIVE'")
    Optional<Fight> findLive();

    @Query("SELECT * FROM fights WHERE status = 'ANNOUNCED'")
    Optional<Fight> findAnnounced();
}
