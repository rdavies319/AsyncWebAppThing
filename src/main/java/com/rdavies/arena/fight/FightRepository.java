package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.FightStatus;
import com.rdavies.arena.fight.records.Fight;
import com.rdavies.arena.fight.records.LastFought;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Package-private: fight rows carry the seed, so only the fight feature reads them. */
interface FightRepository extends CrudRepository<Fight, Long> {

    /** Takes the arena's advisory lock until the transaction ends, or returns false if another tick holds it. */
    @Query("SELECT pg_try_advisory_xact_lock(:key)")
    boolean tryLock(@Param("key") long key);

    /** At most one fight is announced and one live at a time. */
    Optional<Fight> findByStatus(FightStatus status);

    /** As {@link #findByStatus}, row-locking the fight until the transaction ends. */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Fight> findLockedByStatus(FightStatus status);

    Optional<Fight> findFirstByStatusOrderByStartsAtDesc(FightStatus status);

    /** Row-locks the live fight until the engine's transaction ends. */
    default Optional<Fight> lockLive() {
        return findLockedByStatus(FightStatus.LIVE);
    }

    /** Row-locks the announced fight until the engine's transaction ends. */
    default Optional<Fight> lockAnnounced() {
        return findLockedByStatus(FightStatus.ANNOUNCED);
    }

    default Optional<Fight> findLive() {
        return findByStatus(FightStatus.LIVE);
    }

    default Optional<Fight> findAnnounced() {
        return findByStatus(FightStatus.ANNOUNCED);
    }

    default Optional<Fight> findLastSettled() {
        return findFirstByStatusOrderByStartsAtDesc(FightStatus.SETTLED);
    }

    /** Dev forcing: moves the announced fight's start to {@code now}, if it is later. */
    @Modifying
    @Query("UPDATE fights SET starts_at = :now WHERE status = 'ANNOUNCED' AND starts_at > :now")
    void pullStartForward(@Param("now") Instant now);

    /** Dev forcing: moves the live fight's next round to {@code now}, if it is later. */
    @Modifying
    @Query("UPDATE fights SET next_round_at = :now WHERE status = 'LIVE' AND next_round_at > :now")
    void pullRoundForward(@Param("now") Instant now);

    /** Reserves the next fight's id, which its seed's tie-break rolls need before the row exists. */
    @Query("SELECT nextval(pg_get_serial_sequence('fights', 'id'))")
    long nextId();

    /** Inserts an announced fight under an id from {@link #nextId()}; save() would treat a set id as an update. */
    @Modifying
    @Query("""
            INSERT INTO fights (id, starts_at, status, red_id, blue_id, red_elo, blue_elo, seed, red_hp, blue_hp)
            VALUES (:id, :startsAt, 'ANNOUNCED', :redId, :blueId, :redElo, :blueElo, :seed, :redHp, :blueHp)""")
    void insertAnnounced(@Param("id") long id, @Param("startsAt") Instant startsAt,
                         @Param("redId") long redId, @Param("blueId") long blueId,
                         @Param("redElo") int redElo, @Param("blueElo") int blueElo, @Param("seed") byte[] seed,
                         @Param("redHp") int redHp, @Param("blueHp") int blueHp);

    /** Each gladiator's most recent fight start, in any status; a gladiator who never fought has no row. */
    @Query(value = """
            SELECT gladiator_id, MAX(starts_at) AS starts_at FROM (
                SELECT red_id AS gladiator_id, starts_at FROM fights
                UNION ALL
                SELECT blue_id, starts_at FROM fights) AS appearances
            GROUP BY gladiator_id""", rowMapperClass = LastFoughtRowMapper.class)
    List<LastFought> findLastFought();

    class LastFoughtRowMapper implements RowMapper<LastFought> {
        @Override
        public LastFought mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new LastFought(rs.getLong("gladiator_id"), rs.getTimestamp("starts_at").toInstant());
        }
    }
}
