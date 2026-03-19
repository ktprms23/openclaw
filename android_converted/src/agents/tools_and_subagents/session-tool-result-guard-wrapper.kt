package agents.tools_and_subagents

// Converted from src/agents/session-tool-result-guard-wrapper.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { SessionManager } from "@mariozechner/pi-coding-agent";
// TODO: TypeScript import retained for manual wiring: import { getGlobalHookRunner } from "../plugins/hook-runner-global.js";
// TODO: TypeScript import retained for manual wiring: import {
  applyInputProvenanceToUserMessage,
  type InputProvenance,
} from "../sessions/input-provenance.js"
// TODO: TypeScript import retained for manual wiring: import { installSessionToolResultGuard } from "./session-tool-result-guard.js";

type GuardedSessionManager = SessionManager & {
  /** Flush Any? synthetic tool results for pending tool calls. Idempotent. */
  flushPendingToolResults?: () -> Unit
  /** Clear pending tool calls without persisting synthetic tool results. Idempotent. */
  clearPendingToolResults?: () -> Unit
}

/**
 * Apply the tool-result guard to a SessionManager exactly once and expose
 * a flush method on the instance for easy teardown handling.
 */
fun guardSessionManager(
  sessionManager: SessionManager,
  opts?: {
    agentId?: String
    sessionKey?: String
    inputProvenance?: InputProvenance
    allowSyntheticToolResults?: Boolean
    allowedToolNames?: Iterable<String>
  },
): GuardedSessionManager {
  if (typeof (sessionManager as /* TODO */ GuardedSessionManager).flushPendingToolResults == "fun") {
    return sessionManager as /* TODO */ GuardedSessionManager
  }

  val hookRunner = getGlobalHookRunner()
  val beforeMessageWrite = hookRunner?.hasHooks("before_message_write")
    ? (event: { message: import("@mariozechner/pi-agent-core").AgentMessage }) {
        return hookRunner.runBeforeMessageWrite(event, {
          agentId: opts?.agentId,
          sessionKey: opts?.sessionKey,
        })
      }
    : null

  val transform = hookRunner?.hasHooks("tool_result_persist")
    ? // oxlint-disable-next-line typescript/no-explicit-Any?
      (message: Any?, meta: { toolCallId?: String toolName?: String isSynthetic?: Boolean }) {
        val out = hookRunner.runToolResultPersist(
          {
            toolName: meta.toolName,
            toolCallId: meta.toolCallId,
            message,
            isSynthetic: meta.isSynthetic,
          },
          {
            agentId: opts?.agentId,
            sessionKey: opts?.sessionKey,
            toolName: meta.toolName,
            toolCallId: meta.toolCallId,
          },
        )
        return out?.message ?: message
      }
    : null

  val guard = installSessionToolResultGuard(sessionManager, {
    transformMessageForPersistence: (message) ->
      applyInputProvenanceToUserMessage(message, opts?.inputProvenance),
    transformToolResultForPersistence: transform,
    allowSyntheticToolResults: opts?.allowSyntheticToolResults,
    allowedToolNames: opts?.allowedToolNames,
    beforeMessageWriteHook: beforeMessageWrite,
  })
  (sessionManager as /* TODO */ GuardedSessionManager).flushPendingToolResults = guard.flushPendingToolResults
  (sessionManager as /* TODO */ GuardedSessionManager).clearPendingToolResults = guard.clearPendingToolResults
  return sessionManager as /* TODO */ GuardedSessionManager
}
