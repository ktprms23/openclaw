package agents.tools_and_subagents

// Converted from src/agents/subagent-lifecycle-events.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
val SUBAGENT_TARGET_KIND_SUBAGENT = "subagent" as /* TODO */ val
val SUBAGENT_TARGET_KIND_ACP = "acp" as /* TODO */ val

type SubagentLifecycleTargetKind =
  | typeof SUBAGENT_TARGET_KIND_SUBAGENT
  | typeof SUBAGENT_TARGET_KIND_ACP

val SUBAGENT_ENDED_REASON_COMPLETE = "subagent-complete" as /* TODO */ val
val SUBAGENT_ENDED_REASON_ERROR = "subagent-error" as /* TODO */ val
val SUBAGENT_ENDED_REASON_KILLED = "subagent-killed" as /* TODO */ val
val SUBAGENT_ENDED_REASON_SESSION_RESET = "session-reset" as /* TODO */ val
val SUBAGENT_ENDED_REASON_SESSION_DELETE = "session-delete" as /* TODO */ val

type SubagentLifecycleEndedReason =
  | typeof SUBAGENT_ENDED_REASON_COMPLETE
  | typeof SUBAGENT_ENDED_REASON_ERROR
  | typeof SUBAGENT_ENDED_REASON_KILLED
  | typeof SUBAGENT_ENDED_REASON_SESSION_RESET
  | typeof SUBAGENT_ENDED_REASON_SESSION_DELETE

type SubagentSessionLifecycleEndedReason =
  | typeof SUBAGENT_ENDED_REASON_SESSION_RESET
  | typeof SUBAGENT_ENDED_REASON_SESSION_DELETE

val SUBAGENT_ENDED_OUTCOME_OK = "ok" as /* TODO */ val
val SUBAGENT_ENDED_OUTCOME_ERROR = "error" as /* TODO */ val
val SUBAGENT_ENDED_OUTCOME_TIMEOUT = "timeout" as /* TODO */ val
val SUBAGENT_ENDED_OUTCOME_KILLED = "killed" as /* TODO */ val
val SUBAGENT_ENDED_OUTCOME_RESET = "reset" as /* TODO */ val
val SUBAGENT_ENDED_OUTCOME_DELETED = "deleted" as /* TODO */ val

type SubagentLifecycleEndedOutcome =
  | typeof SUBAGENT_ENDED_OUTCOME_OK
  | typeof SUBAGENT_ENDED_OUTCOME_ERROR
  | typeof SUBAGENT_ENDED_OUTCOME_TIMEOUT
  | typeof SUBAGENT_ENDED_OUTCOME_KILLED
  | typeof SUBAGENT_ENDED_OUTCOME_RESET
  | typeof SUBAGENT_ENDED_OUTCOME_DELETED

fun resolveSubagentSessionEndedOutcome(
  reason: SubagentSessionLifecycleEndedReason,
): SubagentLifecycleEndedOutcome {
  if (reason == SUBAGENT_ENDED_REASON_SESSION_RESET) {
    return SUBAGENT_ENDED_OUTCOME_RESET
  }
  return SUBAGENT_ENDED_OUTCOME_DELETED
}
