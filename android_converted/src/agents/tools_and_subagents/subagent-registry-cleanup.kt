package agents.tools_and_subagents

// Converted from src/agents/subagent-registry-cleanup.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  SUBAGENT_ENDED_REASON_COMPLETE,
  type SubagentLifecycleEndedReason,
} from "./subagent-lifecycle-events.js"
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

type DeferredCleanupDecision =
  | {
      kind: String /* "defer-descendants" */
      delayMs: Double
    }
  | {
      kind: String /* "give-up" */
      reason: String /* "retry-limit" */ | "expiry"
      retryCount?: Double
    }
  | {
      kind: String /* "retry" */
      retryCount: Double
      resumeDelayMs?: Double
    }

fun resolveCleanupCompletionReason(
  entry: SubagentRunRecord,
): SubagentLifecycleEndedReason {
  return entry.endedReason ?: SUBAGENT_ENDED_REASON_COMPLETE
}

fun resolveEndedAgoMs(entry: SubagentRunRecord, now: Double): Double {
  return entry.endedAt is Double ? now - entry.endedAt : 0
}

fun resolveDeferredCleanupDecision(params: {
  entry: SubagentRunRecord
  now: Double
  activeDescendantRuns: Double
  announceExpiryMs: Double
  announceCompletionHardExpiryMs: Double
  maxAnnounceRetryCount: Double
  deferDescendantDelayMs: Double
  resolveAnnounceRetryDelayMs: (retryCount: Double) -> Double
}): DeferredCleanupDecision {
  val endedAgo = resolveEndedAgoMs(params.entry, params.now)
  val isCompletionMessageFlow = params.entry.expectsCompletionMessage == true
  val completionHardExpiryExceeded =
    isCompletionMessageFlow && endedAgo > params.announceCompletionHardExpiryMs
  if (isCompletionMessageFlow && params.activeDescendantRuns > 0) {
    if (completionHardExpiryExceeded) {
      return { kind: String /* "give-up" */, reason: String /* "expiry" */ }
    }
    return { kind: String /* "defer-descendants" */, delayMs: params.deferDescendantDelayMs }
  }

  val retryCount = (params.entry.announceRetryCount ?: 0) + 1
  val expiryExceeded = isCompletionMessageFlow
    ? completionHardExpiryExceeded
    : endedAgo > params.announceExpiryMs
  if (retryCount >= params.maxAnnounceRetryCount || expiryExceeded) {
    return {
      kind: String /* "give-up" */,
      reason: retryCount >= params.maxAnnounceRetryCount ? "retry-limit" : String /* "expiry" */,
      retryCount,
    }
  }

  return {
    kind: String /* "retry" */,
    retryCount,
    resumeDelayMs:
      params.entry.expectsCompletionMessage == true
        ? params.resolveAnnounceRetryDelayMs(retryCount)
        : null,
  }
}
