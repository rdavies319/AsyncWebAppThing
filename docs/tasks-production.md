# Production Readiness Tasks

The arena is feature-complete (Phase 2, tasks 01–12), but its deployment setup is still a local dev setup. These tasks make it safe to run on a public host. The target is AWS, chosen partly as a way to learn it: the app and a reverse proxy run under compose on one EC2 instance, and the database is RDS for PostgreSQL. Local development keeps the existing compose file and its Postgres container. The tasks are split into tickets 01–11 in `.scratch/production/issues/`, each with acceptance criteria. Read the ticket before starting it, and keep the tickets and this file in step.

**Status:** tickets 01 and 02 are done: the app refuses the dev roster outside the dev profile (part of P1), and `GET /health` reports whether the engine is ticking (part of P8). The rest hasn't started.

## Order

| # | Task | Priority | Blocked by |
| --- | --- | --- | --- |
| P0 | AWS foundations | Blocker | none |
| P1 | Production roster | Blocker | none |
| P2 | Database credentials and exposure | Blocker | P0 |
| P3 | HTTPS behind a reverse proxy | Blocker | P0 |
| P4 | Freeze migrations | Blocker | first deploy |
| P5 | Restart policies | Should-fix | P0 |
| P6 | Database backups | Should-fix | P0 |
| P7 | Signup and login abuse limits | Should-fix | P3 |
| P8 | Health and uptime monitoring | Should-fix | P3 |

P2, P3 and P5 probably land together as a standalone `docker-compose.prod.yml`. An override of the existing file would be awkward, because production has no `db` service and the existing file hardcodes the credentials under `environment`, which beats `env_file`.

## P0. AWS foundations

Set up the account and the pieces the other tasks build on: one EC2 instance and one RDS for PostgreSQL instance in the same region and VPC.

- **Spending:** create a budget alert first. New accounts get up to $200 in credits on the Free plan, but that plan ends after 6 months or when the credits run out, whichever comes first, and the account then closes. To keep the arena running, it has to move to a paid plan before then. Check the current terms at sign-up.
- **Costs to watch:** every public IPv4 address costs $0.005 an hour, about $3.65 a month, beyond any free allowance. Stay single-AZ on RDS and don't add a NAT gateway.
- **Network:** give each instance its own security group. The app's allows 80 and 443 from anywhere. The database's allows 5432 only from the app's security group. The RDS instance has public access turned off.
- **Shell access:** prefer SSM Session Manager to an open port 22. If you do use SSH, allow only your own IP.
- **Address:** attach an Elastic IP, so DNS survives the instance stopping and starting, and register the domain through Route 53.
- **Instance type:** use x86 (`t3`), which needs no check that the images have Arm64 builds.
- **Image delivery:** CI builds each tested commit on `main` and pushes it to ECR, using OIDC rather than stored AWS keys, and the instance pulls it. A micro instance may be too small to run the Maven build in `Dockerfile`, and this way only tested commits can be deployed.
- **Database version:** use major version 15, to match local development.

To explore: whether to create all this by hand in the console first, then repeat it with Terraform or CloudFormation.

## P1. Production roster

`GAME_ROSTER_FILE` defaults to `classpath:roster-dev.json`, which is checked into git. Deployed as it is, anyone could read the hidden stats and bet with certainty. Generate the real roster offline with `RosterTuningTool`, keep it out of git, mount it on the host, and point `GAME_ROSTER_FILE` at it. This has to happen **before first boot**, because the roster only loads into an empty `gladiators` table (spec §3, Roster).

The app also refuses to start when it would load `roster-dev.json` outside the dev profile. A plain local `docker compose up` stops working as a result, so local runs use `ENV_FILE=.env.dev`.

## P2. Database credentials and exposure

`docker-compose.yml` hardcodes `user`/`password` and runs its own Postgres, published on `5432:5432`. The production compose file drops the `db` service, the `depends_on` and the hardcoded values, and points `SPRING_DATASOURCE_*` at the RDS endpoint. Network exposure is handled by the security groups in P0.

Give the app its own database user that owns the schema, because Flyway creates tables and triggers as it starts, and keep the RDS master user for administration. Connect over TLS with `sslmode` in the JDBC URL: `verify-full` also needs the RDS certificate bundle on the app's classpath or host.

To explore: whether to keep the credentials in an env file on the instance, or in SSM Parameter Store or Secrets Manager, read through the instance's IAM role.

## P3. HTTPS behind a reverse proxy

Form login sends passwords in plain text without TLS. Put a reverse proxy on the instance (Caddy gets Let's Encrypt certificates automatically once a domain points at the Elastic IP), and set `server.forward-headers-strategy=framework` so redirects and cookies know they're behind TLS. Don't publish `8080` in the production compose file, so the app can only be reached through the proxy. Check that SSE (`/live`) passes through the proxy unbuffered.

To explore: an Application Load Balancer with an ACM certificate instead of Caddy. It's worth learning, but it adds an hourly charge and does little for a single instance. Its idle timeout must also outlast the 15-second SSE heartbeat.

## P4. Freeze migrations

`CLAUDE.md` says migrations may still be edited in place because nothing is deployed. After the first real deploy, editing one breaks Flyway's checksum check on startup. Change that rule in `CLAUDE.md` and in spec §11/§12 when the app first deploys.

## P5. Restart policies

Add `restart: unless-stopped` to the app and proxy services, and enable the Docker service at boot, so an instance reboot doesn't leave the arena down. The engine already resumes the fight correctly after an outage (task 08).

The app and database can now fail separately, and RDS has maintenance windows. Reboot the RDS instance once in the middle of a fight, and check that the app reconnects and the fight resumes without a restart. Also check that the app recovers if RDS isn't reachable when it boots. Set the maintenance window to a quiet time.

## P6. Database backups

The ledger and Elo history live only in RDS. Turn on automated backups with a retention period of at least 7 days, checking first what your plan allows, and turn on deletion protection. Test a restore once. A restore creates a new RDS instance, so point a copy of the app at it, check the wallets match their ledgers, and delete it afterwards.

To explore: copying snapshots to another region or an S3 `pg_dump`, which would protect against losing the account or the region. It may not be worth it for virtual points.

## P7. Signup and login abuse limits

Signup and login have no rate limiting or CAPTCHA. Points are virtual, so the stakes are low, but bots could farm the leaderboard and brute-force passwords. Explore per-IP rate limits on `/auth/signup` and `/auth/login` without adding infrastructure (no Redis, per `CLAUDE.md`).

## P8. Health and uptime monitoring

There's no actuator, so nothing alerts anyone when the engine stops ticking. A free external uptime pinger on `/fights/current` is the cheapest fix. Add CloudWatch alarms for the EC2 status checks and for RDS storage and CPU credits, sent through SNS to email. To explore: an endpoint or check that confirms the engine ticked recently, not just that the app is up.
