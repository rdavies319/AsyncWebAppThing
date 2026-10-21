package com.rdavies.arena.fight;

import com.rdavies.arena.fight.records.Attack;
import com.rdavies.arena.fight.records.ExchangeResult;
import com.rdavies.arena.fight.records.FightExchange;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * The append-only exchange log. Plain JDBC, because its composite key (fight_id, exchange_no)
 * doesn't fit a Spring Data aggregate. Package-private: the rows carry the raw rolls.
 */
@Repository
class FightExchangeRepository {
    private final JdbcClient jdbc;

    FightExchangeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(long fightId, ExchangeResult exchange, Instant dueAt, Instant runAt) {
        jdbc.sql("""
                INSERT INTO fight_exchanges (fight_id, exchange_no, due_at, run_at,
                    red_hit_roll, red_dmg_roll, blue_hit_roll, blue_dmg_roll,
                    red_hit, red_damage, blue_hit, blue_damage, red_hp_after, blue_hp_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(fightId, exchange.n(), Timestamp.from(dueAt), Timestamp.from(runAt),
                        exchange.red().hitRoll(), exchange.red().dmgRoll(),
                        exchange.blue().hitRoll(), exchange.blue().dmgRoll(),
                        exchange.red().hit(), exchange.red().damage(),
                        exchange.blue().hit(), exchange.blue().damage(),
                        exchange.redHpAfter(), exchange.blueHpAfter())
                .update();
    }

    /** A fight's exchanges, oldest first. */
    List<FightExchange> findByFight(long fightId) {
        return jdbc.sql("SELECT * FROM fight_exchanges WHERE fight_id = ? ORDER BY exchange_no")
                .param(fightId)
                .query(FightExchangeRepository::map)
                .list();
    }

    private static FightExchange map(ResultSet rs, int row) throws SQLException {
        Attack red = new Attack(rs.getInt("red_hit_roll"), rs.getObject("red_dmg_roll", Integer.class),
                rs.getBoolean("red_hit"), rs.getInt("red_damage"));
        Attack blue = new Attack(rs.getInt("blue_hit_roll"), rs.getObject("blue_dmg_roll", Integer.class),
                rs.getBoolean("blue_hit"), rs.getInt("blue_damage"));
        ExchangeResult result = new ExchangeResult(rs.getInt("exchange_no"), red, blue,
                rs.getInt("red_hp_after"), rs.getInt("blue_hp_after"));
        return new FightExchange(rs.getLong("fight_id"), rs.getTimestamp("due_at").toInstant(),
                rs.getTimestamp("run_at").toInstant(), result);
    }
}
