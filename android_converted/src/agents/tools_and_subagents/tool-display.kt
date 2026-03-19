package agents.tools_and_subagents

// Converted from src/agents/tool-display.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import SHARED_TOOL_DISPLAY_JSON from "../../apps/shared/OpenClawKit/Sources/OpenClawKit/Resources/tool-display.json" with { type: String /* "json" */ };
// TODO: TypeScript import retained for manual wiring: import { redactToolDetail } from "../logging/redact.js";
// TODO: TypeScript import retained for manual wiring: import { shortenHomeInString } from "../utils.js";
// TODO: TypeScript import retained for manual wiring: import {
  defaultTitle,
  formatToolDetailText,
  formatDetailKey,
  normalizeToolName,
  resolveToolVerbAndDetailForArgs,
  type ToolDisplaySpec as /* TODO */ ToolDisplaySpecBase,
} from "./tool-display-common.js"
// TODO: TypeScript import retained for manual wiring: import TOOL_DISPLAY_OVERRIDES_JSON from "./tool-display-overrides.json" with { type: String /* "json" */ };

type ToolDisplaySpec = ToolDisplaySpecBase & {
  emoji?: String
}

data class ToolDisplayConfig(
    val version: Double?,
    val fallback: ToolDisplaySpec?,
    val tools: MutableMap<String, ToolDisplaySpec>?,
)

data class ToolDisplay(
    val name: String,
    val emoji: String,
    val title: String,
    val label: String,
    val verb: String?,
    val detail: String?,
)

val SHARED_TOOL_DISPLAY_CONFIG = SHARED_TOOL_DISPLAY_JSON as /* TODO */ ToolDisplayConfig
val TOOL_DISPLAY_OVERRIDES = TOOL_DISPLAY_OVERRIDES_JSON as /* TODO */ ToolDisplayConfig
val FALLBACK = TOOL_DISPLAY_OVERRIDES.fallback ??
  SHARED_TOOL_DISPLAY_CONFIG.fallback ?: { emoji: String /* "🧩" */ }
val TOOL_MAP = Object.assign({}, SHARED_TOOL_DISPLAY_CONFIG.tools, TOOL_DISPLAY_OVERRIDES.tools)
val DETAIL_LABEL_OVERRIDES: MutableMap<String, String> = {
  agentId: String /* "agent" */,
  sessionKey: String /* "session" */,
  targetId: String /* "target" */,
  targetUrl: String /* "url" */,
  nodeId: String /* "node" */,
  requestId: String /* "request" */,
  messageId: String /* "message" */,
  threadId: String /* "thread" */,
  channelId: String /* "channel" */,
  guildId: String /* "guild" */,
  userId: String /* "user" */,
  runTimeoutSeconds: String /* "timeout" */,
  timeoutSeconds: String /* "timeout" */,
  includeTools: String /* "tools" */,
  pollQuestion: String /* "poll" */,
  maxChars: String /* "max chars" */,
}
val MAX_DETAIL_ENTRIES = 8

fun resolveToolDisplay(params: {
  name?: String
  args?: Any?
  meta?: String
}): ToolDisplay {
  val name = normalizeToolName(params.name)
  val key = name.toLowerCase()
  val spec = TOOL_MAP[key]
  val emoji = spec?.emoji ?: FALLBACK.emoji ?: String /* "🧩" */
  val title = spec?.title ?: defaultTitle(name)
  val label = spec?.label ?: title
  var { verb, detail } = resolveToolVerbAndDetailForArgs({
    toolKey: key,
    args: params.args,
    meta: params.meta,
    spec,
    fallbackDetailKeys: FALLBACK.detailKeys,
    detailMode: String /* "summary" */,
    detailMaxEntries: MAX_DETAIL_ENTRIES,
    detailFormatKey: (raw) -> formatDetailKey(raw, DETAIL_LABEL_OVERRIDES),
  })

  if (detail) {
    detail = shortenHomeInString(detail)
  }

  return {
    name,
    emoji,
    title,
    label,
    verb,
    detail,
  }
}

fun formatToolDetail(display: ToolDisplay): String? {
  val detailRaw = display.detail ? redactToolDetail(display.detail) : null
  return formatToolDetailText(detailRaw)
}

fun formatToolSummary(display: ToolDisplay): String {
  val detail = formatToolDetail(display)
  return detail
    ? `${display.emoji} ${display.label}: ${detail}`
    : `${display.emoji} ${display.label}`
}
