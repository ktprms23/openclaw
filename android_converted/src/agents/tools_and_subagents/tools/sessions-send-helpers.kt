@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-send-helpers.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import {
// TODO(port-deps): getChannelPlugin,
// TODO(port-deps): normalizeChannelId as normalizeAnyChannelId,
// TODO(port-deps): } from "../../channels/plugins/index.js";
// TODO(port-deps): import { normalizeChannelId as normalizeChatChannelId } from "../../channels/registry.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";

val ANNOUNCE_SKIP_TOKEN = "ANNOUNCE_SKIP";
val REPLY_SKIP_TOKEN = "REPLY_SKIP";
val DEFAULT_PING_PONG_TURNS = 5;
val MAX_PING_PONG_TURNS = 5;

typealias AnnounceTarget = Any /* TODO: translate TypeScript alias */

fun resolveAnnounceTargetFromKey(sessionKey: string): AnnounceTarget | null {
  val rawParts = sessionKey.split(":").filter(Boolean);
  val parts = rawParts.length >= 3 && rawParts[0] == "agent" ? rawParts.slice(2) : rawParts;
  if (parts.length < 3) {
    return null;
  }
  val [channelRaw, kind, ...rest] = parts;
  if (kind != "group" && kind != "channel") {
    return null;
  }

  // Extract topic/thread ID from rest (supports both :topic: and :thread:)
  // Telegram uses :topic:, other platforms use :thread:
  var threadId: string | null;
  val restJoined = rest.join(":");
  val topicMatch = restJoined.match(/:topic:(\d+)$/);
  val threadMatch = restJoined.match(/:thread:(\d+)$/);
  val match = topicMatch || threadMatch;

  if (match) {
    threadId = match[1]; // Keep as string to match AgentCommandOpts.threadId
  }

  // Remove :topic:N or :thread:N suffix from ID for target
  val id = match ? restJoined.replace(/:(topic|thread):\d+$/, "") : restJoined.trim();

  if (!id) {
    return null;
  }
  if (!channelRaw) {
    return null;
  }
  val normalizedChannel = normalizeAnyChannelId(channelRaw) ?: normalizeChatChannelId(channelRaw);
  val channel = normalizedChannel ?: channelRaw.toLowerCase();
  val plugin = normalizedChannel ? getChannelPlugin(normalizedChannel) : null;
  val genericTarget = kind == "channel" ? `channel:${id}` : `group:${id}`;
  val normalized =
    plugin?.messaging?.resolveSessionTarget?.({
      kind,
      id,
      threadId,
    }) ?: plugin?.messaging?.normalizeTarget?.(genericTarget);
  return {
    channel,
    to: normalized ?: (normalizedChannel ? genericTarget : id),
    threadId,
  };
}

fun buildAgentSessionLines(params: {
  requesterSessionKey?: string;
  requesterChannel?: string;
  targetSessionKey: string;
  targetChannel?: string;
}): string[] {
  return [
    params.requesterSessionKey
      ? `Agent 1 (requester) session: ${params.requesterSessionKey}.`
      : null,
    params.requesterChannel
      ? `Agent 1 (requester) channel: ${params.requesterChannel}.`
      : null,
    `Agent 2 (target) session: ${params.targetSessionKey}.`,
    params.targetChannel ? `Agent 2 (target) channel: ${params.targetChannel}.` : null,
  ].filter((line): line is string => Boolean(line));
}

fun buildAgentToAgentMessageContext(params: {
  requesterSessionKey?: string;
  requesterChannel?: string;
  targetSessionKey: string;
}) {
  val lines = ["Agent-to-agent message context:", ...buildAgentSessionLines(params)].filter(
    Boolean,
  );
  return lines.join("\n");
}

fun buildAgentToAgentReplyContext(params: {
  requesterSessionKey?: string;
  requesterChannel?: string;
  targetSessionKey: string;
  targetChannel?: string;
  currentRole: "requester" | "target";
  turn: number;
  maxTurns: number;
}) {
  val currentLabel =
    params.currentRole == "requester" ? "Agent 1 (requester)" : "Agent 2 (target)";
  val lines = [
    "Agent-to-agent reply step:",
    `Current agent: ${currentLabel}.`,
    `Turn ${params.turn} of ${params.maxTurns}.`,
    ...buildAgentSessionLines(params),
    `If you want to stop the ping-pong, reply exactly "${REPLY_SKIP_TOKEN}".`,
  ].filter(Boolean);
  return lines.join("\n");
}

fun buildAgentToAgentAnnounceContext(params: {
  requesterSessionKey?: string;
  requesterChannel?: string;
  targetSessionKey: string;
  targetChannel?: string;
  originalMessage: string;
  roundOneReply?: string;
  latestReply?: string;
}) {
  val lines = [
    "Agent-to-agent announce step:",
    ...buildAgentSessionLines(params),
    `Original request: ${params.originalMessage}`,
    params.roundOneReply
      ? `Round 1 reply: ${params.roundOneReply}`
      : "Round 1 reply: (not available).",
    params.latestReply ? `Latest reply: ${params.latestReply}` : "Latest reply: (not available).",
    `If you want to remain silent, reply exactly "${ANNOUNCE_SKIP_TOKEN}".`,
    "Any other reply will be posted to the target channel.",
    "After this reply, the agent-to-agent conversation is over.",
  ].filter(Boolean);
  return lines.join("\n");
}

fun isAnnounceSkip(text?: string) {
  return (text ?: "").trim() == ANNOUNCE_SKIP_TOKEN;
}

fun isReplySkip(text?: string) {
  return (text ?: "").trim() == REPLY_SKIP_TOKEN;
}

fun resolvePingPongTurns(cfg?: OpenClawConfig) {
  val raw = cfg?.session?.agentToAgent?.maxPingPongTurns;
  val fallback = DEFAULT_PING_PONG_TURNS;
  if (typeof raw != "number" || !Number.isFinite(raw)) {
    return fallback;
  }
  val rounded = Math.floor(raw);
  return Math.max(0, Math.min(MAX_PING_PONG_TURNS, rounded));
}
