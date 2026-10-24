# Spec: "Higher or Lower" Minute-Roll Game

Draft v2 (2026-10-19) · Asynchronous online prediction game

> **Superseded** by [spec-arena.md](spec-arena.md) (v3, 2026-10-21). Phase 2 task 01 removed its `round` and `prediction` code, so this is history only.

v2 settles the open questions from v1 and brings the architecture in line with the repo. Section 12 lists what was decided.

## 1. Overview

A persistent online game. Every 60 seconds the server rolls a random integer from 1 to 100. Users predict whether the **next** roll will be higher or lower than the **current** one. It's asynchronous: users can drop in any time, place a prediction, and see results later.

## 2. Points, Never Money

Points are **virtual, permanently**. They can't be bought, sold or cashed out. Players get points only from the signup bonus, the daily top-up and winning predictions.

- Real-money wagering in the UK requires a Gambling Commission licence, age and identity verification, and responsible-gambling tooling. Points that can be bought or cashed out can count as gambling too.
- Points give the same gameplay loop (leaderboards, streaks, daily top-ups) with none of that overhead.

## 3. Core Game Rules

### Rounds

- A **round** is one minute-long slot. Round N opens on a minute boundary (`starts_at`), and its **roll** (`roll_value`) is made at that moment. That roll is the "current number" shown throughout round N.
- During round N, players predict whether the roll of round N+1 will be **Higher** or **Lower** than round N's roll, and stake points.
- At the next minute boundary, round N+1 is rolled and round N's predictions are settled against it.
- **Cutoff:** Predictions for round N are accepted while the server clock is before `starts_at + 50s`. That leaves a 10-second cutoff before the next roll, so latency can't be exploited. The cutoff length is configurable.
- **One prediction per user per round.** Predictions are final: once placed, a prediction can't be cancelled or changed.
- The game is **player versus the house**. Players compete only through the leaderboard.

### Outcomes

- **Win:** The roll moves in the predicted direction. The player is credited `payout`.
- **Loss:** The roll moves the other way. The stake, already debited, is kept.
- **Push:** The roll equals the previous roll (1% chance). The stake is refunded.
- **Impossible predictions:** If the current roll is 100, "Higher" is disabled. If it's 1, "Lower" is disabled. The server rejects both, not just the UI.

### Odds

Odds are dynamic, based on the true probability, with a 2% house edge. With current roll R, the number of winning outcomes W out of 100 is:

- Higher: W = 100 − R
- Lower: W = R − 1

The multiplier is stored as an integer in basis points (10000 = 1.00x):

```
multiplier_bps = floor(980000 / W)            // (1 − 0.02) / (W / 100), in basis points
payout         = floor(stake × multiplier_bps / 10000)   // total credited on a win, stake included
```

Examples: R = 50 gives 19600 (1.96x) for Higher and 20000 (2.00x) for Lower, because 50 itself is a tie. R = 90 Higher gives 98000 (9.8x), and R = 90 Lower gives 11011 (about 1.10x).

The multiplier is **locked in when the prediction is placed** and stored with it. All maths uses integers, never floating point.

### Stakes and limits

| Setting | Value | Notes |
| --- | --- | --- |
| Signup bonus | 1,000 | Credited once, as a ledger row |
| Minimum stake | 10 | |
| Maximum stake | 500 | Lowered further by the payout cap |
| Maximum payout | 10,000 per prediction | `max_stake = min(500, floor(10000 × 10000 / multiplier_bps))` |
| Daily top-up | Refill to 1,000 | See below |

All of these are configuration properties. The payout cap applies to each side separately. For example, R = 99 Higher (98x) allows stakes up to 102. A cap only ever lowers payouts, so it never gives players an edge.

**Daily top-up:** Once per UTC day, a player whose balance is below 1,000 can claim a top-up that raises it back to exactly 1,000. A player at or above 1,000 can't claim, and a refused claim doesn't use up that day's top-up. There are no point sinks or periodic resets in the MVP.

## 4. Fairness and Integrity

- All randomness is generated **server-side** with a CSPRNG (`SecureRandom`), never on the client.
- **Commit-reveal, one seed per round:**
  - Each round's seed is 32 random bytes. Its commitment is `hex(SHA-256(seed bytes))`.
  - Round N+1 is created, with its commitment, when round N opens. So while players bet on round N+1's roll, its commitment is already public.
  - The seed is revealed (shown as hex) once that round has been rolled.
- **Roll derivation** uses rejection sampling, so there's no modulo bias:
  ```
  for counter c = 0, 1, 2, …:
      h = HMAC-SHA256(key = seed bytes, message = ASCII("<round_id>:<c>"))
      for each 4-byte big-endian unsigned word u in h:
          if u < 4294967200:          // floor(2^32 / 100) × 100
              return (u mod 100) + 1
  ```
  Anyone can verify a round from its seed, its id and the published commitment.
- Rolls are stored immutably. Once set, `roll_value`, `seed` and `seed_commit_hash` never change.
- **Never trust client time.** The server clock alone decides whether a prediction arrived before the cutoff. Clients get the server time only to show an accurate countdown.

## 5. Features

### MVP

- Sign up and log in with email, password and a unique display name. There's no email verification yet.
- Points wallet with a signup bonus and a daily top-up
- Live view: current number, countdown, odds and max stake for each side, place a prediction, latest result
- Automatic round settlement
- History of past rolls and your own predictions
- Leaderboard: daily, weekly and all-time

### Later

- OAuth login and email verification
- Streaks and achievements
- Notifications ("your prediction won")
- Public roll history and verification page
- Chat or live feed of other players' activity
- Admin dashboard (rolls, user activity, anomalies)
- Rate limiting and anti-abuse

## 6. Architecture

The application is a **full-stack Spring Boot** app (Spring Boot 4.1, Java 21, Maven). The same app serves the UI and the API. There's no separate JavaScript project and no JS build step.

| Layer | Choice |
| --- | --- |
| Frontend | Thymeleaf server-rendered views plus htmx. Live updates over SSE (htmx SSE extension). |
| Backend | Spring Web MVC, Spring JDBC (no JPA), Spring Security form login with BCrypt |
| Round engine | `@Scheduled` job inside the app, guarded by a Postgres advisory lock |
| Database | PostgreSQL (source of truth), schema managed by Flyway |
| Live fan-out | In-memory SSE emitter registry (single instance) |
| Tests | JUnit with Testcontainers PostgreSQL (`@ServiceConnection`) |
| Hosting | Container (see `Dockerfile` and `docker-compose.yml`) |

There's **no Redis** and **no Lambda**. Redis is reconsidered only if the app runs on more than one instance, because SSE fan-out is in memory. The engine itself is already safe to run on several instances.

Code is organised **by feature** under `com.rdavies.api`: `round` (engine, fairness, odds), `prediction`, `wallet` (wallets, ledger, top-up), `user` (signup, security), `leaderboard`, `live` (SSE) and `web` (Thymeleaf controllers).

All time logic (round boundaries, cutoff, top-up day) reads one injected `java.time.Clock` in UTC, so tests can control time.

### Round engine

The engine runs on cron `0 * * * * *` in UTC, and once more at startup to catch up. Each tick is **one DB transaction**:

1. `SELECT pg_try_advisory_xact_lock(<engine key>)`. If the lock isn't acquired, another instance is ticking, so skip. The lock is released on commit or crash.
2. Lock the `open` round N (`FOR UPDATE`). If there isn't one, **bootstrap**: create round 1 already rolled and `open` on the current minute, plus round 2 `scheduled`. Then stop.
3. If the clock is before round N's `starts_at + 60s`, nothing is due, so stop. Repeat ticks therefore do nothing.
4. Lock the `scheduled` round N+1. Compute its roll from its committed seed, set `roll_value`, and mark it `open`.
5. Settle all of round N's predictions against round N+1's roll with set-based SQL: credit winners, refund pushes, mark losses, write ledger rows. Mark round N `settled`.
6. Create round N+2 as `scheduled` with a fresh seed and commitment, on the slot after round N+1.
7. After commit, publish `round_settled`, `round_open` and the per-user `prediction_settled` events.

**Idempotency:** Every step is gated on round status (`scheduled → open → settled`) under row locks, and `starts_at` is unique. A restart or a duplicate tick can never roll or pay twice.

**Catch-up after downtime:** The tick works the same after downtime. The pending round N is settled with round N+1's **already-committed** seed. That's still fair, because the seed was fixed before anyone bet. Round N+1 then takes the **current** minute slot (its `starts_at` is moved forward), and play continues from there. Minutes missed while the app was down get **no rounds**. The gap is logged as a warning and alerted on, and `/rounds` shows it as a jump in `starts_at`. If round N+1 reopens with less than the cutoff left, it simply takes no predictions and settles at the next boundary.

**Timing targets:** The roll fires within about 1 second of the minute boundary. Settlement for 1,000 predictions takes under 2 seconds.

### Placing a prediction

`POST /predictions` runs one transaction:

1. Lock the round row `FOR SHARE`. It must be `open`, and the clock must be before the cutoff.
2. Validate the direction (not impossible), the stake (from the minimum up to that side's maximum), and that the user has no earlier prediction for this round (also enforced by `UNIQUE(user_id, round_id)`).
3. Debit atomically: `UPDATE wallets SET balance = balance − :stake WHERE user_id = :u AND balance >= :stake`. Zero rows updated means insufficient points.
4. Insert the prediction with its `multiplier_bps`, and a ledger row (`stake`, −stake).

The engine's `FOR UPDATE` on the same round row means a prediction can never slip in while the round is being settled.

## 7. Data Model

| Table | Columns |
| --- | --- |
| **users** | id, email (unique, stored lower-case), display\_name (unique), password\_hash (BCrypt), created\_at |
| **wallets** | user\_id (PK, FK), balance (integer points, `CHECK balance >= 0`), last\_top\_up\_date (UTC date, nullable), updated\_at |
| **rounds** | id, starts\_at (unique), roll\_value (null until rolled), seed\_commit\_hash, seed (secret until rolled), status (`scheduled`, `open`, `settled`), settled\_at |
| **predictions** | id, user\_id, round\_id, direction (`higher`, `lower`), stake, multiplier\_bps, status (`pending`, `won`, `lost`, `push`), payout (null until settled), created\_at, settled\_at. `UNIQUE(user_id, round_id)` |
| **ledger** | id, user\_id, delta, reason, ref\_id, created\_at. Append-only. |

Ledger reasons:

| reason | delta | ref\_id |
| --- | --- | --- |
| `signup_bonus` | +1000 | null |
| `stake` | −stake | prediction id |
| `payout` | +payout | prediction id |
| `refund` | +stake | prediction id |
| `topup` | +(1000 − balance) | null |

A lost prediction writes no settlement row. **Invariant:** for every user, `wallets.balance = SUM(ledger.delta)`.

All points columns are `BIGINT`. Enum-like columns are `VARCHAR` with a `CHECK` constraint.

## 8. API

| Method and path | Purpose | Phase |
| --- | --- | --- |
| `GET /` | Live game page | 1 |
| `GET/POST /auth/signup`, `GET/POST /auth/login`, `POST /auth/logout` | Form signup and login (Spring Security) | 1 |
| `GET /rounds/current` | Round id, roll, `starts_at`, `cutoff_at`, server time, `multiplier_bps` and `max_stake` per side (null if impossible), next round's `seed_commit_hash` | 1 |
| `POST /predictions` | Body: `round_id`, `direction`, `stake`. Returns the stored prediction, including `multiplier_bps`. | 1 |
| `POST /wallet/topup` | Claim the daily top-up | 1 |
| `GET /live` | SSE stream (see below) | 1 |
| `GET /predictions/me` | Your prediction history | 2 |
| `GET /rounds?limit=100` | Roll history, with revealed seeds and commitments | 2 |
| `GET /leaderboard?period=daily\|weekly\|all` | Leaderboard | 2 |

**`GET /live` (SSE)** sends state events, not countdown ticks. The client runs the countdown locally using the server clock offset.

- Every connect or reconnect gets a **snapshot** first: `server_time` plus the current `round_open` payload. Missed events don't matter, so there's no `Last-Event-ID` replay.
- `server_time`: the server's current instant.
- `round_open`: `round_id`, `roll`, `starts_at`, `cutoff_at`, the odds and max stake per side, and the next round's `seed_commit_hash`.
- `round_settled`: `round_id`, the next roll, and the revealed seed of the round that was just rolled.
- `prediction_settled`: sent only to the owning user's streams, after the settlement transaction commits. It contains the status, payout and new balance.

## 9. Non-Functional Requirements

- **Timing:** Rolls fire within about 1 second of the minute boundary.
- **Scale target (MVP):** 1,000 concurrent users, with settlement under 2 seconds.
- **Concurrency:** Atomic conditional updates on wallets, row locks on rounds, and an advisory lock on the engine.
- **Security:** BCrypt passwords, CSRF protection on forms, input validation. Rate limiting and multi-account protection come in Phase 4.
- **Observability:** Logs, metrics, and alerts for missed or late rounds and catch-up gaps.

## 10. Risks

- **Legal:** Kept low by keeping points permanently virtual (section 2).
- **Cheating:** Late-bet exploits (handled by the server-side cutoff and row locks), scripts and multi-accounting (Phase 4).
- **Reliability:** A missed minute breaks trust, so the engine must be robust and idempotent, and gaps must be alerted on.
- **Economy balance:** Point inflation. The top-up only refills to 1,000 and payouts are capped. Revisit sinks or resets after launch.

## 11. Phased Plan

1. **Phase 1:** Schema and Flyway, the round engine (rolls, commit-reveal, settlement, catch-up), form signup and login, wallets with the signup bonus and daily top-up, placing predictions, and the live page over SSE.
2. **Phase 2:** History (`/predictions/me`, `/rounds`) and the leaderboard.
3. **Phase 3:** Provably fair verification page, streaks and notifications.
4. **Phase 4:** Anti-abuse hardening (rate limiting, bots, multi-accounting).

## 12. Resolved Questions

| Question | Decision |
| --- | --- |
| Points or real money? | Points, permanently virtual (section 2) |
| One prediction per round, or several? | One, and final |
| Dynamic odds or flat 2x? | Dynamic, 2% edge, stored in basis points, payout capped at 10,000 |
| Compare with the previous roll or a fixed threshold? | The previous roll |
| PvP or player versus the house? | Player versus the house. Competition is through the leaderboard only. |
| Lambda (README) or an always-on engine? | Always-on `@Scheduled` engine inside the app. No Lambda. |
| MySQL, Java 17 and Redis (v1)? | PostgreSQL, Java 21, no Redis |
| WebSocket or SSE? | SSE at `GET /live` |
| When does auth arrive? | Phase 1, as form login |
| Missed rounds after downtime? | Settle the pending round with its committed seed, skip the gap, and alert. No backfilled rounds. |
| Leaderboard metric? | Net winnings (payouts − stakes) on predictions settled in the period. UTC day, ISO week, all-time. |
