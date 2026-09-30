# Contributing to LinguaLoop

Thank you for contributing! LinguaLoop is a free, low-bandwidth language
learning platform — API, web client, Android app and tooling.

## Code of conduct

Be kind, be patient, assume good intent. Contributions that demean others
will not be merged.

## Repository map

| Path       | What lives there                                     |
| ---------- | ---------------------------------------------------- |
| `/api`     | Java 21 / Spring Boot 3 API (see docs/architecture)  |
| `/web`     | React 18 + TypeScript web client                     |
| `/android` | Kotlin + Jetpack Compose Android client              |
| `/tools`   | Python utilities (seed, audio pipeline, import, sim) |
| `/e2e`     | Playwright + axe end-to-end suite                    |
| `/infra`   | Docker Compose, Dockerfiles, k6 load script          |
| `/docs`    | Architecture, porting, authoring, deployment notes   |

## Getting started

```bash
cd infra && docker compose up --build     # postgres, redis, api, web
cd ../tools && uv sync
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" uv run seed.py
```

Open http://localhost:5173 (web), http://localhost:8080/swagger-ui (API).

## Before opening a PR

- Run the checks CI runs (see `.github/workflows/ci.yml`):
  - `cd api && ./gradlew check` (tests + Testcontainers + JaCoCo gate)
  - `cd web && npm ci && npm run typecheck && npm run lint && npm run test`
  - `cd tools && uv sync && uv run ruff check . && uv run pytest`
  - `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
  - `cd e2e && npm ci && npx playwright install chromium && npx playwright test`
    (against a running stack with `APP_ADMIN_EMAILS=author@e2e.example`)
- Add tests for new behavior; keep the API contract stable — clients in this
  repo share it, and any change must be mirrored in `docs/porting.md`.
- Follow the existing style: no code comments that restate the obvious,
  meaningful commit messages, logical commits.

## Commit conventions

Conventional-commit prefixes per area, e.g. `feat(api):`, `fix(web):`,
`feat(tools):`, `chore(infra):`, `docs:`. Commits are small and self-contained.

## Content contributions

New language units are content, not code — see `CONTENT.md` for the unit
bundle format, licensing rules and the `tools/import_unit.py` flow.

## License

By contributing you agree that your contribution is licensed under the MIT
license (see `LICENSE`). Audio and lesson content have their own rules —
never commit audio or text you don't have redistribution rights for
(see `CONTENT.md`).
