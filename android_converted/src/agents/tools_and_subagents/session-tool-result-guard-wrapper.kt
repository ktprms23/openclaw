@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-tool-result-guard-wrapper.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { SessionManager } from "@mariozechner/pi-coding-agent";
// TODO(port-deps): import { getGlobalHookRunner } from "../plugins/hook-runner-global.js";
// TODO(port-deps): import {
// TODO(port-deps): applyInputProvenanceToUserMessage,
// TODO(port-deps): type InputProvenance,
// TODO(port-deps): } from "../sessions/input-provenance.js";
// TODO(port-deps): import { installSessionToolResultGuard } from "./session-tool-result-guard.js";

typealias GuardedSessionManager = Any /* TODO: translate TypeScript alias */

/**
 * Apply the tool-result guard to a SessionManager exactly once and expose
 * a flush method on the instance for easy teardown handling.
 */
fun guardSessionManager(
  sessionManager: SessionManager,
  opts?: {
    agentId?: string;
    sessionKey?: string;
    inputProvenance?: InputProvenance;
    allowSyntheticToolResults?: boolean;
    allowedToolNames?: Iterable<string>;
  },
): GuardedSessionManager {
  if (typeof (sessionManager as GuardedSessionManager).flushPendingToolResults == "function") {
    return sessionManager as GuardedSessionManager;
  }

  val hookRunner = getGlobalHookRunner();
  val beforeMessageWrite = hookRunner?.hasHooks("before_message_write")
    ? (event: { message: import("@mariozechner/pi-agent-core").AgentMessage }) => {
        return hookRunner.runBeforeMessageWrite(event, {
          agentId: opts?.agentId,
          sessionKey: opts?.sessionKey,
        });
      }
    : null;

  val transform = hookRunner?.hasHooks("tool_result_persist")
    ? // oxlint-disable-next-line typescript/no-explicit-any
      (message: any, meta: { toolCallId?: string; toolName?: string; isSynthetic?: boolean }) => {
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
        );
        return out?.message ?: message;
      }
    : null;

  val guard = installSessionToolResultGuard(sessionManager, {
    transformMessageForPersistence: (message) =>
      applyInputProvenanceToUserMessage(message, opts?.inputProvenance),
    transformToolResultForPersistence: transform,
    allowSyntheticToolResults: opts?.allowSyntheticToolResults,
    allowedToolNames: opts?.allowedToolNames,
    beforeMessageWriteHook: beforeMessageWrite,
  });
  (sessionManager as GuardedSessionManager).flushPendingToolResults = guard.flushPendingToolResults;
  (sessionManager as GuardedSessionManager).clearPendingToolResults = guard.clearPendingToolResults;
  return sessionManager as GuardedSessionManager;
}
