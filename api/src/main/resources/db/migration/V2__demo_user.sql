-- Until auth lands (T9), GameController plays as this user. '!' is not a BCrypt hash, so it can never log in.
INSERT INTO users (id, email, display_name, password_hash) VALUES (1, 'demo@example.invalid', 'demo', '!');
SELECT setval(pg_get_serial_sequence('users', 'id'), 1);

INSERT INTO wallets (user_id, balance) VALUES (1, 1000);
INSERT INTO ledger (user_id, delta, reason, created_at) VALUES (1, 1000, 'SIGNUP_BONUS', now());
