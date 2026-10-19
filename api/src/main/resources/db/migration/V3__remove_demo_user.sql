-- Auth has landed (T9), so the demo user from V2 goes. The ledger is append-only by trigger; this
-- dev fixture is the one exception, so the trigger is lifted only for these deletes.
DELETE FROM predictions WHERE user_id = 1;
ALTER TABLE ledger DISABLE TRIGGER ledger_append_only;
DELETE FROM ledger WHERE user_id = 1;
ALTER TABLE ledger ENABLE TRIGGER ledger_append_only;
DELETE FROM wallets WHERE user_id = 1;
DELETE FROM users WHERE id = 1 AND email = 'demo@example.invalid';
