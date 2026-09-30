import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NetworkError } from "../api/client";
import { clearPending, countPending, enqueueOp, listPending } from "./db";
import { flushQueue } from "./queue";

function mockFetchWith(responses: Array<() => Promise<Response>>) {
  let call = 0;
  const calls: Array<{ url: string; init: RequestInit }> = [];
  const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
    const url = String(_url);
    calls.push({ url, init: init ?? {} });
    const responder = responses[Math.min(call, responses.length - 1)];
    call += 1;
    return responder();
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

function okResponse(body: unknown = {}): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }),
  );
}

function networkFailure(): Promise<Response> {
  return Promise.reject(new TypeError("Failed to fetch"));
}

beforeEach(async () => {
  await clearPending();
  vi.stubGlobal("fetch", vi.fn());
  localStorage.setItem("lingualoop.token", "test-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("offline attempt queue", () => {
  it("persists attempts to IndexedDB", async () => {
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 21, grade: 5, latencyMs: 1200, hintShown: false });
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 22, grade: 2, latencyMs: 900, hintShown: true });
    await enqueueOp({ kind: "complete", sessionId: 9 });

    expect(await countPending()).toBe(3);
    const ops = await listPending();
    expect(ops.map((op) => op.kind)).toEqual(["attempt", "attempt", "complete"]);
  });

  it("flushes in order, attempt before the session completion", async () => {
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 21, grade: 5, latencyMs: 1200, hintShown: false });
    await enqueueOp({ kind: "complete", sessionId: 9 });

    const calls = mockFetchWith([okResponse, okResponse]);
    const result = await flushQueue();

    expect(result).toEqual({ synced: 2, remaining: 0, failed: false });
    expect(calls[0].url).toContain("/api/sessions/9/attempts");
    expect(JSON.parse(calls[0].init.body as string)).toMatchObject({ exerciseId: 21, grade: 5 });
    expect(calls[1].url).toContain("/api/sessions/9/complete");
    expect(calls[1].init.method).toBe("POST");
    expect(await countPending()).toBe(0);
  });

  it("keeps remaining ops when connectivity drops mid-flush", async () => {
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 21, grade: 5, latencyMs: 1200, hintShown: false });
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 22, grade: 3, latencyMs: 800, hintShown: false });

    const calls = mockFetchWith([okResponse, networkFailure]);
    const result = await flushQueue();

    expect(result.synced).toBe(1);
    expect(result.remaining).toBe(1);
    expect(result.failed).toBe(true);
    expect(calls).toHaveLength(2);
  });

  it("drops ops the server rejects with 4xx so they cannot poison the queue", async () => {
    await enqueueOp({ kind: "attempt", sessionId: 9, exerciseId: 21, grade: 5, latencyMs: 1200, hintShown: false });
    await enqueueOp({ kind: "complete", sessionId: 9 });

    const conflict = () =>
      Promise.resolve(
        new Response(JSON.stringify({ detail: "Session already completed" }), {
          status: 409,
          headers: { "Content-Type": "application/problem+json" },
        }),
      );
    mockFetchWith([conflict, okResponse]);
    const result = await flushQueue();

    expect(result.synced).toBe(2);
    expect(result.remaining).toBe(0);
    expect(await countPending()).toBe(0);
  });

  it("stops and reports failure on unexpected server errors", async () => {
    await enqueueOp({ kind: "complete", sessionId: 9 });
    const serverError = () =>
      Promise.resolve(
        new Response("boom", { status: 500 }),
      );
    mockFetchWith([serverError]);
    const result = await flushQueue();
    expect(result.failed).toBe(true);
    expect(result.remaining).toBe(1);
  });

  it("surfaces network failures as NetworkError from the api client", async () => {
    const { apiFetch } = await import("../api/client");
    mockFetchWith([networkFailure]);
    await expect(apiFetch("/api/languages")).rejects.toBeInstanceOf(NetworkError);
  });

  it("surfaces RFC7807 detail in ApiError", async () => {
    const { apiFetch } = await import("../api/client");
    mockFetchWith([
      () =>
        Promise.resolve(
          new Response(JSON.stringify({ detail: "Exercise not found" }), {
            status: 404,
            headers: { "Content-Type": "application/problem+json" },
          }),
        ),
    ]);
    const error = await apiFetch("/api/lessons/999").catch((err) => err);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe("Exercise not found");
  });
});
