import { ApiError, NetworkError, apiFetch, type AttemptResult } from "../api/client";
import { countPending, listPending, removeOp, type StoredOp } from "./db";

export interface FlushResult {
  synced: number;
  remaining: number;
  failed: boolean;
}

/**
 * Replays queued operations in order (attempts before the session's
 * completion, since complete finalizes a session). Network failures stop
 * the flush (we are offline again); server 4xx rejections are dropped so a
 * stale op cannot poison the queue forever.
 */
export async function flushQueue(): Promise<FlushResult> {
  const ops = await listPending();
  let synced = 0;
  let failed = false;
  for (const op of ops) {
    try {
      if (op.kind === "attempt") {
        await postAttempt(op);
      } else {
        await postComplete(op);
      }
      if (op.id !== undefined) {
        await removeOp(op.id);
      }
      synced += 1;
    } catch (error) {
      if (error instanceof NetworkError) {
        failed = true;
        break;
      }
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
        if (op.id !== undefined) {
          await removeOp(op.id);
        }
        synced += 1;
        continue;
      }
      failed = true;
      break;
    }
  }
  return { synced, remaining: await countPending(), failed };
}

async function postAttempt(op: StoredOp): Promise<AttemptResult> {
  return apiFetch<AttemptResult>(`/api/sessions/${op.sessionId}/attempts`, {
    method: "POST",
    body: JSON.stringify({
      exerciseId: op.exerciseId,
      grade: op.grade,
      latencyMs: op.latencyMs,
      hintShown: op.hintShown ?? false,
    }),
  });
}

async function postComplete(op: StoredOp): Promise<unknown> {
  return apiFetch(`/api/sessions/${op.sessionId}/complete`, { method: "POST" });
}
