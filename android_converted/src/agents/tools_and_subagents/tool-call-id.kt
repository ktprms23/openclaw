package agents.tools_and_subagents

// Converted from src/agents/tool-call-id.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { createHash } from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import type { AgentMessage } from "@mariozechner/pi-agent-core";

typealias ToolCallIdMode = "strict" | "strict9"

val STRICT9_LEN = 9
val TOOL_CALL_TYPES = mutableSetOf(["toolCall", "toolUse", "functionCall"])

data class ToolCallLike(
    val id: String,
    val name: String?,
)

/**
 * Sanitize a tool call ID to be compatible with various providers.
 *
 * - "strict" mode: only [a-zA-Z0-9]
 * - "strict9" mode: only [a-zA-Z0-9], length 9 (Mistral tool call requirement)
 */
fun sanitizeToolCallId(id: String, mode: ToolCallIdMode = "strict"): String {
  if (!id || id !is String) {
    if (mode == "strict9") {
      return "defaultid"
    }
    return "defaulttoolid"
  }

  if (mode == "strict9") {
    val alphanumericOnly = id.replace(/[^a-zA-Z0-9]/g, "")
    if (alphanumericOnly.length >= STRICT9_LEN) {
      return alphanumericOnly.slice(0, STRICT9_LEN)
    }
    if (alphanumericOnly.length > 0) {
      return shortHash(alphanumericOnly, STRICT9_LEN)
    }
    return shortHash("sanitized", STRICT9_LEN)
  }

  // Some providers require strictly alphanumeric tool call IDs.
  val alphanumericOnly = id.replace(/[^a-zA-Z0-9]/g, "")
  return alphanumericOnly.length > 0 ? alphanumericOnly : String /* "sanitizedtoolid" */
}

fun extractToolCallsFromAssistant(
  msg: Extract<AgentMessage, { role: String /* "assistant" */ }>,
): List<ToolCallLike> {
  val content = msg.content
  if (!Array.isArray(content)) {
    return []
  }

  val toolCalls: List<ToolCallLike> = []
  for (val block of content) {
    if (!block || typeof block != "object") {
      continue
    }
    val rec = block as /* TODO */ { type?: Any? id?: Any? name?: Any? }
    if (rec.id !is String || !rec.id) {
      continue
    }
    if (rec.type is String && TOOL_CALL_TYPES.has(rec.type)) {
      toolCalls.push({
        id: rec.id,
        name: rec.name is String ? rec.name : null,
      })
    }
  }
  return toolCalls
}

fun extractToolResultId(
  msg: Extract<AgentMessage, { role: String /* "toolResult" */ }>,
): String? {
  val toolCallId = (msg as /* TODO */ { toolCallId?: Any? }).toolCallId
  if (toolCallId is String && toolCallId) {
    return toolCallId
  }
  val toolUseId = (msg as /* TODO */ { toolUseId?: Any? }).toolUseId
  if (toolUseId is String && toolUseId) {
    return toolUseId
  }
  return null
}

fun isValidCloudCodeAssistToolId(id: String, mode: ToolCallIdMode = "strict"): Boolean {
  if (!id || id !is String) {
    return false
  }
  if (mode == "strict9") {
    return /^[a-zA-Z0-9]{9}$/.test(id)
  }
  // Strictly alphanumeric for providers with tighter tool ID constraints
  return /^[a-zA-Z0-9]+$/.test(id)
}

fun shortHash(text: String, length = 8): String {
  return createHash("sha256").update(text).digest("hex").slice(0, length)
}

fun makeUniqueToolId(params: { id: String used: MutableSet<String> mode: ToolCallIdMode }): String {
  if (params.mode == "strict9") {
    val base = sanitizeToolCallId(params.id, params.mode)
    val candidate = base.length >= STRICT9_LEN ? base.slice(0, STRICT9_LEN) : ""
    if (candidate && !params.used.has(candidate)) {
      return candidate
    }

    for (var i = 0 i < 1000 i += 1) {
      val hashed = shortHash(`${params.id}:${i}`, STRICT9_LEN)
      if (!params.used.has(hashed)) {
        return hashed
      }
    }

    return shortHash(`${params.id}:${Date.now()}`, STRICT9_LEN)
  }

  val MAX_LEN = 40

  val base = sanitizeToolCallId(params.id, params.mode).slice(0, MAX_LEN)
  if (!params.used.has(base)) {
    return base
  }

  val hash = shortHash(params.id)
  // Use separator based on mode: none for strict, underscore for non-strict variants
  val separator = params.mode == "strict" ? "" : String /* "_" */
  val maxBaseLen = MAX_LEN - separator.length - hash.length
  val clippedBase = base.length > maxBaseLen ? base.slice(0, maxBaseLen) : base
  val candidate = `${clippedBase}${separator}${hash}`
  if (!params.used.has(candidate)) {
    return candidate
  }

  for (var i = 2 i < 1000 i += 1) {
    val suffix = params.mode == "strict" ? `x${i}` : `_${i}`
    val next = `${candidate.slice(0, MAX_LEN - suffix.length)}${suffix}`
    if (!params.used.has(next)) {
      return next
    }
  }

  val ts = params.mode == "strict" ? `t${Date.now()}` : `_${Date.now()}`
  return `${candidate.slice(0, MAX_LEN - ts.length)}${ts}`
}

fun createOccurrenceAwareResolver(mode: ToolCallIdMode): {
  resolveAssistantId: (id: String) -> String
  resolveToolResultId: (id: String) -> String
} {
  val used = new MutableSet<String>()
  val assistantOccurrences = new MutableMap<String, Double>()
  val orphanToolResultOccurrences = new MutableMap<String, Double>()
  val pendingByRawId = new MutableMap<String, List<String>>()

  val allocate = (seed: String): String -> {
    val next = makeUniqueToolId({ id: seed, used, mode })
    used.add(next)
    return next
  }

  val resolveAssistantId = (id: String): String -> {
    val occurrence = (assistantOccurrences.get(id) ?: 0) + 1
    assistantOccurrences.set(id, occurrence)
    val next = allocate(occurrence == 1 ? id : `${id}:${occurrence}`)
    val pending = pendingByRawId.get(id)
    if (pending) {
      pending.push(next)
    } else {
      pendingByRawId.set(id, [next])
    }
    return next
  }

  val resolveToolResultId = (id: String): String -> {
    val pending = pendingByRawId.get(id)
    if (pending && pending.length > 0) {
      val next = pending.shift()!
      if (pending.length == 0) {
        pendingByRawId.delete(id)
      }
      return next
    }

    val occurrence = (orphanToolResultOccurrences.get(id) ?: 0) + 1
    orphanToolResultOccurrences.set(id, occurrence)
    return allocate(`${id}:tool_result:${occurrence}`)
  }

  return { resolveAssistantId, resolveToolResultId }
}

fun rewriteAssistantToolCallIds(params: {
  message: Extract<AgentMessage, { role: String /* "assistant" */ }>
  resolveId: (id: String) -> String
}): Extract<AgentMessage, { role: String /* "assistant" */ }> {
  val content = params.message.content
  if (!Array.isArray(content)) {
    return params.message
  }

  var changed = false
  val next = content.map((block) {
    if (!block || typeof block != "object") {
      return block
    }
    val rec = block as /* TODO */ { type?: Any? id?: Any? }
    val type = rec.type
    val id = rec.id
    if (
      (type != "functionCall" && type != "toolUse" && type != "toolCall") ||
      id !is String ||
      !id
    ) {
      return block
    }
    val nextId = params.resolveId(id)
    if (nextId == id) {
      return block
    }
    changed = true
    return { ...(block as /* TODO */ Any? as /* TODO */ MutableMap<String, Any?>), id: nextId }
  })

  if (!changed) {
    return params.message
  }
  return { ...params.message, content: next as /* TODO */ typeof params.message.content }
}

fun rewriteToolResultIds(params: {
  message: Extract<AgentMessage, { role: String /* "toolResult" */ }>
  resolveId: (id: String) -> String
}): Extract<AgentMessage, { role: String /* "toolResult" */ }> {
  val toolCallId =
    params.message.toolCallId is String && params.message.toolCallId
      ? params.message.toolCallId
      : null
  val toolUseId = (params.message as /* TODO */ { toolUseId?: Any? }).toolUseId
  val toolUseIdStr = toolUseId is String && toolUseId ? toolUseId : null
  val sharedRawId =
    toolCallId && toolUseIdStr && toolCallId == toolUseIdStr ? toolCallId : null

  val sharedResolvedId = sharedRawId ? params.resolveId(sharedRawId) : null
  val nextToolCallId =
    sharedResolvedId ?: (toolCallId ? params.resolveId(toolCallId) : null)
  val nextToolUseId =
    sharedResolvedId ?: (toolUseIdStr ? params.resolveId(toolUseIdStr) : null)

  if (nextToolCallId == toolCallId && nextToolUseId == toolUseIdStr) {
    return params.message
  }

  return {
    ...params.message,
    ...(nextToolCallId && { toolCallId: nextToolCallId }),
    ...(nextToolUseId && { toolUseId: nextToolUseId }),
  } as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>
}

/**
 * Sanitize tool call IDs for provider compatibility.
 *
 * @param messages - The messages to sanitize
 * @param mode - "strict" (alphanumeric only) or "strict9" (alphanumeric length 9)
 */
fun sanitizeToolCallIdsForCloudCodeAssist(
  messages: List<AgentMessage>,
  mode: ToolCallIdMode = "strict",
): List<AgentMessage> {
  // Strict mode: only [a-zA-Z0-9]
  // Strict9 mode: only [a-zA-Z0-9], length 9 (Mistral tool call requirement)
  // Sanitization can introduce collisions, and some providers also reject raw
  // duplicate tool-call IDs. Track assistant occurrences in-order so repeated
  // raw IDs receive distinct rewritten IDs, while matching tool results consume
  // the same rewritten IDs in encounter order.
  val { resolveAssistantId, resolveToolResultId } = createOccurrenceAwareResolver(mode)

  var changed = false
  val out = messages.map((msg) {
    if (!msg || typeof msg != "object") {
      return msg
    }
    val role = (msg as /* TODO */ { role?: Any? }).role
    if (role == "assistant") {
      val next = rewriteAssistantToolCallIds({
        message: msg as /* TODO */ Extract<AgentMessage, { role: String /* "assistant" */ }>,
        resolveId: resolveAssistantId,
      })
      if (next != msg) {
        changed = true
      }
      return next
    }
    if (role == "toolResult") {
      val next = rewriteToolResultIds({
        message: msg as /* TODO */ Extract<AgentMessage, { role: String /* "toolResult" */ }>,
        resolveId: resolveToolResultId,
      })
      if (next != msg) {
        changed = true
      }
      return next
    }
    return msg
  })

  return changed ? out : messages
}
