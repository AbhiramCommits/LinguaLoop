# LinguaLoop

**Free, low-bandwidth, universally available language learning.** LinguaLoop
teaches vocabulary and listening comprehension on the web and on Android, and
measures whether learners actually retain what they study: every answer feeds
an SM-2 spaced-repetition schedule, a review queue, streaks and per-lesson
mastery. Content is tiny text plus 64 kbps audio, the offline-first clients
keep working without connectivity, and every experiment ships with retention
metrics so the product improves on evidence, not vibes.

## Architecture

```mermaid
flowchart LR
    web[Web<br/>React + Vite + Workbox] --> api
    android[Android<br/>Kotlin + Compose + Room] --> api
    api[Spring Boot 3 API<br/>Java 21] --> pg[(PostgreSQL 16<br/>source of truth)]
    api --> redis[(Redis 7<br/>queue / assignment cache)]
    api --> audio[Audio assets<br/>content-addressed Opus/MP3]
    tools[Python tools<br/>seed · audio pipeline · import · simulate] --> pg
    tools --> api
```

The API owns all learning logic (scheduling, experiments, metrics). Clients
are ports of one shared REST contract — see `docs/porting.md`. Full design:
`docs/architecture.md`.

## Quickstart

```bash
# 1. Start everything (PostgreSQL, Redis, API, web)
cd infra
docker compose up --build
#    (If port 5432 is taken, use `POSTGRES_PORT=15432 docker compose up -d --build`
#     and port 15432 in the DATABASE_URL values below.)

# 2. Seed the Spanish starter dataset (1 unit, 3 lessons, 32 exercises)
cd ../tools
uv sync
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" uv run seed.py

# 3. Generate the LISTEN audio (open-source Piper TTS, CC BY 4.0 — see CONTENT.md)
uv sync --group tts
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" \
  uv run --group tts -- python audio_pipeline.py --tts --output-dir ../audio

# 4. (Optional) fill the experiments dashboard with synthetic learners
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" \
  uv run -- python simulate_learners.py --learners 120
```

Open http://localhost:5173, register an account, and study. Swagger UI:
http://localhost:8080/swagger-ui — or run the full CI checks:

```bash
cd api      && ./gradlew check                 # tests + Testcontainers + coverage gate
cd web      && npm ci && npm run typecheck && npm run lint && npm run test
cd tools    && uv sync && uv run ruff check . && uv run pytest
cd android  && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd e2e      && npm ci && npx playwright install chromium && npx playwright test
```

| Screenshot | |
| --- | --- |
| Home (queue, streak, mastery ring) | Lesson player | Experiments dashboard |
| ![home](docs/images/home.png) | ![lesson](docs/images/lesson-player.png) | ![experiments](docs/images/experiments.png) |
| Author page (admin) | Android app | |
| ![author](docs/images/author.png) | ![android](docs/images/android-login.png) | |

## Experiment results

The experiments dashboard at `/experiments` reports retention per variant
(D1/D7 return, second-exposure accuracy, items per session) with two-proportion
z-test p-values against control. The numbers below are **simulated learners,
N=120 (the dashboard's `includeSimulated=true` view also counts a few real
test accounts, hence n=51+70); forgetting model p(recall) = min(1,
2^(−elapsed_days/h) · (1 + 0.35 · prior_successes)), h ~ LogNormal(median=2.0d,
σ=0.6); 14-day window; seed=42. These are NOT real learner measurements**
(see `tools/simulate_learners.py`).

| Variant | n | D1 return | D1 p-value | D7 return | D7 p-value | 2nd-exposure accuracy |
| --- | --- | --- | --- | --- | --- | --- |
| `due_first` (control) | 51 | 16% | — | 31% | — | 3.94 |
| `interleaved` | 70 | 11% | 0.49 | 27% | 0.61 | 4.23 |

**Metric definitions** — D1/D7 return rate: share of learners with a session
on their first-session day +1/+7 (UTC). Second-exposure accuracy: mean grade
on the second attempt of an exercise, averaged per learner. **How variants
were assigned:** deterministic bucketing — SHA-256 of
`"{experimentKey}:{learnerId}"` mapped into weighted ranges, persisted on
first exposure (see `docs/architecture.md` for the math), 50/50 weights.

## Numbers (measured on this repository)

| Metric | Value |
| --- | --- |
| API tests (JUnit + Testcontainers) | 55 passing |
| Scheduler + experiment package coverage (JaCoCo gate ≥ 75%) | 82.7% instructions (scheduler 82.2%, experiment 81.8%) |
| Web tests (vitest) | 26 passing + Playwright e2e (journey, keyboard-only, axe) |
| Tools tests (pytest) + ruff | 22 passing, 0 lint errors |
| Android tests (JUnit/Turbine/MockWebServer + 1 instrumented Compose e2e) | 11 + 1 passing on emulator |
| API latency (k6, 20 VUs, 60 s) | 2,771 requests, p95 198 ms (lesson 176 ms, queue 189 ms), 0.03% failed |
| Audio size, 8 LISTEN clips | source WAV 1,756 KiB → Opus 430 KiB (−75.5%), MP3 444 KiB (−74.7%) |

## Accessibility

- Web: keyboard-only lesson completion is covered by an e2e test; every
  exercise renderer announces correct/incorrect results through
  `aria-live` regions; captions are an explicit toggle (`aria-expanded`);
  mastery rings are `progressbar`s with visible percentages; icon controls
  carry accessible names; focus is always visible; color is never the only
  signal (feedback always includes words).
- Enforced in CI: `@axe-core/playwright` scans the home, lesson, author and
  experiments pages on every build and **fails on any serious or critical
  violation** (`.github/workflows/ci.yml`).
- Android: TalkBack content descriptions on icons, `liveRegion` semantics
  for answer feedback, Material minimum 48dp touch targets
  (`docs/porting.md`).

## API reference

Interactive docs at `/swagger-ui` (OpenAPI 3). Errors are RFC 7807
`application/problem+json`.

| Endpoint | Auth | Description |
| --- | --- | --- |
| `POST /api/auth/register`, `POST /api/auth/login` | — | Registration / JWT login |
| `GET /api/languages`, `GET /api/languages/{id}/units` | — | Catalogue |
| `GET /api/units/{id}`, `GET /api/lessons/{id}` | — | Units / lessons with exercises |
| `GET /api/audio/{id}` | — | Audio streaming (Opus/MP3, ETag, Range, immutable) |
| `POST /api/sessions` | JWT | Start a study session (assigns hint delay) |
| `POST /api/sessions/{id}/attempts` | JWT | Grade an exercise (updates SM-2 state) |
| `POST /api/sessions/{id}/complete` | JWT | Finish the session (updates streak) |
| `GET /api/learners/me/queue` | JWT | Due review queue (experiment-ordered) |
| `GET /api/learners/me/stats` | JWT | Stats, streak, per-lesson mastery |
| `GET /api/experiments`, `GET /api/experiments/{key}/results` | JWT | Experiments + retention metrics |
| `/api/admin/**` (CRUD + `POST /api/admin/import`) | ADMIN | Content authoring & bundle import |

## Repositories, docs, deployment

Layout, authoring (`docs/authoring.md`), porting notes (`docs/porting.md`),
deployment (`docs/deploy.md` + `infra/docker-compose.prod.yml`), content
rules (`CONTENT.md`) and contribution guide (`CONTRIBUTING.md`). License:
MIT.
