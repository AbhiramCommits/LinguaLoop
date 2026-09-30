#!/usr/bin/env python3
"""Generate synthetic learners that study through the REAL LinguaLoop API.

The simulator exercises every production code path end to end: registration,
lesson browsing, session creation (hint_timing assignment), graded attempts
(SM-2 scheduler, review_state upsert), queue reads (lesson_ordering
assignment), session completion (streaks) and the experiments assignment
tables. Timestamps are then backdated in the database so the retention
metrics (D1/D7 return rates, second-exposure accuracy) have history to
compute from.

WARNING: these are synthetic measurements, not real learner behavior.
Anything reporting them MUST be labeled "simulated learners, N=...".

Forgetting model (parameters from the CLI flags):
    p(recall) = min(1, 2^(-elapsed_days / h) * (1 + boost * prior_successes))
        h       ~ LogNormal(median=--half-life-median, sigma=--half-life-sigma)
        elapsed = days since this learner's previous attempt on the exercise
        prior_successes = number of previously successful attempts on it
    grade   = 5 with probability p(recall), else uniform(0..2)
    latency ~ Normal(1800ms, 600ms), min 200ms
    hints are used with probability 0.08

Usage:
    DATABASE_URL="postgresql://lingualoop:lingualoop@localhost:5432/lingualoop" \
      uv run -- python simulate_learners.py --learners 120 --api-base http://localhost:8080
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import psycopg

DEFAULT_HALF_LIFE_MEDIAN_DAYS = 2.0
DEFAULT_HALF_LIFE_SIGMA = 0.6
DEFAULT_BOOST = 0.35
DEFAULT_WINDOW_DAYS = 14


class ApiError(RuntimeError):
    pass


@dataclass
class Learner:
    learner_id: int
    token: str
    half_life: float
    exercise_history: dict  # exercise_id -> (last_day_offset, prior_successes)
    session_ids: list  # list of (session_id, day_offset, attempt_ids)


def api_request(api_base: str, method: str, path: str, token: str | None = None,
                payload: dict | None = None) -> dict | list:
    url = api_base.rstrip("/") + path
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise ApiError(f"{method} {path} -> {exc.code}: {detail[:300]}") from exc
    except urllib.error.URLError as exc:
        raise ApiError(f"{method} {path} -> {exc.reason}") from exc


def fetch_lessons(api_base: str) -> list[dict]:
    languages = api_request(api_base, "GET", "/api/languages")
    if not languages:
        raise ApiError("No languages in the catalogue; run tools/seed.py first")
    units = api_request(api_base, "GET", f"/api/languages/{languages[0]['id']}/units")
    if not units:
        raise ApiError("No units in the catalogue; run tools/seed.py first")
    lessons = units[0]["lessons"]
    if not lessons:
        raise ApiError("No lessons in the first unit; run tools/seed.py first")
    return [{"id": lesson["id"], "title": lesson["title"]} for lesson in lessons]


def register_learner(api_base: str, rng: random.Random, index: int, run_id: str) -> Learner:
    email = f"sim-{run_id}-{index:05d}@lingualoop.example"
    password = f"sim-password-{run_id}-{index}"
    auth = api_request(api_base, "POST", "/api/auth/register", payload={
        "email": email,
        "password": password,
        "displayName": f"Sim {index}",
        "timezone": "UTC",
    })
    half_life = max(0.1, rng.lognormvariate(math.log(DEFAULT_HALF_LIFE_MEDIAN_DAYS),
                                            DEFAULT_HALF_LIFE_SIGMA))
    return Learner(learner_id=auth["learner"]["id"], token=auth["token"],
                   half_life=half_life, exercise_history={}, session_ids=[])


def recall_probability(learner: Learner, exercise_id: int, day_offset: int, boost: float) -> float:
    history = learner.exercise_history.get(exercise_id)
    if history is None:
        return 1.0
    last_day, prior_successes = history
    elapsed = max(0.0, day_offset - last_day)
    if elapsed <= 0:
        return 1.0
    return min(1.0, 2.0 ** (-elapsed / learner.half_life) * (1.0 + boost * prior_successes))


def study_session(api_base: str, learner: Learner, lesson: dict, day_offset: int,
                  rng: random.Random, boost: float) -> None:
    session = api_request(api_base, "POST", "/api/sessions", token=learner.token,
                          payload={"lessonId": lesson["id"]})
    session_id = session["id"]
    lesson_detail = api_request(api_base, "GET", f"/api/lessons/{lesson['id']}")
    attempt_ids: list[int] = []
    for exercise in lesson_detail["exercises"]:
        exercise_id = exercise["id"]
        prob = recall_probability(learner, exercise_id, day_offset, boost)
        success = rng.random() < prob
        grade = 5 if success else rng.randint(0, 2)
        latency_ms = max(200, int(rng.gauss(1800, 600)))
        hint_shown = rng.random() < 0.08
        result = api_request(api_base, "POST", f"/api/sessions/{session_id}/attempts",
                             token=learner.token,
                             payload={"exerciseId": exercise_id, "grade": grade,
                                      "latencyMs": latency_ms, "hintShown": hint_shown})
        attempt_ids.append(result["attemptId"])
        history = learner.exercise_history.get(exercise_id)
        prior = history[1] if history else 0
        learner.exercise_history[exercise_id] = (day_offset, prior + (1 if success else 0))
    api_request(api_base, "POST", f"/api/sessions/{session_id}/complete", token=learner.token)
    learner.session_ids.append((session_id, day_offset, attempt_ids))


def plan_session_offsets(rng: random.Random, window_days: int) -> list[int]:
    # Guarantee some D1/D7 signal and realistic gaps; day 0 is today.
    pool = [1, 7, 2, 3, 4, 5, 7, 9, 12]
    extra_count = rng.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
    offsets = sorted({0, *rng.sample(pool, min(extra_count, len(pool)))})
    return [offset for offset in offsets if offset < window_days]


def backdate(conn, learner: Learner, rng: random.Random, now: datetime) -> None:
    for session_id, day_offset, attempt_ids in learner.session_ids:
        hour = rng.randint(8, 22)
        minute = rng.randint(0, 59)
        started = (now - timedelta(days=day_offset)).replace(hour=hour, minute=minute,
                                                             second=rng.randint(0, 59))
        ended = started + timedelta(minutes=rng.randint(5, 20))
        conn.execute("UPDATE session SET started_at = %s, ended_at = %s WHERE id = %s",
                     (started, ended, session_id))
        for position, attempt_id in enumerate(attempt_ids):
            conn.execute("UPDATE attempt SET created_at = %s WHERE id = %s",
                         (started + timedelta(seconds=position * 45), attempt_id))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--learners", type=int, default=120, help="Number of simulated learners")
    parser.add_argument("--api-base", default="http://localhost:8080", help="API base URL")
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"),
                        help="PostgreSQL URL (default: $DATABASE_URL)")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--window-days", type=int, default=DEFAULT_WINDOW_DAYS,
                        help="How many days of history to synthesize")
    parser.add_argument("--half-life-median", type=float, default=DEFAULT_HALF_LIFE_MEDIAN_DAYS,
                        help="Median forgetting half-life in days (lognormal)")
    parser.add_argument("--half-life-sigma", type=float, default=DEFAULT_HALF_LIFE_SIGMA,
                        help="Sigma of the lognormal half-life distribution")
    parser.add_argument("--boost", type=float, default=DEFAULT_BOOST,
                        help="Recall boost per prior successful repetition")
    args = parser.parse_args()

    if not args.database_url:
        parser.error("--database-url is required (or set DATABASE_URL)")
    if args.learners <= 0:
        parser.error("--learners must be positive")

    rng = random.Random(args.seed)
    run_id = f"{int(time.time()) % 1_000_000:06d}"
    lessons = fetch_lessons(args.api_base)
    primary_lesson = lessons[0]
    print(f"Catalogue: {len(lessons)} lessons; using '{primary_lesson['title']}' (id {primary_lesson['id']})")

    learners: list[Learner] = []
    now = datetime.now(UTC).replace(microsecond=0)
    for index in range(args.learners):
        learner = register_learner(args.api_base, rng, index, run_id)
        offsets = plan_session_offsets(rng, args.window_days)
        for day_offset in offsets:
            study_session(args.api_base, learner, primary_lesson, day_offset, rng, args.boost)
        # First queue read = first exposure to lesson_ordering
        api_request(args.api_base, "GET", "/api/learners/me/queue", token=learner.token)
        learners.append(learner)
        if (index + 1) % 25 == 0:
            print(f"  {index + 1}/{args.learners} learners studied")

    with psycopg.connect(args.database_url) as conn:
        for learner in learners:
            conn.execute("UPDATE learner SET is_simulated = TRUE WHERE id = %s",
                         (learner.learner_id,))
            backdate(conn, learner, rng, now)

    total_sessions = sum(len(learner.session_ids) for learner in learners)
    total_attempts = sum(len(attempt_ids) for learner in learners
                         for _session_id, _day, attempt_ids in learner.session_ids)
    print(f"Done. {len(learners)} simulated learners, {total_sessions} sessions, "
          f"{total_attempts} attempts, backdated across {args.window_days} days.")
    print()
    print("LABEL FOR ANY REPORTING OF THESE RESULTS:")
    print(f"  simulated learners, N={len(learners)}; forgetting model "
          f"p(recall)=min(1, 2^(-elapsed_days/h)*(1+{args.boost}*prior_successes)), "
          f"h ~ LogNormal(median={args.half_life_median}d, sigma={args.half_life_sigma}); "
          f"window={args.window_days} days; seed={args.seed}. NOT real learner measurements.")
    print()
    print("View results: GET /api/experiments/{lesson_ordering,hint_timing}/results"
          "?includeSimulated=true")


if __name__ == "__main__":
    try:
        main()
    except ApiError as exc:
        print(f"simulation failed: {exc}", file=sys.stderr)
        sys.exit(1)
