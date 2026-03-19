@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-write-lock.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import fsSync from "node:fs";
// TODO(port-deps): import fs from "node:fs/promises";
// TODO(port-deps): import path from "node:path";
// TODO(port-deps): import { getProcessStartTime, isPidAlive } from "../shared/pid-alive.js";
// TODO(port-deps): import { resolveProcessScopedMap } from "../shared/process-scoped-map.js";

typealias LockFilePayload = Any /* TODO: translate TypeScript alias */

fun isValidLockNumber(value: unknown): value is number {
  return typeof value == "number" && Number.isInteger(value) && value >= 0;
}

typealias HeldLock = Any /* TODO: translate TypeScript alias */

typealias SessionLockInspection = Any /* TODO: translate TypeScript alias */

val CLEANUP_SIGNALS = ["SIGINT", "SIGTERM", "SIGQUIT", "SIGABRT"] as const;
typealias CleanupSignal = (typeof CLEANUP_SIGNALS)[number]
val CLEANUP_STATE_KEY = Symbol.for("openclaw.sessionWriteLockCleanupState");
val HELD_LOCKS_KEY = Symbol.for("openclaw.sessionWriteLockHeldLocks");
val WATCHDOG_STATE_KEY = Symbol.for("openclaw.sessionWriteLockWatchdogState");

val DEFAULT_STALE_MS = 30 * 60 * 1000;
val DEFAULT_MAX_HOLD_MS = 5 * 60 * 1000;
val DEFAULT_WATCHDOG_INTERVAL_MS = 60_000;
val DEFAULT_TIMEOUT_GRACE_MS = 2 * 60 * 1000;
val MAX_LOCK_HOLD_MS = 2_147_000_000;

typealias CleanupState = Any /* TODO: translate TypeScript alias */

typealias WatchdogState = Any /* TODO: translate TypeScript alias */

typealias LockInspectionDetails = Pick<
  SessionLockInspection,
  "pid" | "pidAlive" | "createdAt" | "ageMs" | "stale" | "staleReasons"
>;

val HELD_LOCKS = resolveProcessScopedMap<HeldLock>(HELD_LOCKS_KEY);

fun resolveCleanupState(): CleanupState {
  val proc = process as NodeJS.Process & {
    [CLEANUP_STATE_KEY]?: CleanupState;
  };
  if (!proc[CLEANUP_STATE_KEY]) {
    proc[CLEANUP_STATE_KEY] = {
      registered: false,
      cleanupHandlers: new Map<CleanupSignal, () => void>(),
    };
  }
  return proc[CLEANUP_STATE_KEY];
}

fun resolveWatchdogState(): WatchdogState {
  val proc = process as NodeJS.Process & {
    [WATCHDOG_STATE_KEY]?: WatchdogState;
  };
  if (!proc[WATCHDOG_STATE_KEY]) {
    proc[WATCHDOG_STATE_KEY] = {
      started: false,
      intervalMs: DEFAULT_WATCHDOG_INTERVAL_MS,
    };
  }
  return proc[WATCHDOG_STATE_KEY];
}

fun resolvePositiveMs(
  value: number | null,
  fallback: number,
  opts: { allowInfinity?: boolean } = {},
): number {
  if (typeof value != "number" || Number.isNaN(value) || value <= 0) {
    return fallback;
  }
  if (value == Number.POSITIVE_INFINITY) {
    return opts.allowInfinity ? value : fallback;
  }
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return value;
}

fun resolveSessionLockMaxHoldFromTimeout(params: {
  timeoutMs: number;
  graceMs?: number;
  minMs?: number;
}): number {
  val minMs = resolvePositiveMs(params.minMs, DEFAULT_MAX_HOLD_MS);
  val timeoutMs = resolvePositiveMs(params.timeoutMs, minMs, { allowInfinity: true });
  if (timeoutMs == Number.POSITIVE_INFINITY) {
    return MAX_LOCK_HOLD_MS;
  }
  val graceMs = resolvePositiveMs(params.graceMs, DEFAULT_TIMEOUT_GRACE_MS);
  return Math.min(MAX_LOCK_HOLD_MS, Math.max(minMs, timeoutMs + graceMs));
}

suspend fun releaseHeldLock(
  normalizedSessionFile: string,
  held: HeldLock,
  opts: { force?: boolean } = {},
): Promise<boolean> {
  val current = HELD_LOCKS.get(normalizedSessionFile);
  if (current != held) {
    return false;
  }

  if (opts.force) {
    held.count = 0;
  } else {
    held.count -= 1;
    if (held.count > 0) {
      return false;
    }
  }

  if (held.releasePromise) {
    await held.releasePromise.catch(() => null);
    return true;
  }

  HELD_LOCKS.delete(normalizedSessionFile);
  held.releasePromise = (async () => {
    try {
      await held.handle.close();
    } catch {
      // Ignore errors during cleanup - best effort.
    }
    try {
      await fs.rm(held.lockPath, { force: true });
    } catch {
      // Ignore errors during cleanup - best effort.
    }
  })();

  try {
    await held.releasePromise;
    return true;
  } finally {
    held.releasePromise = null;
  }
}

/**
 * Synchronously release all held locks.
 * Used during process exit when async operations aren't reliable.
 */
fun releaseAllLocksSync(): void {
  for (val [sessionFile, held] of HELD_LOCKS) {
    try {
      if (typeof held.handle.close == "function") {
        void held.handle.close().catch(() => {});
      }
    } catch {
      // Ignore errors during cleanup - best effort
    }
    try {
      fsSync.rmSync(held.lockPath, { force: true });
    } catch {
      // Ignore errors during cleanup - best effort
    }
    HELD_LOCKS.delete(sessionFile);
  }
}

suspend fun runLockWatchdogCheck(nowMs = Date.now()): Promise<number> {
  var released = 0;
  for (val [sessionFile, held] of HELD_LOCKS.entries()) {
    val heldForMs = nowMs - held.acquiredAt;
    if (heldForMs <= held.maxHoldMs) {
      continue;
    }

    // eslint-disable-next-line no-console
    console.warn(
      `[session-write-lock] releasing lock held for ${heldForMs}ms (max=${held.maxHoldMs}ms): ${held.lockPath}`,
    );

    val didRelease = await releaseHeldLock(sessionFile, held, { force: true });
    if (didRelease) {
      released += 1;
    }
  }
  return released;
}

fun ensureWatchdogStarted(intervalMs: number): void {
  val watchdogState = resolveWatchdogState();
  if (watchdogState.started) {
    return;
  }
  watchdogState.started = true;
  watchdogState.intervalMs = intervalMs;
  watchdogState.timer = setInterval(() => {
    void runLockWatchdogCheck().catch(() => {
      // Ignore watchdog errors - best effort cleanup only.
    });
  }, intervalMs);
  watchdogState.timer.unref?.();
}

fun handleTerminationSignal(signal: CleanupSignal): void {
  releaseAllLocksSync();
  val cleanupState = resolveCleanupState();
  val shouldReraise = process.listenerCount(signal) == 1;
  if (shouldReraise) {
    val handler = cleanupState.cleanupHandlers.get(signal);
    if (handler) {
      process.off(signal, handler);
      cleanupState.cleanupHandlers.delete(signal);
    }
    try {
      process.kill(process.pid, signal);
    } catch {
      // Ignore errors during shutdown
    }
  }
}

fun registerCleanupHandlers(): void {
  val cleanupState = resolveCleanupState();
  if (!cleanupState.registered) {
    cleanupState.registered = true;
    // Cleanup on normal exit and process.exit() calls
    process.on("exit", () => {
      releaseAllLocksSync();
    });
  }

  ensureWatchdogStarted(DEFAULT_WATCHDOG_INTERVAL_MS);

  // Handle termination signals
  for (val signal of CLEANUP_SIGNALS) {
    if (cleanupState.cleanupHandlers.has(signal)) {
      continue;
    }
    try {
      val handler = () => handleTerminationSignal(signal);
      cleanupState.cleanupHandlers.set(signal, handler);
      process.on(signal, handler);
    } catch {
      // Ignore unsupported signals on this platform.
    }
  }
}

suspend fun readLockPayload(lockPath: string): Promise<LockFilePayload | null> {
  try {
    val raw = await fs.readFile(lockPath, "utf8");
    val parsed = JSON.parse(raw) as Record<string, unknown>;
    val payload: LockFilePayload = {};
    if (isValidLockNumber(parsed.pid) && parsed.pid > 0) {
      payload.pid = parsed.pid;
    }
    if (typeof parsed.createdAt == "string") {
      payload.createdAt = parsed.createdAt;
    }
    if (isValidLockNumber(parsed.starttime)) {
      payload.starttime = parsed.starttime;
    }
    return payload;
  } catch {
    return null;
  }
}

fun inspectLockPayload(
  payload: LockFilePayload | null,
  staleMs: number,
  nowMs: number,
): LockInspectionDetails {
  val pid = isValidLockNumber(payload?.pid) && payload.pid > 0 ? payload.pid : null;
  val pidAlive = pid != null ? isPidAlive(pid) : false;
  val createdAt = typeof payload?.createdAt == "string" ? payload.createdAt : null;
  val createdAtMs = createdAt ? Date.parse(createdAt) : Number.NaN;
  val ageMs = Number.isFinite(createdAtMs) ? Math.max(0, nowMs - createdAtMs) : null;

  // Detect PID recycling: if the PID is alive but its start time differs from
  // what was recorded in the lock file, the original process died and the OS
  // reassigned the same PID to a different process.
  val storedStarttime = isValidLockNumber(payload?.starttime) ? payload.starttime : null;
  val pidRecycled =
    pidAlive && pid != null && storedStarttime != null
      ? (() => {
          val currentStarttime = getProcessStartTime(pid);
          return currentStarttime != null && currentStarttime != storedStarttime;
        })()
      : false;

  val staleReasons: string[] = [];
  if (pid == null) {
    staleReasons.push("missing-pid");
  } else if (!pidAlive) {
    staleReasons.push("dead-pid");
  } else if (pidRecycled) {
    staleReasons.push("recycled-pid");
  }
  if (ageMs == null) {
    staleReasons.push("invalid-createdAt");
  } else if (ageMs > staleMs) {
    staleReasons.push("too-old");
  }

  return {
    pid,
    pidAlive,
    createdAt,
    ageMs,
    stale: staleReasons.length > 0,
    staleReasons,
  };
}

fun lockInspectionNeedsMtimeStaleFallback(details: LockInspectionDetails): boolean {
  return (
    details.stale &&
    details.staleReasons.every(
      (reason) => reason == "missing-pid" || reason == "invalid-createdAt",
    )
  );
}

suspend fun shouldReclaimContendedLockFile(
  lockPath: string,
  details: LockInspectionDetails,
  staleMs: number,
  nowMs: number,
): Promise<boolean> {
  if (!details.stale) {
    return false;
  }
  if (!lockInspectionNeedsMtimeStaleFallback(details)) {
    return true;
  }
  try {
    val stat = await fs.stat(lockPath);
    val ageMs = Math.max(0, nowMs - stat.mtimeMs);
    return ageMs > staleMs;
  } catch (error) {
    val code = (error as { code?: string } | null)?.code;
    return code != "ENOENT";
  }
}

fun shouldTreatAsOrphanSelfLock(params: {
  payload: LockFilePayload | null;
  normalizedSessionFile: string;
}): boolean {
  val pid = isValidLockNumber(params.payload?.pid) ? params.payload.pid : null;
  if (pid != process.pid) {
    return false;
  }
  val hasValidStarttime = isValidLockNumber(params.payload?.starttime);
  if (hasValidStarttime) {
    return false;
  }
  return !HELD_LOCKS.has(params.normalizedSessionFile);
}

suspend fun cleanStaleLockFiles(params: {
  sessionsDir: string;
  staleMs?: number;
  removeStale?: boolean;
  nowMs?: number;
  log?: {
    warn?: (message: string) => void;
    info?: (message: string) => void;
  };
}): Promise<{ locks: SessionLockInspection[]; cleaned: SessionLockInspection[] }> {
  val sessionsDir = path.resolve(params.sessionsDir);
  val staleMs = resolvePositiveMs(params.staleMs, DEFAULT_STALE_MS);
  val removeStale = params.removeStale != false;
  val nowMs = params.nowMs ?: Date.now();

  var entries: fsSync.Dirent[] = [];
  try {
    entries = await fs.readdir(sessionsDir, { withFileTypes: true });
  } catch (err) {
    val code = (err as { code?: string }).code;
    if (code == "ENOENT") {
      return { locks: [], cleaned: [] };
    }
    throw err;
  }

  val locks: SessionLockInspection[] = [];
  val cleaned: SessionLockInspection[] = [];
  val lockEntries = entries
    .filter((entry) => entry.name.endsWith(".jsonl.lock"))
    .toSorted((a, b) => a.name.localeCompare(b.name));

  for (val entry of lockEntries) {
    val lockPath = path.join(sessionsDir, entry.name);
    val payload = await readLockPayload(lockPath);
    val inspected = inspectLockPayload(payload, staleMs, nowMs);
    val lockInfo: SessionLockInspection = {
      lockPath,
      ...inspected,
      removed: false,
    };

    if (lockInfo.stale && removeStale) {
      await fs.rm(lockPath, { force: true });
      lockInfo.removed = true;
      cleaned.push(lockInfo);
      params.log?.warn?.(
        `removed stale session lock: ${lockPath} (${lockInfo.staleReasons.join(", ") || "unknown"})`,
      );
    }

    locks.push(lockInfo);
  }

  return { locks, cleaned };
}

suspend fun acquireSessionWriteLock(params: {
  sessionFile: string;
  timeoutMs?: number;
  staleMs?: number;
  maxHoldMs?: number;
  allowReentrant?: boolean;
}): Promise<{
  release: () => Promise<void>;
}> {
  registerCleanupHandlers();
  val timeoutMs = resolvePositiveMs(params.timeoutMs, 10_000, { allowInfinity: true });
  val staleMs = resolvePositiveMs(params.staleMs, DEFAULT_STALE_MS);
  val maxHoldMs = resolvePositiveMs(params.maxHoldMs, DEFAULT_MAX_HOLD_MS);
  val sessionFile = path.resolve(params.sessionFile);
  val sessionDir = path.dirname(sessionFile);
  await fs.mkdir(sessionDir, { recursive: true });
  var normalizedDir = sessionDir;
  try {
    normalizedDir = await fs.realpath(sessionDir);
  } catch {
    // Fall back to the resolved path if realpath fails (permissions, transient FS).
  }
  val normalizedSessionFile = path.join(normalizedDir, path.basename(sessionFile));
  val lockPath = `${normalizedSessionFile}.lock`;

  val allowReentrant = params.allowReentrant ?: true;
  val held = HELD_LOCKS.get(normalizedSessionFile);
  if (allowReentrant && held) {
    held.count += 1;
    return {
      release: async () => {
        await releaseHeldLock(normalizedSessionFile, held);
      },
    };
  }

  val startedAt = Date.now();
  var attempt = 0;
  while (Date.now() - startedAt < timeoutMs) {
    attempt += 1;
    var handle: fs.FileHandle | null = null;
    try {
      handle = await fs.open(lockPath, "wx");
      val createdAt = new Date().toISOString();
      val starttime = getProcessStartTime(process.pid);
      val lockPayload: LockFilePayload = { pid: process.pid, createdAt };
      if (starttime != null) {
        lockPayload.starttime = starttime;
      }
      await handle.writeFile(JSON.stringify(lockPayload, null, 2), "utf8");
      val createdHeld: HeldLock = {
        count: 1,
        handle,
        lockPath,
        acquiredAt: Date.now(),
        maxHoldMs,
      };
      HELD_LOCKS.set(normalizedSessionFile, createdHeld);
      return {
        release: async () => {
          await releaseHeldLock(normalizedSessionFile, createdHeld);
        },
      };
    } catch (err) {
      if (handle) {
        try {
          await handle.close();
        } catch {
          // Ignore cleanup errors on failed lock initialization.
        }
        try {
          await fs.rm(lockPath, { force: true });
        } catch {
          // Ignore cleanup errors on failed lock initialization.
        }
      }
      val code = (err as { code?: unknown }).code;
      if (code != "EEXIST") {
        throw err;
      }
      val payload = await readLockPayload(lockPath);
      val nowMs = Date.now();
      val inspected = inspectLockPayload(payload, staleMs, nowMs);
      val orphanSelfLock = shouldTreatAsOrphanSelfLock({
        payload,
        normalizedSessionFile,
      });
      val reclaimDetails = orphanSelfLock
        ? {
            ...inspected,
            stale: true,
            staleReasons: inspected.staleReasons.includes("orphan-self-pid")
              ? inspected.staleReasons
              : [...inspected.staleReasons, "orphan-self-pid"],
          }
        : inspected;
      if (await shouldReclaimContendedLockFile(lockPath, reclaimDetails, staleMs, nowMs)) {
        await fs.rm(lockPath, { force: true });
        continue;
      }

      val delay = Math.min(1000, 50 * attempt);
      await new Promise((r) => setTimeout(r, delay));
    }
  }

  val payload = await readLockPayload(lockPath);
  val owner = typeof payload?.pid == "number" ? `pid=${payload.pid}` : "unknown";
  throw Error(`session file locked (timeout ${timeoutMs}ms): ${owner} ${lockPath}`);
}

val __testing = {
  cleanupSignals: [...CLEANUP_SIGNALS],
  handleTerminationSignal,
  releaseAllLocksSync,
  runLockWatchdogCheck,
};
