-- Users, wallets and the ledger: spec v2 section 7, kept as they are by the arena spec (section 8).
-- Enum-like columns are VARCHAR + CHECK rather than Postgres ENUM types:
-- Spring Data JDBC binds Java enums as varchar (their name()), which Postgres ENUM columns reject.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    display_name  VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    user_id          BIGINT      PRIMARY KEY REFERENCES users (id),
    balance          BIGINT      NOT NULL CHECK (balance >= 0),
    last_top_up_date DATE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only record of every balance change: for every user, wallets.balance = SUM(ledger.delta).
-- ref_id is the bet id for STAKE, PAYOUT and REFUND rows.
CREATE TABLE ledger (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    delta      BIGINT      NOT NULL CHECK (delta <> 0),
    reason     VARCHAR(16) NOT NULL CHECK (reason IN ('SIGNUP_BONUS', 'STAKE', 'PAYOUT', 'REFUND', 'TOPUP')),
    ref_id     BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ledger_by_user ON ledger (user_id);

CREATE FUNCTION ledger_reject_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_append_only BEFORE UPDATE OR DELETE ON ledger
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_changes();
