package com.rdavies.arena.fight;

import com.rdavies.arena.fight.records.Attack;
import com.rdavies.arena.fight.records.RoundResult;
import com.rdavies.arena.fight.records.FightRound;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * The append-only round log. Plain JDBC, because its composite key (fight_id, round_no)
 * doesn't fit a Spring Data aggregate. Package-private: the rows carry the raw rolls.
 */
@Repository
class FightRoundRepository {
    private final JdbcClient jdbc;

    FightRoundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(long fightId, RoundResult round, Instant dueAt, Instant runAt) {
        jdbc.sql("""
                INSERT INTO fight_rounds (fight_id, round_no, due_at, run_at,
                    red_hit_roll, red_dmg_roll, blue_hit_roll, blue_dmg_roll,
                    red_hit, red_damage, blue_hit, blue_damage, red_hp_after, blue_hp_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(fightId, round.n(), Timestamp.from(dueAt), Timestamp.from(runAt),
                        round.red().hitRoll(), round.red().dmgRoll(),
                        round.blue().hitRoll(), round.blue().dmgRoll(),
                        round.red().hit(), round.red().damage(),
                        round.blue().hit(), round.blue().damage(),
                        round.redHpAfter(), round.blueHpAfter())
                .update();
    }

    /** A fight's rounds, oldest first. */
    List<FightRound> findByFight(long fightId) {
        return jdbc.sql("SELECT * FROM fight_rounds WHERE fight_id = ? ORDER BY round_no")
                .param(fightId)
                .query(FightRoundRepository::map)
                .list();
    }

    private static FightRound map(ResultSet rs, int row) throws SQLException {
        Attack red = new Attack(rs.getInt("red_hit_roll"), rs.getObject("red_dmg_roll", Integer.class),
                rs.getBoolean("red_hit"), rs.getInt("red_damage"));
        Attack blue = new Attack(rs.getInt("blue_hit_roll"), rs.getObject("blue_dmg_roll", Integer.class),
                rs.getBoolean("blue_hit"), rs.getInt("blue_damage"));
        RoundResult result = new RoundResult(rs.getInt("round_no"), red, blue,
                rs.getInt("red_hp_after"), rs.getInt("blue_hp_after"));
        return new FightRound(rs.getLong("fight_id"), rs.getTimestamp("due_at").toInstant(),
                rs.getTimestamp("run_at").toInstant(), result);
    }
}
