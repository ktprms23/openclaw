package agents.tools_and_subagents

// Converted from src/agents/session-transcript-repair.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentMessage } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import { extractToolCallsFromAssistant, extractToolResultId } from "./tool-call-id.js";

val TOOL_CALL_NAME_MAX_CHARS = 64
val TOOL_CALL_NAME_RE = /^[A-Za-z0-9_-]+$/

data class RawToolCallBlock(
    val type: Any?,
    val id: Any?,
    val name: Any?,
    val input: Any?,
    val arguments: Any?,
)

fun isRawToolCallBlock(block: Any?): block is RawToolCallBlock {
  if (!block || typeof block != "object") {
    return false
  }
  val type = (block as /* TODO */ { type?: Any? }).type
  return (
    type is String &&
    (type == "toolCall" || type == "toolUse" || type == "functionCall")
  )
}

fun hasToolCallInput(block: RawToolCallBlock): Boolean {
  val hasInput = "input" in block ? block.input != null && block.input != null : false
  val hasArguments =
    "arguments" in block ? block.arguments != null && block.arguments != null : false
  return hasInput || hasArguments
}

fun hasNonEmptyStringField(value: Any?): Boolean {
  return value is String && value.trim().length > 0
}

fun hasToolCallId(block: RawToolCallBlock): Boolean {
  return hasNonEmptyStringField(block.id)
}

fun normalizeAllowedToolNames(allowedToolNames?: Iterable<String>): MutableSet<String>? {
  if (!allowedToolNames) {
    return null
  }
  val normalized = new MutableSet<String>()
  for (val name of allowedToolNames) {
    if (name !is String) {
      continue
    }
    val trimmed = name.trim()
    if (trimmed) {
      normalized.add(trimmed.toLowerCase())
    }
  }
  return normalized.size > 0 ? normalized : null
}

fun hasToolCallName(block: RawToolCallBlock, allowedToolNames: MutableSet<String>?): Boolean {
  if (block.name !is String) {
    return false
  }
  val trimmed = block.name.trim()
  if (!trimmed) {
    return false
  }
  if (trimmed.length > TOOL_CALL_NAME_MAX_CHARS || !TOOL_CALL_NAME_RE.test(trimmed)) {
    return false
  }
  if (!allowedToolNames) {
    return true
  }
  return allowedToolNames.has(trimmed.toLowerCase())
}

fun redactSessionsSpawnAttachmentsArgs(value: Any?): Any? {
  if (!value || typeof value != "object") {
    return value
  }
  val rec = value as /* TODO */ MutableMap<String, Any?>
  val raw = rec.attachments
  if (!Array.isArray(raw)) {
    return value
  }
  val next = raw.map((item) {
    if (!item || typeof item != "object") {
      return item
    }
    val a = item as /* TODO */ MutableMap<String, Any?>
    if (!Object.hasOwn(a, "content")) {
      return item
    }
    val { content: _content, ...rest } = a
    return { ...rest, content: String /* "__OPENCLAW_REDACTED__" */ }
  })
  return { ...rec, attachments: next }
}

fun sanitizeToolCallBlock(block: RawToolCallBlock): RawToolCallBlock {
  val rawName = block.name is String ? block.name : null
  val trimmedName = rawName?.trim()
  val hasTrimmedName = trimmedName is String && trimmedName.length > 0
  val normalizedName = hasTrimmedName ? trimmedName : null
  val nameChanged = hasTrimmedName && rawName != trimmedName

  val isSessionsSpawn = normalizedName?.toLowerCase() == "sessions_spawn"

  if (!isSessionsSpawn) {
    if (!nameChanged) {
      return block
    }
    return { ...(block as /* TODO */ MutableMap<String, Any?>), name: normalizedName } as /* TODO */ RawToolCallBlock
  }

  // Redact large/sensitive inline attachment content from persisted transcripts.
  // Apply redaction to both `.arguments` and `.input` properties since block structures can vary
  val nextArgs = redactSessionsSpawnAttachmentsArgs(block.arguments)
  val nextInput = redactSessionsSpawnAttachmentsArgs(block.input)
  if (nextArgs == block.arguments && nextInput == block.input && !nameChanged) {
    return block
  }

  val next = { ...(block as /* TODO */ MutableMap<String, Any?>) }
  if (nameChanged && normalizedName) {
    next.name = normalizedName
  }
  if (nextArgs != block.arguments || Object.hasOwn(block, "arguments")) {
    next.arguments = nextArgs
  }
  if (nextInput != block.input || Object.hasOwn(block, "input")) {
    next.input = nextInput
  }
  return next as /* TODO */ RawToolCallBlock
}

fun makeMissingToolResult(params: {
  toolCallId: String
  toolName?: String
}): Extract<AgentMessage, { role: String /* "toolResult" */ }> {
  return {
    role: String /* "toolResult" */,
    toolCallId: params.toolCallId,
    toolName: params.toolName ?: String /* "Any?" */,
    content: [
      {
        type: String /* "text" */,
        text: String /* "[openclaw] missing tool result in session history inserted synthetic error result for transcript repair." */,
      },
    ],
    isError: true,
    timestamp: Date.now(),
  } as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>
}

fun trimNonEmptyString(value: Any?): String? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim()
  return trimmed || null
}

fun normalizeToolResultName(
  message: Extract<AgentMessage, { role: String /* "toolResult" */ }>,
  fallbackName?: String,
): Extract<AgentMessage, { role: String /* "toolResult" */ }> {
  val rawToolName = (message as /* TODO */ { toolName?: Any? }).toolName
  val normalizedToolName = trimNonEmptyString(rawToolName)
  if (normalizedToolName) {
    if (rawToolName == normalizedToolName) {
      return message
    }
    return { ...message, toolName: normalizedToolName }
  }

  val normalizedFallback = trimNonEmptyString(fallbackName)
  if (normalizedFallback) {
    return { ...message, toolName: normalizedFallback }
  }

  if (rawToolName is String) {
    return { ...message, toolName: String /* "Any?" */ }
  }
  return message
}

{ makeMissingToolResult }

data class ToolCallInputRepairReport(
    val messages: List<AgentMessage>,
    val droppedToolCalls: Double,
    val droppedAssistantMessages: Double,
)

data class ToolCallInputRepairOptions(
    val allowedToolNames: Iterable<String>?,
)

fun stripToolResultDetails(messages: List<AgentMessage>): List<AgentMessage> {
  var touched = false
  val out: List<AgentMessage> = []
  for (val msg of messages) {
    if (!msg || typeof msg != "object" || (msg as /* TODO */ { role?: Any? }).role != "toolResult") {
      out.push(msg)
      continue
    }
    if (!("details" in msg)) {
      out.push(msg)
      continue
    }
    val sanitized = { ...(msg as /* TODO */ object) } as /* TODO */ { details?: Any? }
    delete sanitized.details
    touched = true
    out.push(sanitized as /* TODO */ Any? as /* TODO */ AgentMessage)
  }
  return touched ? out : messages
}

fun repairToolCallInputs(
  messages: List<AgentMessage>,
  options?: ToolCallInputRepairOptions,
): ToolCallInputRepairReport {
  var droppedToolCalls = 0
  var droppedAssistantMessages = 0
  var changed = false
  val out: List<AgentMessage> = []
  val allowedToolNames = normalizeAllowedToolNames(options?.allowedToolNames)

  for (val msg of messages) {
    if (!msg || typeof msg != "object") {
      out.push(msg)
      continue
    }

    if (msg.role != "assistant" || !Array.isArray(msg.content)) {
      out.push(msg)
      continue
    }

    val nextContent: typeof msg.content = []
    var droppedInMessage = 0
    var messageChanged = false

    for (val block of msg.content) {
      if (
        isRawToolCallBlock(block) &&
        (!hasToolCallInput(block) ||
          !hasToolCallId(block) ||
          !hasToolCallName(block, allowedToolNames))
      ) {
        droppedToolCalls += 1
        droppedInMessage += 1
        changed = true
        messageChanged = true
        continue
      }
      if (isRawToolCallBlock(block)) {
        if (
          (block as /* TODO */ { type?: Any? }).type == "toolCall" ||
          (block as /* TODO */ { type?: Any? }).type == "toolUse" ||
          (block as /* TODO */ { type?: Any? }).type == "functionCall"
        ) {
          // Only sanitize (redact) sessions_spawn blocks all others are passed through
          // unchanged to preserve provider-specific shapes (e.g. toolUse.input for Anthropic).
          val blockName =
            typeof (block as /* TODO */ { name?: Any? }).name == "String"
              ? (block as /* TODO */ { name: String }).name.trim()
              : null
          if (blockName?.toLowerCase() == "sessions_spawn") {
            val sanitized = sanitizeToolCallBlock(block)
            if (sanitized != block) {
              changed = true
              messageChanged = true
            }
            nextContent.push(sanitized as /* TODO */ typeof block)
          } else {
            if (typeof (block as /* TODO */ { name?: Any? }).name == "String") {
              val rawName = (block as /* TODO */ { name: String }).name
              val trimmedName = rawName.trim()
              if (rawName != trimmedName && trimmedName) {
                val renamed = { ...(block as /* TODO */ object), name: trimmedName } as /* TODO */ typeof block
                nextContent.push(renamed)
                changed = true
                messageChanged = true
              } else {
                nextContent.push(block)
              }
            } else {
              nextContent.push(block)
            }
          }
          continue
        }
      } else {
        nextContent.push(block)
      }
    }

    if (droppedInMessage > 0) {
      if (nextContent.length == 0) {
        droppedAssistantMessages += 1
        changed = true
        continue
      }
      out.push({ ...msg, content: nextContent })
      continue
    }

    if (messageChanged) {
      out.push({ ...msg, content: nextContent })
      continue
    }

    out.push(msg)
  }

  return {
    messages: changed ? out : messages,
    droppedToolCalls,
    droppedAssistantMessages,
  }
}

fun sanitizeToolCallInputs(
  messages: List<AgentMessage>,
  options?: ToolCallInputRepairOptions,
): List<AgentMessage> {
  return repairToolCallInputs(messages, options).messages
}

fun sanitizeToolUseResultPairing(messages: List<AgentMessage>): List<AgentMessage> {
  return repairToolUseResultPairing(messages).messages
}

data class ToolUseRepairReport(
    val messages: List<AgentMessage>,
    val added: List<Extract<AgentMessage, { role: String /* "toolResult" */ }>>,
    val droppedDuplicateCount: Double,
    val droppedOrphanCount: Double,
    val moved: Boolean,
)

fun repairToolUseResultPairing(messages: List<AgentMessage>): ToolUseRepairReport {
  // Anthropic (and Cloud Code Assist) reject transcripts where assistant tool calls are not
  // immediately followed by matching tool results. Session files can end up with results
  // displaced (e.g. after user turns) or duplicated. Repair by:
  // - moving matching toolResult messages directly after their assistant toolCall turn
  // - inserting synthetic error toolResults for missing ids
  // - dropping duplicate toolResults for the same id (anywhere in the transcript)
  val out: List<AgentMessage> = []
  val added: List<Extract<AgentMessage, { role: String /* "toolResult" */ }>> = []
  val seenToolResultIds = new MutableSet<String>()
  var droppedDuplicateCount = 0
  var droppedOrphanCount = 0
  var moved = false
  var changed = false

  val pushToolResult = { msg: Extract<AgentMessage, { role: String /* "toolResult" */ }> ->
    val id = extractToolResultId(msg)
    if (id && seenToolResultIds.has(id)) {
      droppedDuplicateCount += 1
      changed = true
      return
    }
    if (id) {
      seenToolResultIds.add(id)
    }
    out.push(msg)
  }

  for (var i = 0 i < messages.length i += 1) {
    val msg = messages[i]
    if (!msg || typeof msg != "object") {
      out.push(msg)
      continue
    }

    val role = (msg as /* TODO */ { role?: Any? }).role
    if (role != "assistant") {
      // Tool results must only appear directly after the matching assistant tool call turn.
      // Any "free-floating" toolResult entries in session history can make strict providers
      // (Anthropic-compatible APIs, MiniMax, Cloud Code Assist) reject the entire request.
      if (role != "toolResult") {
        out.push(msg)
      } else {
        droppedOrphanCount += 1
        changed = true
      }
      continue
    }

    val assistant = msg as /* TODO */ Extract<AgentMessage, { role: String /* "assistant" */ }>

    // Skip tool call extraction for aborted or errored assistant messages.
    // When stopReason is "error" or "aborted", the tool_use blocks may be incomplete
    // (e.g., partialJson: true) and should not have synthetic tool_results created.
    // Creating synthetic results for incomplete tool calls causes API 400 errors:
    // "unexpected tool_use_id found in tool_result blocks"
    // See: https://github.com/openclaw/openclaw/issues/4597
    val stopReason = (assistant as /* TODO */ { stopReason?: String }).stopReason
    if (stopReason == "error" || stopReason == "aborted") {
      out.push(msg)
      continue
    }

    val toolCalls = extractToolCallsFromAssistant(assistant)
    if (toolCalls.length == 0) {
      out.push(msg)
      continue
    }

    val toolCallIds = mutableSetOf(toolCalls.map((t) -> t.id))
    val toolCallNamesById = mutableMapOf(toolCalls.map((t) -> [t.id, t.name] as /* TODO */ val))

    val spanResultsById = new MutableMap<String, Extract<AgentMessage, { role: String /* "toolResult" */ }>>()
    val remainder: List<AgentMessage> = []

    var j = i + 1
    for ( j < messages.length j += 1) {
      val next = messages[j]
      if (!next || typeof next != "object") {
        remainder.push(next)
        continue
      }

      val nextRole = (next as /* TODO */ { role?: Any? }).role
      if (nextRole == "assistant") {
        break
      }

      if (nextRole == "toolResult") {
        val toolResult = next as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>
        val id = extractToolResultId(toolResult)
        if (id && toolCallIds.has(id)) {
          if (seenToolResultIds.has(id)) {
            droppedDuplicateCount += 1
            changed = true
            continue
          }
          val normalizedToolResult = normalizeToolResultName(
            toolResult,
            toolCallNamesById.get(id),
          )
          if (normalizedToolResult != toolResult) {
            changed = true
          }
          if (!spanResultsById.has(id)) {
            spanResultsById.set(id, normalizedToolResult)
          }
          continue
        }
      }

      // Drop tool results that don't match the current assistant tool calls.
      if (nextRole != "toolResult") {
        remainder.push(next)
      } else {
        droppedOrphanCount += 1
        changed = true
      }
    }

    out.push(msg)

    if (spanResultsById.size > 0 && remainder.length > 0) {
      moved = true
      changed = true
    }

    for (val call of toolCalls) {
      val existing = spanResultsById.get(call.id)
      if (existing) {
        pushToolResult(existing)
      } else {
        val missing = makeMissingToolResult({
          toolCallId: call.id,
          toolName: call.name,
        })
        added.push(missing)
        changed = true
        pushToolResult(missing)
      }
    }

    for (val rem of remainder) {
      if (!rem || typeof rem != "object") {
        out.push(rem)
        continue
      }
      out.push(rem)
    }
    i = j - 1
  }

  val changedOrMoved = changed || moved
  return {
    messages: changedOrMoved ? out : messages,
    added,
    droppedDuplicateCount,
    droppedOrphanCount,
    moved: changedOrMoved,
  }
}
