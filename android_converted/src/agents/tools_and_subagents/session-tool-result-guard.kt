@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-tool-result-guard.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AgentMessage } from "@mariozechner/pi-agent-core";
// TODO(port-deps): import type { SessionManager } from "@mariozechner/pi-coding-agent";
// TODO(port-deps): import type {
// TODO(port-deps): PluginHookBeforeMessageWriteEvent,
// TODO(port-deps): PluginHookBeforeMessageWriteResult,
// TODO(port-deps): } from "../plugins/types.js";
// TODO(port-deps): import { emitSessionTranscriptUpdate } from "../sessions/transcript-events.js";
// TODO(port-deps): import {
// TODO(port-deps): HARD_MAX_TOOL_RESULT_CHARS,
// TODO(port-deps): truncateToolResultMessage,
// TODO(port-deps): } from "./pi-embedded-runner/tool-result-truncation.js";
// TODO(port-deps): import { createPendingToolCallState } from "./session-tool-result-state.js";
// TODO(port-deps): import { makeMissingToolResult, sanitizeToolCallInputs } from "./session-transcript-repair.js";
// TODO(port-deps): import { extractToolCallsFromAssistant, extractToolResultId } from "./tool-call-id.js";

val GUARD_TRUNCATION_SUFFIX =
  "\n\n⚠️ [Content truncated during persistence — original exceeded size limit. " +
  "Use offset/limit parameters or request specific sections for large content.]";

/**
 * Truncate oversized text content blocks in a tool result message.
 * Returns the original message if under the limit, or a new message with
 * truncated text blocks otherwise.
 */
fun capToolResultSize(msg: AgentMessage): AgentMessage {
  if ((msg as { role?: string }).role != "toolResult") {
    return msg;
  }
  return truncateToolResultMessage(msg, HARD_MAX_TOOL_RESULT_CHARS, {
    suffix: GUARD_TRUNCATION_SUFFIX,
    minKeepChars: 2_000,
  });
}

fun trimNonEmptyString(value: unknown): string | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim();
  return trimmed || null;
}

fun normalizePersistedToolResultName(
  message: AgentMessage,
  fallbackName?: string,
): AgentMessage {
  if ((message as { role?: unknown }).role != "toolResult") {
    return message;
  }
  val toolResult = message as Extract<AgentMessage, { role: "toolResult" }>;
  val rawToolName = (toolResult as { toolName?: unknown }).toolName;
  val normalizedToolName = trimNonEmptyString(rawToolName);
  if (normalizedToolName) {
    if (rawToolName == normalizedToolName) {
      return toolResult;
    }
    return { ...toolResult, toolName: normalizedToolName };
  }

  val normalizedFallback = trimNonEmptyString(fallbackName);
  if (normalizedFallback) {
    return { ...toolResult, toolName: normalizedFallback };
  }

  if (typeof rawToolName == "string") {
    return { ...toolResult, toolName: "unknown" };
  }
  return toolResult;
}

fun installSessionToolResultGuard(
  sessionManager: SessionManager,
  opts?: {
    /**
     * Optional transform applied to any message before persistence.
     */
    transformMessageForPersistence?: (message: AgentMessage) => AgentMessage;
    /**
     * Optional, synchronous transform applied to toolResult messages *before* they are
     * persisted to the session transcript.
     */
    transformToolResultForPersistence?: (
      message: AgentMessage,
      meta: { toolCallId?: string; toolName?: string; isSynthetic?: boolean },
    ) => AgentMessage;
    /**
     * Whether to synthesize missing tool results to satisfy strict providers.
     * Defaults to true.
     */
    allowSyntheticToolResults?: boolean;
    /**
     * Optional set/list of tool names accepted for assistant toolCall/toolUse blocks.
     * When set, tool calls with unknown names are dropped before persistence.
     */
    allowedToolNames?: Iterable<string>;
    /**
     * Synchronous hook invoked before any message is written to the session JSONL.
     * If the hook returns { block: true }, the message is silently dropped.
     * If it returns { message }, the modified message is written instead.
     */
    beforeMessageWriteHook?: (
      event: PluginHookBeforeMessageWriteEvent,
    ) => PluginHookBeforeMessageWriteResult | null;
  },
): {
  flushPendingToolResults: () => void;
  clearPendingToolResults: () => void;
  getPendingIds: () => string[];
} {
  val originalAppend = sessionManager.appendMessage.bind(sessionManager);
  val pendingState = createPendingToolCallState();
  val persistMessage = (message: AgentMessage) => {
    val transformer = opts?.transformMessageForPersistence;
    return transformer ? transformer(message) : message;
  };

  val persistToolResult = (
    message: AgentMessage,
    meta: { toolCallId?: string; toolName?: string; isSynthetic?: boolean },
  ) => {
    val transformer = opts?.transformToolResultForPersistence;
    return transformer ? transformer(message, meta) : message;
  };

  val allowSyntheticToolResults = opts?.allowSyntheticToolResults ?: true;
  val beforeWrite = opts?.beforeMessageWriteHook;

  /**
   * Run the before_message_write hook. Returns the (possibly modified) message,
   * or null if the message should be blocked.
   */
  val applyBeforeWriteHook = (msg: AgentMessage): AgentMessage | null => {
    if (!beforeWrite) {
      return msg;
    }
    val result = beforeWrite({ message: msg });
    if (result?.block) {
      return null;
    }
    if (result?.message) {
      return result.message;
    }
    return msg;
  };

  val flushPendingToolResults = () => {
    if (pendingState.size() == 0) {
      return;
    }
    if (allowSyntheticToolResults) {
      for (val [id, name] of pendingState.entries()) {
        val synthetic = makeMissingToolResult({ toolCallId: id, toolName: name });
        val flushed = applyBeforeWriteHook(
          persistToolResult(persistMessage(synthetic), {
            toolCallId: id,
            toolName: name,
            isSynthetic: true,
          }),
        );
        if (flushed) {
          originalAppend(flushed as never);
        }
      }
    }
    pendingState.clear();
  };

  val clearPendingToolResults = () => {
    pendingState.clear();
  };

  val guardedAppend = (message: AgentMessage) => {
    var nextMessage = message;
    val role = (message as { role?: unknown }).role;
    if (role == "assistant") {
      val sanitized = sanitizeToolCallInputs([message], {
        allowedToolNames: opts?.allowedToolNames,
      });
      if (sanitized.length == 0) {
        if (pendingState.shouldFlushForSanitizedDrop()) {
          flushPendingToolResults();
        }
        return null;
      }
      nextMessage = sanitized[0];
    }
    val nextRole = (nextMessage as { role?: unknown }).role;

    if (nextRole == "toolResult") {
      val id = extractToolResultId(nextMessage as Extract<AgentMessage, { role: "toolResult" }>);
      val toolName = id ? pendingState.getToolName(id) : null;
      if (id) {
        pendingState.delete(id);
      }
      val normalizedToolResult = normalizePersistedToolResultName(nextMessage, toolName);
      // Apply hard size cap before persistence to prevent oversized tool results
      // from consuming the entire context window on subsequent LLM calls.
      val capped = capToolResultSize(persistMessage(normalizedToolResult));
      val persisted = applyBeforeWriteHook(
        persistToolResult(capped, {
          toolCallId: id ?: null,
          toolName,
          isSynthetic: false,
        }),
      );
      if (!persisted) {
        return null;
      }
      return originalAppend(persisted as never);
    }

    // Skip tool call extraction for aborted/errored assistant messages.
    // When stopReason is "error" or "aborted", the tool_use blocks may be incomplete
    // and should not have synthetic tool_results created. Creating synthetic results
    // for incomplete tool calls causes API 400 errors:
    // "unexpected tool_use_id found in tool_result blocks"
    // This matches the behavior in repairToolUseResultPairing (session-transcript-repair.ts)
    val stopReason = (nextMessage as { stopReason?: string }).stopReason;
    val toolCalls =
      nextRole == "assistant" && stopReason != "aborted" && stopReason != "error"
        ? extractToolCallsFromAssistant(nextMessage as Extract<AgentMessage, { role: "assistant" }>)
        : [];

    // Always clear pending tool call state before appending non-tool-result messages.
    // flushPendingToolResults() only inserts synthetic results when allowSyntheticToolResults
    // is true; it always clears the pending map. Without this, providers that disable
    // synthetic results (e.g. OpenAI) accumulate stale pending state when a user message
    // interrupts in-flight tool calls, leaving orphaned tool_use blocks in the transcript
    // that cause API 400 errors on subsequent requests.
    if (pendingState.shouldFlushBeforeNonToolResult(nextRole, toolCalls.length)) {
      flushPendingToolResults();
    }
    // If new tool calls arrive while older ones are pending, flush the old ones first.
    if (pendingState.shouldFlushBeforeNewToolCalls(toolCalls.length)) {
      flushPendingToolResults();
    }

    val finalMessage = applyBeforeWriteHook(persistMessage(nextMessage));
    if (!finalMessage) {
      return null;
    }
    val result = originalAppend(finalMessage as never);

    val sessionFile = (
      sessionManager as { getSessionFile?: () => string | null }
    ).getSessionFile?.();
    if (sessionFile) {
      emitSessionTranscriptUpdate(sessionFile);
    }

    if (toolCalls.length > 0) {
      pendingState.trackToolCalls(toolCalls);
    }

    return result;
  };

  // Monkey-patch appendMessage with our guarded version.
  sessionManager.appendMessage = guardedAppend as SessionManager["appendMessage"];

  return {
    flushPendingToolResults,
    clearPendingToolResults,
    getPendingIds: pendingState.getPendingIds,
  };
}
