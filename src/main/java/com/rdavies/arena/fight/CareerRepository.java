package com.rdavies.arena.fight;

import com.rdavies.arena.fight.enums.EndedBy;
import com.rdavies.arena.fight.enums.Result;
import com.rdavies.arena.fight.records.Career;
import com.rdavies.arena.fight.records.Fight;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * The public records of the gladiators (spec section 9), computed in SQL from the public columns of
 * fights and fight_rounds only: never the seed or the raw rolls. Settled fights only, so a fight
 * counts once its result is final. Package-private: CareerService puts names to the gladiator ids.
 */
interface CareerRepository extends Repository<Fight, Long> {

    /** Every settled fight twice, once from each gladiator's side ("me" against "opponent"). */
    String SIDES = """
            WITH sides AS (
                SELECT id, starts_at, ended_by, rounds_done, red_id AS me, blue_id AS opponent,
                       CASE winner WHEN 'RED' THEN 'WON' WHEN 'BLUE' THEN 'LOST' ELSE 'DREW' END AS result
                FROM fights WHERE status = 'SETTLED'
                UNION ALL
                SELECT id, starts_at, ended_by, rounds_done, blue_id, red_id,
                       CASE winner WHEN 'BLUE' THEN 'WON' WHEN 'RED' THEN 'LOST' ELSE 'DREW' END
                FROM fights WHERE status = 'SETTLED')
            """;

    /** Integer division throughout: the rates and averages round down. */
    @Query(value = SIDES + """
            , mine AS (SELECT * FROM sides WHERE me = :id),
            my_rounds AS (
                SELECT CASE WHEN f.red_id = :id THEN r.red_damage ELSE r.blue_damage END AS dealt,
                       CASE WHEN f.red_id = :id THEN r.blue_damage ELSE r.red_damage END AS taken,
                       CASE WHEN f.red_id = :id THEN NOT r.blue_hit ELSE NOT r.red_hit END AS dodged
                FROM fight_rounds r JOIN fights f ON f.id = r.fight_id
                WHERE f.status = 'SETTLED' AND :id IN (f.red_id, f.blue_id))
            SELECT
                (SELECT COUNT(*) FROM mine) AS fights,
                (SELECT COUNT(*) FROM mine WHERE result = 'WON') AS wins,
                (SELECT COUNT(*) FROM mine WHERE result = 'LOST') AS losses,
                (SELECT COUNT(*) FROM mine WHERE result = 'DREW') AS draws,
                (SELECT COUNT(*) FILTER (WHERE result = 'WON') * 10000 / NULLIF(COUNT(*), 0) FROM mine) AS win_rate_bps,
                (SELECT COUNT(*) FILTER (WHERE result = 'WON' AND ended_by = 'KO') * 10000
                        / NULLIF(COUNT(*) FILTER (WHERE result = 'WON'), 0) FROM mine) AS ko_rate_bps,
                (SELECT SUM(rounds_done) * 100 / NULLIF(COUNT(*), 0) FROM mine) AS avg_rounds_x100,
                (SELECT COUNT(*) FROM my_rounds) AS rounds_fought,
                (SELECT SUM(dealt) * 100 / NULLIF(COUNT(*), 0) FROM my_rounds) AS avg_damage_dealt_x100,
                (SELECT SUM(taken) * 100 / NULLIF(COUNT(*), 0) FROM my_rounds) AS avg_damage_taken_x100,
                (SELECT COUNT(*) FILTER (WHERE dodged) * 10000 / NULLIF(COUNT(*), 0) FROM my_rounds) AS dodge_rate_bps
            """, rowMapperClass = CareerRowMapper.class)
    Career findCareer(@Param("id") long gladiatorId);

    /** One row per opponent this gladiator has met, by opponent id. */
    @Query(value = SIDES + """
            SELECT opponent,
                   COUNT(*) FILTER (WHERE result = 'WON') AS wins,
                   COUNT(*) FILTER (WHERE result = 'LOST') AS losses,
                   COUNT(*) FILTER (WHERE result = 'DREW') AS draws
            FROM sides WHERE me = :id
            GROUP BY opponent ORDER BY opponent
            """, rowMapperClass = HeadToHeadRowMapper.class)
    List<HeadToHeadRow> findHeadToHead(@Param("id") long gladiatorId);

    @Query(value = SIDES + """
            SELECT id, starts_at, opponent, result, ended_by
            FROM sides WHERE me = :id
            ORDER BY starts_at DESC LIMIT :limit
            """, rowMapperClass = RecentRowMapper.class)
    List<RecentRow> findRecent(@Param("id") long gladiatorId, @Param("limit") int limit);

    /** W/L/D for every gladiator who has a settled fight. */
    @Query(value = SIDES + """
            SELECT me AS opponent,
                   COUNT(*) FILTER (WHERE result = 'WON') AS wins,
                   COUNT(*) FILTER (WHERE result = 'LOST') AS losses,
                   COUNT(*) FILTER (WHERE result = 'DREW') AS draws
            FROM sides GROUP BY me
            """, rowMapperClass = HeadToHeadRowMapper.class)
    List<HeadToHeadRow> findRecords();

    /** W/L/D against {@code opponent}; findRecords reuses it with the gladiator's own id there. */
    record HeadToHeadRow(long opponent, int wins, int losses, int draws) {}

    record RecentRow(long fightId, Instant startsAt, long opponent, Result result, EndedBy endedBy) {}

    class CareerRowMapper implements RowMapper<Career> {
        @Override
        public Career mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Career(rs.getInt("fights"), rs.getInt("wins"), rs.getInt("losses"), rs.getInt("draws"),
                    nullable(rs, "win_rate_bps"), nullable(rs, "ko_rate_bps"), nullable(rs, "avg_rounds_x100"),
                    rs.getInt("rounds_fought"), nullable(rs, "avg_damage_dealt_x100"),
                    nullable(rs, "avg_damage_taken_x100"), nullable(rs, "dodge_rate_bps"));
        }

        private static Integer nullable(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }
    }

    class HeadToHeadRowMapper implements RowMapper<HeadToHeadRow> {
        @Override
        public HeadToHeadRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new HeadToHeadRow(rs.getLong("opponent"), rs.getInt("wins"), rs.getInt("losses"), rs.getInt("draws"));
        }
    }

    class RecentRowMapper implements RowMapper<RecentRow> {
        @Override
        public RecentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RecentRow(rs.getLong("id"), rs.getTimestamp("starts_at").toInstant(), rs.getLong("opponent"),
                    Result.valueOf(rs.getString("result")), EndedBy.valueOf(rs.getString("ended_by")));
        }
    }
}
