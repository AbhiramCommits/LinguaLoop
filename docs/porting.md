# Porting notes: web ↔ Android

The Android client (`/android`) consumes the **exact same REST API** as the web
client (`/web`). No API changes were made for Android, and none were needed:
when something was missing, it is recorded below instead of forking the
contract.

## Shared REST contract

| Concern                 | Endpoint                                        | Used by both clients? |
| ----------------------- | ----------------------------------------------- | --------------------- |
| Register / login        | `POST /api/auth/register`, `POST /api/auth/login` | Yes (JWT bearer)    |
| Lesson content          | `GET /api/lessons/{id}`                          | Yes                  |
| Home: queue             | `GET /api/learners/me/queue`                     | Yes                  |
| Home: stats/streak/mastery | `GET /api/learners/me/stats`                   | Yes                  |
| Start session           | `POST /api/sessions`                             | Yes                  |
| Grade an exercise       | `POST /api/sessions/{id}/attempts`               | Yes                  |
| Finish session          | `POST /api/sessions/{id}/complete`               | Yes                  |
| Audio (LISTEN)          | `GET /api/audio/{id}` (Opus default, MP3 via `Accept`) | Yes           |

Field names are byte-for-byte identical in both clients, including awkward
ones: the queue item's "is this new?" flag is the JSON field `"new"` (web:
`@JsonProperty("new")`, Android: `@SerialName("new")`).

## Logic shared *conceptually* (not shared as code)

Both clients implement the same semantics independently:

- **Grading**: answer normalization (strip diacritics → lowercase → drop
  non-alphanumerics) and grade mapping (correct → 5, wrong → 2). The same
  algorithm exists in `web/src/api/client.ts` and
  `android/.../util/Answers.kt`.
- **Offline attempt queue**: attempts (and the session completion) are written
  locally on network failure, flushed FIFO when connectivity returns —
  attempts strictly before the completion they belong to — and server 4xx
  responses drop the op (poison-pill protection) while network errors stop
  the pass. Web: IndexedDB + `online` event; Android: Room + WorkManager
  (`NetworkType.CONNECTED` constraint).
- **Optimistic grading**: mark the exercise answered immediately; roll back
  only on real server errors, keep it queued on connectivity errors.
- **`hint_timing`**: the session payload's `hintDelaySeconds` gates the hint
  button / captions toggle with a countdown; `hintShown` is recorded on the
  attempt in both clients.
- **Everything about the scheduler, SM-2 state, streaks, queue ordering and
  experiments lives server-side** — clients never compute due dates, mastery
  or assignments; that is the main reason the two ports can stay in lockstep.

## What had to be reimplemented per platform

| Concern              | Web                                      | Android                                    |
| -------------------- | ---------------------------------------- | ------------------------------------------ |
| Language             | TypeScript (strict)                      | Kotlin 2.x                                 |
| UI                   | React 18                                 | Jetpack Compose + Material 3               |
| State management     | TanStack Query                           | ViewModel + StateFlow (Coroutines/Flow)    |
| Networking           | `fetch`                                  | Retrofit + OkHttp + kotlinx.serialization  |
| Auth storage         | `localStorage`                           | `EncryptedSharedPreferences` (Keystore)    |
| Offline cache        | IndexedDB (via `idb`) + Workbox SW       | Room (lesson JSON, sessions, pending ops)  |
| Sync trigger         | `online` event + SW runtime caching      | WorkManager `CoroutineWorker` (network constraint) |
| Audio                | HTML `<audio>`                           | ExoPlayer (Media3), downloaded to app-private storage |
| Navigation           | React Router                            | Navigation Compose                         |
| Home persistence     | SW-cached API responses                 | Room `cache_entries` snapshot              |

## Contract gaps and constraints (noted, not forked)

1. **A session can only be started online.** `POST /api/sessions` must reach
   the server to mint a session id. Offline *completion* of an already-started
   lesson works in both clients; offline *start* of a fresh lesson is
   impossible by contract. Android surfaces this with an explicit error.
2. **There is no `GET /api/sessions/{id}`.** A client that loses its process
   state cannot re-fetch an in-progress session; both clients keep session
   state locally (web: React state; Android: Room `sessions`).
3. **No pagination or incremental sync** on `/queue` or `/stats`. Both
   clients fetch the full payload and cache the last-known snapshot.
4. **No per-exercise audio integrity metadata** beyond the asset id,
   duration and byte size in the lesson JSON. Android trusts the downloaded
   file once it is non-empty.
5. **Attempt ordering across devices** is client-local: two clients
   (web + phone) can queue attempts for the same session concurrently; the
   server applies them in arrival order. No attempt idempotency key exists
   in the contract, so a crash between POST and queue-delete can duplicate an
   attempt (web and Android share this exposure).
6. **No endpoint lists a unit's lessons with mastery** other than
   `/api/learners/me/stats` (which embeds the active unit only). Android
   mirrors the web home screen and shows exactly that.
