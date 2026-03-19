@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-display.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import SHARED_TOOL_DISPLAY_JSON from "../../apps/shared/OpenClawKit/Sources/OpenClawKit/Resources/tool-display.json" with { type: "json" };
// TODO(port-deps): import { redactToolDetail } from "../logging/redact.js";
// TODO(port-deps): import { shortenHomeInString } from "../utils.js";
// TODO(port-deps): import {
// TODO(port-deps): defaultTitle,
// TODO(port-deps): formatToolDetailText,
// TODO(port-deps): formatDetailKey,
// TODO(port-deps): normalizeToolName,
// TODO(port-deps): resolveToolVerbAndDetailForArgs,
// TODO(port-deps): type ToolDisplaySpec as ToolDisplaySpecBase,
// TODO(port-deps): } from "./tool-display-common.js";
// TODO(port-deps): import TOOL_DISPLAY_OVERRIDES_JSON from "./tool-display-overrides.json" with { type: "json" };

typealias ToolDisplaySpec = Any /* TODO: translate TypeScript alias */

typealias ToolDisplayConfig = Any /* TODO: translate TypeScript alias */

typealias ToolDisplay = Any /* TODO: translate TypeScript alias */

val SHARED_TOOL_DISPLAY_CONFIG = SHARED_TOOL_DISPLAY_JSON as ToolDisplayConfig;
val TOOL_DISPLAY_OVERRIDES = TOOL_DISPLAY_OVERRIDES_JSON as ToolDisplayConfig;
val FALLBACK = TOOL_DISPLAY_OVERRIDES.fallback ??
  SHARED_TOOL_DISPLAY_CONFIG.fallback ?: { emoji: "🧩" };
val TOOL_MAP = Object.assign({}, SHARED_TOOL_DISPLAY_CONFIG.tools, TOOL_DISPLAY_OVERRIDES.tools);
val DETAIL_LABEL_OVERRIDES: Record<string, string> = {
  agentId: "agent",
  sessionKey: "session",
  targetId: "target",
  targetUrl: "url",
  nodeId: "node",
  requestId: "request",
  messageId: "message",
  threadId: "thread",
  channelId: "channel",
  guildId: "guild",
  userId: "user",
  runTimeoutSeconds: "timeout",
  timeoutSeconds: "timeout",
  includeTools: "tools",
  pollQuestion: "poll",
  maxChars: "max chars",
};
val MAX_DETAIL_ENTRIES = 8;

fun resolveToolDisplay(params: {
  name?: string;
  args?: unknown;
  meta?: string;
}): ToolDisplay {
  val name = normalizeToolName(params.name);
  val key = name.toLowerCase();
  val spec = TOOL_MAP[key];
  val emoji = spec?.emoji ?: FALLBACK.emoji ?: "🧩";
  val title = spec?.title ?: defaultTitle(name);
  val label = spec?.label ?: title;
  var { verb, detail } = resolveToolVerbAndDetailForArgs({
    toolKey: key,
    args: params.args,
    meta: params.meta,
    spec,
    fallbackDetailKeys: FALLBACK.detailKeys,
    detailMode: "summary",
    detailMaxEntries: MAX_DETAIL_ENTRIES,
    detailFormatKey: (raw) => formatDetailKey(raw, DETAIL_LABEL_OVERRIDES),
  });

  if (detail) {
    detail = shortenHomeInString(detail);
  }

  return {
    name,
    emoji,
    title,
    label,
    verb,
    detail,
  };
}

fun formatToolDetail(display: ToolDisplay): string | null {
  val detailRaw = display.detail ? redactToolDetail(display.detail) : null;
  return formatToolDetailText(detailRaw);
}

fun formatToolSummary(display: ToolDisplay): string {
  val detail = formatToolDetail(display);
  return detail
    ? `${display.emoji} ${display.label}: ${detail}`
    : `${display.emoji} ${display.label}`;
}
