package agents.tools_and_subagents

// Converted from src/agents/subagent-registry.types.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { DeliveryContext } from "../utils/delivery-context.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunOutcome } from "./subagent-announce.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentLifecycleEndedReason } from "./subagent-lifecycle-events.js";
// TODO: TypeScript import retained for manual wiring: import type { SpawnSubagentMode } from "./subagent-spawn.js";

data class SubagentRunRecord(
    val runId: String,
    val childSessionKey: String,
    val controllerSessionKey: String?,
    val requesterSessionKey: String,
    val requesterOrigin: DeliveryContext?,
    val requesterDisplayKey: String,
    val task: String,
    val cleanup: String /* "delete" */ | "keep",
    val label: String?,
    val model: String?,
    val workspaceDir: String?,
    val runTimeoutSeconds: Double?,
    val spawnMode: SpawnSubagentMode?,
    val createdAt: Double,
    val startedAt: Double?,
    val endedAt: Double?,
    val outcome: SubagentRunOutcome?,
    val archiveAtMs: Double?,
    val cleanupCompletedAt: Double?,
    val cleanupHandled: Boolean?,
    val suppressAnnounceReason: String /* "steer-restart" */ | "killed"?,
    val expectsCompletionMessage: Boolean?,
    val announceRetryCount: Double?,
    val lastAnnounceRetryAt: Double?,
    val endedReason: SubagentLifecycleEndedReason?,
    val wakeOnDescendantSettle: Boolean?,
    val frozenResultText: String?,
    val frozenResultCapturedAt: Double?,
    val fallbackFrozenResultText: String?,
    val fallbackFrozenResultCapturedAt: Double?,
    val endedHookEmittedAt: Double?,
    val attachmentsDir: String?,
    val attachmentsRootDir: String?,
    val retainAttachmentsOnKeep: Boolean?,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   /** Number of announce delivery attempts that returned false (deferred). */
    //   /** Timestamp of the last announce retry attempt (for backoff). */
    //   /** Terminal lifecycle reason recorded when the run finishes. */
    //   /** Run ended while descendants were still pending and should be re-invoked once they settle. */
    //   /**
    //    * Latest frozen completion output captured for announce delivery.
    //    * Seeded at first end transition and refreshed by later assistant turns
    //    * while completion delivery is still pending for this session.
    //    */
    //   /** Timestamp when frozenResultText was last captured. */
    //   /**
    //    * Fallback completion output preserved across wake continuation restarts.
    //    * Used when a late wake run replies with NO_REPLY after the real final
    //    * summary was already produced by the prior run.
    //    */
    //   /** Timestamp when fallbackFrozenResultText was preserved. */
    //   /** Set after the subagent_ended hook has been emitted successfully once. */
}
