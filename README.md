# AsyncWebAppThing

"Higher or Lower", a persistent asynchronous prediction game. Every minute the server rolls a number from 1 to 100, and players stake virtual points on whether the next roll will be higher or lower than the current one. Rolls are provably fair: each roll's seed is committed (hashed) before anyone bets and revealed afterwards.

It's a single full-stack Spring Boot app (Java 21, PostgreSQL, Thymeleaf + htmx, SSE for live updates) in [`api/`](api/). The round engine runs inside the app as a scheduled job.

## Running it

```bash
docker compose up --build
```

Then open <http://localhost:8080> and sign up. Anyone can watch, and playing needs an account.

To run the app or tests directly, use the Maven wrapper in `api/` (`./mvnw spring-boot:run`, `./mvnw test`). The app needs a PostgreSQL datasource (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`), for example the `db` service from `docker compose up db`.

## Docs

- [Game spec](docs/spec-higher-or-lower.md): rules, odds, fairness, data model, API
- [Phase 1 tasks](docs/tasks-phase-1.md): work to align the code with the spec
