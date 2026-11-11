# Production compose stack

`docker-compose.prod.yml` runs Caddy and the app against an external database. Caddy is the only way in: it serves HTTPS, redirects HTTP to it, and the app's port isn't published. Everything specific to a host comes from `.env.prod`, which is gitignored. `.env.prod.example` lists what it needs.

Pass the env file on every command, because compose reads it to fill in the file:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f app
docker compose -f docker-compose.prod.yml --env-file .env.prod down
```

Don't use `down -v` on a real host: it deletes Caddy's certificates.

## Trying it on a laptop

This needs Docker and nothing from AWS. Ports 80 and 443 must be free. With `SITE_DOMAIN=localhost`, Caddy uses its own internal certificate, so a browser warns about it and `curl` needs `-k`.

1. Build the image and start a throwaway Postgres:

   ```bash
   docker build -t arena:local .
   docker run --rm -d --name arena-prod-test-db -e POSTGRES_DB=arena -e POSTGRES_USER=arena \
     -e POSTGRES_PASSWORD=local-only -p 55432:5432 postgres:15-alpine
   ```

2. Create `.env.prod`. The test roster is safe for this, and the last three lines run a fight every 3 minutes instead of every hour:

   ```bash
   SITE_DOMAIN=localhost
   APP_IMAGE=arena:local
   SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:55432/arena
   SPRING_DATASOURCE_USERNAME=arena
   SPRING_DATASOURCE_PASSWORD=local-only
   ROSTER_FILE=./src/test/resources/roster-test.json
   GAME_FIGHT_CRON=0 */3 * * * *
   GAME_ROUND_INTERVAL=5s
   GAME_ENGINE_CRON=*/5 * * * * *
   ```

   `host.docker.internal` works on Docker Desktop. On Linux, use the host's Docker bridge address instead, usually `172.17.0.1`.

3. Start the stack with the first command above. The app may restart once if Postgres isn't ready yet.

4. Check it:

   - `curl -i http://localhost/` answers with a 308 to `https://localhost/`.
   - `curl -k https://localhost/health/engine` reports `OK`.
   - `curl -m 3 http://localhost:8080/` fails to connect.
   - In a browser at <https://localhost>, sign up, log in, bet on the next fight, and watch it play out round by round. After log-in, the `JSESSIONID` cookie is marked `Secure`.
   - `curl -kN https://localhost/live` prints each event as it happens, and a `:` heartbeat comment every 15 seconds.

5. Clean up. Delete `.env.prod` too before using this checkout to deploy:

   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod down -v
   docker stop arena-prod-test-db
   ```

## Client IPs

The app trusts forwarded headers (`server.forward-headers-strategy=framework`), which is only safe because nothing can reach it except Caddy. Caddy replaces `X-Forwarded-For`, `-Proto` and `-Host` with what it saw, and `deploy/Caddyfile` removes the other forwarded headers the app reads, so a client that sends them can't change what the app believes.

The signup and login limits count attempts by that client IP, so check it on any new host. Send more signups than `GAME_AUTH_LIMITS_SIGNUP_MAX_ATTEMPTS` with a different forged header each time, and the extra ones must still get a 429. Fetch a page first for the session cookie and CSRF token, because a POST without them gets a 403 and isn't counted:

```bash
TOKEN=$(curl -ks -c /tmp/arena-cookies https://localhost/auth/signup | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p')
for i in 1 2 3 4 5 6 7; do
  curl -ks -o /dev/null -w '%{http_code}\n' -b /tmp/arena-cookies -H "X-Forwarded-For: 203.0.113.$i" \
    --data-urlencode "_csrf=$TOKEN" -d 'email=bad&displayName=nobody&password=nothing-real' https://localhost/auth/signup
done
```

At the default limit, five lines of `200` are followed by `429`s. If real players are refused together, the app is seeing one shared address: check that Caddy logs each client's own IP (Docker's networking can hide it, notably for IPv6), and give Caddy `trusted_proxies` if a load balancer ever sits in front of it.
