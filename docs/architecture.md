# LinguaLoop architecture

## Overview

LinguaLoop is a spaced-repetition language-learning platform. Learners study
lessons (vocabulary, translation, multiple-choice and listening exercises),
and every graded attempt feeds a per-learner review schedule (SM-2 variant)
that decides when each exercise must be reviewed again. The review queue is
what a learner sees on a "review day".

```
┌──────────┐   ┌───────────┐   ┌─────────────────┐
│ web      │──▶│ api       │──▶│ postgres        │  source of truth
│ android* │   │ (Java 21  │   │  + Flyway V1    │
└──────────┘   │  Boot 3)  │   └─────────────────┘
               │           │──▶│ redis           │  review-queue + session-state cache
               └───────────┘   └─────────────────┘
```

*Android client is planned, not implemented.

## API modules

| Package            | Responsibility                                              |
| ------------------ | ----------------------------------------------------------- |
| `content`          | Languages, units, lessons, exercises (read-only catalogue)  |
| `learner`          | Learners, sessions, attempts, streaks, review queue, stats  |
| `scheduler`        | SM-2 style scheduling of reviews (`review_state` updates)   |
| `experiment`       | Session variant assignment for A/B experiments              |
| `audio`            | Content-addressed audio assets + streaming (Opus/MP3)       |
| `auth`             | JWT issuance/validation, bcrypt password hashing            |
| `common`           | RFC 7807 error handling, CORS, OpenAPI config               |

## Data model (Flyway `V1__init.sql`)

- `language` → `unit` → `lesson` → `exercise` (ordered catalogue).
- `exercise.type` is a PostgreSQL enum: `TRANSLATE`, `MULTIPLE_CHOICE`,
  `LISTEN`. `choices` is `JSONB` (only used by `MULTIPLE_CHOICE`).
  `audio_asset_id`/`caption` are used by `LISTEN` exercises.
- `audio_asset` (v2) holds pipeline-produced audio: `sha256`, `opus_path`,
  `mp3_path` (content-addressed relative paths), `duration_ms`, `bytes`.
  `GET /api/audio/{id}` streams with ETag, immutable caching, Range support
  and Opus/MP3 Accept negotiation; files live under `APP_AUDIO_DIR`.
- `learner` holds bcrypt password hashes; registration hashes, never stores,
  plaintext passwords.
- `review_state` is unique per `(learner_id, exercise_id)` and is the
  scheduler's working memory: `ease_factor`, `interval_days`, `repetitions`,
  `due_at`, `last_grade`, `lapses`.
- `session` records a study session (with an experiment `variant_key`);
  `attempt` records each graded answer (`grade` 0..5, `latency_ms`,
  `hint_shown`).
- `streak` tracks consecutive active days per learner (computed in the
  learner's timezone).

## Review scheduling (SM-2 variant)

On every attempt `Sm2Scheduler` (pure, dependency-free) computes the new
`review_state` values. With grade `g ∈ {0..5}`, current ease factor `EF`,
interval `I` and repetition count `r`:

- `g < 3` (failed recall):
  - `r' = 0`, `I' = 1` day, `lapses' = lapses + 1`, `EF' = max(1.3, EF)`
- `g >= 3` (successful recall):
  - `r' = r + 1`
  - `EF' = clamp(EF + (0.1 − (5−g)·(0.08 + (5−g)·0.02)), 1.3, 2.5)`
  - `I' = 1` when `r' = 1`; `I' = 6` when `r' = 2`;
    otherwise `I' = round(I · EF')`
- `due_at` is anchored to the learner's local day:
  `due = localToday(timezone) + I' days` at 00:00 in that timezone.

The review queue returns exercises with `due_at <= now()` ordered by
`due_at`, capped at a configurable daily limit (default 30), backfilled
with never-seen exercises from the learner's active unit — subject to the
`lesson_ordering` experiment (below).

## Mastery and streaks

- Mastery per lesson = fraction of its exercises with
  `repetitions >= 3` and `easeFactor >= 2.0`. `GET /api/learners/me/stats`
  exposes per-lesson and per-unit mastery for the active unit.
- Streaks update on session completion (not per attempt), using the
  learner's timezone for the day boundary. Same-day completion does not
  double-increment; a skipped day resets `current_days` but never lowers
  `longest_days`.

## Redis usage

Redis is a cache, never the source of truth:

- `queue:{learnerId}` — serialized review queue JSON, TTL 15 min,
  invalidated on every attempt write.
- `session-state:{sessionId}` — lightweight study-session cursor (JSON),
  TTL 24 h, refreshed per attempt; the `session`/`attempt` tables remain
  authoritative.

If Redis is unavailable or cold the API still serves correct queues and
sessions straight from PostgreSQL (degraded mode).

## Auth

- `POST /api/auth/register` — creates learner, bcrypt-hashes password.
- `POST /api/auth/login` — returns a signed HS256 JWT (subject = learner id).
- All `/api/learners/**` and `/api/sessions/**` routes require
  `Authorization: Bearer <token>`. Content routes are public.

## Errors

All error responses follow RFC 7807 (`application/problem+json`) with
`type`, `title`, `status`, `detail` and `instance` fields.

## Experiments

`experiment` → `variant` (weighted, JSONB config, explicit control flag) →
`assignment` (unique per learner+experiment, persisted on first exposure).
`ExperimentClient.variantFor()` resolves DB-first, falls back to
deterministic SHA-256 bucketing on first exposure (upsert-safe against
races), and caches the result in Redis. DRAFT experiments never assign and
expose no results; STOPPED serves persisted assignments only.

Bucketing math: `u = int64(first 8 bytes of SHA-256("{key}:{learnerId}")) & (2^63-1) / 2^63`
maps each learner to a uniform value in `[0, 1)`; the variant is the first
index `i` where `u · Σweights − Σ_{j≤i} weight_j < 0`. Because the
assignment row is persisted and wins thereafter, later weight edits never
reshuffle existing learners — the hash only decides first exposure.

- `lesson_ordering` reorders the review queue (`due_first` control vs
  `interleaved` at `config.ratio`).
- `hint_timing` sets `session.hint_delay_seconds`, which the web lesson
  player honors before enabling hints.

Retention metrics (D1/D7 return rate in UTC, second-exposure accuracy,
items per session) are computed from real session/attempt data with a
two-proportion z-test against control; simulated learners
(`learner.is_simulated`) are excluded unless explicitly requested.
