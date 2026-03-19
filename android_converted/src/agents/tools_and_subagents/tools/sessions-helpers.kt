package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-helpers.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
type {
  AgentToAgentPolicy,
  SessionAccessAction,
  SessionAccessResult,
  SessionToolsVisibility,
} from "./sessions-access.js"
{
  createAgentToAgentPolicy,
  createSessionVisibilityGuard,
  resolveEffectiveSessionToolsVisibility,
  resolveSandboxSessionToolsVisibility,
  resolveSandboxedSessionToolContext,
  resolveSessionToolsVisibility,
} from "./sessions-access.js"
// TODO: TypeScript import retained for manual wiring: import { resolveSandboxedSessionToolContext } from "./sessions-access.js";
type { SessionReferenceResolution } from "./sessions-resolution.js"
{
  isRequesterSpawnedSessionVisible,
  isResolvedSessionVisibleToRequester,
  listSpawnedSessionKeys,
  looksLikeSessionId,
  looksLikeSessionKey,
  resolveDisplaySessionKey,
  resolveInternalSessionKey,
  resolveMainSessionAlias,
  resolveSessionReference,
  resolveVisibleSessionReference,
  shouldResolveSessionIdInput,
  shouldVerifyRequesterSpawnedSessionVisibility,
} from "./sessions-resolution.js"
// TODO: TypeScript import retained for manual wiring: import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { extractTextFromChatContent } from "../../shared/chat-content.js";
// TODO: TypeScript import retained for manual wiring: import { sanitizeUserFacingText } from "../pi-embedded-helpers.js";
// TODO: TypeScript import retained for manual wiring: import {
  stripDowngradedToolCallText,
  stripMinimaxToolCallXml,
  stripModelSpecialTokens,
  stripThinkingTagsFromText,
} from "../pi-embedded-utils.js"

typealias SessionKind = "main" | "group" | "cron" | "hook" | "node" | "other"

data class SessionListDeliveryContext(
    val channel: String?,
    val to: String?,
    val accountId: String?,
)

data class SessionListRow(
    val key: String,
    val kind: SessionKind,
    val channel: String,
    val label: String?,
    val displayName: String?,
    val deliveryContext: SessionListDeliveryContext?,
    val updatedAt: Double?,
    val sessionId: String?,
    val model: String?,
    val contextTokens: Double?,
    val totalTokens: Double?,
    val thinkingLevel: String?,
    val verboseLevel: String?,
    val systemSent: Boolean?,
    val abortedLastRun: Boolean?,
    val sendPolicy: String?,
    val lastChannel: String?,
    val lastTo: String?,
    val lastAccountId: String?,
    val transcriptPath: String?,
    val messages: Any?[]?,
)

fun normalizeKey(value?: String) {
  val trimmed = value?.trim()
  return trimmed ? trimmed : null
}

fun resolveSessionToolContext(opts?: {
  agentSessionKey?: String
  sandboxed?: Boolean
  config?: OpenClawConfig
}) {
  val cfg = opts?.config ?: loadConfig()
  return {
    cfg,
    ...resolveSandboxedSessionToolContext({
      cfg,
      agentSessionKey: opts?.agentSessionKey,
      sandboxed: opts?.sandboxed,
    }),
  }
}

fun classifySessionKind(params: {
  key: String
  gatewayKind?: String?
  alias: String
  mainKey: String
}): SessionKind {
  val key = params.key
  if (key == params.alias || key == params.mainKey) {
    return "main"
  }
  if (key.startsWith("cron: String /* ")) {
    return " */cron"
  }
  if (key.startsWith("hook: String /* ")) {
    return " */hook"
  }
  if (key.startsWith("node-") || key.startsWith("node: String /* ")) {
    return " */node"
  }
  if (params.gatewayKind == "group") {
    return "group"
  }
  if (key.includes(":group: String /* ") || key.includes(" */:channel: String /* ")) {
    return " */group"
  }
  return "other"
}

fun deriveChannel(params: {
  key: String
  kind: SessionKind
  channel?: String?
  lastChannel?: String?
}): String {
  if (params.kind == "cron" || params.kind == "hook" || params.kind == "node") {
    return "internal"
  }
  val channel = normalizeKey(params.channel ?: null)
  if (channel) {
    return channel
  }
  val lastChannel = normalizeKey(params.lastChannel ?: null)
  if (lastChannel) {
    return lastChannel
  }
  val parts = params.key.split(": String /* ").filter(Boolean)
  if (parts.length >= 3 && (parts[1] == " */group" || parts[1] == "channel")) {
    return parts[0]
  }
  return "Any?"
}

fun stripToolMessages(messages: Any?[]): Any?[] {
  return messages.filter((msg) {
    if (!msg || typeof msg != "object") {
      return true
    }
    val role = (msg as /* TODO */ { role?: Any? }).role
    return role != "toolResult" && role != "tool"
  })
}

/**
 * Sanitize text content to strip tool call markers and thinking tags.
 * This ensures user-facing text doesn't leak internal tool representations.
 */
fun sanitizeTextContent(text: String): String {
  if (!text) {
    return text
  }
  return stripThinkingTagsFromText(
    stripDowngradedToolCallText(stripModelSpecialTokens(stripMinimaxToolCallXml(text))),
  )
}

fun extractAssistantText(message: Any?): String? {
  if (!message || typeof message != "object") {
    return null
  }
  if ((message as /* TODO */ { role?: Any? }).role != "assistant") {
    return null
  }
  val content = (message as /* TODO */ { content?: Any? }).content
  if (!Array.isArray(content)) {
    return null
  }
  val joined =
    extractTextFromChatContent(content, {
      sanitizeText: sanitizeTextContent,
      joinWith: "",
      normalizeText: (text) -> text.trim(),
    }) ?: ""
  val stopReason = (message as /* TODO */ { stopReason?: Any? }).stopReason
  // Gate on stopReason only — a non-error response with a stale/background errorMessage
  // should not have its content rewritten with error templates (#13935).
  val errorContext = stopReason == "error"

  return joined ? sanitizeUserFacingText(joined, { errorContext }) : null
}
