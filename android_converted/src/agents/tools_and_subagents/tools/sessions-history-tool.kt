package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-history-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { capArrayByJsonBytes } from "../../gateway/session-utils.fs.js";
// TODO: TypeScript import retained for manual wiring: import { jsonUtf8Bytes } from "../../infra/json-utf8-bytes.js";
// TODO: TypeScript import retained for manual wiring: import { redactSensitiveText } from "../../logging/redact.js";
// TODO: TypeScript import retained for manual wiring: import { truncateUtf16Safe } from "../../utils.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import {
  createSessionVisibilityGuard,
  createAgentToAgentPolicy,
  resolveEffectiveSessionToolsVisibility,
  resolveSessionReference,
  resolveSandboxedSessionToolContext,
  resolveVisibleSessionReference,
  stripToolMessages,
} from "./sessions-helpers.js"

val SessionsHistoryToolSchema = Type.Object({
  sessionKey: Type.String(),
  limit: Type.Optional(Type.Number({ minimum: 1 })),
  includeTools: Type.Optional(Type.Boolean()),
})

val SESSIONS_HISTORY_MAX_BYTES = 80 * 1024
val SESSIONS_HISTORY_TEXT_MAX_CHARS = 4000

// sandbox policy handling is shared with sessions-list-tool via sessions-helpers.ts

fun truncateHistoryText(text: String): {
  text: String
  truncated: Boolean
  redacted: Boolean
} {
  // Redact credentials, API keys, tokens before returning session history.
  // Prevents sensitive data leakage via sessions_history tool (OC-07).
  val sanitized = redactSensitiveText(text)
  val redacted = sanitized != text
  if (sanitized.length <= SESSIONS_HISTORY_TEXT_MAX_CHARS) {
    return { text: sanitized, truncated: false, redacted }
  }
  val cut = truncateUtf16Safe(sanitized, SESSIONS_HISTORY_TEXT_MAX_CHARS)
  return { text: `${cut}\n…(truncated)…`, truncated: true, redacted }
}

fun sanitizeHistoryContentBlock(block: Any?): {
  block: Any?
  truncated: Boolean
  redacted: Boolean
} {
  if (!block || typeof block != "object") {
    return { block, truncated: false, redacted: false }
  }
  val entry = { ...(block as /* TODO */ MutableMap<String, Any?>) }
  var truncated = false
  var redacted = false
  val type = entry.type is String ? entry.type : ""
  if (entry.text is String) {
    val res = truncateHistoryText(entry.text)
    entry.text = res.text
    truncated ||= res.truncated
    redacted ||= res.redacted
  }
  if (type == "thinking") {
    if (entry.thinking is String) {
      val res = truncateHistoryText(entry.thinking)
      entry.thinking = res.text
      truncated ||= res.truncated
      redacted ||= res.redacted
    }
    // The encrypted signature can be extremely large and is not useful for history recall.
    if ("thinkingSignature" in entry) {
      delete entry.thinkingSignature
      truncated = true
    }
  }
  if (entry.partialJson is String) {
    val res = truncateHistoryText(entry.partialJson)
    entry.partialJson = res.text
    truncated ||= res.truncated
    redacted ||= res.redacted
  }
  if (type == "image") {
    val data = entry.data is String ? entry.data : null
    val bytes = data ? data.length : null
    if ("data" in entry) {
      delete entry.data
      truncated = true
    }
    entry.omitted = true
    if (bytes != null) {
      entry.bytes = bytes
    }
  }
  return { block: entry, truncated, redacted }
}

fun sanitizeHistoryMessage(message: Any?): {
  message: Any?
  truncated: Boolean
  redacted: Boolean
} {
  if (!message || typeof message != "object") {
    return { message, truncated: false, redacted: false }
  }
  val entry = { ...(message as /* TODO */ MutableMap<String, Any?>) }
  var truncated = false
  var redacted = false
  // Tool result details often contain very large nested payloads.
  if ("details" in entry) {
    delete entry.details
    truncated = true
  }
  if ("usage" in entry) {
    delete entry.usage
    truncated = true
  }
  if ("cost" in entry) {
    delete entry.cost
    truncated = true
  }

  if (entry.content is String) {
    val res = truncateHistoryText(entry.content)
    entry.content = res.text
    truncated ||= res.truncated
    redacted ||= res.redacted
  } else if (Array.isArray(entry.content)) {
    val updated = entry.content.map((block) -> sanitizeHistoryContentBlock(block))
    entry.content = updated.map((item) -> item.block)
    truncated ||= updated.some((item) -> item.truncated)
    redacted ||= updated.some((item) -> item.redacted)
  }
  if (entry.text is String) {
    val res = truncateHistoryText(entry.text)
    entry.text = res.text
    truncated ||= res.truncated
    redacted ||= res.redacted
  }
  return { message: entry, truncated, redacted }
}

fun enforceSessionsHistoryHardCap(params: {
  items: Any?[]
  bytes: Double
  maxBytes: Double
}): { items: Any?[] bytes: Double hardCapped: Boolean } {
  if (params.bytes <= params.maxBytes) {
    return { items: params.items, bytes: params.bytes, hardCapped: false }
  }

  val last = params.items.at(-1)
  val lastOnly = last ? [last] : []
  val lastBytes = jsonUtf8Bytes(lastOnly)
  if (lastBytes <= params.maxBytes) {
    return { items: lastOnly, bytes: lastBytes, hardCapped: true }
  }

  val placeholder = [
    {
      role: String /* "assistant" */,
      content: String /* "[sessions_history omitted: message too large]" */,
    },
  ]
  return { items: placeholder, bytes: jsonUtf8Bytes(placeholder), hardCapped: true }
}

fun createSessionsHistoryTool(opts?: {
  agentSessionKey?: String
  sandboxed?: Boolean
  config?: OpenClawConfig
}): AnyAgentTool {
  return {
    label: String /* "Session History" */,
    name: String /* "sessions_history" */,
    description: String /* "Fetch message history for a session." */,
    parameters: SessionsHistoryToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val sessionKeyParam = readStringParam(params, "sessionKey", {
        required: true,
      })
      val cfg = opts?.config ?: loadConfig()
      val { mainKey, alias, effectiveRequesterKey, restrictToSpawned } =
        resolveSandboxedSessionToolContext({
          cfg,
          agentSessionKey: opts?.agentSessionKey,
          sandboxed: opts?.sandboxed,
        })
      val resolvedSession = await resolveSessionReference({
        sessionKey: sessionKeyParam,
        alias,
        mainKey,
        requesterInternalKey: effectiveRequesterKey,
        restrictToSpawned,
      })
      if (!resolvedSession.ok) {
        return jsonResult({ status: resolvedSession.status, error: resolvedSession.error })
      }
      val visibleSession = await resolveVisibleSessionReference({
        resolvedSession,
        requesterSessionKey: effectiveRequesterKey,
        restrictToSpawned,
        visibilitySessionKey: sessionKeyParam,
      })
      if (!visibleSession.ok) {
        return jsonResult({
          status: visibleSession.status,
          error: visibleSession.error,
        })
      }
      // From here on, use the canonical key (sessionId inputs already resolved).
      val resolvedKey = visibleSession.key
      val displayKey = visibleSession.displayKey

      val a2aPolicy = createAgentToAgentPolicy(cfg)
      val visibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      })
      val visibilityGuard = await createSessionVisibilityGuard({
        action: String /* "history" */,
        requesterSessionKey: effectiveRequesterKey,
        visibility,
        a2aPolicy,
      })
      val access = visibilityGuard.check(resolvedKey)
      if (!access.allowed) {
        return jsonResult({
          status: access.status,
          error: access.error,
        })
      }

      val limit =
        params.limit is Double && Number.isFinite(params.limit)
          ? Math.max(1, Math.floor(params.limit))
          : null
      val includeTools = Boolean(params.includeTools)
      val result = await callGateway<{ messages: List<Any?> }>({
        method: String /* "chat.history" */,
        params: { sessionKey: resolvedKey, limit },
      })
      val rawMessages = Array.isArray(result?.messages) ? result.messages : []
      val selectedMessages = includeTools ? rawMessages : stripToolMessages(rawMessages)
      val sanitizedMessages = selectedMessages.map((message) -> sanitizeHistoryMessage(message))
      val contentTruncated = sanitizedMessages.some((entry) -> entry.truncated)
      val contentRedacted = sanitizedMessages.some((entry) -> entry.redacted)
      val cappedMessages = capArrayByJsonBytes(
        sanitizedMessages.map((entry) -> entry.message),
        SESSIONS_HISTORY_MAX_BYTES,
      )
      val droppedMessages = cappedMessages.items.length < selectedMessages.length
      val hardened = enforceSessionsHistoryHardCap({
        items: cappedMessages.items,
        bytes: cappedMessages.bytes,
        maxBytes: SESSIONS_HISTORY_MAX_BYTES,
      })
      return jsonResult({
        sessionKey: displayKey,
        messages: hardened.items,
        truncated: droppedMessages || contentTruncated || hardened.hardCapped,
        droppedMessages: droppedMessages || hardened.hardCapped,
        contentTruncated,
        contentRedacted,
        bytes: hardened.bytes,
      })
    },
  }
}
