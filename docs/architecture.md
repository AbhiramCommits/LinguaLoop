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

On every attempt the scheduler updates `review_state`:

- First time an exercise is graded: `repetitions = 1`; interval is 1 day if
  `grade >= 3`, else 0 days (due again immediately).
- Subsequent reviews, `grade >= 3`: `interval = interval * ease_factor`
  (minimum 1 day for the second review), `ease` nudged up.
- Any `grade < 3`: `lapses += 1`, `repetitions = 0`, interval reset to 1 day.

The queue endpoint returns every exercise whose `due_at <= now()`, ordered by
`due_at`.

## Redis usage

Redis is a cache, never the source of truth:

- `review-queue:{learnerId}` — serialized due-queue JSON, TTL 5 min,
  invalidated on every new attempt.
- `session-state:{sessionId}` — lightweight study-session cursor (JSON),
  TTL 24 h, refreshed per attempt; the `session`/`attempt` tables remain
  authoritative.

If Redis is unavailable the API still serves queues and sessions straight
from PostgreSQL (degraded mode).

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
