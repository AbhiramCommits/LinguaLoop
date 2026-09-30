import { useSyncExternalStore } from "react";
import { getOfflineStatus, subscribeOfflineStatus } from "./status";

export function OfflineBanner() {
  const status = useSyncExternalStore(subscribeOfflineStatus, getOfflineStatus);

  if (status.online && status.pending === 0) {
    return null;
  }

  const message = !status.online
    ? status.pending > 0
      ? `Offline — ${status.pending} answer${status.pending === 1 ? "" : "s"} saved on this device. They will sync when you are back online.`
      : "You are offline. Cached lessons are still available."
    : status.syncing
      ? "Back online — syncing saved answers…"
      : `${status.pending} saved answer${status.pending === 1 ? "" : "s"} pending sync.`;

  return (
    <div className="offline-banner" role="status" aria-live="polite">
      {message}
    </div>
  );
}
