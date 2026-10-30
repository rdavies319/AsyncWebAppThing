# Spec: The Arena

Draft v3 (2026-10-21) · Persistent gladiator tournament, asynchronous betting

v3 replaces the "Higher or Lower" game ([spec-higher-or-lower.md](spec-higher-or-lower.md), v2). Users, wallets, the ledger, auth, SSE and the htmx board carry over from v2. Its rounds, predictions and minute roll are retired (section 11). Section 12 records the decisions from the 2026-10-21 design session.

## 1. Overview

A fixed roster of gladiators fights a 1v1 duel at the top of every hour. Each minute of the fight is one **round**: both gladiators attack at once, and each may dodge or take damage. A fight ends at a knockout, or after 30 rounds on remaining HP.

Players bet virtual points on who wins the **next** fight. The gladiators' stats are hidden. Players pick a champion from public records: results, Elo ratings, damage averages and head-to-head history. The combat is random enough that the strongest gladiator beats the weakest only about 70% of the time.

This is a toy. Results are random and logged for internal checking, but the game makes no provably-fair promise to players.

## 2. Points, Never Money

Unchanged from v2 section 2. Points are permanently virtual. They can't be bought, sold or cashed out. Players get points from the signup bonus, the daily top-up and winning bets.

## 3. Gladiators

### Roster

- **8 gladiators**, fixed. Each has a slug, a name, an epithet and a short bio.
- Each has a **hidden stat block** that never changes:

| Stat | Meaning | Typical range |
| --- | --- | --- |
| `hp` | Starting hit points | 80–140 |
| `attack_min`, `attack_max` | Damage roll range per hit, before defence | 4–22 |
| `defence` | Flat reduction to every hit taken. A hit always does at least 1. | 0–6 |
| `agility` | Percent chance to dodge an incoming attack | 5–30 |

The ranges are a guide for the tuning tool (section 3, Tuning). The rules don't depend on them.

- Each gladiator also has a public **Elo rating**, starting at 1000. It is the only per-gladiator number that changes (section 5).

### Hidden stats

- The stats live only in the `gladiators` table and the roster file. No API, page, SSE event or log line shows them. The stats are package-private in the `gladiator` feature, and a test checks that no public response contains a stat field.
- The **real roster is kept out of git.** On first boot, when `gladiators` is empty, the app loads the roster from `game.roster-file`, a JSON file outside the repo (a secret mount in production). After that the database is the only source, and the file is ignored. Dev and tests use a checked-in throwaway roster at `classpath:roster-dev.json`.
- A trigger makes the stat columns immutable.

### Tuning

A simulation tool, run from a JUnit test, plays thousands of seeded fights for every pairing and reports:

- the win rate of the strongest gladiator against the weakest (target **65–75%**)
- the spread of fight lengths (target median **8–15 rounds**, with fewer than 5% of fights reaching the cap)
- each gladiator's overall win rate against the field

The same simulation runs as a regression test against `roster-dev.json` and asserts both targets, so a change to the combat rules can't quietly break the balance. To generate the production roster, run the tool offline and keep its output out of git.

## 4. Fights

### Schedule

- A **fight** has two corners, `red` and `blue`, and a `starts_at` taken from `game.fight-cron` (default `0 0 * * * *`, the top of every hour, in UTC).
- When fight N **starts**, fight N+1 is **announced** for the next slot, with its matchup and odds. There is always one fight open for betting.
- **Bootstrap:** when no fights exist, the engine announces fight 1 for the next slot. Nothing is fighting until then.
- Fight N+1 starts only once fight N is settled. If N is somehow still live at N+1's slot, N+1 moves to the following slot.

### Pairing

Run at announcement time:

1. Leave out the two gladiators who are fighting right now (fight N).
2. Rank the rest by the `starts_at` of their last fight, oldest first. A gladiator who has never fought ranks before everyone.
3. Red is the top of the ranking.
4. Blue is the highest-ranked of the rest who isn't one of red's last 3 opponents. If all of them are, blue is simply the highest-ranked of the rest.
5. Break ties with the new fight's seed (section 6), so the pick is reproducible.

Step 4 exists because ranking by rest alone pairs the same two forever: two gladiators who fight each other rest for the same time, so they reach the top of the ranking together again (with 8 gladiators the first four pairs repeated endlessly). With it, all 28 matchups come round, a rematch is at least 13 fights apart, and each gladiator fights about every 4 hours, never more than 6 apart.

### The round

Round `n` (1-based) is due at `starts_at + n × game.round-interval` (default 1 minute). Both attacks resolve together, from the state before the round:

```
for attacker in (red, blue), defender = the other:
    hit_roll    = roll(fight, n, attacker, "hit", 100)        // 1..100
    if hit_roll <= defender.agility:   attack is dodged, damage 0
    else:
        dmg_roll = roll(fight, n, attacker, "dmg", attacker.attack_max − attacker.attack_min + 1)
        damage   = max(1, attacker.attack_min + dmg_roll − 1 − defender.defence)
red_hp  -= blue's damage
blue_hp -= red's damage
```

The `roll` function is defined in section 6. All maths uses integers.

### Ending

After each round:

| Condition | Result | `ended_by` |
| --- | --- | --- |
| Exactly one gladiator at HP ≤ 0 | The other wins | `ko` |
| Both at HP ≤ 0 | Draw | `double_ko` |
| Round 30 done (`game.max-rounds`), both standing | Higher remaining HP share wins, comparing `hp_left × 10000 / hp` (integer division) | `decision` |
| Round 30 done and the shares are equal | Draw | `decision` |

The default cap is 30 rounds at 1 minute each, so every fight is over by :30. The result then stays on screen until the next fight starts.

## 5. Betting

### Rules

- Players bet on fight N+1 while it's `announced` and the server clock is before `starts_at − game.cutoff` (default 10s, so betting closes at **:59:50**).
- **One bet per player per fight**, on one corner. Bets are final: no adding to, switching or cancelling them.
- A stake must be one of `game.stake-options` (default 10, 100, 500), the same coins the page shows. No other amount is a stake. The payout cap drops any option above `floor(max-payout × 10000 / multiplier_bps)`, which leaves each corner's `stakes`. A player's **bet position** (their bet, or the stakes on each corner their balance covers, or nothing once betting closes) is what the page offers and exactly what placing accepts. The stake is debited atomically: `UPDATE wallets … WHERE balance >= :stake`.
- **Win:** credit `payout = floor(stake × multiplier_bps / 10000)`, stake included, at most 10,000. **Loss:** the stake is kept. **Draw:** the stake is refunded (`push`).
- Player versus the house. Players compete only on the leaderboard.
- **In-fight betting is out of scope**, but the model leaves room for it. Odds belong to a fight's market, not to its gladiators, so a later "live market" could price bets from the current HP without changing settled bets.

### Odds

Odds come only from public data, never from the hidden stats, so a player who studies the records can beat the line.

At announcement, with ratings `Ra` (red) and `Rb` (blue):

```
p_red_bps  = round(10000 / (1 + 10^((Rb − Ra) / 400)))     // Elo expected score
p_red_bps  = clamp(p_red_bps, 1000, 9000)                   // 10%..90%
p_blue_bps = 10000 − p_red_bps
multiplier_bps(side) = floor(98_000_000 / p_side_bps)       // 2% edge: 0.98 / p, in basis points
```

Examples: equal ratings give 19600 (1.96x) on both sides. A 90% favourite pays 10888 (about 1.09x), and the 10% underdog pays 98000 (9.8x).

- The ratings and both multipliers are **stored on the fight at announcement**. Bets copy the multiplier when they're placed. The odds can't move while betting is open, because fight N's fighters are excluded from fight N+1 (section 4, Pairing).
- `p_red_bps` is the one floating-point calculation in the game. It is rounded to an integer once, stored, and everything after it uses integers.

### Elo update

When a fight settles, using the **unclamped** expected score `E` from the stored ratings:

```
S       = 1 for a win, 0.5 for a draw, 0 for a loss
delta   = round(32 × (S − E_red))          // K = 32
red.elo  += delta
blue.elo −= delta
```

The ratings are integers, and the change is zero-sum.

## 6. Randomness

- **One seed per fight:** 32 random bytes from `SecureRandom`, created at announcement. The seed is **internal and never revealed**. Nothing publishes a commitment to it either.
- The seed and the stats fix the whole fight before betting opens, so a fight can be replayed exactly. Tests use this to plant outcomes, and the engine uses it to resume after a crash (section 7).
- **Rolls:** a uniform integer in `1..k`, using rejection sampling to avoid modulo bias:

  ```
  roll(fight, n, corner, kind, k):
      limit = floor(2^32 / k) × k
      for counter c = 0, 1, 2, …:
          h = HMAC-SHA256(key = seed, message = ASCII("<fight_id>:<n>:<corner>:<kind>:<c>"))
          for each 4-byte big-endian unsigned word u in h:
              if u < limit: return (u mod k) + 1
  ```

  The pairing tie-break picks from the tied gladiators, ordered by id: `roll(fight, 0, "red", "pair", k)` picks red from the longest-rested, and if blue also comes from a tie, `roll(fight, 0, "blue", "pair", k)` picks blue from the longest-rested of blue's candidates. The fight's id is reserved before its row is inserted, so the rolls can use it.
- **Internal action log:** every round row stores its raw `hit` and `dmg` rolls next to the outcome, for internal fact-checking. Those columns, like the seed and the stats, never leave the server.
- **Server clock only.** The cutoff, round due times and fight starts all read the injected `Clock`. Clients get `server_time` only to drive countdowns.

## 7. Fight Engine

The engine replaces Higher or Lower's `RoundEngine`. It runs on `game.engine.cron` (default `0 * * * * *`, every minute; dev uses every second) and once at startup. Each tick is **one transaction**:

1. `pg_try_advisory_xact_lock(<arena key>)`. If the lock isn't acquired, skip.
2. **Live fight:** lock it `FOR UPDATE`. While `next_round_at <= now` and the fight isn't over, run the round, write its row, update `red_hp`, `blue_hp` and `rounds_done`, and advance `next_round_at`. If the fight ended, settle it (step 4).
3. **Announced fight:** lock it `FOR UPDATE`.
   - If `starts_at <= now` and no fight is live, start it: mark it `live`, set `next_round_at = starts_at + interval`, and announce the next fight (section 4).
   - If `starts_at` is more than one round interval in the past (the app was down at the start), move `starts_at` to the next slot after `now` instead. Betting reopens until the new cutoff, and existing bets stand. Log a WARN.
   - If no fight exists at all, bootstrap.
4. **Settle:** in set-based SQL, mark bets `won`, `lost` or `push`, credit the wallets, and write ledger rows. Update both Elo ratings and mark the fight `settled`.
5. After commit, publish the events (section 9).

**Idempotent:** every step is gated on fight status (`announced → live → settled`) and `rounds_done`, under row locks. `PRIMARY KEY(fight_id, round_no)` backs this up. A repeated tick never runs a round or pays out twice.

**Crash in the middle of a fight: resume, never void.** Each round commits with the fight's new HP and `rounds_done`, so a crash loses at most the round that was in flight. On restart, the tick reads the fight from its row and runs every overdue round straight away, from the same seed. The result is exactly what it would have been without the crash. The fight then carries on live: `next_round_at` moves to the next interval after `now`, and the gap is logged at WARN. A missed start moves the fight forward (step 3). Whole fights are never backfilled, and the engine never voids a fight on its own.

**Placing a bet:** lock the fight `FOR SHARE`. It must be `announced` and before the cutoff. Validate the corner and stake, debit atomically, and insert the bet and its `stake` ledger row. `UNIQUE(user_id, fight_id)` turns a duplicate into a 409. The engine's `FOR UPDATE` in step 3 means a bet can't slip in while the fight starts.

**Timing targets:** each round lands within about 1 second of its due time. Settling 1,000 bets takes under 2 seconds.

### Configuration and profiles

- **Production values are the defaults** in `GameProperties`. Every `game.*` setting can be overridden by an environment variable, which Spring Boot binds automatically (`GAME_ROUND_INTERVAL=5s`, `GAME_FIGHT_CRON=0 */3 * * * *`).
- **The dev profile holds the fast dev cadence** in `application-dev.properties` (a fight every 3 minutes, 5-second rounds and a tick every second), so compose and `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` read the same values.
- **`.env` files per environment.** `docker-compose.yml` loads the file named by `ENV_FILE` through `env_file`. `.env.dev` holds `SPRING_PROFILES_ACTIVE=dev`, so `ENV_FILE=.env.dev docker compose up` runs dev and plain `docker compose up` runs production values.
- `.env*` files are gitignored. A committed `.env.example` lists every key with its production default.
- At startup the app checks that `max-rounds × round-interval` is shorter than the gap between two consecutive fight-cron slots, and refuses to start otherwise.

### Dev controls

- **Forcing**, only under `@Profile("dev")`, behind login, CSRF on:
  - `POST /dev/fights/start` moves the announced fight's `starts_at` to `now` and ticks.
  - `POST /dev/fights/round` moves the live fight's `next_round_at` to `now` and ticks.
  - `POST /dev/fights/finish` repeats that until the fight settles.

  Each verb only pulls a due time forward and then runs the real `tick()`, so dev uses the same code path and lock as production. Forcing never changes a result, which the seed has already fixed. The game page shows a small dev panel with these buttons only under the dev profile.
- **Seeded ratings**, under the dev profile only: on a fresh arena (no fights yet), every gladiator's starting Elo comes from a simulated season, 50 deterministic round-robins of the roster played through the real Elo update, so the first fights already have uneven odds. It is built from the hidden stats, which is acceptable only because the dev roster is checked in. Production always starts at 1000 (section 5, Odds). A `game.engine.cron` of `-` turns it off, as it does the startup tick.
- `TestGame` gets the same verbs: `startFight()`, `nextRound()`, `finishFight()`, and `plantFight(...)` to search for a seed that gives a wanted outcome.

## 8. Data Model

| Table | Columns |
| --- | --- |
| **users**, **wallets**, **ledger** | Unchanged from v2 |
| **gladiators** | id, slug (unique), name, epithet, bio, hp, attack\_min, attack\_max, defence, agility, elo (int, starts 1000), created\_at. The stat columns are immutable (trigger). |
| **fights** | id, starts\_at (unique), status (`announced`, `live`, `settled`), red\_id, blue\_id (FK gladiators, different), red\_elo, blue\_elo (at announcement), red\_multiplier\_bps, blue\_multiplier\_bps, seed (internal), red\_hp, blue\_hp (current), rounds\_done, next\_round\_at, winner (`red`, `blue`, `draw`, null), ended\_by (`ko`, `double_ko`, `decision`, null), settled\_at |
| **fight\_rounds** | fight\_id, round\_no, due\_at, run\_at, red\_hit\_roll, red\_dmg\_roll, blue\_hit\_roll, blue\_dmg\_roll (internal, null where not rolled), red\_hit (bool), red\_damage, blue\_hit, blue\_damage, red\_hp\_after, blue\_hp\_after. PK (fight\_id, round\_no). Append-only (trigger). |
| **bets** | id, user\_id, fight\_id, corner (`red`, `blue`), stake, multiplier\_bps, status (`pending`, `won`, `lost`, `push`), payout, created\_at, settled\_at. `UNIQUE(user_id, fight_id)` |

Constraints:

- A partial unique index allows at most one `announced` and one `live` fight.
- Triggers make `seed`, the matchup, the stored ratings and multipliers, and a set `winner` immutable.
- Only the `fight` feature reads `seed` and the roll columns, and no public view carries them.

**Ledger:** reasons are unchanged from v2 (`signup_bonus`, `stake`, `payout`, `refund`, `topup`). `ref_id` on `stake`, `payout` and `refund` rows is a bet id. **Invariant:** `wallets.balance = SUM(ledger.delta)` for every user.

## 9. API and Pages

| Method and path | Purpose |
| --- | --- |
| `GET /` | Arena page (section 10) |
| `GET /fights/current` | The live fight (corners, names, HP, rounds so far, next round time) and the announced fight (matchup, Elo, `multiplier_bps` and `stakes` per corner, `cutoff_at`), plus `server_time` |
| `POST /bets` | Body: `fight_id`, `corner`, `stake`. Returns the stored bet. 400 when invalid (including a stake that isn't one of the corner's `stakes`), 409 when closed, duplicate or insufficient. |
| `POST /wallet/topup` | Unchanged |
| `GET /gladiators`, `GET /gladiators/{slug}` | Public records (below), as a page or as JSON |
| `GET /fights?limit=50`, `GET /fights/{id}` | Fight history, and one fight's page: matchup, odds, result and the public round log |
| `GET /bets/me` | Your bet history |
| `GET /leaderboard` | The top balances (below) |
| `GET /live` | SSE stream |
| `POST /dev/fights/{start,round,finish}` | Dev profile only (section 7) |

Watching stays public: `/`, `/fragments/board`, `/fights/**`, `/gladiators/**`, `/leaderboard` and `/live`. Everything else needs login, with the same 401 and `HX-Redirect` behaviour as v2.

**Public gladiator record:** W/L/D, win %, Elo, average damage dealt and taken per round (misses included), dodge rate (attacks dodged ÷ attacks received), KO rate (KO wins ÷ wins), average fight length, a head-to-head record against each opponent, and the last 5 results with links to `/fights/{id}`. All of it is computed from the public columns of `fights` and `fight_rounds` only.

**Leaderboard:** players ranked by balance, highest first, ties to whoever joined first. `WalletService.highestBalances(limit)` gives the user ids and balances, and the `leaderboard` feature adds each display name from `UserService`. It never reads the wallet or user tables itself, and the wallet never asks the user feature for names (the user feature already depends on the wallet).

**`GET /live` (SSE):** each connect gets a snapshot (`server_time` plus the `/fights/current` payload). Then:

- `matchup_announced`: the announced fight's payload
- `fight_started`: fight id, corners, starting HP
- `round`: fight id, n, each side's hit and damage, HP after
- `fight_settled`: fight id, winner, `ended_by`, the new Elo ratings
- `bet_settled`: to the owning user only. Status, payout and new balance.

All events are published after commit (`@TransactionalEventListener`).

## 10. Arena Page

One SSE connection plus the htmx `board` fragment, refetched on every arena event. As in v2, the board renders one `board.Board` value from `BoardService`. The board shows:

- **Now fighting:** two corner cards (name, epithet, HP bar), a countdown to the next round, and the round log, newest first ("Brutus hits Vex for 14 · Vex dodges"). Between fights, it shows the last result.
- **Next fight:** both gladiators with Elo and record, odds and the largest stake per corner, the stake buttons the player can afford and bet form (or your bet once placed), and a countdown to the cutoff.
- **You:** balance, the top-up button when a claim would succeed, and your last 5 bets.
- **Dev panel**, under the dev profile only.

Gladiator, fight and leaderboard pages are plain server-rendered pages with no SSE.

## 11. Removing Higher or Lower

Nothing has been deployed, so the old game is removed outright, as the first Phase 2 task, before any arena code. The V1 schema is edited in place to drop the old game's `rounds` and `predictions` tables, and local databases are wiped. There are no refunds and no conversion migration. The arena's tables follow as new migrations. The old `round` and `prediction` packages, `RoundEngine`, `/rounds/current`, `/predictions` and the v2 page are deleted. Users, wallets and the ledger schema stay.

## 12. Resolved Questions (2026-10-21)

| Question | Decision |
| --- | --- |
| Replace Higher or Lower, or run both? | Replace. Keep users, wallets, ledger, auth, SSE and the board. |
| Fight format? | One 1v1 duel per hour. Corners are red and blue. |
| Who fights? | Least recently fought, seeded tie-break. Not the current fighters, and not one of red's last 3 opponents (amended 2026-10-24: the original "not the same pair twice in a row" let the same four pairs repeat forever). |
| When can players bet? | Before the fight only, closing at :59:50. In-fight betting may come later. |
| How random? | The strongest beats the weakest about 70% of the time (65–75%, checked by a simulation test) |
| Provably fair? | No, this is a toy. The seed and stats are internal and never revealed. Raw rolls are logged per round for internal fact-checking. |
| Reveal the stats? | Never. No commitment hash either. |
| Fixed or evolving roster? | Fixed. 8 gladiators. |
| Stats? | HP, attack range, defence, agility. Simultaneous rounds. A crit stat may come later. |
| Fight length? | Usually 8–15 rounds. Capped at 30, then decided on HP share. Double KO or equal share is a draw. |
| Odds model? | Fixed odds from public Elo (K = 32), clamped to 10–90%, 2% edge, locked at announcement. Never from the hidden stats. |
| Bet rules? | One final bet per fight. A draw refunds. A stake is one of `game.stake-options` (10, 100, 500), less any the payout cap (10,000) rules out on that corner. This replaces v2's 10–500 range, so the page and the API share one rule: what the page offers is exactly what a bet may be. |
| Crash in the middle of a fight? | Resume from the fight row and replay overdue rounds from the seed. Never void automatically. |
| Downtime at a fight's start? | Move to the next slot with betting reopened. No backfill. |
| Configuration? | Production defaults in `GameProperties`, overridden by environment variables, with `.env` files through compose. The dev cadence is in `application-dev.properties`. `.env.example` is committed. |
| Dev forcing? | Dev-profile endpoints that pull due times forward and tick the real engine |
| Where do the real stats live? | Outside git, in a roster file loaded once on first boot |
| Ledger? | Unchanged reasons and wording. `ref_id` is the bet id. |
| Domain words? | "Fight" and "bet" for the arena. The ledger keeps v2's words. |
| Migration? | None. Nothing is deployed, so Higher or Lower is removed first, by editing the V1 schema. |
| Leaderboard? | Top balances, from `WalletService.highestBalances(limit)` |
| Fight page? | `/fights/{id}`: matchup, odds, result, public round log. Nothing internal. |
