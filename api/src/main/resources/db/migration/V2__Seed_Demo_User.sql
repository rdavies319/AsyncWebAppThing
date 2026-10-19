-- Until auth exists (phase 2), GameController plays as this user.
INSERT INTO users (username) VALUES ('demo');

INSERT INTO wallets (user_id, balance)
SELECT user_id, 1000 FROM users WHERE username = 'demo';

INSERT INTO ledger (user_id, amount, type)
SELECT user_id, 1000, 'TOPUP' FROM users WHERE username = 'demo';
