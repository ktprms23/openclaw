package agents.tools_and_subagents

// Converted from src/agents/subagent-registry-completion.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { getGlobalHookRunner } from "../plugins/hook-runner-global.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunOutcome } from "./subagent-announce.js";
// TODO: TypeScript import retained for manual wiring: import {
  SUBAGENT_ENDED_OUTCOME_ERROR,
  SUBAGENT_ENDED_OUTCOME_OK,
  SUBAGENT_ENDED_OUTCOME_TIMEOUT,
  SUBAGENT_TARGET_KIND_SUBAGENT,
  type SubagentLifecycleEndedOutcome,
  type SubagentLifecycleEndedReason,
} from "./subagent-lifecycle-events.js"
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

fun runOutcomesEqual(
  a: SubagentRunOutcome?,
  b: SubagentRunOutcome?,
): Boolean {
  if (!a && !b) {
    return true
  }
  if (!a || !b) {
    return false
  }
  if (a.status != b.status) {
    return false
  }
  if (a.status == "error" && b.status == "error") {
    return (a.error ?: "") == (b.error ?: "")
  }
  return true
}

fun resolveLifecycleOutcomeFromRunOutcome(
  outcome: SubagentRunOutcome?,
): SubagentLifecycleEndedOutcome {
  if (outcome?.status == "error") {
    return SUBAGENT_ENDED_OUTCOME_ERROR
  }
  if (outcome?.status == "timeout") {
    return SUBAGENT_ENDED_OUTCOME_TIMEOUT
  }
  return SUBAGENT_ENDED_OUTCOME_OK
}

suspend fun emitSubagentEndedHookOnce(params: {
  entry: SubagentRunRecord
  reason: SubagentLifecycleEndedReason
  sendFarewell?: Boolean
  accountId?: String
  outcome?: SubagentLifecycleEndedOutcome
  error?: String
  inFlightRunIds: MutableSet<String>
  persist: () -> Unit
}) {
  val runId = params.entry.runId.trim()
  if (!runId) {
    return false
  }
  if (params.entry.endedHookEmittedAt) {
    return false
  }
  if (params.inFlightRunIds.has(runId)) {
    return false
  }

  params.inFlightRunIds.add(runId)
  try {
    val hookRunner = getGlobalHookRunner()
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
      )
    }
    params.entry.endedHookEmittedAt = Date.now()
    params.persist()
    return true
  } catch (_: Throwable) {
    return false
  } finally {
    params.inFlightRunIds.delete(runId)
  }
}
