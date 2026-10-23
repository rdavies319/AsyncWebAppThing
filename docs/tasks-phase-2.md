# Phase 2 Tasks: The Arena

These tasks replace Higher or Lower with the gladiator arena in [spec-arena.md](spec-arena.md) v3. Each task is a vertical slice that can be shown or checked on its own. Each is built test-first: write the failing test for an acceptance criterion, then make it pass. Each task leaves `./mvnw test` green.

The full ticket for each task, with acceptance criteria, is in `.scratch/arena/issues/NN-*.md`. Keep this file and the tickets in step as tasks land.

**Status:** 01–08 and 11 done.

## Order

| # | Task | Blocked by |
| --- | --- | --- |
| 01 | Retire Higher or Lower | none |
| 02 | Combat core and dev roster | none |
| 03 | Arena scaffold (MVP fight loop) | 01, 02 |
| 04 | Full fight engine | 03 |
| 05 | Watch the fight live, with dev cadence and forcing | 04 |
| 06 | Elo ratings and odds | 05 |
| 07 | Bet on the next fight | 06 |
| 08 | Crash resume and missed starts | 04 |
| 09 | Bet history | 07 |
| 10 | Gladiator pages | 06 |
| 11 | Fight history pages | 04 |
| 12 | Leaderboard | 01 |

01 and 02 can start immediately. Once 04 lands, 05, 08 and 11 can run in parallel. 12 can start as soon as 01 is done.

New packages: `gladiator` (roster, stats, Elo, public records), `fight` (engine, combat, rolls, odds, pairing), `bet` and `dev`. `board`, `live`, `web`, `wallet`, `user` and `leaderboard` stay. `round` and `prediction` are deleted in 01.

## 01. Retire Higher or Lower

Nothing has been deployed, so there's no migration path. Edit the V1 schema to remove `rounds` and `predictions`, then wipe local databases (`docker compose down -v`, `./mvnw clean`). Delete:

- the `round` and `prediction` features and the round engine
- `/rounds/current`, `/predictions` and the predict form
- the wallet's round-payout credit
- the tests that only covered these

The board shows "arena opening soon", plus balance and top-up. `/live` sends a `server_time` snapshot. The auth tests move to `/wallet/topup`. CLAUDE.md drops the Higher or Lower notes.

## 02. Combat core and dev roster

Pure code with no Spring: seeded HMAC dice rolls with rejection sampling, a round, the end-of-fight rules and a whole-fight simulator. Also the checked-in 8-gladiator dev roster. A balance test asserts that the strongest beats the weakest 65–75% of the time, the median length is 8–15 rounds, and under 5% of fights reach the cap. An offline tool generates the production roster.

## 03. Arena scaffold (MVP fight loop)

Schema for `gladiators`, `fights` and `fight_rounds` (with triggers and indexes), and roster loading on first boot. An engine tick under its own advisory lock bootstraps, starts, runs rounds and settles a fight. `GET /fights/current` is public and shows nothing internal. The cadence properties go into `GameProperties`. `TestGame` gets `startFight`, `nextRound` and `finishFight`. Pairing and announcement are deliberately simple here, and there are no events yet.

## 04. Full fight engine

The real pairing rules, and the next fight announced when the current one starts. A fight only starts once the previous one has settled. Every ending stores the right `winner` and `ended_by`. The events are delivered after commit, the row locks match the spec, the concurrent-tick test passes, and `TestGame.plantFight` exists.

## 05. Watch the fight live, with dev cadence and forcing

The arena `Board` covers every fight state, the SSE events and snapshot carry the arena, and countdowns run on server time. Environment-variable overrides, `.env.example` and `.env.dev`, and `ENV_FILE` in compose. The app refuses to start if a capped fight can't fit between two slots. Dev-profile `/dev/fights/{start,round,finish}` endpoints and a dev panel. Run commands documented.

## 06. Elo ratings and odds

Elo maths and odds maths, both pure. Both ratings and both multipliers are stored and immutable at announcement. Ratings update when a fight settles. The board and `/fights/current` show Elo, odds and max stake.

## 07. Bet on the next fight

Landed in three parts: (a) the wallet batch-credit prefactor, (b) placing a bet, (c) settlement and `bet_settled`.

The parts:
- The wallet batch-credit prefactor.
- The `bets` table, and placing a bet: cutoff, one final bet per fight, stake limits and payout cap, atomic debit, the `stake` ledger row. `POST /bets` and `POST /bet` share one service.
- Set-based settlement in the engine tick, with `payout` and `refund` rows, and per-user `bet_settled`.
- The board shows the bet form, your bet, and its result.
- 1,000 bets settle in under 2s.

## 08. Crash resume and missed starts

Overdue rounds replay from the seed and give the same result as no outage. The fight then realigns to the next interval and carries on live. A missed start moves to the next slot and betting reopens. Gaps are logged at WARN. Fights are never voided. Once 07 has landed, bets stand when a start moves.

## 09. Bet history

`GET /bets/me`, and your last 5 bets on the board.

## 10. Gladiator pages

`/gladiators` and `/gladiators/{slug}` with the full public record, computed from public columns only. A test sweeps every public response for hidden fields.

## 11. Fight history pages

`/fights` and `/fights/{id}`: the matchup, odds, result and public round log, with links from the board.

## 12. Leaderboard

`WalletService.highestBalances(limit)` and the public `/leaderboard`, as HTML and JSON.
