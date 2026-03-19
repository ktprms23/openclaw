package agents.tools_and_subagents

// Converted from src/agents/tool-mutation.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
val MUTATING_TOOL_NAMES = mutableSetOf([
  "write",
  "edit",
  "apply_patch",
  "exec",
  "bash",
  "process",
  "message",
  "sessions_send",
  "cron",
  "gateway",
  "canvas",
  "nodes",
  "session_status",
])

val READ_ONLY_ACTIONS = mutableSetOf([
  "get",
  "list",
  "read",
  "status",
  "show",
  "fetch",
  "search",
  "query",
  "view",
  "poll",
  "log",
  "inspect",
  "check",
  "probe",
])

val PROCESS_MUTATING_ACTIONS = mutableSetOf(["write", "send_keys", "submit", "paste", "kill"])

val MESSAGE_MUTATING_ACTIONS = mutableSetOf([
  "send",
  "reply",
  "thread_reply",
  "threadreply",
  "edit",
  "delete",
  "react",
  "pin",
  "unpin",
])

data class ToolMutationState(
    val mutatingAction: Boolean,
    val actionFingerprint: String?,
)

data class ToolActionRef(
    val toolName: String,
    val meta: String?,
    val actionFingerprint: String?,
)

fun asRecord(value: Any?): MutableMap<String, Any?>? {
  return value && typeof value == "object" ? (value as /* TODO */ MutableMap<String, Any?>) : null
}

fun normalizeActionName(value: Any?): String? {
  if (value !is String) {
    return null
  }
  val normalized = value
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, "_")
  return normalized || null
}

fun normalizeFingerprintValue(value: Any?): String? {
  if (value is String) {
    val normalized = value.trim()
    return normalized ? normalized.toLowerCase() : null
  }
  if (value is Double || typeof value == "bigint" || value is Boolean) {
    return String(value).toLowerCase()
  }
  return null
}

fun isLikelyMutatingToolName(toolName: String): Boolean {
  val normalized = toolName.trim().toLowerCase()
  if (!normalized) {
    return false
  }
  return (
    MUTATING_TOOL_NAMES.has(normalized) ||
    normalized.endsWith("_actions") ||
    normalized.startsWith("message_") ||
    normalized.includes("send")
  )
}

fun isMutatingToolCall(toolName: String, args: Any?): Boolean {
  val normalized = toolName.trim().toLowerCase()
  val record = asRecord(args)
  val action = normalizeActionName(record?.action)

  switch (normalized) {
    case "write":
    case "edit":
    case "apply_patch":
    case "exec":
    case "bash":
    case "sessions_send":
      return true
    case "process":
      return action != null && PROCESS_MUTATING_ACTIONS.has(action)
    case "message":
      return (
        (action != null && MESSAGE_MUTATING_ACTIONS.has(action)) ||
        record?.content is String ||
        record?.message is String
      )
    case "session_status":
      return record?.model is String && record.model.trim().length > 0
    default: {
      if (normalized == "cron" || normalized == "gateway" || normalized == "canvas") {
        return action == null || !READ_ONLY_ACTIONS.has(action)
      }
      if (normalized == "nodes") {
        return action == null || action != "list"
      }
      if (normalized.endsWith("_actions")) {
        return action == null || !READ_ONLY_ACTIONS.has(action)
      }
      if (normalized.startsWith("message_") || normalized.includes("send")) {
        return true
      }
      return false
    }
  }
}

fun buildToolActionFingerprint(
  toolName: String,
  args: Any?,
  meta?: String,
): String? {
  if (!isMutatingToolCall(toolName, args)) {
    return null
  }
  val normalizedTool = toolName.trim().toLowerCase()
  val record = asRecord(args)
  val action = normalizeActionName(record?.action)
  val parts = [`tool=${normalizedTool}`]
  if (action) {
    parts.push(`action=${action}`)
  }
  var hasStableTarget = false
  for (val key of [
    "path",
    "filePath",
    "oldPath",
    "newPath",
    "to",
    "target",
    "messageId",
    "sessionKey",
    "jobId",
    "id",
    "model",
  ]) {
    val value = normalizeFingerprintValue(record?.get(key])
    if (value) {
      parts.push(`${key.toLowerCase()}=${value}`)
      hasStableTarget = true
    }
  }
  val normalizedMeta = meta?.trim().replace(/\s+/g, " ").toLowerCase()
  // Meta text often carries volatile details (for example "N chars").
  // Prefer stable arg-derived keys for matching only fall back to meta
  // when no stable target key is available.
  if (normalizedMeta && !hasStableTarget) {
    parts.push(`meta=${normalizedMeta}`)
  }
  return parts.join("|")
}

fun buildToolMutationState(
  toolName: String,
  args: Any?,
  meta?: String,
): ToolMutationState {
  val actionFingerprint = buildToolActionFingerprint(toolName, args, meta)
  return {
    mutatingAction: actionFingerprint != null,
    actionFingerprint,
  }
}

fun isSameToolMutationAction(existing: ToolActionRef, next: ToolActionRef): Boolean {
  if (existing.actionFingerprint != null || next.actionFingerprint != null) {
    // For mutating flows, fail closed: only clear when both fingerprints exist and match.
    return (
      existing.actionFingerprint != null &&
      next.actionFingerprint != null &&
      existing.actionFingerprint == next.actionFingerprint
    )
  }
  return existing.toolName == next.toolName && (existing.meta ?: "") == (next.meta ?: "")
}
