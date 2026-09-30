import "@testing-library/jest-dom/vitest";
import "fake-indexeddb/auto";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(cleanup);

// Node >= 25 ships a built-in localStorage global that shadows jsdom's
// Storage; when it is present (and non-functional in the jsdom environment)
// replace it with a working in-memory implementation.
function installLocalStorage() {
  let broken = false;
  try {
    if (typeof localStorage.setItem !== "function") {
      broken = true;
    }
  } catch {
    broken = true;
  }
  if (!broken) {
    return;
  }

  let store = new Map<string, string>();
  const storage = {
    get length() {
      return store.size;
    },
    key(index: number): string | null {
      return Array.from(store.keys())[index] ?? null;
    },
    getItem(key: string): string | null {
      return store.has(key) ? store.get(key)! : null;
    },
    setItem(key: string, value: string): void {
      store.set(String(key), String(value));
    },
    removeItem(key: string): void {
      store.delete(key);
    },
    clear(): void {
      store = new Map();
    },
  };
  Object.defineProperty(globalThis, "localStorage", {
    value: storage,
    writable: true,
    configurable: true,
  });
  Object.defineProperty(window, "localStorage", {
    value: storage,
    writable: true,
    configurable: true,
  });
}

installLocalStorage();
