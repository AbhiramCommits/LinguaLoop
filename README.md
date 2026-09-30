# LinguaLoop

An open-source language-learning platform that teaches vocabulary and listening
comprehension, and measures whether learners actually retain material.

## Repository layout

| Path       | Description                                                        |
| ---------- | ------------------------------------------------------------------ |
| `/api`     | Java 21 + Spring Boot 3 REST API (Gradle Kotlin DSL)               |
| `/web`     | React 18 + TypeScript + Vite web client                            |
| `/android` | Kotlin Android app (planned; placeholder for now)                  |
| `/tools`   | Python 3.12 CLI utilities (uv + `pyproject.toml`), incl. `seed.py` |
| `/infra`   | Docker Compose, Dockerfiles, SQL init scripts                      |
| `/docs`    | Architecture and design docs                                       |

## Run the whole stack

```bash
# 1. Start everything (PostgreSQL 16, Redis 7, API, web)
cd infra
docker compose up --build

# 2. Seed the Spanish starter dataset (1 unit, 3 lessons, 32 exercises, 8 LISTEN)
cd ../tools
uv sync
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" uv run seed.py
```

Then open:

- Web app: http://localhost:5173 (register an account, browse content, study, review)
- Swagger UI: http://localhost:8080/swagger-ui
- API health: http://localhost:8080/actuator/health

The seed is idempotent — run it again and it reports "nothing to do".
Pass `--reset` to wipe and reseed.

> If something else already listens on port 5432, run
> `POSTGRES_PORT=15432 docker compose up -d` and use
> `postgresql://lingualoop:lingualoop@localhost:15432/lingualoop` when seeding.

### Local development (Docker only for Postgres + Redis)

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis

cd api
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/lingualoop"
export SPRING_DATASOURCE_USERNAME=lingualoop
export SPRING_DATASOURCE_PASSWORD=lingualoop
export JWT_SECRET="$(openssl rand -hex 32)"
./gradlew bootRun        # API on :8080

cd ../web
npm install
npm run dev              # Vite on :5173, proxies /api to :8080
```

Run the API test suite (needs Docker for Testcontainers):

```bash
cd api
./gradlew test
```

## Configuration

The API reads all configuration from environment variables — no secrets are
hardcoded in code. Docker Compose ships dev-only defaults (see
`infra/.env.example`).

| Variable                     | Required | Default                 |
| ---------------------------- | -------- | ----------------------- |
| `SPRING_DATASOURCE_URL`      | yes      | —                       |
| `SPRING_DATASOURCE_USERNAME` | yes      | —                       |
| `SPRING_DATASOURCE_PASSWORD` | yes      | —                       |
| `JWT_SECRET`                 | yes      | — (min 32 chars, HS256) |
| `JWT_TTL`                    | no       | `24h`                   |
| `SPRING_DATA_REDIS_HOST`     | no       | `localhost`             |
| `SPRING_DATA_REDIS_PORT`     | no       | `6379`                  |
| `APP_CORS_ORIGINS`           | no       | `http://localhost:5173` |
| `APP_AUDIO_DIR`              | no       | `./audio`               |

## API surface

| Endpoint                         | Auth | Description                                  |
| -------------------------------- | ---- | -------------------------------------------- |
| `POST /api/auth/register`        | —    | Create account (bcrypt password hash)        |
| `POST /api/auth/login`           | —    | Obtain JWT bearer token                      |
| `GET /api/languages`             | —    | Language catalogue                           |
| `GET /api/languages/{id}/units`  | —    | Units of a language                          |
| `GET /api/units/{id}`            | —    | Unit with lessons                            |
| `GET /api/lessons/{id}`          | —    | Lesson with exercises                        |
| `POST /api/sessions`             | JWT  | Start a study session (assigns variant)      |
| `POST /api/sessions/{id}/attempts` | JWT | Grade an exercise (0–5), updates SM-2 state  |
| `POST /api/sessions/{id}/complete` | JWT | End the session                              |
| `GET /api/learners/me/queue`     | JWT  | Due spaced-repetition review queue           |
| `GET /api/learners/me/stats`     | JWT  | Retention stats (attempts, mastery, streak)  |
| `GET /api/audio/assets/{id}`     | —    | Audio asset metadata (LISTEN exercises)      |

Errors are RFC 7807 `application/problem+json`. See
`docs/architecture.md` for the data model, scheduling algorithm and Redis
caching design.

## Audio for LISTEN exercises

The seed registers audio asset metadata whose URLs point to
`/audio/es/lesson-3/...mp3`. Drop the actual mp3 files into the API's audio
directory (env `APP_AUDIO_DIR`, default `./audio` in `api/`) and the API will
serve them. Captions carry full transcripts, so LISTEN exercises remain
usable before audio files are present.
