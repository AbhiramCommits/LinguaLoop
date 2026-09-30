// k6 load test for the LinguaLoop API (README "Numbers" section).
// Usage: k6 run --vus 20 --duration 60s scripts/load.js
// Requires a registered learner: JWT via K6_EMAIL / K6_PASSWORD.

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const API = __ENV.API_BASE ?? "http://localhost:8080";
const EMAIL = __ENV.K6_EMAIL ?? "load@lingualoop.example";
const PASSWORD = __ENV.K6_PASSWORD ?? "load-password-123";

const lessonLatency = new Trend("lesson_latency");
const queueLatency = new Trend("queue_latency");
const languagesLatency = new Trend("languages_latency");

export function setup() {
  const register = http.post(`${API}/api/auth/register`, JSON.stringify({
    email: EMAIL, password: PASSWORD, displayName: "Load Learner", timezone: "UTC",
  }), { headers: { "Content-Type": "application/json" } });
  const login = http.post(`${API}/api/auth/login`, JSON.stringify({
    email: EMAIL, password: PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  const body = login.json();
  return { token: body.token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const languages = http.get(`${API}/api/languages`);
  languagesLatency.add(languages.timings.duration);
  check(languages, { "languages 200": (r) => r.status === 200 });

  const lesson = http.get(`${API}/api/lessons/1`);
  lessonLatency.add(lesson.timings.duration);
  check(lesson, { "lesson 200": (r) => r.status === 200 });

  const queue = http.get(`${API}/api/learners/me/queue`, { headers });
  queueLatency.add(queue.timings.duration);
  check(queue, { "queue 200": (r) => r.status === 200 });

  sleep(1);
}
