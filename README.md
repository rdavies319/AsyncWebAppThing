# AsyncWebAppThing

The Arena, a persistent gladiator tournament. A fixed roster with hidden stats fights a 1v1 duel every hour, one round per minute, and players stake virtual points on who wins the next fight. The arena is being built in Phase 2. Anyone can watch the fights live now; betting comes in a later task. Players can sign up, see their balance and claim the daily top-up.

It's a single full-stack Spring Boot app (Java 21, PostgreSQL, Thymeleaf + htmx, SSE for live updates), with the Maven project at the repo root.

## Running it

Local runs use the `dev` profile. Create `.env.dev` in the repo root:

```bash
SPRING_PROFILES_ACTIVE=dev
```

Then run:

```bash
ENV_FILE=.env.dev docker compose up --build
```

In PowerShell, set the variable first: `$env:ENV_FILE = ".env.dev"; docker compose up --build`.

Then open <http://localhost:8080> and sign up. Anyone can watch, and playing needs an account.

Plain `docker compose up` doesn't run the arena on a fresh database. Outside the dev profile the app refuses to load the checked-in dev roster, whose hidden stats are public, and stops with a message saying to set `GAME_ROSTER_FILE`.

To run the app or tests directly, use the Maven wrapper (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, `./mvnw test`). The app needs a PostgreSQL datasource (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`), for example the `db` service from `docker compose up db`.

### Production

[docs/production-compose.md](docs/production-compose.md) covers the production stack (Caddy and the app against an external database) and how to try it on a laptop.

### Settings

Production values are the defaults, except the roster: `GAME_ROSTER_FILE` must point at a real roster outside the dev profile. Any `game.*` setting can be overridden by an environment variable, such as `GAME_ROUND_INTERVAL=5s`. [`.env.example`](.env.example) lists every key with its default. Copy it to `.env` to change them: `docker compose up` loads `.env` if there is one. Every `.env*` file except the examples is gitignored.

### Dev mode

Production fights take an hour, so the `dev` profile runs a fight every 3 minutes with 5-second rounds (set in [`application-dev.properties`](src/main/resources/application-dev.properties)), and adds a dev panel to start a fight, run a round or finish a fight straight away. With `.env.dev` in place (see [Running it](#running-it)), run either:

```bash
ENV_FILE=.env.dev docker compose up --build            # everything in Docker
docker compose up db                                   # or Postgres in Docker...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # ...and the app from Maven
```

`ENV_FILE=.env.dev docker compose …` is Bash syntax. The PowerShell form under [Running it](#running-it) stays set for that PowerShell session, so run `Remove-Item Env:ENV_FILE` to clear it.

Maven doesn't need `.env.dev`. Any `GAME_*` environment variable still overrides the dev cadence. The datasource still comes from `SPRING_DATASOURCE_*` (`jdbc:postgresql://localhost:5432/arena`, user `user`, password `password` for the compose database). Log in to see the dev panel. The buttons only pull due times forward, so a forced fight ends exactly as it would have.

## Docs

- [Arena spec](docs/spec-arena.md): rules, odds, data model, API
- [Phase 2 tasks](docs/tasks-phase-2.md): the ordered arena tasks
- [Higher or Lower spec](docs/spec-higher-or-lower.md): the original game, now removed (history only)
- [Phase 1 tasks](docs/tasks-phase-1.md): the Phase 1 work on Higher or Lower
