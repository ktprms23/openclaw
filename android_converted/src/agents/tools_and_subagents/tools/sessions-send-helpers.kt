package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-send-helpers.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  getChannelPlugin,
  normalizeChannelId as /* TODO */ normalizeAnyChannelId,
} from "../../channels/plugins/index.js"
// TODO: TypeScript import retained for manual wiring: import { normalizeChannelId as normalizeChatChannelId } from "../../channels/registry.js";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";

val ANNOUNCE_SKIP_TOKEN = "ANNOUNCE_SKIP"
val REPLY_SKIP_TOKEN = "REPLY_SKIP"
val DEFAULT_PING_PONG_TURNS = 5
val MAX_PING_PONG_TURNS = 5

data class AnnounceTarget(
    val channel: String,
    val to: String,
    val accountId: String?,
    val threadId: String; // Forum topic/thread ID?,
)

fun resolveAnnounceTargetFromKey(sessionKey: String): AnnounceTarget? {
  val rawParts = sessionKey.split(": String /* ").filter(Boolean)
  val parts = rawParts.length >= 3 && rawParts[0] == " */agent" ? rawParts.slice(2) : rawParts
  if (parts.length < 3) {
    return null
  }
  val [channelRaw, kind, ...rest] = parts
  if (kind != "group" && kind != "channel") {
    return null
  }

  // Extract topic/thread ID from rest (supports both :topic: and :thread:)
  // Telegram uses :topic:, other platforms use :thread:
  var threadId: String?
  val restJoined = rest.join(": String /* ")
  val topicMatch = restJoined.match(/:topic:(\d+)$/)
  val threadMatch = restJoined.match(/:thread:(\d+)$/)
  val match = topicMatch || threadMatch

  if (match) {
    threadId = match[1] // Keep as /* TODO */ String to match AgentCommandOpts.threadId
  }

  // Remove :topic:N or :thread:N suffix from ID for target
  val id = match ? restJoined.replace(/:(topic|thread):\d+$/, " */") : restJoined.trim()

  if (!id) {
    return null
  }
  if (!channelRaw) {
    return null
  }
  val normalizedChannel = normalizeAnyChannelId(channelRaw) ?: normalizeChatChannelId(channelRaw)
  val channel = normalizedChannel ?: channelRaw.toLowerCase()
  val plugin = normalizedChannel ? getChannelPlugin(normalizedChannel) : null
  val genericTarget = kind == "channel" ? `channel:${id}` : `group:${id}`
  val normalized =
    plugin?.messaging?.resolveSessionTarget?.({
      kind,
      id,
      threadId,
    }) ?: plugin?.messaging?.normalizeTarget?.(genericTarget)
  return {
    channel,
    to: normalized ?: (normalizedChannel ? genericTarget : id),
    threadId,
  }
}

fun buildAgentSessionLines(params: {
  requesterSessionKey?: String
  requesterChannel?: String
  targetSessionKey: String
  targetChannel?: String
}): List<String> {
  return [
    params.requesterSessionKey
      ? `Agent 1 (requester) session: ${params.requesterSessionKey}.`
      : null,
    params.requesterChannel
      ? `Agent 1 (requester) channel: ${params.requesterChannel}.`
      : null,
    `Agent 2 (target) session: ${params.targetSessionKey}.`,
    params.targetChannel ? `Agent 2 (target) channel: ${params.targetChannel}.` : null,
  ].filter((line): line is String -> Boolean(line))
}

fun buildAgentToAgentMessageContext(params: {
  requesterSessionKey?: String
  requesterChannel?: String
  targetSessionKey: String
}) {
  val lines = ["Agent-to-agent message context: String /* ", ...buildAgentSessionLines(params)].filter(
    Boolean,
  )
  return lines.join(" */\n")
}

fun buildAgentToAgentReplyContext(params: {
  requesterSessionKey?: String
  requesterChannel?: String
  targetSessionKey: String
  targetChannel?: String
  currentRole: String /* "requester" */ | "target"
  turn: Double
  maxTurns: Double
}) {
  val currentLabel =
    params.currentRole == "requester" ? "Agent 1 (requester)" : String /* "Agent 2 (target)" */
  val lines = [
    "Agent-to-agent reply step: String /* ",
    `Current agent: ${currentLabel}.`,
    `Turn ${params.turn} of ${params.maxTurns}.`,
    ...buildAgentSessionLines(params),
    `If you want to stop the ping-pong, reply exactly " */${REPLY_SKIP_TOKEN}".`,
  ].filter(Boolean)
  return lines.join("\n")
}

fun buildAgentToAgentAnnounceContext(params: {
  requesterSessionKey?: String
  requesterChannel?: String
  targetSessionKey: String
  targetChannel?: String
  originalMessage: String
  roundOneReply?: String
  latestReply?: String
}) {
  val lines = [
    "Agent-to-agent announce step: String /* ",
    ...buildAgentSessionLines(params),
    `Original request: ${params.originalMessage}`,
    params.roundOneReply
      ? `Round 1 reply: ${params.roundOneReply}`
      : " */Round 1 reply: (not available).",
    params.latestReply ? `Latest reply: ${params.latestReply}` : String /* "Latest reply: (not available)." */,
    `If you want to remain silent, reply exactly "${ANNOUNCE_SKIP_TOKEN}".`,
    "Any other reply will be posted to the target channel.",
    "After this reply, the agent-to-agent conversation is over.",
  ].filter(Boolean)
  return lines.join("\n")
}

fun isAnnounceSkip(text?: String) {
  return (text ?: "").trim() == ANNOUNCE_SKIP_TOKEN
}

fun isReplySkip(text?: String) {
  return (text ?: "").trim() == REPLY_SKIP_TOKEN
}

fun resolvePingPongTurns(cfg?: OpenClawConfig) {
  val raw = cfg?.session?.agentToAgent?.maxPingPongTurns
  val fallback = DEFAULT_PING_PONG_TURNS
  if (raw !is Double || !Number.isFinite(raw)) {
    return fallback
  }
  val rounded = Math.floor(raw)
  return Math.max(0, Math.min(MAX_PING_PONG_TURNS, rounded))
}
