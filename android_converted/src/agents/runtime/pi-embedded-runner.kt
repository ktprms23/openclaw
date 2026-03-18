@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

object PiEmbeddedRunnerFacade {
    val exportedMembers: List<String> = listOf(
        "MessagingToolSend <- ./pi-embedded-messaging.js",
        "compactEmbeddedPiSession <- ./pi-embedded-runner/compact.js",
        "applyExtraParamsToAgent, resolveExtraParams <- ./pi-embedded-runner/extra-params.js",
        "applyGoogleTurnOrderingFix <- ./pi-embedded-runner/google.js",
        "getDmHistoryLimitFromSessionKey, getHistoryLimitFromSessionKey, limitHistoryTurns,  <- ./pi-embedded-runner/history.js",
        "resolveEmbeddedSessionLane <- ./pi-embedded-runner/lanes.js",
        "runEmbeddedPiAgent <- ./pi-embedded-runner/run.js",
        "abortEmbeddedPiRun, isEmbeddedPiRunActive, isEmbeddedPiRunStreaming, queueEmbeddedPiMessage, waitForEmbeddedPiRunEnd,  <- ./pi-embedded-runner/runs.js",
        "buildEmbeddedSandboxInfo <- ./pi-embedded-runner/sandbox-info.js",
        "createSystemPromptOverride <- ./pi-embedded-runner/system-prompt.js",
        "splitSdkTools <- ./pi-embedded-runner/tool-split.js",
        "EmbeddedPiAgentMeta, EmbeddedPiCompactResult, EmbeddedPiRunMeta, EmbeddedPiRunResult,  <- ./pi-embedded-runner/types.js",
    )
}

/*
Original TypeScript reference:
export type { MessagingToolSend } from "./pi-embedded-messaging.js";
export { compactEmbeddedPiSession } from "./pi-embedded-runner/compact.js";
export { applyExtraParamsToAgent, resolveExtraParams } from "./pi-embedded-runner/extra-params.js";

export { applyGoogleTurnOrderingFix } from "./pi-embedded-runner/google.js";
export {
  getDmHistoryLimitFromSessionKey,
  getHistoryLimitFromSessionKey,
  limitHistoryTurns,
} from "./pi-embedded-runner/history.js";
export { resolveEmbeddedSessionLane } from "./pi-embedded-runner/lanes.js";
export { runEmbeddedPiAgent } from "./pi-embedded-runner/run.js";
export {
  abortEmbeddedPiRun,
  isEmbeddedPiRunActive,
  isEmbeddedPiRunStreaming,
  queueEmbeddedPiMessage,
  waitForEmbeddedPiRunEnd,
} from "./pi-embedded-runner/runs.js";
export { buildEmbeddedSandboxInfo } from "./pi-embedded-runner/sandbox-info.js";
export { createSystemPromptOverride } from "./pi-embedded-runner/system-prompt.js";
export { splitSdkTools } from "./pi-embedded-runner/tool-split.js";
export type {
  EmbeddedPiAgentMeta,
  EmbeddedPiCompactResult,
  EmbeddedPiRunMeta,
  EmbeddedPiRunResult,
} from "./pi-embedded-runner/types.js";

*/
