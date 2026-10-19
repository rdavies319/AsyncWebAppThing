# Phase 1 Tasks: Align the Code With Spec v2

These tasks bring the existing `api/` code in line with [spec-higher-or-lower.md](spec-higher-or-lower.md) v2. The code already has a working engine, commit-reveal, odds, predictions, ledger, SSE and a Thymeleaf page. It differs from the spec in its round model, catch-up behaviour, roll derivation, cutoff, schema, tests and package layout.

Work through the tasks in order unless a task says otherwise. Each task should leave `./mvnw test` green.

**Status:** T0 done (`777cb7e`) · T1 done (`efb736d`) · T2 done (`dd592b5`) · T3 done (`c53be01`) · T4–T6 done together (`82dc25f`; the schema change removed the columns the old engine used, so they couldn't land separately) · T7 done (`d262ad6`) · T8 and T9 deferred (2026-10-19), T8 since done; T9 still deferred, so the game keeps playing as the demo user · T10 done (`4bd87bb`; streams are keyed by `CurrentUser`, which returns the demo user until T9) · T12 done (`7db9e85`; JSON enum values are lower-case, as in spec section 7; insufficient points is a 409) · T11 done (`488d44f`). The board refetches on `round_open` only: that event follows the settlement commit, so it already reflects results and balance. · T8 done (`9b17ae3`; the page shows the top-up button only when a claim would succeed) · T9 done: Phase 1 complete. `V3__remove_demo_user.sql` removes the demo user, briefly lifting the ledger trigger for that one dev fixture. Logged-out API calls get 401, and htmx calls get 401 with `HX-Redirect`.

Carried forward: T6's `RoundSettled` and per-prediction events moved to T10, because nothing consumes them before then. The demo user (id 1) stays in `V2__demo_user.sql` until T9.

## T0. Commit the current state

All of `api/`, `docs/`, `Dockerfile` and `docker-compose.yml` are untracked. Commit them as they are before changing anything, so there's a known-good point to go back to. Add `.idea/` and `api/target/` to `.gitignore` instead of committing them.

**Done when:** `git status` is clean apart from ignored files.

## T1. Fix CLAUDE.md and the README

- CLAUDE.md: Remove "only a bare Spring Boot scaffold exists". Describe the actual code, and say that Thymeleaf, Flyway and Spring Data JDBC are already in use. Replace "Open decisions" with the settled rules (spec section 12), including the `scheduled → open → settled` statuses, the ledger reasons, the 10s cutoff and the payout cap. Change the datasource note to say tests use Testcontainers (needs Docker).
- README: Remove the Lambda line, and describe the app and how to run it (`docker compose up`).

## T2. Switch tests to Testcontainers Postgres

- Remove `h2`. Add `spring-boot-testcontainers` and the Testcontainers `postgresql` module (test scope). Add a `@TestConfiguration` with a `@ServiceConnection` `PostgreSQLContainer` (`postgres:15-alpine`, matching compose).
- Remove the H2 datasource settings from `src/test/resources/application.properties`.
- In `RoundEngine`, remove the `advisoryLocks` product check. The advisory lock always runs.
- Add a test showing that a second concurrent `tick()` skips while the first holds the advisory lock.

**Done when:** All existing tests pass against Postgres, and nothing references H2.

## T3. Reorganise packages by feature

Move the code with no behaviour change: `round` (engine, `ProvablyFair`, `Odds`, round domain and repository), `prediction`, `wallet` (wallet, ledger), `user`, `live` (`LiveController`), `web` (`GameController`). `leaderboard` waits for Phase 2.

**Done when:** `controller/`, `service/`, `repository/` and `domain/` no longer exist, and the tests are green.

## T4. Schema to match spec section 7

Nothing has been deployed, so **replace V1–V3 with a fresh `V1__schema.sql`** instead of stacking conversion migrations. Local databases must then be wiped (`docker compose down -v`). If a database you care about already exists, write a `V4` conversion instead.

- `users`: email (unique, lower-case), display_name (unique), password_hash. Drop `username`.
- `wallets`: `last_top_up_date DATE` (nullable), `updated_at`. Keep the `balance >= 0` check.
- `rounds`: `roll_value` (nullable), `seed_commit_hash`, `seed`, status `scheduled|open|settled`, `settled_at`, unique `starts_at`. Drop `previous_roll`, `current_roll`, `end_time`, `CLOSED` and `VOIDED`.
- `predictions`: `direction`, `multiplier_bps`, status `pending|won|lost|push`, `payout`, `settled_at`. Keep `UNIQUE(user_id, round_id)`.
- `ledger`: `delta`, `reason` (`signup_bonus|stake|payout|refund|topup`), `ref_id`. Drop `round_id` and `type`.
- Drop the V2 demo-user seed. Until T9 lands, tests create their own user and wallet.
- Update the domain records and repositories to match.

**Done when:** Migrations run on a clean Postgres, and the domain code compiles against the new columns.

## T5. Roll derivation: HMAC with rejection sampling

- Rewrite `ProvablyFair`: the seed is 32 raw bytes (stored and revealed as hex), the commitment is `hex(SHA-256(seed bytes))`, and the roll uses the HMAC-SHA256 rejection-sampling loop from spec section 4 with message `"<round_id>:<c>"`.
- Remove the "round has no committed seed" fallback in `RoundEngine.rollFor`. A missing seed is an error.
- Tests: known-answer vectors for a fixed seed and round id, a property check that the roll is always in 1..100, a forced-rejection case, and a check that the commitment matches.

## T6. Round engine: scheduled-next model, settle-not-void catch-up

Depends on T4 and T5. Rewrite `RoundEngine.tick()` to follow the steps in spec section 6:

- Opening round N also creates round N+1 as `scheduled` with its commitment. Bootstrap creates round 1 (rolled, `open`) and round 2 (`scheduled`).
- At round N's `starts_at + 60s`, the tick rolls N+1 from its committed seed, settles N, opens N+1 and creates N+2.
- **Catch-up:** Remove `VOIDED` and the `ROLL_GRACE` voiding. A late tick settles round N normally. If one or more whole minutes were missed, round N+1's `starts_at` is moved to the current minute slot, and the gap is logged at WARN with the number of missed minutes.
- Settlement uses set-based SQL (one `UPDATE … FROM` for prediction statuses and payouts, one wallet update, one ledger `INSERT … SELECT`), not a loop over each prediction.
- Events (`RoundSettled`, `RoundOpened`, one per settled prediction) are published inside the transaction and delivered `@TransactionalEventListener` after commit.
- Tests: bootstrap, normal settlement (win, loss and push each produce the right ledger rows), duplicate ticks never pay twice, catch-up after a 10-minute gap settles with the committed seed and moves the slot, seeds stay hidden until rolled, the ledger total equals the balance throughout, and 1,000 predictions settle in under 2s.

## T7. Odds, limits and placing predictions

Depends on T4.

- Add `GameProperties` (`@ConfigurationProperties("game")`) with: cutoff (10s), min stake (10), max stake (500), max payout (10,000), signup bonus (1,000), top-up target (1,000).
- `Odds`: `multiplierBps(R, direction) = floor(980000 / W)`, `payout(stake, bps)`, `maxStake(bps)`. Update `OddsTests`, including R = 99 Higher giving max stake 102.
- `placePrediction`: lock the round `FOR SHARE`, check the cutoff from properties (10s, not 5s), validate stake limits per side, store `multiplier_bps`, and write the `stake` ledger row with `ref_id` = prediction id. Turn a unique-constraint violation into "already predicted".
- Tests: cutoff at exactly `starts_at + 50s`, stakes below the minimum and above the per-side maximum, impossible directions, insufficient points, and the multiplier staying locked in.

## T8. Wallet: signup bonus and daily top-up

Depends on T4.

- `WalletService.createFor(user)`: a wallet with 1,000 plus a `signup_bonus` ledger row.
- `claimTopUp(user)`: only when the balance is below 1,000 and `last_top_up_date` is before today (UTC, from `Clock`). Credit `1000 − balance` as `topup` and set the date, using one conditional UPDATE so two claims can't race.
- Add `POST /wallet/topup`.
- Tests: one claim per day, refused at or above 1,000 without using up the day, allowed again on the next UTC day, and the ledger invariant holds.

## T9. Auth: form signup and login

Depends on T8.

- Add `spring-boot-starter-security`. Use form login at `/auth/login`, a signup form at `/auth/signup` (email, display name, password, validated), BCrypt, and logout at `/auth/logout`. CSRF is on, and htmx sends the token.
- Signup creates the user and their wallet (T8) in one transaction.
- Replace every hardcoded user `1L` in `GameController` with the authenticated user. `GET /`, `GET /rounds/current` and `GET /live` are public. Predicting and topping up require login.
- Tests: signup, duplicate email or display name, login, and that an anonymous POST to `/predictions` is rejected.

## T10. Live updates (SSE)

Depends on T6 and T9. Includes publishing `RoundSettled` and one event per settled prediction from the engine (carried over from T6). Settlement is set-based SQL, so the engine must query the settled predictions to build them.

- Move to `live/`. On connect, send `server_time` plus a `round_open` snapshot. Emit `round_open` and `round_settled` with the payloads in spec section 8.
- Keep a registry of emitters keyed by user, and send each `prediction_settled` only to that user's streams.
- Clean up dead emitters, and send a heartbeat comment to keep proxies from closing idle connections.
- Tests: the snapshot arrives on connect, and a user only receives their own `prediction_settled`.

## T11. Game page: Thymeleaf and htmx

Depends on T7, T8, T9 and T10.

- Load htmx and its SSE extension with pinned versions. Don't add a JS build.
- Show the current roll, a countdown driven by the server clock offset, the multiplier and max stake for each side (impossible sides disabled), the stake input, your prediction for this round, the last result, your balance, and a top-up button when eligible.
- Swap fragments on `round_open`, `round_settled` and `prediction_settled` instead of reloading the whole page.

## T12. JSON endpoints

Depends on T7.

- `GET /rounds/current` and `POST /predictions` (JSON body), as in spec section 8.
- Validation errors return 400, and "closed" or "already predicted" return 409, as `ProblemDetail`.
- The game page can keep posting forms. Both routes go through the same service.
