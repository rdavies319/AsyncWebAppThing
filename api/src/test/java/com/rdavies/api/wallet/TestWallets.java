package com.rdavies.api.wallet;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one place tests change wallets without going through the game, for states that would
 * otherwise take many rounds to reach. Every change writes its ledger row, so the invariant
 * balance = SUM(ledger.delta) holds as it does in production. Autowire it in any GameTest.
 */
public class TestWallets {
    private final JdbcTemplate jdbc;

    TestWallets(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Takes {@code points} from the player's wallet with a matching STAKE row, as a lost stake would. */
    public void spend(long userId, long points) {
        jdbc.update("UPDATE wallets SET balance = balance - ? WHERE user_id = ?", points, userId);
        jdbc.update("INSERT INTO ledger (user_id, delta, reason, created_at) VALUES (?, ?, 'STAKE', now())",
                userId, -points);
    }

    /**
     * Opens a wallet with the signup bonus for every user whose email matches {@code emailPattern}
     * (SQL LIKE), in two statements: for load tests that create users in bulk.
     */
    public void openWalletsForUsers(String emailPattern, long signupBonus) {
        jdbc.update("INSERT INTO wallets (user_id, balance) SELECT id, ? FROM users WHERE email LIKE ?",
                signupBonus, emailPattern);
        jdbc.update("""
                INSERT INTO ledger (user_id, delta, reason, created_at)
                SELECT id, ?, 'SIGNUP_BONUS', now() FROM users WHERE email LIKE ?""", signupBonus, emailPattern);
    }

    /** Takes the stake of every pending prediction on the round, as placing them would: for bulk-inserted predictions. */
    public void debitPendingStakes(long roundId) {
        jdbc.update("""
                UPDATE wallets w SET balance = w.balance - p.stake
                FROM predictions p WHERE p.user_id = w.user_id AND p.round_id = ? AND p.status = 'PENDING'""", roundId);
        jdbc.update("""
                INSERT INTO ledger (user_id, delta, reason, ref_id, created_at)
                SELECT user_id, -stake, 'STAKE', id, now() FROM predictions WHERE round_id = ? AND status = 'PENDING'""",
                roundId);
    }

    /** The ledger invariant for every wallet: balance = SUM(ledger.delta). */
    public void assertEveryWalletMatchesItsLedger() {
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallets w
                WHERE w.balance <> (SELECT COALESCE(SUM(delta), 0) FROM ledger l WHERE l.user_id = w.user_id)""",
                Integer.class), "wallets whose balance doesn't match their ledger");
    }
}
