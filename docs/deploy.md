# Deploying LinguaLoop

This file describes the production deployment path, migration strategy,
health checks and a concrete hosting recipe. The images are multi-stage
builds (see `infra/api.Dockerfile` and `infra/web.Dockerfile`); the
development stack lives in `infra/docker-compose.yml`, a production-oriented
variant in `infra/docker-compose.prod.yml`.

## Images

- `infra/api.Dockerfile` — stage 1 `gradle:8.14.3-jdk21` runs `gradle build
  -x test`; stage 2 is a slim `eclipse-temurin:21-jre-alpine` running as a
  non-root user. The jar is `build/libs/lingualoop-api-0.1.0.jar`.
- `infra/web.Dockerfile` — stage 1 `node:22-alpine` runs `npm ci` and
  `npm run build`; stage 2 `nginx:alpine` serves `dist/` and proxies
  `/api/` to the API service (`web/nginx.conf`).

CI builds and pushes the API image to GHCR on every tag
(`ghcr.io/<owner>/lingualoop-api:<tag>` and `:latest`) — see
`.github/workflows/ci.yml`.

## Required environment variables

All configuration comes from the environment (no secrets in code):

| Variable                     | Required | Notes                                          |
| ---------------------------- | -------- | ---------------------------------------------- |
| `SPRING_DATASOURCE_URL`      | yes      | `jdbc:postgresql://host:5432/db`               |
| `SPRING_DATASOURCE_USERNAME` | yes      |                                                |
| `SPRING_DATASOURCE_PASSWORD` | yes      |                                                |
| `JWT_SECRET`                 | yes      | >= 32 chars (HS256); generate with `openssl rand -hex 32` |
| `JWT_TTL`                    | no       | default `24h`                                  |
| `SPRING_DATA_REDIS_HOST/PORT`| no       | default `localhost:6379`                       |
| `APP_CORS_ORIGINS`           | no       | comma-separated; set to the web origin(s)      |
| `APP_AUDIO_DIR`              | no       | where content-addressed audio lives (mounted volume) |
| `APP_ADMIN_EMAILS`           | no       | comma-separated; accounts registered with these emails become ADMIN (bootstrap only) |

## Flyway migration strategy

- Migrations are versioned SQL in `api/src/main/resources/db/migration`
  (`V1__init.sql` … `V4__learner_role.sql`) and run automatically at API
  startup. **Never edit an applied migration** — add a new `V<n>` file.
- Deploy order: run the new API version against the old database schema
  (backwards-compatible migrations), then roll out. Rollback = deploy the
  previous API image; write migrations so old code keeps working.
- `spring.jpa.hibernate.ddl-auto=validate` fails startup on any schema
  drift, so a broken migration cannot silently run a mismatched app.
- Backups: `pg_dump` before each release; the schema is fully
  reconstructible from Flyway + `tools/seed.py` + `tools/import_unit.py`.

## Health checks

- Liveness/readiness: `GET /actuator/health` (returns `{"status":"UP"}`;
  exposed in the compose healthcheck with `wget`).
- The web app is static; nginx returns 200 on `/`.
- Logs go to stdout (`docker logs`).

## One concrete hosting path

- **API**: Fly.io (`fly launch` with the `infra/api.Dockerfile`; 1GB RAM
  is plenty) or Railway (GitHub-connected service pointing at
  `infra/api.Dockerfile`).
- **Postgres**: Neon (serverless, connection-pooler URL works with JDBC)
  or Supabase. Run `tools/seed.py` once after the first boot.
- **Redis**: Upstash (compatible with Spring Data Redis over TLS) or
  Fly.io's managed Redis.
- **Web**: Cloudflare Pages — build command `npm run build`, output
  `dist/`, root `web/`; add a rewrite `/* -> /index.html` and set
  `APP_CORS_ORIGINS` on the API to the Pages origin. (Or serve the nginx
  image anywhere.)
- **Audio**: the API serves `/api/audio/{id}` from `APP_AUDIO_DIR`; put
  the content-addressed files on an object store or a persistent volume,
  or front the endpoint with a CDN since responses carry
  `Cache-Control: public, max-age=31536000, immutable`.
- **TLS**: terminate at the platform edge for API and web.

## Production compose example

`infra/docker-compose.prod.yml` runs the built images with pinned
versions, healthchecks and a Postgres backup sidecar schedule. Set the
environment via a `.env` file (never commit secrets) and start with:

```bash
cd infra
docker compose -f docker-compose.prod.yml --env-file .env up -d
```
