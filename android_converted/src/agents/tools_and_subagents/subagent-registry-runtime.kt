@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry-runtime.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

object SubagentRegistryRuntimeFacade {
    val exports = listOf(
        "countActiveDescendantRuns, countPendingDescendantRuns, countPendingDescendantRunsExcludingRun, isSubagentSessionRunActive, listSubagentRunsForRequester, replaceSubagentRunAfterSteer, resolveRequesterForChildSession, shouldIgnorePostCompletionAnnounceForSession, <- ./subagent-registry.js",
    )
}
