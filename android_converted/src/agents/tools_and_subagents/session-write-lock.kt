package agents.tools_and_subagents

// Converted from src/agents/session-write-lock.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import fsSync from "node:fs";
// TODO: TypeScript import retained for manual wiring: import fs from "node:fs/promises";
// TODO: TypeScript import retained for manual wiring: import path from "node:path";
// TODO: TypeScript import retained for manual wiring: import { getProcessStartTime, isPidAlive } from "../shared/pid-alive.js";
// TODO: TypeScript import retained for manual wiring: import { resolveProcessScopedMap } from "../shared/process-scoped-map.js";

data class LockFilePayload(
    val pid: Double?,
    val createdAt: String?,
    val starttime: Double?,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   /** Process start time in clock ticks (from /proc/pid/stat field 22). */
}

fun isValidLockNumber(value: Any?): value is Double {
  return value is Double && Number.isInteger(value) && value >= 0
}

data class HeldLock(
    val count: Double,
    val handle: fs.FileHandle,
    val lockPath: String,
    val acquiredAt: Double,
    val maxHoldMs: Double,
    val releasePromise: Deferred<Unit>?,
)

data class SessionLockInspection(
    val lockPath: String,
    val pid: Double?,
    val pidAlive: Boolean,
    val createdAt: String?,
    val ageMs: Double?,
    val stale: Boolean,
    val staleReasons: List<String>,
    val removed: Boolean,
)

val CLEANUP_SIGNALS = ["SIGINT", "SIGTERM", "SIGQUIT", "SIGABRT"] as /* TODO */ val
typealias CleanupSignal = (typeof CLEANUP_SIGNALS)[Double]
val CLEANUP_STATE_KEY = Symbol.for("openclaw.sessionWriteLockCleanupState")
val HELD_LOCKS_KEY = Symbol.for("openclaw.sessionWriteLockHeldLocks")
val WATCHDOG_STATE_KEY = Symbol.for("openclaw.sessionWriteLockWatchdogState")

val DEFAULT_STALE_MS = 30 * 60 * 1000
val DEFAULT_MAX_HOLD_MS = 5 * 60 * 1000
val DEFAULT_WATCHDOG_INTERVAL_MS = 60_000
val DEFAULT_TIMEOUT_GRACE_MS = 2 * 60 * 1000
val MAX_LOCK_HOLD_MS = 2_147_000_000

data class CleanupState(
    val registered: Boolean,
    val cleanupHandlers: MutableMap<CleanupSignal, () => Unit>,
)

data class WatchdogState(
    val started: Boolean,
    val intervalMs: Double,
    val timer: NodeJS.Timeout?,
)

type LockInspectionDetails = Pick<
  SessionLockInspection,
  "pid" | "pidAlive" | "createdAt" | "ageMs" | "stale" | "staleReasons"
>

val HELD_LOCKS = resolveProcessScopedMutableMap<HeldLock>(HELD_LOCKS_KEY)

fun resolveCleanupState(): CleanupState {
  val proc = process as /* TODO */ NodeJS.Process & {
    [CLEANUP_STATE_KEY]?: CleanupState
  }
  if (!proc[CLEANUP_STATE_KEY]) {
    proc[CLEANUP_STATE_KEY] = {
      registered: false,
      cleanupHandlers: new MutableMap<CleanupSignal, () -> Unit>(),
    }
  }
  return proc[CLEANUP_STATE_KEY]
}

fun resolveWatchdogState(): WatchdogState {
  val proc = process as /* TODO */ NodeJS.Process & {
    [WATCHDOG_STATE_KEY]?: WatchdogState
  }
  if (!proc[WATCHDOG_STATE_KEY]) {
    proc[WATCHDOG_STATE_KEY] = {
      started: false,
      intervalMs: DEFAULT_WATCHDOG_INTERVAL_MS,
    }
  }
  return proc[WATCHDOG_STATE_KEY]
}

fun resolvePositiveMs(
  value: Double?,
  fallback: Double,
  opts: { allowInfinity?: Boolean } = {},
): Double {
  if (value !is Double || Number.isNaN(value) || value <= 0) {
    return fallback
  }
  if (value == Number.POSITIVE_INFINITY) {
    return opts.allowInfinity ? value : fallback
  }
  if (!Number.isFinite(value)) {
    return fallback
  }
  return value
}

fun resolveSessionLockMaxHoldFromTimeout(params: {
  timeoutMs: Double
  graceMs?: Double
  minMs?: Double
}): Double {
  val minMs = resolvePositiveMs(params.minMs, DEFAULT_MAX_HOLD_MS)
  val timeoutMs = resolvePositiveMs(params.timeoutMs, minMs, { allowInfinity: true })
  if (timeoutMs == Number.POSITIVE_INFINITY) {
    return MAX_LOCK_HOLD_MS
  }
  val graceMs = resolvePositiveMs(params.graceMs, DEFAULT_TIMEOUT_GRACE_MS)
  return Math.min(MAX_LOCK_HOLD_MS, Math.max(minMs, timeoutMs + graceMs))
}

suspend fun releaseHeldLock(
  normalizedSessionFile: String,
  held: HeldLock,
  opts: { force?: Boolean } = {},
): Deferred<Boolean> {
  val current = HELD_LOCKS.get(normalizedSessionFile)
  if (current != held) {
    return false
  }

  if (opts.force) {
    held.count = 0
  } else {
    held.count -= 1
    if (held.count > 0) {
      return false
    }
  }

  if (held.releasePromise) {
    await held.releasePromise.catch(() -> null)
    return true
  }

  HELD_LOCKS.delete(normalizedSessionFile)
  held.releasePromise = { async ( ->
    try {
      await held.handle.close()
    } catch (_: Throwable) {
      // Ignore errors during cleanup - best effort.
    }
    try {
      await fs.rm(held.lockPath, { force: true })
    } catch (_: Throwable) {
      // Ignore errors during cleanup - best effort.
    }
  })()

  try {
    await held.releasePromise
    return true
  } finally {
    held.releasePromise = null
  }
}

/**
 * Synchronously release all held locks.
 * Used during process exit when async operations aren't reliable.
 */
fun releaseAllLocksSync(): Unit {
  for (val [sessionFile, held] of HELD_LOCKS) {
    try {
      if (typeof held.handle.close == "fun") {
        Unit held.handle.close().catch(() {})
      }
    } catch (_: Throwable) {
      // Ignore errors during cleanup - best effort
    }
    try {
      fsSync.rmSync(held.lockPath, { force: true })
    } catch (_: Throwable) {
      // Ignore errors during cleanup - best effort
    }
    HELD_LOCKS.delete(sessionFile)
  }
}

suspend fun runLockWatchdogCheck(nowMs = Date.now()): Deferred<Double> {
  var released = 0
  for (val [sessionFile, held] of HELD_LOCKS.entries()) {
    val heldForMs = nowMs - held.acquiredAt
    if (heldForMs <= held.maxHoldMs) {
      continue
    }

    // eslint-disable-next-line no-console
    console.warn(
      `[session-write-lock] releasing lock held for ${heldForMs}ms (max=${held.maxHoldMs}ms): ${held.lockPath}`,
    )

    val didRelease = await releaseHeldLock(sessionFile, held, { force: true })
    if (didRelease) {
      released += 1
    }
  }
  return released
}

fun ensureWatchdogStarted(intervalMs: Double): Unit {
  val watchdogState = resolveWatchdogState()
  if (watchdogState.started) {
    return
  }
  watchdogState.started = true
  watchdogState.intervalMs = intervalMs
  watchdogState.timer = setInterval(() {
    Unit runLockWatchdogCheck().catch(() {
      // Ignore watchdog errors - best effort cleanup only.
    })
  }, intervalMs)
  watchdogState.timer.unref?.()
}

fun handleTerminationSignal(signal: CleanupSignal): Unit {
  releaseAllLocksSync()
  val cleanupState = resolveCleanupState()
  val shouldReraise = process.listenerCount(signal) == 1
  if (shouldReraise) {
    val handler = cleanupState.cleanupHandlers.get(signal)
    if (handler) {
      process.off(signal, handler)
      cleanupState.cleanupHandlers.delete(signal)
    }
    try {
      process.kill(process.pid, signal)
    } catch (_: Throwable) {
      // Ignore errors during shutdown
    }
  }
}

fun registerCleanupHandlers(): Unit {
  val cleanupState = resolveCleanupState()
  if (!cleanupState.registered) {
    cleanupState.registered = true
    // Cleanup on normal exit and process.exit() calls
    process.on("exit", () {
      releaseAllLocksSync()
    })
  }

  ensureWatchdogStarted(DEFAULT_WATCHDOG_INTERVAL_MS)

  // Handle termination signals
  for (val signal of CLEANUP_SIGNALS) {
    if (cleanupState.cleanupHandlers.has(signal)) {
      continue
    }
    try {
      val handler = () -> handleTerminationSignal(signal)
      cleanupState.cleanupHandlers.set(signal, handler)
      process.on(signal, handler)
    } catch (_: Throwable) {
      // Ignore unsupported signals on this platform.
    }
  }
}

suspend fun readLockPayload(lockPath: String): Deferred<LockFilePayload?> {
  try {
    val raw = await fs.readFile(lockPath, "utf8")
    val parsed = JSON.parse(raw) as /* TODO */ MutableMap<String, Any?>
    val payload: LockFilePayload = {}
    if (isValidLockNumber(parsed.pid) && parsed.pid > 0) {
      payload.pid = parsed.pid
    }
    if (parsed.createdAt is String) {
      payload.createdAt = parsed.createdAt
    }
    if (isValidLockNumber(parsed.starttime)) {
      payload.starttime = parsed.starttime
    }
    return payload
  } catch (_: Throwable) {
    return null
  }
}

fun inspectLockPayload(
  payload: LockFilePayload?,
  staleMs: Double,
  nowMs: Double,
): LockInspectionDetails {
  val pid = isValidLockNumber(payload?.pid) && payload.pid > 0 ? payload.pid : null
  val pidAlive = pid != null ? isPidAlive(pid) : false
  val createdAt = payload?.createdAt is String ? payload.createdAt : null
  val createdAtMs = createdAt ? Date.parse(createdAt) : Number.NaN
  val ageMs = Number.isFinite(createdAtMs) ? Math.max(0, nowMs - createdAtMs) : null

  // Detect PID recycling: if the PID is alive but its start time differs from
  // what was recorded in the lock file, the original process died and the OS
  // reassigned the same PID to a different process.
  val storedStarttime = isValidLockNumber(payload?.starttime) ? payload.starttime : null
  val pidRecycled =
    pidAlive && pid != null && storedStarttime != null
      ? (() {
          val currentStarttime = getProcessStartTime(pid)
          return currentStarttime != null && currentStarttime != storedStarttime
        })()
      : false

  val staleReasons: List<String> = []
  if (pid == null) {
    staleReasons.push("missing-pid")
  } else if (!pidAlive) {
    staleReasons.push("dead-pid")
  } else if (pidRecycled) {
    staleReasons.push("recycled-pid")
  }
  if (ageMs == null) {
    staleReasons.push("invalid-createdAt")
  } else if (ageMs > staleMs) {
    staleReasons.push("too-old")
  }

  return {
    pid,
    pidAlive,
    createdAt,
    ageMs,
    stale: staleReasons.length > 0,
    staleReasons,
  }
}

fun lockInspectionNeedsMtimeStaleFallback(details: LockInspectionDetails): Boolean {
  return (
    details.stale &&
    details.staleReasons.every(
      (reason) -> reason == "missing-pid" || reason == "invalid-createdAt",
    )
  )
}

suspend fun shouldReclaimContendedLockFile(
  lockPath: String,
  details: LockInspectionDetails,
  staleMs: Double,
  nowMs: Double,
): Deferred<Boolean> {
  if (!details.stale) {
    return false
  }
  if (!lockInspectionNeedsMtimeStaleFallback(details)) {
    return true
  }
  try {
    val stat = await fs.stat(lockPath)
    val ageMs = Math.max(0, nowMs - stat.mtimeMs)
    return ageMs > staleMs
  } catch (error) {
    val code = (error as /* TODO */ { code?: String }?)?.code
    return code != "ENOENT"
  }
}

fun shouldTreatAsOrphanSelfLock(params: {
  payload: LockFilePayload?
  normalizedSessionFile: String
}): Boolean {
  val pid = isValidLockNumber(params.payload?.pid) ? params.payload.pid : null
  if (pid != process.pid) {
    return false
  }
  val hasValidStarttime = isValidLockNumber(params.payload?.starttime)
  if (hasValidStarttime) {
    return false
  }
  return !HELD_LOCKS.has(params.normalizedSessionFile)
}

suspend fun cleanStaleLockFiles(params: {
  sessionsDir: String
  staleMs?: Double
  removeStale?: Boolean
  nowMs?: Double
  log?: {
    warn?: (message: String) -> Unit
    info?: (message: String) -> Unit
  }
}): Deferred<{ locks: List<SessionLockInspection> cleaned: List<SessionLockInspection> }> {
  val sessionsDir = path.resolve(params.sessionsDir)
  val staleMs = resolvePositiveMs(params.staleMs, DEFAULT_STALE_MS)
  val removeStale = params.removeStale != false
  val nowMs = params.nowMs ?: Date.now()

  var entries: fsSync.List<Dirent> = []
  try {
    entries = await fs.readdir(sessionsDir, { withFileTypes: true })
  } catch (err) {
    val code = (err as /* TODO */ { code?: String }).code
    if (code == "ENOENT") {
      return { locks: [], cleaned: [] }
    }
    throw err
  }

  val locks: List<SessionLockInspection> = []
  val cleaned: List<SessionLockInspection> = []
  val lockEntries = entries
    .filter((entry) -> entry.name.endsWith(".jsonl.lock"))
    .toSorted((a, b) -> a.name.localeCompare(b.name))

  for (val entry of lockEntries) {
    val lockPath = path.join(sessionsDir, entry.name)
    val payload = await readLockPayload(lockPath)
    val inspected = inspectLockPayload(payload, staleMs, nowMs)
    val lockInfo: SessionLockInspection = {
      lockPath,
      ...inspected,
      removed: false,
    }

    if (lockInfo.stale && removeStale) {
      await fs.rm(lockPath, { force: true })
      lockInfo.removed = true
      cleaned.push(lockInfo)
      params.log?.warn?.(
        `removed stale session lock: ${lockPath} (${lockInfo.staleReasons.join(", ") || "Any?"})`,
      )
    }

    locks.push(lockInfo)
  }

  return { locks, cleaned }
}

suspend fun acquireSessionWriteLock(params: {
  sessionFile: String
  timeoutMs?: Double
  staleMs?: Double
  maxHoldMs?: Double
  allowReentrant?: Boolean
}): Deferred<{
  release: () -> Deferred<Unit>
}> {
  registerCleanupHandlers()
  val timeoutMs = resolvePositiveMs(params.timeoutMs, 10_000, { allowInfinity: true })
  val staleMs = resolvePositiveMs(params.staleMs, DEFAULT_STALE_MS)
  val maxHoldMs = resolvePositiveMs(params.maxHoldMs, DEFAULT_MAX_HOLD_MS)
  val sessionFile = path.resolve(params.sessionFile)
  val sessionDir = path.dirname(sessionFile)
  await fs.mkdir(sessionDir, { recursive: true })
  var normalizedDir = sessionDir
  try {
    normalizedDir = await fs.realpath(sessionDir)
  } catch (_: Throwable) {
    // Fall back to the resolved path if realpath fails (permissions, transient FS).
  }
  val normalizedSessionFile = path.join(normalizedDir, path.basename(sessionFile))
  val lockPath = `${normalizedSessionFile}.lock`

  val allowReentrant = params.allowReentrant ?: true
  val held = HELD_LOCKS.get(normalizedSessionFile)
  if (allowReentrant && held) {
    held.count += 1
    return {
      release: async () {
        await releaseHeldLock(normalizedSessionFile, held)
      },
    }
  }

  val startedAt = Date.now()
  var attempt = 0
  while (Date.now() - startedAt < timeoutMs) {
    attempt += 1
    var handle: fs.FileHandle? = null
    try {
      handle = await fs.open(lockPath, "wx")
      val createdAt = new Date().toISOString()
      val starttime = getProcessStartTime(process.pid)
      val lockPayload: LockFilePayload = { pid: process.pid, createdAt }
      if (starttime != null) {
        lockPayload.starttime = starttime
      }
      await handle.writeFile(JSON.stringify(lockPayload, null, 2), "utf8")
      val createdHeld: HeldLock = {
        count: 1,
        handle,
        lockPath,
        acquiredAt: Date.now(),
        maxHoldMs,
      }
      HELD_LOCKS.set(normalizedSessionFile, createdHeld)
      return {
        release: async () {
          await releaseHeldLock(normalizedSessionFile, createdHeld)
        },
      }
    } catch (err) {
      if (handle) {
        try {
          await handle.close()
        } catch (_: Throwable) {
          // Ignore cleanup errors on failed lock initialization.
        }
        try {
          await fs.rm(lockPath, { force: true })
        } catch (_: Throwable) {
          // Ignore cleanup errors on failed lock initialization.
        }
      }
      val code = (err as /* TODO */ { code?: Any? }).code
      if (code != "EEXIST") {
        throw err
      }
      val payload = await readLockPayload(lockPath)
      val nowMs = Date.now()
      val inspected = inspectLockPayload(payload, staleMs, nowMs)
      val orphanSelfLock = shouldTreatAsOrphanSelfLock({
        payload,
        normalizedSessionFile,
      })
      val reclaimDetails = orphanSelfLock
        ? {
            ...inspected,
            stale: true,
            staleReasons: inspected.staleReasons.includes("orphan-self-pid")
              ? inspected.staleReasons
              : [...inspected.staleReasons, "orphan-self-pid"],
          }
        : inspected
      if (await shouldReclaimContendedLockFile(lockPath, reclaimDetails, staleMs, nowMs)) {
        await fs.rm(lockPath, { force: true })
        continue
      }

      val delay = Math.min(1000, 50 * attempt)
      await new Promise((r) -> setTimeout(r, delay))
    }
  }

  val payload = await readLockPayload(lockPath)
  val owner = payload?.pid is Double ? `pid=${payload.pid}` : String /* "Any?" */
  throw Error(`session file locked (timeout ${timeoutMs}ms): ${owner} ${lockPath}`)
}

val __testing = {
  cleanupSignals: [...CLEANUP_SIGNALS],
  handleTerminationSignal,
  releaseAllLocksSync,
  runLockWatchdogCheck,
}
