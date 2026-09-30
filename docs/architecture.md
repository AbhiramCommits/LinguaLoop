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
| `audio`            | Audio asset metadata + static audio file serving            |
| `auth`             | JWT issuance/validation, bcrypt password hashing            |
| `common`           | RFC 7807 error handling, CORS, OpenAPI config               |

## Data model (Flyway `V1__init.sql`)

- `language` → `unit` → `lesson` → `exercise` (ordered catalogue).
- `exercise.type` is a PostgreSQL enum: `TRANSLATE`, `MULTIPLE_CHOICE`,
  `LISTEN`. `choices` is `JSONB` (only used by `MULTIPLE_CHOICE`).
  `audio_asset_id`/`caption` are used by `LISTEN` exercises.
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
`review_state` values:

- `grade < 3` (failed recall): `repetitions = 0`, `interval_days = 1`,
  `lapses + 1`, ease factor floored at 1.3.
- `grade >= 3` (successful recall): `repetitions + 1`; interval 1 day after
  the first success, 6 days after the second, then
  `round(intervalDays * easeFactor)`; ease factor
  `EF' = EF + (0.1 - (5-g) * (0.08 + (5-g) * 0.02))` clamped to [1.3, 2.5].
- `due_at` is anchored to the start of the learner's local day: local
  today (learner timezone) + `intervalDays` days at 00:00.

The review queue endpoint returns every exercise with `due_at <= now()`
ordered by `due_at`, capped at a configurable daily limit (default 30),
backfilled with never-seen exercises from the learner's active unit (the
unit of their most recent session, falling back to the first unit in the
catalogue).

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

`POST /api/sessions` assigns a `variant_key` from a weighted list configured
in `application.yml` (`app.experiment.variants`). All client behavior driven
by the variant key is implemented in clients, keeping the API neutral.
