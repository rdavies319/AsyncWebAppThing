-- The arena: spec v3, section 8. Odds columns (red/blue_multiplier_bps) arrive with Elo and odds in task 06.

-- The stat columns are hidden: only the gladiator and fight features read them, and no public view carries them.
CREATE TABLE gladiators (
    id         BIGSERIAL    PRIMARY KEY,
    slug       VARCHAR(40)  NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]+$'),
    name       VARCHAR(60)  NOT NULL,
    epithet    VARCHAR(80)  NOT NULL,
    bio        TEXT         NOT NULL,
    hp         INT          NOT NULL CHECK (hp > 0),
    attack_min INT          NOT NULL CHECK (attack_min >= 1),
    attack_max INT          NOT NULL,
    defence    INT          NOT NULL CHECK (defence >= 0),
    agility    INT          NOT NULL CHECK (agility BETWEEN 0 AND 100),
    elo        INT          NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (attack_max >= attack_min)
);

-- A gladiator's stats never change; only the Elo rating does.
CREATE FUNCTION gladiators_keep_stats_immutable() RETURNS trigger AS $$
BEGIN
    IF (NEW.hp, NEW.attack_min, NEW.attack_max, NEW.defence, NEW.agility)
            IS DISTINCT FROM (OLD.hp, OLD.attack_min, OLD.attack_max, OLD.defence, OLD.agility) THEN
        RAISE EXCEPTION 'gladiator %: stats are immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER gladiators_immutable_stats BEFORE UPDATE ON gladiators
    FOR EACH ROW EXECUTE FUNCTION gladiators_keep_stats_immutable();

-- announced -> live -> settled. The seed is internal and fixes the whole fight at announcement.
-- red_elo/blue_elo are the ratings at announcement.
CREATE TABLE fights (
    id               BIGSERIAL   PRIMARY KEY,
    starts_at        TIMESTAMPTZ NOT NULL UNIQUE,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('ANNOUNCED', 'LIVE', 'SETTLED')),
    red_id           BIGINT      NOT NULL REFERENCES gladiators (id),
    blue_id          BIGINT      NOT NULL REFERENCES gladiators (id),
    red_elo          INT         NOT NULL,
    blue_elo         INT         NOT NULL,
    seed             BYTEA       NOT NULL CHECK (octet_length(seed) = 32),
    red_hp           INT         NOT NULL,
    blue_hp          INT         NOT NULL,
    rounds_done   INT         NOT NULL DEFAULT 0 CHECK (rounds_done >= 0),
    next_round_at TIMESTAMPTZ,
    winner           VARCHAR(8)  CHECK (winner IN ('RED', 'BLUE', 'DRAW')),
    ended_by         VARCHAR(16) CHECK (ended_by IN ('KO', 'DOUBLE_KO', 'CAP')),
    settled_at       TIMESTAMPTZ,
    CHECK (red_id <> blue_id),
    CHECK ((status = 'LIVE') = (next_round_at IS NOT NULL)),
    CHECK ((status = 'SETTLED') = (winner IS NOT NULL)),
    CHECK ((winner IS NULL) = (ended_by IS NULL)),
    CHECK ((status = 'SETTLED') = (settled_at IS NOT NULL))
);

-- At most one fight open for betting and one fighting.
CREATE UNIQUE INDEX fights_one_per_open_status ON fights (status) WHERE status IN ('ANNOUNCED', 'LIVE');
CREATE INDEX fights_by_red ON fights (red_id);
CREATE INDEX fights_by_blue ON fights (blue_id);

-- The matchup, its seed and ratings are fixed at announcement, and a result is final.
CREATE FUNCTION fights_keep_announcement_immutable() RETURNS trigger AS $$
BEGIN
    IF (NEW.seed, NEW.red_id, NEW.blue_id, NEW.red_elo, NEW.blue_elo)
            IS DISTINCT FROM (OLD.seed, OLD.red_id, OLD.blue_id, OLD.red_elo, OLD.blue_elo)
            OR (OLD.winner IS NOT NULL AND (NEW.winner, NEW.ended_by) IS DISTINCT FROM (OLD.winner, OLD.ended_by)) THEN
        RAISE EXCEPTION 'fight %: the matchup, seed, ratings and result are immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fights_immutable_announcement BEFORE UPDATE ON fights
    FOR EACH ROW EXECUTE FUNCTION fights_keep_announcement_immutable();

-- One row per round. The *_roll columns are the raw dice rolls, kept for internal
-- fact-checking and never published; a dmg roll is null when the attack was dodged.
CREATE TABLE fight_rounds (
    fight_id      BIGINT      NOT NULL REFERENCES fights (id),
    round_no   INT         NOT NULL CHECK (round_no >= 1),
    due_at        TIMESTAMPTZ NOT NULL,
    run_at        TIMESTAMPTZ NOT NULL,
    red_hit_roll  INT         NOT NULL,
    red_dmg_roll  INT,
    blue_hit_roll INT         NOT NULL,
    blue_dmg_roll INT,
    red_hit       BOOLEAN     NOT NULL,
    red_damage    INT         NOT NULL CHECK (red_damage >= 0),
    blue_hit      BOOLEAN     NOT NULL,
    blue_damage   INT         NOT NULL CHECK (blue_damage >= 0),
    red_hp_after  INT         NOT NULL,
    blue_hp_after INT         NOT NULL,
    PRIMARY KEY (fight_id, round_no),
    CHECK (red_hit = (red_dmg_roll IS NOT NULL)),
    CHECK (blue_hit = (blue_dmg_roll IS NOT NULL))
);

CREATE FUNCTION fight_rounds_reject_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fight_rounds is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fight_rounds_append_only BEFORE UPDATE OR DELETE ON fight_rounds
    FOR EACH ROW EXECUTE FUNCTION fight_rounds_reject_changes();
