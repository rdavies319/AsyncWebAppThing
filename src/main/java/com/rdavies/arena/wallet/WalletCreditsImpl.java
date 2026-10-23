package com.rdavies.arena.wallet;

import com.rdavies.arena.wallet.records.Credit;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

class WalletCreditsImpl implements WalletCredits {
    private final JdbcTemplate jdbc;

    WalletCreditsImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int creditAll(List<Credit> credits, Instant now) {
        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    WITH credited AS (
                        INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
                        SELECT user_id, delta, reason, ref_id, ?
                        FROM unnest(?::bigint[], ?::bigint[], ?::varchar[], ?::bigint[]) AS c (user_id, delta, reason, ref_id)
                        RETURNING user_id, delta)
                    UPDATE wallets w SET balance = w.balance + c.delta, updated_at = ?
                    FROM (SELECT user_id, SUM(delta) AS delta FROM credited GROUP BY user_id) AS c
                    WHERE w.user_id = c.user_id""");
            Timestamp at = Timestamp.from(now);
            statement.setTimestamp(1, at);
            statement.setArray(2, array(connection, "bigint", credits.stream().map(Credit::userId).toArray()));
            statement.setArray(3, array(connection, "bigint", credits.stream().map(Credit::amount).toArray()));
            statement.setArray(4, array(connection, "varchar", credits.stream().map(c -> c.reason().name()).toArray()));
            statement.setArray(5, array(connection, "bigint", credits.stream().map(Credit::refId).toArray()));
            statement.setTimestamp(6, at);
            return statement;
        });
    }

    private static Array array(Connection connection, String type, Object[] values) throws SQLException {
        return connection.createArrayOf(type, values);
    }
}
