@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry-cleanup.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import {
// TODO(port-deps): SUBAGENT_ENDED_REASON_COMPLETE,
// TODO(port-deps): type SubagentLifecycleEndedReason,
// TODO(port-deps): } from "./subagent-lifecycle-events.js";
// TODO(port-deps): import type { SubagentRunRecord } from "./subagent-registry.types.js";

type DeferredCleanupDecision =
  | {
      kind: "defer-descendants";
      delayMs: number;
    }
  | {
      kind: "give-up";
      reason: "retry-limit" | "expiry";
      retryCount?: number;
    }
  | {
      kind: "retry";
      retryCount: number;
      resumeDelayMs?: number;
    };

fun resolveCleanupCompletionReason(
  entry: SubagentRunRecord,
): SubagentLifecycleEndedReason {
  return entry.endedReason ?: SUBAGENT_ENDED_REASON_COMPLETE;
}

fun resolveEndedAgoMs(entry: SubagentRunRecord, now: number): number {
  return typeof entry.endedAt == "number" ? now - entry.endedAt : 0;
}

fun resolveDeferredCleanupDecision(params: {
  entry: SubagentRunRecord;
  now: number;
  activeDescendantRuns: number;
  announceExpiryMs: number;
  announceCompletionHardExpiryMs: number;
  maxAnnounceRetryCount: number;
  deferDescendantDelayMs: number;
  resolveAnnounceRetryDelayMs: (retryCount: number) => number;
}): DeferredCleanupDecision {
  val endedAgo = resolveEndedAgoMs(params.entry, params.now);
  val isCompletionMessageFlow = params.entry.expectsCompletionMessage == true;
  val completionHardExpiryExceeded =
    isCompletionMessageFlow && endedAgo > params.announceCompletionHardExpiryMs;
  if (isCompletionMessageFlow && params.activeDescendantRuns > 0) {
    if (completionHardExpiryExceeded) {
      return { kind: "give-up", reason: "expiry" };
    }
    return { kind: "defer-descendants", delayMs: params.deferDescendantDelayMs };
  }

  val retryCount = (params.entry.announceRetryCount ?: 0) + 1;
  val expiryExceeded = isCompletionMessageFlow
    ? completionHardExpiryExceeded
    : endedAgo > params.announceExpiryMs;
  if (retryCount >= params.maxAnnounceRetryCount || expiryExceeded) {
    return {
      kind: "give-up",
      reason: retryCount >= params.maxAnnounceRetryCount ? "retry-limit" : "expiry",
      retryCount,
    };
  }

  return {
    kind: "retry",
    retryCount,
    resumeDelayMs:
      params.entry.expectsCompletionMessage == true
        ? params.resolveAnnounceRetryDelayMs(retryCount)
        : null,
  };
}
