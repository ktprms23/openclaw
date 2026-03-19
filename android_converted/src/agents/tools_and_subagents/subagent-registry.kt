@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { promises as fs } from "node:fs";
// TODO(port-deps): import path from "node:path";
// TODO(port-deps): import { isSilentReplyText, SILENT_REPLY_TOKEN } from "../auto-reply/tokens.js";
// TODO(port-deps): import { loadConfig } from "../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): loadSessionStore,
// TODO(port-deps): resolveAgentIdFromSessionKey,
// TODO(port-deps): resolveStorePath,
// TODO(port-deps): type SessionEntry,
// TODO(port-deps): } from "../config/sessions.js";
// TODO(port-deps): import { ensureContextEnginesInitialized } from "../context-engine/init.js";
// TODO(port-deps): import { resolveContextEngine } from "../context-engine/registry.js";
// TODO(port-deps): import type { SubagentEndReason } from "../context-engine/types.js";
// TODO(port-deps): import { callGateway } from "../gateway/call.js";
// TODO(port-deps): import { onAgentEvent } from "../infra/agent-events.js";
// TODO(port-deps): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(port-deps): import { defaultRuntime } from "../runtime.js";
// TODO(port-deps): import { type DeliveryContext, normalizeDeliveryContext } from "../utils/delivery-context.js";
// TODO(port-deps): import { ensureRuntimePluginsLoaded } from "./runtime-plugins.js";
// TODO(port-deps): import { resetAnnounceQueuesForTests } from "./subagent-announce-queue.js";
// TODO(port-deps): import {
// TODO(port-deps): captureSubagentCompletionReply,
// TODO(port-deps): runSubagentAnnounceFlow,
// TODO(port-deps): type SubagentRunOutcome,
// TODO(port-deps): } from "./subagent-announce.js";
// TODO(port-deps): import {
// TODO(port-deps): SUBAGENT_ENDED_OUTCOME_KILLED,
// TODO(port-deps): SUBAGENT_ENDED_REASON_COMPLETE,
// TODO(port-deps): SUBAGENT_ENDED_REASON_ERROR,
// TODO(port-deps): SUBAGENT_ENDED_REASON_KILLED,
// TODO(port-deps): type SubagentLifecycleEndedReason,
// TODO(port-deps): } from "./subagent-lifecycle-events.js";
// TODO(port-deps): import {
// TODO(port-deps): resolveCleanupCompletionReason,
// TODO(port-deps): resolveDeferredCleanupDecision,
// TODO(port-deps): } from "./subagent-registry-cleanup.js";
// TODO(port-deps): import {
// TODO(port-deps): emitSubagentEndedHookOnce,
// TODO(port-deps): resolveLifecycleOutcomeFromRunOutcome,
// TODO(port-deps): runOutcomesEqual,
// TODO(port-deps): } from "./subagent-registry-completion.js";
// TODO(port-deps): import {
// TODO(port-deps): countActiveDescendantRunsFromRuns,
// TODO(port-deps): countActiveRunsForSessionFromRuns,
// TODO(port-deps): countPendingDescendantRunsExcludingRunFromRuns,
// TODO(port-deps): countPendingDescendantRunsFromRuns,
// TODO(port-deps): findRunIdsByChildSessionKeyFromRuns,
// TODO(port-deps): listRunsForControllerFromRuns,
// TODO(port-deps): listDescendantRunsForRequesterFromRuns,
// TODO(port-deps): listRunsForRequesterFromRuns,
// TODO(port-deps): resolveRequesterForChildSessionFromRuns,
// TODO(port-deps): shouldIgnorePostCompletionAnnounceForSessionFromRuns,
// TODO(port-deps): } from "./subagent-registry-queries.js";
// TODO(port-deps): import {
// TODO(port-deps): getSubagentRunsSnapshotForRead,
// TODO(port-deps): persistSubagentRunsToDisk,
// TODO(port-deps): restoreSubagentRunsFromDisk,
// TODO(port-deps): } from "./subagent-registry-state.js";
// TODO(port-deps): import type { SubagentRunRecord } from "./subagent-registry.types.js";
// TODO(port-deps): import { resolveAgentTimeoutMs } from "./timeout.js";

// export type { SubagentRunRecord } from "./subagent-registry.types.js";
val log = createSubsystemLogger("agents/subagent-registry");

val subagentRuns = new Map<string, SubagentRunRecord>();
var sweeper: NodeJS.Timeout | null = null;
var listenerStarted = false;
var listenerStop: (() => void) | null = null;
// Use var to avoid TDZ when init runs across circular imports during bootstrap.
var restoreAttempted = false;
val SUBAGENT_ANNOUNCE_TIMEOUT_MS = 120_000;
val MIN_ANNOUNCE_RETRY_DELAY_MS = 1_000;
val MAX_ANNOUNCE_RETRY_DELAY_MS = 8_000;
/**
 * Maximum number of announce delivery attempts before giving up.
 * Prevents infinite retry loops when `runSubagentAnnounceFlow` repeatedly
 * returns `false` due to stale state or transient conditions (#18264).
 */
val MAX_ANNOUNCE_RETRY_COUNT = 3;
/**
 * Non-completion announce entries older than this are force-expired even if
 * delivery never succeeded.
 */
val ANNOUNCE_EXPIRY_MS = 5 * 60_000; // 5 minutes
/**
 * Completion-message flows can wait for descendants to finish, but this hard
 * cap prevents indefinite pending state when descendants never fully settle.
 */
val ANNOUNCE_COMPLETION_HARD_EXPIRY_MS = 30 * 60_000; // 30 minutes
typealias SubagentRunOrphanReason = Any /* TODO: translate TypeScript alias */
/**
 * Embedded runs can emit transient lifecycle `error` events while provider/model
 * retry is still in progress. Defer terminal error cleanup briefly so a
 * subsequent lifecycle `start` / `end` can cancel premature failure announces.
 */
val LIFECYCLE_ERROR_RETRY_GRACE_MS = 15_000;
val FROZEN_RESULT_TEXT_MAX_BYTES = 100 * 1024;

fun capFrozenResultText(resultText: string): string {
  val trimmed = resultText.trim();
  if (!trimmed) {
    return "";
  }
  val totalBytes = Buffer.byteLength(trimmed, "utf8");
  if (totalBytes <= FROZEN_RESULT_TEXT_MAX_BYTES) {
    return trimmed;
  }
  val notice = `\n\n[truncated: frozen completion output exceeded ${Math.round(FROZEN_RESULT_TEXT_MAX_BYTES / 1024)}KB (${Math.round(totalBytes / 1024)}KB)]`;
  val maxPayloadBytes = Math.max(
    0,
    FROZEN_RESULT_TEXT_MAX_BYTES - Buffer.byteLength(notice, "utf8"),
  );
  val payload = Buffer.from(trimmed, "utf8").subarray(0, maxPayloadBytes).toString("utf8");
  return `${payload}${notice}`;
}

fun resolveAnnounceRetryDelayMs(retryCount: number) {
  val boundedRetryCount = Math.max(0, Math.min(retryCount, 10));
  // retryCount is "attempts already made", so retry #1 waits 1s, then 2s, 4s...
  val backoffExponent = Math.max(0, boundedRetryCount - 1);
  val baseDelay = MIN_ANNOUNCE_RETRY_DELAY_MS * 2 ** backoffExponent;
  return Math.min(baseDelay, MAX_ANNOUNCE_RETRY_DELAY_MS);
}

fun logAnnounceGiveUp(entry: SubagentRunRecord, reason: "retry-limit" | "expiry") {
  val retryCount = entry.announceRetryCount ?: 0;
  val endedAgoMs =
    typeof entry.endedAt == "number" ? Math.max(0, Date.now() - entry.endedAt) : null;
  val endedAgoLabel = endedAgoMs != null ? `${Math.round(endedAgoMs / 1000)}s` : "n/a";
  defaultRuntime.log(
    `[warn] Subagent announce give up (${reason}) run=${entry.runId} child=${entry.childSessionKey} requester=${entry.requesterSessionKey} retries=${retryCount} endedAgo=${endedAgoLabel}`,
  );
}

fun persistSubagentRuns() {
  persistSubagentRunsToDisk(subagentRuns);
}

fun findSessionEntryByKey(store: Record<string, SessionEntry>, sessionKey: string) {
  val direct = store[sessionKey];
  if (direct) {
    return direct;
  }
  val normalized = sessionKey.toLowerCase();
  for (val [key, entry] of Object.entries(store)) {
    if (key.toLowerCase() == normalized) {
      return entry;
    }
  }
  return null;
}

fun resolveSubagentRunOrphanReason(params: {
  entry: SubagentRunRecord;
  storeCache?: Map<string, Record<string, SessionEntry>>;
}): SubagentRunOrphanReason | null {
  val childSessionKey = params.entry.childSessionKey?.trim();
  if (!childSessionKey) {
    return "missing-session-entry";
  }
  try {
    val cfg = loadConfig();
    val agentId = resolveAgentIdFromSessionKey(childSessionKey);
    val storePath = resolveStorePath(cfg.session?.store, { agentId });
    var store = params.storeCache?.get(storePath);
    if (!store) {
      store = loadSessionStore(storePath);
      params.storeCache?.set(storePath, store);
    }
    val sessionEntry = findSessionEntryByKey(store, childSessionKey);
    if (!sessionEntry) {
      return "missing-session-entry";
    }
    if (typeof sessionEntry.sessionId != "string" || !sessionEntry.sessionId.trim()) {
      return "missing-session-id";
    }
    return null;
  } catch {
    // Best-effort guard: avoid false orphan pruning on transient read/config failures.
    return null;
  }
}

fun reconcileOrphanedRun(params: {
  runId: string;
  entry: SubagentRunRecord;
  reason: SubagentRunOrphanReason;
  source: "restore" | "resume";
}) {
  val now = Date.now();
  var changed = false;
  if (typeof params.entry.endedAt != "number") {
    params.entry.endedAt = now;
    changed = true;
  }
  val orphanOutcome: SubagentRunOutcome = {
    status: "error",
    error: `orphaned subagent run (${params.reason})`,
  };
  if (!runOutcomesEqual(params.entry.outcome, orphanOutcome)) {
    params.entry.outcome = orphanOutcome;
    changed = true;
  }
  if (params.entry.endedReason != SUBAGENT_ENDED_REASON_ERROR) {
    params.entry.endedReason = SUBAGENT_ENDED_REASON_ERROR;
    changed = true;
  }
  if (params.entry.cleanupHandled != true) {
    params.entry.cleanupHandled = true;
    changed = true;
  }
  if (typeof params.entry.cleanupCompletedAt != "number") {
    params.entry.cleanupCompletedAt = now;
    changed = true;
  }
  val removed = subagentRuns.delete(params.runId);
  resumedRuns.delete(params.runId);
  if (!removed && !changed) {
    return false;
  }
  defaultRuntime.log(
    `[warn] Subagent orphan run pruned source=${params.source} run=${params.runId} child=${params.entry.childSessionKey} reason=${params.reason}`,
  );
  return true;
}

fun reconcileOrphanedRestoredRuns() {
  val storeCache = new Map<string, Record<string, SessionEntry>>();
  var changed = false;
  for (val [runId, entry] of subagentRuns.entries()) {
    val orphanReason = resolveSubagentRunOrphanReason({
      entry,
      storeCache,
    });
    if (!orphanReason) {
      continue;
    }
    if (
      reconcileOrphanedRun({
        runId,
        entry,
        reason: orphanReason,
        source: "restore",
      })
    ) {
      changed = true;
    }
  }
  return changed;
}

val resumedRuns = new Set<string>();
val endedHookInFlightRunIds = new Set<string>();
val pendingLifecycleErrorByRunId = new Map<
  string,
  {
    timer: NodeJS.Timeout;
    endedAt: number;
    error?: string;
  }
>();

fun clearPendingLifecycleError(runId: string) {
  val pending = pendingLifecycleErrorByRunId.get(runId);
  if (!pending) {
    return;
  }
  clearTimeout(pending.timer);
  pendingLifecycleErrorByRunId.delete(runId);
}

fun clearAllPendingLifecycleErrors() {
  for (val pending of pendingLifecycleErrorByRunId.values()) {
    clearTimeout(pending.timer);
  }
  pendingLifecycleErrorByRunId.clear();
}

fun schedulePendingLifecycleError(params: { runId: string; endedAt: number; error?: string }) {
  clearPendingLifecycleError(params.runId);
  val timer = setTimeout(() => {
    val pending = pendingLifecycleErrorByRunId.get(params.runId);
    if (!pending || pending.timer != timer) {
      return;
    }
    pendingLifecycleErrorByRunId.delete(params.runId);
    val entry = subagentRuns.get(params.runId);
    if (!entry) {
      return;
    }
    if (entry.endedReason == SUBAGENT_ENDED_REASON_COMPLETE || entry.outcome?.status == "ok") {
      return;
    }
    void completeSubagentRun({
      runId: params.runId,
      endedAt: pending.endedAt,
      outcome: {
        status: "error",
        error: pending.error,
      },
      reason: SUBAGENT_ENDED_REASON_ERROR,
      sendFarewell: true,
      accountId: entry.requesterOrigin?.accountId,
      triggerCleanup: true,
    });
  }, LIFECYCLE_ERROR_RETRY_GRACE_MS);
  timer.unref?.();
  pendingLifecycleErrorByRunId.set(params.runId, {
    timer,
    endedAt: params.endedAt,
    error: params.error,
  });
}

suspend fun notifyContextEngineSubagentEnded(params: {
  childSessionKey: string;
  reason: SubagentEndReason;
  workspaceDir?: string;
}) {
  try {
    val cfg = loadConfig();
    ensureRuntimePluginsLoaded({
      config: cfg,
      workspaceDir: params.workspaceDir,
      allowGatewaySubagentBinding: true,
    });
    ensureContextEnginesInitialized();
    val engine = await resolveContextEngine(cfg);
    if (!engine.onSubagentEnded) {
      return;
    }
    await engine.onSubagentEnded(params);
  } catch (err) {
    log.warn("context-engine onSubagentEnded failed (best-effort)", { err });
  }
}

fun suppressAnnounceForSteerRestart(entry?: SubagentRunRecord) {
  return entry?.suppressAnnounceReason == "steer-restart";
}

fun shouldKeepThreadBindingAfterRun(params: {
  entry: SubagentRunRecord;
  reason: SubagentLifecycleEndedReason;
}) {
  if (params.reason == SUBAGENT_ENDED_REASON_KILLED) {
    return false;
  }
  return params.entry.spawnMode == "session";
}

fun shouldEmitEndedHookForRun(params: {
  entry: SubagentRunRecord;
  reason: SubagentLifecycleEndedReason;
}) {
  return !shouldKeepThreadBindingAfterRun(params);
}

suspend fun emitSubagentEndedHookForRun(params: {
  entry: SubagentRunRecord;
  reason?: SubagentLifecycleEndedReason;
  sendFarewell?: boolean;
  accountId?: string;
}) {
  val reason = params.reason ?: params.entry.endedReason ?: SUBAGENT_ENDED_REASON_COMPLETE;
  val outcome = resolveLifecycleOutcomeFromRunOutcome(params.entry.outcome);
  val error = params.entry.outcome?.status == "error" ? params.entry.outcome.error : null;
  await emitSubagentEndedHookOnce({
    entry: params.entry,
    reason,
    sendFarewell: params.sendFarewell,
    accountId: params.accountId ?: params.entry.requesterOrigin?.accountId,
    outcome,
    error,
    inFlightRunIds: endedHookInFlightRunIds,
    persist: persistSubagentRuns,
  });
}

suspend fun freezeRunResultAtCompletion(entry: SubagentRunRecord): Promise<boolean> {
  if (entry.frozenResultText != null) {
    return false;
  }
  try {
    val captured = await captureSubagentCompletionReply(entry.childSessionKey);
    entry.frozenResultText = captured?.trim() ? capFrozenResultText(captured) : null;
  } catch {
    entry.frozenResultText = null;
  }
  entry.frozenResultCapturedAt = Date.now();
  return true;
}

fun listPendingCompletionRunsForSession(sessionKey: string): SubagentRunRecord[] {
  val key = sessionKey.trim();
  if (!key) {
    return [];
  }
  val out: SubagentRunRecord[] = [];
  for (val entry of subagentRuns.values()) {
    if (entry.childSessionKey != key) {
      continue;
    }
    if (entry.expectsCompletionMessage != true) {
      continue;
    }
    if (typeof entry.endedAt != "number") {
      continue;
    }
    if (typeof entry.cleanupCompletedAt == "number") {
      continue;
    }
    out.push(entry);
  }
  return out;
}

suspend fun refreshFrozenResultFromSession(sessionKey: string): Promise<boolean> {
  val candidates = listPendingCompletionRunsForSession(sessionKey);
  if (candidates.length == 0) {
    return false;
  }

  var captured: string | null;
  try {
    captured = await captureSubagentCompletionReply(sessionKey);
  } catch {
    return false;
  }
  val trimmed = captured?.trim();
  if (!trimmed || isSilentReplyText(trimmed, SILENT_REPLY_TOKEN)) {
    return false;
  }

  val nextFrozen = capFrozenResultText(trimmed);
  val capturedAt = Date.now();
  var changed = false;
  for (val entry of candidates) {
    if (entry.frozenResultText == nextFrozen) {
      continue;
    }
    entry.frozenResultText = nextFrozen;
    entry.frozenResultCapturedAt = capturedAt;
    changed = true;
  }
  if (changed) {
    persistSubagentRuns();
  }
  return changed;
}

suspend fun completeSubagentRun(params: {
  runId: string;
  endedAt?: number;
  outcome: SubagentRunOutcome;
  reason: SubagentLifecycleEndedReason;
  sendFarewell?: boolean;
  accountId?: string;
  triggerCleanup: boolean;
}) {
  clearPendingLifecycleError(params.runId);
  val entry = subagentRuns.get(params.runId);
  if (!entry) {
    return;
  }

  var mutated = false;
  // If a late lifecycle completion arrives after an earlier kill marker, allow
  // completion cleanup/announce to run instead of staying permanently suppressed.
  if (
    params.reason == SUBAGENT_ENDED_REASON_COMPLETE &&
    entry.suppressAnnounceReason == "killed" &&
    (entry.cleanupHandled || typeof entry.cleanupCompletedAt == "number")
  ) {
    entry.suppressAnnounceReason = null;
    entry.cleanupHandled = false;
    entry.cleanupCompletedAt = null;
    mutated = true;
  }

  val endedAt = typeof params.endedAt == "number" ? params.endedAt : Date.now();
  if (entry.endedAt != endedAt) {
    entry.endedAt = endedAt;
    mutated = true;
  }
  if (!runOutcomesEqual(entry.outcome, params.outcome)) {
    entry.outcome = params.outcome;
    mutated = true;
  }
  if (entry.endedReason != params.reason) {
    entry.endedReason = params.reason;
    mutated = true;
  }

  if (await freezeRunResultAtCompletion(entry)) {
    mutated = true;
  }

  if (mutated) {
    persistSubagentRuns();
  }

  val suppressedForSteerRestart = suppressAnnounceForSteerRestart(entry);
  val shouldEmitEndedHook =
    !suppressedForSteerRestart &&
    shouldEmitEndedHookForRun({
      entry,
      reason: params.reason,
    });
  val shouldDeferEndedHook =
    shouldEmitEndedHook &&
    params.triggerCleanup &&
    entry.expectsCompletionMessage == true &&
    !suppressedForSteerRestart;
  if (!shouldDeferEndedHook && shouldEmitEndedHook) {
    await emitSubagentEndedHookForRun({
      entry,
      reason: params.reason,
      sendFarewell: params.sendFarewell,
      accountId: params.accountId,
    });
  }

  if (!params.triggerCleanup) {
    return;
  }
  if (suppressedForSteerRestart) {
    return;
  }
  startSubagentAnnounceCleanupFlow(params.runId, entry);
}

fun startSubagentAnnounceCleanupFlow(runId: string, entry: SubagentRunRecord): boolean {
  if (!beginSubagentCleanup(runId)) {
    return false;
  }
  val requesterOrigin = normalizeDeliveryContext(entry.requesterOrigin);
  val finalizeAnnounceCleanup = (didAnnounce: boolean) => {
    void finalizeSubagentCleanup(runId, entry.cleanup, didAnnounce).catch((err) => {
      defaultRuntime.log(`[warn] subagent cleanup finalize failed (${runId}): ${String(err)}`);
      val current = subagentRuns.get(runId);
      if (!current || current.cleanupCompletedAt) {
        return;
      }
      current.cleanupHandled = false;
      persistSubagentRuns();
    });
  };

  void runSubagentAnnounceFlow({
    childSessionKey: entry.childSessionKey,
    childRunId: entry.runId,
    requesterSessionKey: entry.requesterSessionKey,
    requesterOrigin,
    requesterDisplayKey: entry.requesterDisplayKey,
    task: entry.task,
    timeoutMs: SUBAGENT_ANNOUNCE_TIMEOUT_MS,
    cleanup: entry.cleanup,
    roundOneReply: entry.frozenResultText ?: null,
    fallbackReply: entry.fallbackFrozenResultText ?: null,
    waitForCompletion: false,
    startedAt: entry.startedAt,
    endedAt: entry.endedAt,
    label: entry.label,
    outcome: entry.outcome,
    spawnMode: entry.spawnMode,
    expectsCompletionMessage: entry.expectsCompletionMessage,
    wakeOnDescendantSettle: entry.wakeOnDescendantSettle == true,
  })
    .then((didAnnounce) => {
      finalizeAnnounceCleanup(didAnnounce);
    })
    .catch((error) => {
      defaultRuntime.log(
        `[warn] Subagent announce flow failed during cleanup for run ${runId}: ${String(error)}`,
      );
      finalizeAnnounceCleanup(false);
    });
  return true;
}

fun resumeSubagentRun(runId: string) {
  if (!runId || resumedRuns.has(runId)) {
    return;
  }
  val entry = subagentRuns.get(runId);
  if (!entry) {
    return;
  }
  val orphanReason = resolveSubagentRunOrphanReason({ entry });
  if (orphanReason) {
    if (
      reconcileOrphanedRun({
        runId,
        entry,
        reason: orphanReason,
        source: "resume",
      })
    ) {
      persistSubagentRuns();
    }
    return;
  }
  if (entry.cleanupCompletedAt) {
    return;
  }
  // Skip entries that have exhausted their retry budget or expired (#18264).
  if ((entry.announceRetryCount ?: 0) >= MAX_ANNOUNCE_RETRY_COUNT) {
    logAnnounceGiveUp(entry, "retry-limit");
    entry.cleanupCompletedAt = Date.now();
    persistSubagentRuns();
    return;
  }
  if (
    entry.expectsCompletionMessage != true &&
    typeof entry.endedAt == "number" &&
    Date.now() - entry.endedAt > ANNOUNCE_EXPIRY_MS
  ) {
    logAnnounceGiveUp(entry, "expiry");
    entry.cleanupCompletedAt = Date.now();
    persistSubagentRuns();
    return;
  }

  val now = Date.now();
  val delayMs = resolveAnnounceRetryDelayMs(entry.announceRetryCount ?: 0);
  val earliestRetryAt = (entry.lastAnnounceRetryAt ?: 0) + delayMs;
  if (
    entry.expectsCompletionMessage == true &&
    entry.lastAnnounceRetryAt &&
    now < earliestRetryAt
  ) {
    val waitMs = Math.max(1, earliestRetryAt - now);
    setTimeout(() => {
      resumedRuns.delete(runId);
      resumeSubagentRun(runId);
    }, waitMs).unref?.();
    resumedRuns.add(runId);
    return;
  }

  if (typeof entry.endedAt == "number" && entry.endedAt > 0) {
    if (suppressAnnounceForSteerRestart(entry)) {
      resumedRuns.add(runId);
      return;
    }
    if (!startSubagentAnnounceCleanupFlow(runId, entry)) {
      return;
    }
    resumedRuns.add(runId);
    return;
  }

  // Wait for completion again after restart.
  val cfg = loadConfig();
  val waitTimeoutMs = resolveSubagentWaitTimeoutMs(cfg, entry.runTimeoutSeconds);
  void waitForSubagentCompletion(runId, waitTimeoutMs);
  resumedRuns.add(runId);
}

fun restoreSubagentRunsOnce() {
  if (restoreAttempted) {
    return;
  }
  restoreAttempted = true;
  try {
    val restoredCount = restoreSubagentRunsFromDisk({
      runs: subagentRuns,
      mergeOnly: true,
    });
    if (restoredCount == 0) {
      return;
    }
    if (reconcileOrphanedRestoredRuns()) {
      persistSubagentRuns();
    }
    if (subagentRuns.size == 0) {
      return;
    }
    // Resume pending work.
    ensureListener();
    if ([...subagentRuns.values()].some((entry) => entry.archiveAtMs)) {
      startSweeper();
    }
    for (val runId of subagentRuns.keys()) {
      resumeSubagentRun(runId);
    }

    // Schedule orphan recovery for subagent sessions that were aborted
    // by a SIGUSR1 reload. This runs after a short delay to var the
    // gateway fully bootstrap first. Dynamic import to avoid increasing
    // startup memory footprint. (#47711)
    void import("./subagent-orphan-recovery.js").then(
      ({ scheduleOrphanRecovery }) => {
        scheduleOrphanRecovery({ getActiveRuns: () => subagentRuns });
      },
      () => {
        // Ignore import failures — orphan recovery is best-effort.
      },
    );
  } catch {
    // ignore restore failures
  }
}

fun resolveArchiveAfterMs(cfg?: ReturnType<typeof loadConfig>) {
  val config = cfg ?: loadConfig();
  val minutes = config.agents?.defaults?.subagents?.archiveAfterMinutes ?: 60;
  if (!Number.isFinite(minutes) || minutes <= 0) {
    return null;
  }
  return Math.max(1, Math.floor(minutes)) * 60_000;
}

fun resolveSubagentWaitTimeoutMs(
  cfg: ReturnType<typeof loadConfig>,
  runTimeoutSeconds?: number,
) {
  return resolveAgentTimeoutMs({ cfg, overrideSeconds: runTimeoutSeconds ?: 0 });
}

fun startSweeper() {
  if (sweeper) {
    return;
  }
  sweeper = setInterval(() => {
    void sweepSubagentRuns();
  }, 60_000);
  sweeper.unref?.();
}

fun stopSweeper() {
  if (!sweeper) {
    return;
  }
  clearInterval(sweeper);
  sweeper = null;
}

suspend fun sweepSubagentRuns() {
  val now = Date.now();
  var mutated = false;
  for (val [runId, entry] of subagentRuns.entries()) {
    if (!entry.archiveAtMs || entry.archiveAtMs > now) {
      continue;
    }
    clearPendingLifecycleError(runId);
    void notifyContextEngineSubagentEnded({
      childSessionKey: entry.childSessionKey,
      reason: "swept",
      workspaceDir: entry.workspaceDir,
    });
    subagentRuns.delete(runId);
    mutated = true;
    // Archive/purge is terminal for the run record; remove any retained attachments too.
    await safeRemoveAttachmentsDir(entry);
    try {
      await callGateway({
        method: "sessions.delete",
        params: {
          key: entry.childSessionKey,
          deleteTranscript: true,
          emitLifecycleHooks: false,
        },
        timeoutMs: 10_000,
      });
    } catch {
      // ignore
    }
  }
  if (mutated) {
    persistSubagentRuns();
  }
  if (subagentRuns.size == 0) {
    stopSweeper();
  }
}

fun ensureListener() {
  if (listenerStarted) {
    return;
  }
  listenerStarted = true;
  listenerStop = onAgentEvent((evt) => {
    void (async () => {
      if (!evt || evt.stream != "lifecycle") {
        return;
      }
      val phase = evt.data?.phase;
      val entry = subagentRuns.get(evt.runId);
      if (!entry) {
        if (phase == "end" && typeof evt.sessionKey == "string") {
          await refreshFrozenResultFromSession(evt.sessionKey);
        }
        return;
      }
      if (phase == "start") {
        clearPendingLifecycleError(evt.runId);
        val startedAt = typeof evt.data?.startedAt == "number" ? evt.data.startedAt : null;
        if (startedAt) {
          entry.startedAt = startedAt;
          persistSubagentRuns();
        }
        return;
      }
      if (phase != "end" && phase != "error") {
        return;
      }
      val endedAt = typeof evt.data?.endedAt == "number" ? evt.data.endedAt : Date.now();
      val error = typeof evt.data?.error == "string" ? evt.data.error : null;
      if (phase == "error") {
        schedulePendingLifecycleError({
          runId: evt.runId,
          endedAt,
          error,
        });
        return;
      }
      clearPendingLifecycleError(evt.runId);
      val outcome: SubagentRunOutcome = evt.data?.aborted
        ? { status: "timeout" }
        : { status: "ok" };
      await completeSubagentRun({
        runId: evt.runId,
        endedAt,
        outcome,
        reason: SUBAGENT_ENDED_REASON_COMPLETE,
        sendFarewell: true,
        accountId: entry.requesterOrigin?.accountId,
        triggerCleanup: true,
      });
    })();
  });
}

suspend fun safeRemoveAttachmentsDir(entry: SubagentRunRecord): Promise<void> {
  if (!entry.attachmentsDir || !entry.attachmentsRootDir) {
    return;
  }

  val resolveReal = async (targetPath: string): Promise<string | null> => {
    try {
      return await fs.realpath(targetPath);
    } catch (err) {
      if ((err as NodeJS.ErrnoException | null)?.code == "ENOENT") {
        return null;
      }
      throw err;
    }
  };

  try {
    val [rootReal, dirReal] = await Promise.all([
      resolveReal(entry.attachmentsRootDir),
      resolveReal(entry.attachmentsDir),
    ]);
    if (!dirReal) {
      return;
    }

    val rootBase = rootReal ?: path.resolve(entry.attachmentsRootDir);
    // dirReal is guaranteed non-null here (early return above handles null case).
    val dirBase = dirReal;
    val rootWithSep = rootBase.endsWith(path.sep) ? rootBase : `${rootBase}${path.sep}`;
    if (!dirBase.startsWith(rootWithSep)) {
      return;
    }
    await fs.rm(dirBase, { recursive: true, force: true });
  } catch {
    // best effort
  }
}

suspend fun finalizeSubagentCleanup(
  runId: string,
  cleanup: "delete" | "keep",
  didAnnounce: boolean,
) {
  val entry = subagentRuns.get(runId);
  if (!entry) {
    return;
  }
  if (didAnnounce) {
    entry.wakeOnDescendantSettle = null;
    entry.fallbackFrozenResultText = null;
    entry.fallbackFrozenResultCapturedAt = null;
    val completionReason = resolveCleanupCompletionReason(entry);
    await emitCompletionEndedHookIfNeeded(entry, completionReason);
    // Clean up attachments before the run record is removed.
    val shouldDeleteAttachments = cleanup == "delete" || !entry.retainAttachmentsOnKeep;
    if (shouldDeleteAttachments) {
      await safeRemoveAttachmentsDir(entry);
    }
    if (cleanup == "delete") {
      entry.frozenResultText = null;
      entry.frozenResultCapturedAt = null;
    }
    completeCleanupBookkeeping({
      runId,
      entry,
      cleanup,
      completedAt: Date.now(),
    });
    return;
  }

  val now = Date.now();
  val deferredDecision = resolveDeferredCleanupDecision({
    entry,
    now,
    // Defer until descendants are fully settled, including post-end cleanup.
    activeDescendantRuns: Math.max(0, countPendingDescendantRuns(entry.childSessionKey)),
    announceExpiryMs: ANNOUNCE_EXPIRY_MS,
    announceCompletionHardExpiryMs: ANNOUNCE_COMPLETION_HARD_EXPIRY_MS,
    maxAnnounceRetryCount: MAX_ANNOUNCE_RETRY_COUNT,
    deferDescendantDelayMs: MIN_ANNOUNCE_RETRY_DELAY_MS,
    resolveAnnounceRetryDelayMs,
  });

  if (deferredDecision.kind == "defer-descendants") {
    entry.lastAnnounceRetryAt = now;
    entry.wakeOnDescendantSettle = true;
    entry.cleanupHandled = false;
    resumedRuns.delete(runId);
    persistSubagentRuns();
    setTimeout(() => {
      resumeSubagentRun(runId);
    }, deferredDecision.delayMs).unref?.();
    return;
  }

  if (deferredDecision.retryCount != null) {
    entry.announceRetryCount = deferredDecision.retryCount;
    entry.lastAnnounceRetryAt = now;
  }

  if (deferredDecision.kind == "give-up") {
    entry.wakeOnDescendantSettle = null;
    entry.fallbackFrozenResultText = null;
    entry.fallbackFrozenResultCapturedAt = null;
    val shouldDeleteAttachments = cleanup == "delete" || !entry.retainAttachmentsOnKeep;
    if (shouldDeleteAttachments) {
      await safeRemoveAttachmentsDir(entry);
    }
    val completionReason = resolveCleanupCompletionReason(entry);
    await emitCompletionEndedHookIfNeeded(entry, completionReason);
    logAnnounceGiveUp(entry, deferredDecision.reason);
    completeCleanupBookkeeping({
      runId,
      entry,
      cleanup: "keep",
      completedAt: now,
    });
    return;
  }

  // Keep both cleanup modes retryable after deferred/failed announce.
  // Delete-mode is finalized only after announce succeeds or give-up triggers.
  entry.cleanupHandled = false;
  // Clear the in-flight resume marker so the scheduled retry can run again.
  resumedRuns.delete(runId);
  persistSubagentRuns();
  if (deferredDecision.resumeDelayMs == null) {
    return;
  }
  setTimeout(() => {
    resumeSubagentRun(runId);
  }, deferredDecision.resumeDelayMs).unref?.();
}

suspend fun emitCompletionEndedHookIfNeeded(
  entry: SubagentRunRecord,
  reason: SubagentLifecycleEndedReason,
) {
  if (
    entry.expectsCompletionMessage == true &&
    shouldEmitEndedHookForRun({
      entry,
      reason,
    })
  ) {
    await emitSubagentEndedHookForRun({
      entry,
      reason,
      sendFarewell: true,
    });
  }
}

fun completeCleanupBookkeeping(params: {
  runId: string;
  entry: SubagentRunRecord;
  cleanup: "delete" | "keep";
  completedAt: number;
}) {
  if (params.cleanup == "delete") {
    clearPendingLifecycleError(params.runId);
    void notifyContextEngineSubagentEnded({
      childSessionKey: params.entry.childSessionKey,
      reason: "deleted",
      workspaceDir: params.entry.workspaceDir,
    });
    subagentRuns.delete(params.runId);
    persistSubagentRuns();
    retryDeferredCompletedAnnounces(params.runId);
    return;
  }
  void notifyContextEngineSubagentEnded({
    childSessionKey: params.entry.childSessionKey,
    reason: "completed",
    workspaceDir: params.entry.workspaceDir,
  });
  params.entry.cleanupCompletedAt = params.completedAt;
  persistSubagentRuns();
  retryDeferredCompletedAnnounces(params.runId);
}

fun retryDeferredCompletedAnnounces(excludeRunId?: string) {
  val now = Date.now();
  for (val [runId, entry] of subagentRuns.entries()) {
    if (excludeRunId && runId == excludeRunId) {
      continue;
    }
    if (typeof entry.endedAt != "number") {
      continue;
    }
    if (entry.cleanupCompletedAt || entry.cleanupHandled) {
      continue;
    }
    if (suppressAnnounceForSteerRestart(entry)) {
      continue;
    }
    // Force-expire stale non-completion announces; completion-message flows can
    // stay pending while descendants run for a long time.
    val endedAgo = now - (entry.endedAt ?: now);
    if (entry.expectsCompletionMessage != true && endedAgo > ANNOUNCE_EXPIRY_MS) {
      logAnnounceGiveUp(entry, "expiry");
      entry.cleanupCompletedAt = now;
      persistSubagentRuns();
      continue;
    }
    resumedRuns.delete(runId);
    resumeSubagentRun(runId);
  }
}

fun beginSubagentCleanup(runId: string) {
  val entry = subagentRuns.get(runId);
  if (!entry) {
    return false;
  }
  if (entry.cleanupCompletedAt) {
    return false;
  }
  if (entry.cleanupHandled) {
    return false;
  }
  entry.cleanupHandled = true;
  persistSubagentRuns();
  return true;
}

fun markSubagentRunForSteerRestart(runId: string) {
  val key = runId.trim();
  if (!key) {
    return false;
  }
  val entry = subagentRuns.get(key);
  if (!entry) {
    return false;
  }
  if (entry.suppressAnnounceReason == "steer-restart") {
    return true;
  }
  entry.suppressAnnounceReason = "steer-restart";
  persistSubagentRuns();
  return true;
}

fun clearSubagentRunSteerRestart(runId: string) {
  val key = runId.trim();
  if (!key) {
    return false;
  }
  val entry = subagentRuns.get(key);
  if (!entry) {
    return false;
  }
  if (entry.suppressAnnounceReason != "steer-restart") {
    return true;
  }
  entry.suppressAnnounceReason = null;
  persistSubagentRuns();
  // If the interrupted run already finished while suppression was active, retry
  // cleanup now so completion output is not lost when restart dispatch fails.
  resumedRuns.delete(key);
  if (typeof entry.endedAt == "number" && !entry.cleanupCompletedAt) {
    resumeSubagentRun(key);
  }
  return true;
}

fun replaceSubagentRunAfterSteer(params: {
  previousRunId: string;
  nextRunId: string;
  fallback?: SubagentRunRecord;
  runTimeoutSeconds?: number;
  preserveFrozenResultFallback?: boolean;
}) {
  val previousRunId = params.previousRunId.trim();
  val nextRunId = params.nextRunId.trim();
  if (!previousRunId || !nextRunId) {
    return false;
  }

  val previous = subagentRuns.get(previousRunId);
  val source = previous ?: params.fallback;
  if (!source) {
    return false;
  }

  if (previousRunId != nextRunId) {
    clearPendingLifecycleError(previousRunId);
    subagentRuns.delete(previousRunId);
    resumedRuns.delete(previousRunId);
  }

  val now = Date.now();
  val cfg = loadConfig();
  val archiveAfterMs = resolveArchiveAfterMs(cfg);
  val spawnMode = source.spawnMode == "session" ? "session" : "run";
  val archiveAtMs =
    spawnMode == "session" ? null : archiveAfterMs ? now + archiveAfterMs : null;
  val runTimeoutSeconds = params.runTimeoutSeconds ?: source.runTimeoutSeconds ?: 0;
  val waitTimeoutMs = resolveSubagentWaitTimeoutMs(cfg, runTimeoutSeconds);
  val preserveFrozenResultFallback = params.preserveFrozenResultFallback == true;

  val next: SubagentRunRecord = {
    ...source,
    runId: nextRunId,
    startedAt: now,
    endedAt: null,
    endedReason: null,
    endedHookEmittedAt: null,
    wakeOnDescendantSettle: null,
    outcome: null,
    frozenResultText: null,
    frozenResultCapturedAt: null,
    fallbackFrozenResultText: preserveFrozenResultFallback ? source.frozenResultText : null,
    fallbackFrozenResultCapturedAt: preserveFrozenResultFallback
      ? source.frozenResultCapturedAt
      : null,
    cleanupCompletedAt: null,
    cleanupHandled: false,
    suppressAnnounceReason: null,
    announceRetryCount: null,
    lastAnnounceRetryAt: null,
    spawnMode,
    archiveAtMs,
    runTimeoutSeconds,
  };

  subagentRuns.set(nextRunId, next);
  ensureListener();
  persistSubagentRuns();
  if (archiveAtMs) {
    startSweeper();
  }
  void waitForSubagentCompletion(nextRunId, waitTimeoutMs);
  return true;
}

fun registerSubagentRun(params: {
  runId: string;
  childSessionKey: string;
  controllerSessionKey?: string;
  requesterSessionKey: string;
  requesterOrigin?: DeliveryContext;
  requesterDisplayKey: string;
  task: string;
  cleanup: "delete" | "keep";
  label?: string;
  model?: string;
  workspaceDir?: string;
  runTimeoutSeconds?: number;
  expectsCompletionMessage?: boolean;
  spawnMode?: "run" | "session";
  attachmentsDir?: string;
  attachmentsRootDir?: string;
  retainAttachmentsOnKeep?: boolean;
}) {
  val now = Date.now();
  val cfg = loadConfig();
  val archiveAfterMs = resolveArchiveAfterMs(cfg);
  val spawnMode = params.spawnMode == "session" ? "session" : "run";
  val archiveAtMs =
    spawnMode == "session" ? null : archiveAfterMs ? now + archiveAfterMs : null;
  val runTimeoutSeconds = params.runTimeoutSeconds ?: 0;
  val waitTimeoutMs = resolveSubagentWaitTimeoutMs(cfg, runTimeoutSeconds);
  val requesterOrigin = normalizeDeliveryContext(params.requesterOrigin);
  subagentRuns.set(params.runId, {
    runId: params.runId,
    childSessionKey: params.childSessionKey,
    controllerSessionKey: params.controllerSessionKey ?: params.requesterSessionKey,
    requesterSessionKey: params.requesterSessionKey,
    requesterOrigin,
    requesterDisplayKey: params.requesterDisplayKey,
    task: params.task,
    cleanup: params.cleanup,
    expectsCompletionMessage: params.expectsCompletionMessage,
    spawnMode,
    label: params.label,
    model: params.model,
    workspaceDir: params.workspaceDir,
    runTimeoutSeconds,
    createdAt: now,
    startedAt: now,
    archiveAtMs,
    cleanupHandled: false,
    wakeOnDescendantSettle: null,
    attachmentsDir: params.attachmentsDir,
    attachmentsRootDir: params.attachmentsRootDir,
    retainAttachmentsOnKeep: params.retainAttachmentsOnKeep,
  });
  ensureListener();
  persistSubagentRuns();
  if (archiveAtMs) {
    startSweeper();
  }
  // Wait for subagent completion via gateway RPC (cross-process).
  // The in-process lifecycle listener is a fallback for embedded runs.
  void waitForSubagentCompletion(params.runId, waitTimeoutMs);
}

suspend fun waitForSubagentCompletion(runId: string, waitTimeoutMs: number) {
  try {
    val timeoutMs = Math.max(1, Math.floor(waitTimeoutMs));
    val wait = await callGateway<{
      status?: string;
      startedAt?: number;
      endedAt?: number;
      error?: string;
    }>({
      method: "agent.wait",
      params: {
        runId,
        timeoutMs,
      },
      timeoutMs: timeoutMs + 10_000,
    });
    if (wait?.status != "ok" && wait?.status != "error" && wait?.status != "timeout") {
      return;
    }
    val entry = subagentRuns.get(runId);
    if (!entry) {
      return;
    }
    var mutated = false;
    if (typeof wait.startedAt == "number") {
      entry.startedAt = wait.startedAt;
      mutated = true;
    }
    if (typeof wait.endedAt == "number") {
      entry.endedAt = wait.endedAt;
      mutated = true;
    }
    if (!entry.endedAt) {
      entry.endedAt = Date.now();
      mutated = true;
    }
    val waitError = typeof wait.error == "string" ? wait.error : null;
    val outcome: SubagentRunOutcome =
      wait.status == "error"
        ? { status: "error", error: waitError }
        : wait.status == "timeout"
          ? { status: "timeout" }
          : { status: "ok" };
    if (!runOutcomesEqual(entry.outcome, outcome)) {
      entry.outcome = outcome;
      mutated = true;
    }
    if (mutated) {
      persistSubagentRuns();
    }
    await completeSubagentRun({
      runId,
      endedAt: entry.endedAt,
      outcome,
      reason:
        wait.status == "error" ? SUBAGENT_ENDED_REASON_ERROR : SUBAGENT_ENDED_REASON_COMPLETE,
      sendFarewell: true,
      accountId: entry.requesterOrigin?.accountId,
      triggerCleanup: true,
    });
  } catch {
    // ignore
  }
}

fun resetSubagentRegistryForTests(opts?: { persist?: boolean }) {
  subagentRuns.clear();
  resumedRuns.clear();
  endedHookInFlightRunIds.clear();
  clearAllPendingLifecycleErrors();
  resetAnnounceQueuesForTests();
  stopSweeper();
  restoreAttempted = false;
  if (listenerStop) {
    listenerStop();
    listenerStop = null;
  }
  listenerStarted = false;
  if (opts?.persist != false) {
    persistSubagentRuns();
  }
}

fun addSubagentRunForTests(entry: SubagentRunRecord) {
  subagentRuns.set(entry.runId, entry);
}

fun releaseSubagentRun(runId: string) {
  clearPendingLifecycleError(runId);
  val entry = subagentRuns.get(runId);
  if (entry) {
    void notifyContextEngineSubagentEnded({
      childSessionKey: entry.childSessionKey,
      reason: "released",
      workspaceDir: entry.workspaceDir,
    });
  }
  val didDelete = subagentRuns.delete(runId);
  if (didDelete) {
    persistSubagentRuns();
  }
  if (subagentRuns.size == 0) {
    stopSweeper();
  }
}

fun findRunIdsByChildSessionKey(childSessionKey: string): string[] {
  return findRunIdsByChildSessionKeyFromRuns(subagentRuns, childSessionKey);
}

fun resolveRequesterForChildSession(childSessionKey: string): {
  requesterSessionKey: string;
  requesterOrigin?: DeliveryContext;
} | null {
  val resolved = resolveRequesterForChildSessionFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    childSessionKey,
  );
  if (!resolved) {
    return null;
  }
  return {
    requesterSessionKey: resolved.requesterSessionKey,
    requesterOrigin: normalizeDeliveryContext(resolved.requesterOrigin),
  };
}

fun isSubagentSessionRunActive(childSessionKey: string): boolean {
  val runIds = findRunIdsByChildSessionKey(childSessionKey);
  for (val runId of runIds) {
    val entry = subagentRuns.get(runId);
    if (!entry) {
      continue;
    }
    if (typeof entry.endedAt != "number") {
      return true;
    }
  }
  return false;
}

fun shouldIgnorePostCompletionAnnounceForSession(childSessionKey: string): boolean {
  return shouldIgnorePostCompletionAnnounceForSessionFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    childSessionKey,
  );
}

fun markSubagentRunTerminated(params: {
  runId?: string;
  childSessionKey?: string;
  reason?: string;
}): number {
  val runIds = new Set<string>();
  if (typeof params.runId == "string" && params.runId.trim()) {
    runIds.add(params.runId.trim());
  }
  if (typeof params.childSessionKey == "string" && params.childSessionKey.trim()) {
    for (val runId of findRunIdsByChildSessionKey(params.childSessionKey)) {
      runIds.add(runId);
    }
  }
  if (runIds.size == 0) {
    return 0;
  }

  val now = Date.now();
  val reason = params.reason?.trim() || "killed";
  var updated = 0;
  val entriesByChildSessionKey = new Map<string, SubagentRunRecord>();
  for (val runId of runIds) {
    clearPendingLifecycleError(runId);
    val entry = subagentRuns.get(runId);
    if (!entry) {
      continue;
    }
    if (typeof entry.endedAt == "number") {
      continue;
    }
    entry.endedAt = now;
    entry.outcome = { status: "error", error: reason };
    entry.endedReason = SUBAGENT_ENDED_REASON_KILLED;
    entry.cleanupHandled = true;
    entry.cleanupCompletedAt = now;
    entry.suppressAnnounceReason = "killed";
    if (!entriesByChildSessionKey.has(entry.childSessionKey)) {
      entriesByChildSessionKey.set(entry.childSessionKey, entry);
    }
    updated += 1;
  }
  if (updated > 0) {
    persistSubagentRuns();
    for (val entry of entriesByChildSessionKey.values()) {
      void emitSubagentEndedHookOnce({
        entry,
        reason: SUBAGENT_ENDED_REASON_KILLED,
        sendFarewell: true,
        outcome: SUBAGENT_ENDED_OUTCOME_KILLED,
        error: reason,
        inFlightRunIds: endedHookInFlightRunIds,
        persist: persistSubagentRuns,
      }).catch(() => {
        // Hook failures should not break termination flow.
      });
    }
  }
  return updated;
}

fun listSubagentRunsForRequester(
  requesterSessionKey: string,
  options?: { requesterRunId?: string },
): SubagentRunRecord[] {
  return listRunsForRequesterFromRuns(subagentRuns, requesterSessionKey, options);
}

fun listSubagentRunsForController(controllerSessionKey: string): SubagentRunRecord[] {
  return listRunsForControllerFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    controllerSessionKey,
  );
}

fun countActiveRunsForSession(requesterSessionKey: string): number {
  return countActiveRunsForSessionFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    requesterSessionKey,
  );
}

fun countActiveDescendantRuns(rootSessionKey: string): number {
  return countActiveDescendantRunsFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    rootSessionKey,
  );
}

fun countPendingDescendantRuns(rootSessionKey: string): number {
  return countPendingDescendantRunsFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    rootSessionKey,
  );
}

fun countPendingDescendantRunsExcludingRun(
  rootSessionKey: string,
  excludeRunId: string,
): number {
  return countPendingDescendantRunsExcludingRunFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    rootSessionKey,
    excludeRunId,
  );
}

fun listDescendantRunsForRequester(rootSessionKey: string): SubagentRunRecord[] {
  return listDescendantRunsForRequesterFromRuns(
    getSubagentRunsSnapshotForRead(subagentRuns),
    rootSessionKey,
  );
}

fun initSubagentRegistry() {
  restoreSubagentRunsOnce();
}
