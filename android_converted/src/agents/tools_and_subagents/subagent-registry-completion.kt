@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry-completion.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { getGlobalHookRunner } from "../plugins/hook-runner-global.js";
// TODO(port-deps): import type { SubagentRunOutcome } from "./subagent-announce.js";
// TODO(port-deps): import {
// TODO(port-deps): SUBAGENT_ENDED_OUTCOME_ERROR,
// TODO(port-deps): SUBAGENT_ENDED_OUTCOME_OK,
// TODO(port-deps): SUBAGENT_ENDED_OUTCOME_TIMEOUT,
// TODO(port-deps): SUBAGENT_TARGET_KIND_SUBAGENT,
// TODO(port-deps): type SubagentLifecycleEndedOutcome,
// TODO(port-deps): type SubagentLifecycleEndedReason,
// TODO(port-deps): } from "./subagent-lifecycle-events.js";
// TODO(port-deps): import type { SubagentRunRecord } from "./subagent-registry.types.js";

fun runOutcomesEqual(
  a: SubagentRunOutcome | null,
  b: SubagentRunOutcome | null,
): boolean {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  if (a.status != b.status) {
    return false;
  }
  if (a.status == "error" && b.status == "error") {
    return (a.error ?: "") == (b.error ?: "");
  }
  return true;
}

fun resolveLifecycleOutcomeFromRunOutcome(
  outcome: SubagentRunOutcome | null,
): SubagentLifecycleEndedOutcome {
  if (outcome?.status == "error") {
    return SUBAGENT_ENDED_OUTCOME_ERROR;
  }
  if (outcome?.status == "timeout") {
    return SUBAGENT_ENDED_OUTCOME_TIMEOUT;
  }
  return SUBAGENT_ENDED_OUTCOME_OK;
}

suspend fun emitSubagentEndedHookOnce(params: {
  entry: SubagentRunRecord;
  reason: SubagentLifecycleEndedReason;
  sendFarewell?: boolean;
  accountId?: string;
  outcome?: SubagentLifecycleEndedOutcome;
  error?: string;
  inFlightRunIds: Set<string>;
  persist: () => void;
}) {
  val runId = params.entry.runId.trim();
  if (!runId) {
    return false;
  }
  if (params.entry.endedHookEmittedAt) {
    return false;
  }
  if (params.inFlightRunIds.has(runId)) {
    return false;
  }

  params.inFlightRunIds.add(runId);
  try {
    val hookRunner = getGlobalHookRunner();
    if (hookRunner?.hasHooks("subagent_ended")) {
      await hookRunner.runSubagentEnded(
        {
          targetSessionKey: params.entry.childSessionKey,
          targetKind: SUBAGENT_TARGET_KIND_SUBAGENT,
          reason: params.reason,
          sendFarewell: params.sendFarewell,
          accountId: params.accountId,
          runId: params.entry.runId,
          endedAt: params.entry.endedAt,
          outcome: params.outcome,
          error: params.error,
        },
        {
          runId: params.entry.runId,
          childSessionKey: params.entry.childSessionKey,
          requesterSessionKey: params.entry.requesterSessionKey,
        },
      );
    }
    params.entry.endedHookEmittedAt = Date.now();
    params.persist();
    return true;
  } catch {
    return false;
  } finally {
    params.inFlightRunIds.delete(runId);
  }
}
