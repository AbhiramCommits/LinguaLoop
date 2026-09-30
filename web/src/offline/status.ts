import { countPending } from "./db";
import { flushQueue } from "./queue";

export interface OfflineStatus {
  online: boolean;
  pending: number;
  syncing: boolean;
}

let status: OfflineStatus = {
  online: typeof navigator === "undefined" ? true : navigator.onLine,
  pending: 0,
  syncing: false,
};

const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) {
    listener();
  }
}

function setStatus(patch: Partial<OfflineStatus>) {
  status = { ...status, ...patch };
  emit();
}

async function syncNow() {
  if (!status.online || status.syncing) {
    return;
  }
  setStatus({ syncing: true });
  try {
    const result = await flushQueue();
    setStatus({ syncing: false, pending: result.remaining });
  } catch {
    setStatus({ syncing: false });
  }
}

async function refreshPending() {
  try {
    setStatus({ pending: await countPending() });
  } catch {
    // IndexedDB unavailable; the banner just shows connectivity.
  }
}

function handleOnline() {
  setStatus({ online: true });
  void syncNow();
}

function handleOffline() {
  setStatus({ online: false });
}

export function subscribeOfflineStatus(listener: () => void): () => void {
  listeners.add(listener);
  window.addEventListener("online", handleOnline);
  window.addEventListener("offline", handleOffline);
  void refreshPending();
  if (status.online) {
    void syncNow();
  }
  return () => {
    listeners.delete(listener);
    window.removeEventListener("online", handleOnline);
    window.removeEventListener("offline", handleOffline);
  };
}

export function getOfflineStatus(): OfflineStatus {
  return status;
}

export function notifyQueued(count: number) {
  setStatus({ pending: count });
}
