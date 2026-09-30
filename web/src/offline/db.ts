import { openDB, type IDBPDatabase } from "idb";

export type PendingOp = AttemptOp | CompleteOp;

export interface AttemptOp {
  kind: "attempt";
  sessionId: number;
  exerciseId: number;
  grade: number;
  latencyMs: number;
  hintShown: boolean;
}

export interface CompleteOp {
  kind: "complete";
  sessionId: number;
}

export interface StoredOp {
  id?: number;
  queuedAt: string;
  kind: "attempt" | "complete";
  sessionId: number;
  exerciseId?: number;
  grade?: number;
  latencyMs?: number;
  hintShown?: boolean;
}

interface LinguaLoopDb {
  "pending-ops": {
    key: number;
    value: StoredOp;
  };
}

const DB_NAME = "lingualoop-offline";
const STORE = "pending-ops";

let dbPromise: Promise<IDBPDatabase<LinguaLoopDb>> | null = null;

function getDb(): Promise<IDBPDatabase<LinguaLoopDb>> {
  if (!dbPromise) {
    dbPromise = openDB<LinguaLoopDb>(DB_NAME, 1, {
      upgrade(db) {
        db.createObjectStore(STORE, { keyPath: "id", autoIncrement: true });
      },
    });
  }
  return dbPromise;
}

export async function enqueueOp(op: PendingOp): Promise<void> {
  const db = await getDb();
  await db.add(STORE, { ...op, queuedAt: new Date().toISOString() });
}

export async function countPending(): Promise<number> {
  const db = await getDb();
  return db.count(STORE);
}

export async function listPending(): Promise<StoredOp[]> {
  const db = await getDb();
  return db.getAll(STORE);
}

export async function removeOp(id: number): Promise<void> {
  const db = await getDb();
  await db.delete(STORE, id);
}

export async function clearPending(): Promise<void> {
  const db = await getDb();
  await db.clear(STORE);
}
