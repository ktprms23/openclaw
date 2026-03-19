package agents.tools_and_subagents

// Converted from src/agents/session-tool-result-guard.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentMessage } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import type { SessionManager } from "@mariozechner/pi-coding-agent";
// TODO: TypeScript import retained for manual wiring: import type {
  PluginHookBeforeMessageWriteEvent,
  PluginHookBeforeMessageWriteResult,
} from "../plugins/types.js"
// TODO: TypeScript import retained for manual wiring: import { emitSessionTranscriptUpdate } from "../sessions/transcript-events.js";
// TODO: TypeScript import retained for manual wiring: import {
  HARD_MAX_TOOL_RESULT_CHARS,
  truncateToolResultMessage,
} from "./pi-embedded-runner/tool-result-truncation.js"
// TODO: TypeScript import retained for manual wiring: import { createPendingToolCallState } from "./session-tool-result-state.js";
// TODO: TypeScript import retained for manual wiring: import { makeMissingToolResult, sanitizeToolCallInputs } from "./session-transcript-repair.js";
// TODO: TypeScript import retained for manual wiring: import { extractToolCallsFromAssistant, extractToolResultId } from "./tool-call-id.js";

val GUARD_TRUNCATION_SUFFIX =
  "\n\n⚠️ [Content truncated during persistence — original exceeded size limit. " +
  "Use offset/limit parameters or request specific sections for large content.]"

/**
 * Truncate oversized text content blocks in a tool result message.
 * Returns the original message if under the limit, or a new message with
 * truncated text blocks otherwise.
 */
fun capToolResultSize(msg: AgentMessage): AgentMessage {
  if ((msg as /* TODO */ { role?: String }).role != "toolResult") {
    return msg
  }
  return truncateToolResultMessage(msg, HARD_MAX_TOOL_RESULT_CHARS, {
    suffix: GUARD_TRUNCATION_SUFFIX,
    minKeepChars: 2_000,
  })
}

fun trimNonEmptyString(value: Any?): String? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim()
  return trimmed || null
}

fun normalizePersistedToolResultName(
  message: AgentMessage,
  fallbackName?: String,
): AgentMessage {
  if ((message as /* TODO */ { role?: Any? }).role != "toolResult") {
    return message
  }
  val toolResult = message as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>
  val rawToolName = (toolResult as /* TODO */ { toolName?: Any? }).toolName
  val normalizedToolName = trimNonEmptyString(rawToolName)
  if (normalizedToolName) {
    if (rawToolName == normalizedToolName) {
      return toolResult
    }
    return { ...toolResult, toolName: normalizedToolName }
  }

  val normalizedFallback = trimNonEmptyString(fallbackName)
  if (normalizedFallback) {
    return { ...toolResult, toolName: normalizedFallback }
  }

  if (rawToolName is String) {
    return { ...toolResult, toolName: String /* "Any?" */ }
  }
  return toolResult
}

fun installSessionToolResultGuard(
  sessionManager: SessionManager,
  opts?: {
    /**
     * Optional transform applied to Any? message before persistence.
     */
    transformMessageForPersistence?: (message: AgentMessage) -> AgentMessage
    /**
     * Optional, synchronous transform applied to toolResult messages *before* they are
     * persisted to the session transcript.
     */
    transformToolResultForPersistence?: (
      message: AgentMessage,
      meta: { toolCallId?: String toolName?: String isSynthetic?: Boolean },
    ) -> AgentMessage
    /**
     * Whether to synthesize missing tool results to satisfy strict providers.
     * Defaults to true.
     */
    allowSyntheticToolResults?: Boolean
    /**
     * Optional set/list of tool names accepted for assistant toolCall/toolUse blocks.
     * When set, tool calls with Any? names are dropped before persistence.
     */
    allowedToolNames?: Iterable<String>
    /**
     * Synchronous hook invoked before Any? message is written to the session JSONL.
     * If the hook returns { block: true }, the message is silently dropped.
     * If it returns { message }, the modified message is written instead.
     */
    beforeMessageWriteHook?: (
      event: PluginHookBeforeMessageWriteEvent,
    ) -> PluginHookBeforeMessageWriteResult?
  },
): {
  flushPendingToolResults: () -> Unit
  clearPendingToolResults: () -> Unit
  getPendingIds: () -> List<String>
} {
  val originalAppend = sessionManager.appendMessage.bind(sessionManager)
  val pendingState = createPendingToolCallState()
  val persistMessage = { message: AgentMessage ->
    val transformer = opts?.transformMessageForPersistence
    return transformer ? transformer(message) : message
  }

  val persistToolResult = (
    message: AgentMessage,
    meta: { toolCallId?: String toolName?: String isSynthetic?: Boolean },
  ) {
    val transformer = opts?.transformToolResultForPersistence
    return transformer ? transformer(message, meta) : message
  }

  val allowSyntheticToolResults = opts?.allowSyntheticToolResults ?: true
  val beforeWrite = opts?.beforeMessageWriteHook

  /**
   * Run the before_message_write hook. Returns the (possibly modified) message,
   * or null if the message should be blocked.
   */
  val applyBeforeWriteHook = (msg: AgentMessage): AgentMessage? -> {
    if (!beforeWrite) {
      return msg
    }
    val result = beforeWrite({ message: msg })
    if (result?.block) {
      return null
    }
    if (result?.message) {
      return result.message
    }
    return msg
  }

  val flushPendingToolResults = {  ->
    if (pendingState.size() == 0) {
      return
    }
    if (allowSyntheticToolResults) {
      for (val [id, name] of pendingState.entries()) {
        val synthetic = makeMissingToolResult({ toolCallId: id, toolName: name })
        val flushed = applyBeforeWriteHook(
          persistToolResult(persistMessage(synthetic), {
            toolCallId: id,
            toolName: name,
            isSynthetic: true,
          }),
        )
        if (flushed) {
          originalAppend(flushed as /* TODO */ Nothing)
        }
      }
    }
    pendingState.clear()
  }

  val clearPendingToolResults = {  ->
    pendingState.clear()
  }

  val guardedAppend = { message: AgentMessage ->
    var nextMessage = message
    val role = (message as /* TODO */ { role?: Any? }).role
    if (role == "assistant") {
      val sanitized = sanitizeToolCallInputs([message], {
        allowedToolNames: opts?.allowedToolNames,
      })
      if (sanitized.length == 0) {
        if (pendingState.shouldFlushForSanitizedDrop()) {
          flushPendingToolResults()
        }
        return null
      }
      nextMessage = sanitized[0]
    }
    val nextRole = (nextMessage as /* TODO */ { role?: Any? }).role

    if (nextRole == "toolResult") {
      val id = extractToolResultId(nextMessage as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>)
      val toolName = id ? pendingState.getToolName(id) : null
      if (id) {
        pendingState.delete(id)
      }
      val normalizedToolResult = normalizePersistedToolResultName(nextMessage, toolName)
      // Apply hard size cap before persistence to prevent oversized tool results
      // from consuming the entire context window on subsequent LLM calls.
      val capped = capToolResultSize(persistMessage(normalizedToolResult))
      val persisted = applyBeforeWriteHook(
        persistToolResult(capped, {
          toolCallId: id ?: null,
          toolName,
          isSynthetic: false,
        }),
      )
      if (!persisted) {
        return null
      }
      return originalAppend(persisted as /* TODO */ Nothing)
    }

    // Skip tool call extraction for aborted/errored assistant messages.
    // When stopReason is "error" or "aborted", the tool_use blocks may be incomplete
    // and should not have synthetic tool_results created. Creating synthetic results
    // for incomplete tool calls causes API 400 errors:
    // "unexpected tool_use_id found in tool_result blocks"
    // This matches the behavior in repairToolUseResultPairing (session-transcript-repair.ts)
    val stopReason = (nextMessage as /* TODO */ { stopReason?: String }).stopReason
    val toolCalls =
      nextRole == "assistant" && stopReason != "aborted" && stopReason != "error"
        ? extractToolCallsFromAssistant(nextMessage as /* TODO */ Extract<AgentMessage, { role: String /* "assistant" */ }>)
        : []

    // Always clear pending tool call state before appending non-tool-result messages.
    // flushPendingToolResults() only inserts synthetic results when allowSyntheticToolResults
    // is true it always clears the pending map. Without this, providers that disable
    // synthetic results (e.g. OpenAI) accumulate stale pending state when a user message
    // interrupts in-flight tool calls, leaving orphaned tool_use blocks in the transcript
    // that cause API 400 errors on subsequent requests.
    if (pendingState.shouldFlushBeforeNonToolResult(nextRole, toolCalls.length)) {
      flushPendingToolResults()
    }
    // If new tool calls arrive while older ones are pending, flush the old ones first.
    if (pendingState.shouldFlushBeforeNewToolCalls(toolCalls.length)) {
      flushPendingToolResults()
    }

    val finalMessage = applyBeforeWriteHook(persistMessage(nextMessage))
    if (!finalMessage) {
      return null
    }
    val result = originalAppend(finalMessage as /* TODO */ Nothing)

    val sessionFile = (
      sessionManager as /* TODO */ { getSessionFile?: () -> String? }
    ).getSessionFile?.()
    if (sessionFile) {
      emitSessionTranscriptUpdate(sessionFile)
    }

    if (toolCalls.length > 0) {
      pendingState.trackToolCalls(toolCalls)
    }

    return result
  }

  // Monkey-patch appendMessage with our guarded version.
  sessionManager.appendMessage = guardedAppend as /* TODO */ SessionManager["appendMessage"]

  return {
    flushPendingToolResults,
    clearPendingToolResults,
    getPendingIds: pendingState.getPendingIds,
  }
}
