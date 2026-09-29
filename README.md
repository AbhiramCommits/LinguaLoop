# LinguaLoop

An open-source language-learning platform that teaches vocabulary and listening
comprehension, and measures whether learners actually retain material.

## Repository layout

| Path      | Description                                                        |
| --------- | ------------------------------------------------------------------ |
| `/api`    | Java 21 + Spring Boot 3 REST API (Gradle Kotlin DSL)               |
| `/web`    | React 18 + TypeScript + Vite web client                            |
| `/android`| Kotlin Android app (planned; placeholder for now)                  |
| `/tools`  | Python 3.12 CLI utilities (uv + `pyproject.toml`), incl. `seed.py` |
| `/infra`  | Docker Compose, Dockerfiles, SQL init scripts                      |
| `/docs`   | Architecture and design docs                                       |

## Quick start

See the root [README section below](#run-the-whole-stack-with-docker-compose)
for one-command setup, and `docs/architecture.md` for how the system works.

## Run the whole stack with Docker Compose

```bash
docker compose -f infra/docker-compose.yml up --build
```

This brings up PostgreSQL 16, Redis 7, the API (port 8080) and the web app
(port 5173, proxying `/api` to the API).

### Seed the content

```bash
cd tools
uv sync
DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" uv run seed.py
```

### Try it

- Web app: http://localhost:5173
- Swagger UI: http://localhost:8080/swagger-ui
- Health: `curl http://localhost:8080/actuator/health`

### Local development (without Docker)

```bash
# Postgres + Redis only
docker compose -f infra/docker-compose.yml up -d postgres redis

# API
cd api
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/lingualoop"
export SPRING_DATASOURCE_USERNAME=lingualoop
export SPRING_DATASOURCE_PASSWORD=lingualoop
export JWT_SECRET="$(openssl rand -hex 32)"
./gradlew bootRun

# Web (Vite dev server, proxies /api to :8080)
cd web
npm install
npm run dev
```

## Configuration

The API reads all configuration from environment variables — there are no
hardcoded secrets anywhere.

| Variable                    | Required | Default                      |
| --------------------------- | -------- | ---------------------------- |
| `SPRING_DATASOURCE_URL`     | yes      | —                            |
| `SPRING_DATASOURCE_USERNAME`| yes      | —                            |
| `SPRING_DATASOURCE_PASSWORD`| yes      | —                            |
| `JWT_SECRET`                | yes      | — (min 32 chars, HS256)      |
| `JWT_TTL`                   | no       | `24h`                        |
| `SPRING_DATA_REDIS_HOST`    | no       | `localhost`                  |
| `SPRING_DATA_REDIS_PORT`    | no       | `6379`                       |
| `APP_CORS_ORIGINS`          | no       | `http://localhost:5173`      |
