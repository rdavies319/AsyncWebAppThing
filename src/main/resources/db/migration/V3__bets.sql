-- Bets on the announced fight: spec v3, sections 5 and 8.
-- multiplier_bps is copied from the fight's corner when the bet is placed. payout is what settlement
-- credited, stake included: the win, the refunded stake on a draw, or 0 on a loss; null while pending.
CREATE TABLE bets (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    fight_id       BIGINT      NOT NULL REFERENCES fights (id),
    corner         VARCHAR(8)  NOT NULL CHECK (corner IN ('RED', 'BLUE')),
    stake          BIGINT      NOT NULL CHECK (stake > 0),
    multiplier_bps INT         NOT NULL CHECK (multiplier_bps > 10000),
    status         VARCHAR(8)  NOT NULL CHECK (status IN ('PENDING', 'WON', 'LOST', 'PUSH')),
    payout         BIGINT      CHECK (payout >= 0),
    created_at     TIMESTAMPTZ NOT NULL,
    settled_at     TIMESTAMPTZ,
    UNIQUE (user_id, fight_id),
    CHECK ((status = 'PENDING') = (payout IS NULL)),
    CHECK ((status = 'PENDING') = (settled_at IS NULL))
);

-- Settlement finds a fight's bets by this index.
CREATE INDEX bets_by_fight ON bets (fight_id);
