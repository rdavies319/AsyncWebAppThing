-- Provably fair commit-reveal: seed_commit_hash is published while the round is open,
-- seed stays secret until the round is settled or voided.
ALTER TABLE rounds ADD COLUMN seed_commit_hash VARCHAR(64);
ALTER TABLE rounds ADD COLUMN seed VARCHAR(64);

-- Allow VOIDED (a round the engine missed during downtime, stakes refunded).
-- V1's inline CHECK has a vendor-specific name, so swap the column instead of dropping the
-- constraint: dropping a column also drops the single-column CHECK on it, in Postgres and H2.
ALTER TABLE rounds ADD COLUMN status_new VARCHAR(16) DEFAULT 'OPEN';
UPDATE rounds SET status_new = status;
ALTER TABLE rounds DROP COLUMN status;
ALTER TABLE rounds RENAME COLUMN status_new TO status;
ALTER TABLE rounds ALTER COLUMN status SET NOT NULL;
ALTER TABLE rounds ADD CONSTRAINT rounds_status_check
    CHECK (status IN ('OPEN', 'CLOSED', 'SETTLED', 'VOIDED'));

-- One round per minute slot: a second engine instance can never open the same round twice.
ALTER TABLE rounds ADD CONSTRAINT rounds_start_time_unique UNIQUE (start_time);
