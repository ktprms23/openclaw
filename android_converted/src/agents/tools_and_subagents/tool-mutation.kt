@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-mutation.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

val MUTATING_TOOL_NAMES = new Set([
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
]);

val READ_ONLY_ACTIONS = new Set([
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
]);

val PROCESS_MUTATING_ACTIONS = new Set(["write", "send_keys", "submit", "paste", "kill"]);

val MESSAGE_MUTATING_ACTIONS = new Set([
  "send",
  "reply",
  "thread_reply",
  "threadreply",
  "edit",
  "delete",
  "react",
  "pin",
  "unpin",
]);

typealias ToolMutationState = Any /* TODO: translate TypeScript alias */

typealias ToolActionRef = Any /* TODO: translate TypeScript alias */

fun asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value == "object" ? (value as Record<string, unknown>) : null;
}

fun normalizeActionName(value: unknown): string | null {
  if (typeof value != "string") {
    return null;
  }
  val normalized = value
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, "_");
  return normalized || null;
}

fun normalizeFingerprintValue(value: unknown): string | null {
  if (typeof value == "string") {
    val normalized = value.trim();
    return normalized ? normalized.toLowerCase() : null;
  }
  if (typeof value == "number" || typeof value == "bigint" || typeof value == "boolean") {
    return String(value).toLowerCase();
  }
  return null;
}

fun isLikelyMutatingToolName(toolName: string): boolean {
  val normalized = toolName.trim().toLowerCase();
  if (!normalized) {
    return false;
  }
  return (
    MUTATING_TOOL_NAMES.has(normalized) ||
    normalized.endsWith("_actions") ||
    normalized.startsWith("message_") ||
    normalized.includes("send")
  );
}

fun isMutatingToolCall(toolName: string, args: unknown): boolean {
  val normalized = toolName.trim().toLowerCase();
  val record = asRecord(args);
  val action = normalizeActionName(record?.action);

  switch (normalized) {
    case "write":
    case "edit":
    case "apply_patch":
    case "exec":
    case "bash":
    case "sessions_send":
      return true;
    case "process":
      return action != null && PROCESS_MUTATING_ACTIONS.has(action);
    case "message":
      return (
        (action != null && MESSAGE_MUTATING_ACTIONS.has(action)) ||
        typeof record?.content == "string" ||
        typeof record?.message == "string"
      );
    case "session_status":
      return typeof record?.model == "string" && record.model.trim().length > 0;
    default: {
      if (normalized == "cron" || normalized == "gateway" || normalized == "canvas") {
        return action == null || !READ_ONLY_ACTIONS.has(action);
      }
      if (normalized == "nodes") {
        return action == null || action != "list";
      }
      if (normalized.endsWith("_actions")) {
        return action == null || !READ_ONLY_ACTIONS.has(action);
      }
      if (normalized.startsWith("message_") || normalized.includes("send")) {
        return true;
      }
      return false;
    }
  }
}

fun buildToolActionFingerprint(
  toolName: string,
  args: unknown,
  meta?: string,
): string | null {
  if (!isMutatingToolCall(toolName, args)) {
    return null;
  }
  val normalizedTool = toolName.trim().toLowerCase();
  val record = asRecord(args);
  val action = normalizeActionName(record?.action);
  val parts = [`tool=${normalizedTool}`];
  if (action) {
    parts.push(`action=${action}`);
  }
  var hasStableTarget = false;
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
    val value = normalizeFingerprintValue(record?.[key]);
    if (value) {
      parts.push(`${key.toLowerCase()}=${value}`);
      hasStableTarget = true;
    }
  }
  val normalizedMeta = meta?.trim().replace(/\s+/g, " ").toLowerCase();
  // Meta text often carries volatile details (for example "N chars").
  // Prefer stable arg-derived keys for matching; only fall back to meta
  // when no stable target key is available.
  if (normalizedMeta && !hasStableTarget) {
    parts.push(`meta=${normalizedMeta}`);
  }
  return parts.join("|");
}

fun buildToolMutationState(
  toolName: string,
  args: unknown,
  meta?: string,
): ToolMutationState {
  val actionFingerprint = buildToolActionFingerprint(toolName, args, meta);
  return {
    mutatingAction: actionFingerprint != null,
    actionFingerprint,
  };
}

fun isSameToolMutationAction(existing: ToolActionRef, next: ToolActionRef): boolean {
  if (existing.actionFingerprint != null || next.actionFingerprint != null) {
    // For mutating flows, fail closed: only clear when both fingerprints exist and match.
    return (
      existing.actionFingerprint != null &&
      next.actionFingerprint != null &&
      existing.actionFingerprint == next.actionFingerprint
    );
  }
  return existing.toolName == next.toolName && (existing.meta ?: "") == (next.meta ?: "");
}
