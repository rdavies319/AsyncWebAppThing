-- Schema for spec v2, section 7.
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

-- Round N's roll_value is the number shown during minute N; it is rolled when the round opens.
-- Round N+1 is created SCHEDULED (seed committed, not yet rolled) when round N opens.
CREATE TABLE rounds (
    id               BIGSERIAL   PRIMARY KEY,
    starts_at        TIMESTAMPTZ NOT NULL UNIQUE,
    roll_value       INT         CHECK (roll_value BETWEEN 1 AND 100),
    seed_commit_hash CHAR(64)    NOT NULL,
    seed             CHAR(64)    NOT NULL,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED', 'OPEN', 'SETTLED')),
    settled_at       TIMESTAMPTZ,
    CHECK ((status = 'SCHEDULED') = (roll_value IS NULL)),
    CHECK ((status = 'SETTLED') = (settled_at IS NOT NULL))
);

-- At most one OPEN and one SCHEDULED round at any time.
CREATE UNIQUE INDEX rounds_one_live_per_status ON rounds (status) WHERE status IN ('SCHEDULED', 'OPEN');

-- Provably fair: once published, a seed and its commitment never change, and a roll is final.
CREATE FUNCTION rounds_keep_rolls_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.seed <> OLD.seed OR NEW.seed_commit_hash <> OLD.seed_commit_hash
            OR (OLD.roll_value IS NOT NULL AND NEW.roll_value IS DISTINCT FROM OLD.roll_value) THEN
        RAISE EXCEPTION 'round %: seed, commitment and roll are immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER rounds_immutable_rolls BEFORE UPDATE ON rounds
    FOR EACH ROW EXECUTE FUNCTION rounds_keep_rolls_immutable();

CREATE TABLE predictions (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    round_id       BIGINT      NOT NULL REFERENCES rounds (id),
    direction      VARCHAR(8)  NOT NULL CHECK (direction IN ('HIGHER', 'LOWER')),
    stake          BIGINT      NOT NULL CHECK (stake > 0),
    multiplier_bps BIGINT      NOT NULL CHECK (multiplier_bps > 0),
    status         VARCHAR(8)  NOT NULL CHECK (status IN ('PENDING', 'WON', 'LOST', 'PUSH')),
    payout         BIGINT      CHECK (payout >= 0),
    created_at     TIMESTAMPTZ NOT NULL,
    settled_at     TIMESTAMPTZ,
    UNIQUE (user_id, round_id),
    CHECK ((status = 'PENDING') = (payout IS NULL)),
    CHECK ((status = 'PENDING') = (settled_at IS NULL))
);

CREATE INDEX predictions_by_round ON predictions (round_id);

-- Append-only record of every balance change: for every user, wallets.balance = SUM(ledger.delta).
-- ref_id is the prediction id for STAKE, PAYOUT and REFUND rows.
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
