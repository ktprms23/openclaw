@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-lifecycle-events.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

val SUBAGENT_TARGET_KIND_SUBAGENT = "subagent" as const;
val SUBAGENT_TARGET_KIND_ACP = "acp" as const;

type SubagentLifecycleTargetKind =
  | typeof SUBAGENT_TARGET_KIND_SUBAGENT
  | typeof SUBAGENT_TARGET_KIND_ACP;

val SUBAGENT_ENDED_REASON_COMPLETE = "subagent-complete" as const;
val SUBAGENT_ENDED_REASON_ERROR = "subagent-error" as const;
val SUBAGENT_ENDED_REASON_KILLED = "subagent-killed" as const;
val SUBAGENT_ENDED_REASON_SESSION_RESET = "session-reset" as const;
val SUBAGENT_ENDED_REASON_SESSION_DELETE = "session-delete" as const;

type SubagentLifecycleEndedReason =
  | typeof SUBAGENT_ENDED_REASON_COMPLETE
  | typeof SUBAGENT_ENDED_REASON_ERROR
  | typeof SUBAGENT_ENDED_REASON_KILLED
  | typeof SUBAGENT_ENDED_REASON_SESSION_RESET
  | typeof SUBAGENT_ENDED_REASON_SESSION_DELETE;

type SubagentSessionLifecycleEndedReason =
  | typeof SUBAGENT_ENDED_REASON_SESSION_RESET
  | typeof SUBAGENT_ENDED_REASON_SESSION_DELETE;

val SUBAGENT_ENDED_OUTCOME_OK = "ok" as const;
val SUBAGENT_ENDED_OUTCOME_ERROR = "error" as const;
val SUBAGENT_ENDED_OUTCOME_TIMEOUT = "timeout" as const;
val SUBAGENT_ENDED_OUTCOME_KILLED = "killed" as const;
val SUBAGENT_ENDED_OUTCOME_RESET = "reset" as const;
val SUBAGENT_ENDED_OUTCOME_DELETED = "deleted" as const;

type SubagentLifecycleEndedOutcome =
  | typeof SUBAGENT_ENDED_OUTCOME_OK
  | typeof SUBAGENT_ENDED_OUTCOME_ERROR
  | typeof SUBAGENT_ENDED_OUTCOME_TIMEOUT
  | typeof SUBAGENT_ENDED_OUTCOME_KILLED
  | typeof SUBAGENT_ENDED_OUTCOME_RESET
  | typeof SUBAGENT_ENDED_OUTCOME_DELETED;

fun resolveSubagentSessionEndedOutcome(
  reason: SubagentSessionLifecycleEndedReason,
): SubagentLifecycleEndedOutcome {
  if (reason == SUBAGENT_ENDED_REASON_SESSION_RESET) {
    return SUBAGENT_ENDED_OUTCOME_RESET;
  }
  return SUBAGENT_ENDED_OUTCOME_DELETED;
}
