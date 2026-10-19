CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallets (
    user_id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 1000 CHECK (balance >= 0),
    last_top_up TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Enum-like columns are VARCHAR + CHECK rather than Postgres ENUM types:
-- Spring Data JDBC binds Java enums as varchar, which Postgres ENUM columns reject.
CREATE TABLE rounds (
    round_id BIGSERIAL PRIMARY KEY,
    previous_roll INT NOT NULL CHECK (previous_roll BETWEEN 1 AND 100),
    current_roll INT CHECK (current_roll BETWEEN 1 AND 100),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'SETTLED')),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE predictions (
    prediction_id BIGSERIAL PRIMARY KEY,
    round_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    prediction VARCHAR(8) NOT NULL CHECK (prediction IN ('HIGHER', 'LOWER')),
    stake BIGINT NOT NULL CHECK (stake > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (round_id, user_id),
    CONSTRAINT fk_pred_round FOREIGN KEY (round_id) REFERENCES rounds(round_id),
    CONSTRAINT fk_pred_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    round_id BIGINT,
    amount BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('STAKE', 'PAYOUT', 'REFUND', 'TOPUP')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_ledger_round FOREIGN KEY (round_id) REFERENCES rounds(round_id)
);
