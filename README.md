# LinguaLoop

An open-source language-learning platform that teaches vocabulary and listening
comprehension, and measures whether learners actually retain material.

## Repository layout

| Path       | Description                                                        |
| ---------- | ------------------------------------------------------------------ |
| `/api`     | Java 21 + Spring Boot 3 REST API (Gradle Kotlin DSL)               |
| `/web`     | React 18 + TypeScript + Vite web client                            |
| `/android` | Kotlin + Jetpack Compose Android client (see `docs/porting.md`)     |
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
| `GET /api/audio/{id}`            | —    | Stream the asset (Opus/MP3, ETag, Range)     |
| `GET /api/experiments`           | JWT  | Experiment definitions                       |
| `GET /api/experiments/{key}/results` | JWT | Retention metrics + z-test p-values (DRAFT-guarded) |

Errors are RFC 7807 `application/problem+json`. See
`docs/architecture.md` for the data model, scheduling algorithm and Redis
caching design.

## Experiments

A/B experiments live in the `assignment` table: learners are bucketed
deterministically (SHA-256 of `{experimentKey}:{learnerId}` into weighted
variant ranges) on first exposure, and the assignment is persisted — so
weights can change later without reshuffling anyone. Two experiments ship:

- `lesson_ordering` — `due_first` (control, strict SM-2 due order) vs
  `interleaved` (due + new items mixed at `config.ratio`). Changes the
  review queue order.
- `hint_timing` — `hint_at_5s` vs `hint_at_12s`. The delay is returned in
  the session payload and the web lesson player gates its hint button on it.

Retention results (D1/D7 return rate, second-exposure accuracy, items per
session, two-proportion z-test p-values against control) are computed from
real session/attempt data at `GET /api/experiments/{key}/results`, guarded
against DRAFT experiments, and shown on the `/experiments` page in the web
app (a variant needs n >= 30 before its row counts as "enough data").

### Simulated learners

`tools/simulate_learners.py` generates synthetic learners that study through
the real API (register, sessions, attempts, queue, completion) so the
assignment, scheduler and metrics code paths run end to end; session
timestamps are then backdated so retention metrics have history. Every
simulated learner is tagged `learner.is_simulated = true`, and results
exclude them by default (`?includeSimulated=true` to opt in).

**Anything reported from those runs must carry this label:**

> simulated learners, N=120; forgetting model
> p(recall)=min(1, 2^(-elapsed_days/h)·(1+0.35·prior_successes)),
> h ~ LogNormal(median=2.0d, sigma=0.6); window=14 days; seed=42.
> NOT real learner measurements.

## Audio for LISTEN exercises

Audio is produced by the pipeline in `tools/audio_pipeline.py`:

1. It loudness-normalizes (EBU R128) and transcodes each clip to 64 kbps
   Opus + 64 kbps MP3 with ffmpeg.
2. Outputs land in a content-addressed layout
   (`audio/<sha256[:2]>/<sha256>.<ext>`) under the output dir.
3. It upserts `audio_asset` rows and links them to exercises — idempotent:
   re-running on unchanged input creates no new assets.

Generate the Spanish clips from the seed transcripts with Piper TTS (open
source; the `es_ES-mls_10246-low` voice is derived from Multilingual
LibriSpeech, CC BY 4.0 — see `CONTENT.md`):

```bash
cd tools
uv sync --group tts
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" \
  uv run --group tts -- python audio_pipeline.py --tts \
    --tts-data-dir tts-models --output-dir ../audio
```

Or feed your own recordings via a CSV manifest
(`exercise_id, source_file, transcript`):

```bash
uv run -- python audio_pipeline.py --manifest clips.csv --sources recordings --output-dir ../audio
```

The API streams assets from `APP_AUDIO_DIR` (the repo-root `audio/` dir is
mounted into the API container by Docker Compose) via
`GET /api/audio/{id}` with ETag, `Cache-Control: public, max-age=31536000,
immutable`, HTTP Range support and Opus/MP3 Accept negotiation. Audio files
are gitignored; only their provenance is recorded in `CONTENT.md`.

