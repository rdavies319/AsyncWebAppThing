# AsyncWebAppThing

The Arena, a persistent gladiator tournament. A fixed roster with hidden stats fights a 1v1 duel every hour, one exchange per minute, and players stake virtual points on who wins the next fight. The arena is being built in Phase 2. Until the fight engine lands, the page shows "Arena opening soon", and players can sign up, see their balance and claim the daily top-up.

It's a single full-stack Spring Boot app (Java 21, PostgreSQL, Thymeleaf + htmx, SSE for live updates) in [`api/`](api/).

## Running it

```bash
docker compose up --build
```

Then open <http://localhost:8080> and sign up. Anyone can watch, and playing needs an account.

To run the app or tests directly, use the Maven wrapper in `api/` (`./mvnw spring-boot:run`, `./mvnw test`). The app needs a PostgreSQL datasource (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`), for example the `db` service from `docker compose up db`.

## Docs

- [Arena spec](docs/spec-arena.md): rules, odds, data model, API
- [Phase 2 tasks](docs/tasks-phase-2.md): the ordered arena tasks
- [Higher or Lower spec](docs/spec-higher-or-lower.md): the original game, now removed (history only)
- [Phase 1 tasks](docs/tasks-phase-1.md): the Phase 1 work on Higher or Lower
