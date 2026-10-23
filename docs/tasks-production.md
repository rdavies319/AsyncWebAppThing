# Production Readiness Tasks

The arena is feature-complete (Phase 2, tasks 01–12), but its deployment setup is still a local dev setup. These tasks make it safe to run on a public host. The likely host is an Oracle Cloud Always Free Arm VM running the existing compose file. These are not specced or ticketed yet, so explore each one before building it.

**Status:** not started.

## Order

| # | Task | Priority | Blocked by |
| --- | --- | --- | --- |
| P1 | Production roster | Blocker | none |
| P2 | Database credentials and exposure | Blocker | none |
| P3 | HTTPS behind a reverse proxy | Blocker | none |
| P4 | Freeze migrations | Blocker | first deploy |
| P5 | Restart policies | Should-fix | none |
| P6 | Database backups | Should-fix | P2 |
| P7 | Signup and login abuse limits | Should-fix | none |
| P8 | Health and uptime monitoring | Should-fix | P3 |

P2, P3 and P5 probably land together as a production compose override.

## P1. Production roster

`GAME_ROSTER_FILE` defaults to `classpath:roster-dev.json`, which is checked into git. Deployed as it is, anyone could read the hidden stats and bet with certainty. Generate the real roster offline with `RosterTuningTool`, keep it out of git, mount it on the host, and point `GAME_ROSTER_FILE` at it. This has to happen **before first boot**, because the roster only loads into an empty `gladiators` table (spec §3, Roster).

To explore: should the app refuse to start when it would load `roster-dev.json` outside the dev profile?

## P2. Database credentials and exposure

`docker-compose.yml` hardcodes `user`/`password` and publishes `5432:5432`. On a public VM that's an open Postgres. Read the credentials from the env file, and drop the port mapping, because the app reaches `db` on the compose network.

## P3. HTTPS behind a reverse proxy

Form login sends passwords in plain text without TLS. Put a reverse proxy in front (Caddy gets Let's Encrypt certificates automatically once there's a domain), and set `server.forward-headers-strategy=framework` so redirects and cookies know they're behind TLS. Check that SSE (`/live`) passes through the proxy unbuffered.

## P4. Freeze migrations

`CLAUDE.md` says migrations may still be edited in place because nothing is deployed. After the first real deploy, editing one breaks Flyway's checksum check on startup. Change that rule in `CLAUDE.md` and in spec §11/§12 when the app first deploys.

## P5. Restart policies

Add `restart: unless-stopped` to both compose services, so a host reboot doesn't leave the arena down. The engine already resumes the fight correctly after an outage (task 08).

## P6. Database backups

The ledger and Elo history exist only in the Docker volume. Run a nightly `pg_dump` to off-host storage (Oracle's free object storage, for example), and test a restore once.

## P7. Signup and login abuse limits

Signup and login have no rate limiting or CAPTCHA. Points are virtual, so the stakes are low, but bots could farm the leaderboard and brute-force passwords. Explore per-IP rate limits on `/auth/signup` and `/auth/login` without adding infrastructure (no Redis, per `CLAUDE.md`).

## P8. Health and uptime monitoring

There's no actuator, so nothing alerts anyone when the engine stops ticking. A free external uptime pinger on `/fights/current` is the cheapest fix, and the traffic also helps keep Oracle from reclaiming the VM as idle. To explore: an endpoint or check that confirms the engine ticked recently, not just that the app is up.
