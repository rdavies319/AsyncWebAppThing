package com.rdavies.arena.wallet;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one place tests change wallets without going through the game, for states that would
 * otherwise take many fights to reach. Every change writes its ledger row, so the invariant
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

    /** Adds {@code points} to the player's wallet with a matching PAYOUT row, as a big win would. */
    public void grant(long userId, long points) {
        jdbc.update("UPDATE wallets SET balance = balance + ? WHERE user_id = ?", points, userId);
        jdbc.update("INSERT INTO ledger (user_id, delta, reason, created_at) VALUES (?, ?, 'PAYOUT', now())",
                userId, points);
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

    /**
     * Takes {@code points} from every wallet whose user's email matches {@code emailPattern} (SQL
     * LIKE), each with a STAKE row, in two statements: for load tests that place bets in bulk.
     */
    public void spendForUsers(String emailPattern, long points) {
        jdbc.update("UPDATE wallets SET balance = balance - ? WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                points, emailPattern);
        jdbc.update("""
                INSERT INTO ledger (user_id, delta, reason, created_at)
                SELECT id, ?, 'STAKE', now() FROM users WHERE email LIKE ?""", -points, emailPattern);
    }

    /** The ledger invariant for every wallet: balance = SUM(ledger.delta). */
    public void assertEveryWalletMatchesItsLedger() {
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallets w
                WHERE w.balance <> (SELECT COALESCE(SUM(delta), 0) FROM ledger l WHERE l.user_id = w.user_id)""",
                Integer.class), "wallets whose balance doesn't match their ledger");
    }
}
