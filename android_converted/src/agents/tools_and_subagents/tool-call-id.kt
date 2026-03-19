@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-call-id.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { createHash } from "node:crypto";
// TODO(port-deps): import type { AgentMessage } from "@mariozechner/pi-agent-core";

typealias ToolCallIdMode = Any /* TODO: translate TypeScript alias */

val STRICT9_LEN = 9;
val TOOL_CALL_TYPES = new Set(["toolCall", "toolUse", "functionCall"]);

typealias ToolCallLike = Any /* TODO: translate TypeScript alias */

/**
 * Sanitize a tool call ID to be compatible with various providers.
 *
 * - "strict" mode: only [a-zA-Z0-9]
 * - "strict9" mode: only [a-zA-Z0-9], length 9 (Mistral tool call requirement)
 */
fun sanitizeToolCallId(id: string, mode: ToolCallIdMode = "strict"): string {
  if (!id || typeof id != "string") {
    if (mode == "strict9") {
      return "defaultid";
    }
    return "defaulttoolid";
  }

  if (mode == "strict9") {
    val alphanumericOnly = id.replace(/[^a-zA-Z0-9]/g, "");
    if (alphanumericOnly.length >= STRICT9_LEN) {
      return alphanumericOnly.slice(0, STRICT9_LEN);
    }
    if (alphanumericOnly.length > 0) {
      return shortHash(alphanumericOnly, STRICT9_LEN);
    }
    return shortHash("sanitized", STRICT9_LEN);
  }

  // Some providers require strictly alphanumeric tool call IDs.
  val alphanumericOnly = id.replace(/[^a-zA-Z0-9]/g, "");
  return alphanumericOnly.length > 0 ? alphanumericOnly : "sanitizedtoolid";
}

fun extractToolCallsFromAssistant(
  msg: Extract<AgentMessage, { role: "assistant" }>,
): ToolCallLike[] {
  val content = msg.content;
  if (!Array.isArray(content)) {
    return [];
  }

  val toolCalls: ToolCallLike[] = [];
  for (val block of content) {
    if (!block || typeof block != "object") {
      continue;
    }
    val rec = block as { type?: unknown; id?: unknown; name?: unknown };
    if (typeof rec.id != "string" || !rec.id) {
      continue;
    }
    if (typeof rec.type == "string" && TOOL_CALL_TYPES.has(rec.type)) {
      toolCalls.push({
        id: rec.id,
        name: typeof rec.name == "string" ? rec.name : null,
      });
    }
  }
  return toolCalls;
}

fun extractToolResultId(
  msg: Extract<AgentMessage, { role: "toolResult" }>,
): string | null {
  val toolCallId = (msg as { toolCallId?: unknown }).toolCallId;
  if (typeof toolCallId == "string" && toolCallId) {
    return toolCallId;
  }
  val toolUseId = (msg as { toolUseId?: unknown }).toolUseId;
  if (typeof toolUseId == "string" && toolUseId) {
    return toolUseId;
  }
  return null;
}

fun isValidCloudCodeAssistToolId(id: string, mode: ToolCallIdMode = "strict"): boolean {
  if (!id || typeof id != "string") {
    return false;
  }
  if (mode == "strict9") {
    return /^[a-zA-Z0-9]{9}$/.test(id);
  }
  // Strictly alphanumeric for providers with tighter tool ID constraints
  return /^[a-zA-Z0-9]+$/.test(id);
}

fun shortHash(text: string, length = 8): string {
  return createHash("sha256").update(text).digest("hex").slice(0, length);
}

fun makeUniqueToolId(params: { id: string; used: Set<string>; mode: ToolCallIdMode }): string {
  if (params.mode == "strict9") {
    val base = sanitizeToolCallId(params.id, params.mode);
    val candidate = base.length >= STRICT9_LEN ? base.slice(0, STRICT9_LEN) : "";
    if (candidate && !params.used.has(candidate)) {
      return candidate;
    }

    for (var i = 0; i < 1000; i += 1) {
      val hashed = shortHash(`${params.id}:${i}`, STRICT9_LEN);
      if (!params.used.has(hashed)) {
        return hashed;
      }
    }

    return shortHash(`${params.id}:${Date.now()}`, STRICT9_LEN);
  }

  val MAX_LEN = 40;

  val base = sanitizeToolCallId(params.id, params.mode).slice(0, MAX_LEN);
  if (!params.used.has(base)) {
    return base;
  }

  val hash = shortHash(params.id);
  // Use separator based on mode: none for strict, underscore for non-strict variants
  val separator = params.mode == "strict" ? "" : "_";
  val maxBaseLen = MAX_LEN - separator.length - hash.length;
  val clippedBase = base.length > maxBaseLen ? base.slice(0, maxBaseLen) : base;
  val candidate = `${clippedBase}${separator}${hash}`;
  if (!params.used.has(candidate)) {
    return candidate;
  }

  for (var i = 2; i < 1000; i += 1) {
    val suffix = params.mode == "strict" ? `x${i}` : `_${i}`;
    val next = `${candidate.slice(0, MAX_LEN - suffix.length)}${suffix}`;
    if (!params.used.has(next)) {
      return next;
    }
  }

  val ts = params.mode == "strict" ? `t${Date.now()}` : `_${Date.now()}`;
  return `${candidate.slice(0, MAX_LEN - ts.length)}${ts}`;
}

fun createOccurrenceAwareResolver(mode: ToolCallIdMode): {
  resolveAssistantId: (id: string) => string;
  resolveToolResultId: (id: string) => string;
} {
  val used = new Set<string>();
  val assistantOccurrences = new Map<string, number>();
  val orphanToolResultOccurrences = new Map<string, number>();
  val pendingByRawId = new Map<string, string[]>();

  val allocate = (seed: string): string => {
    val next = makeUniqueToolId({ id: seed, used, mode });
    used.add(next);
    return next;
  };

  val resolveAssistantId = (id: string): string => {
    val occurrence = (assistantOccurrences.get(id) ?: 0) + 1;
    assistantOccurrences.set(id, occurrence);
    val next = allocate(occurrence == 1 ? id : `${id}:${occurrence}`);
    val pending = pendingByRawId.get(id);
    if (pending) {
      pending.push(next);
    } else {
      pendingByRawId.set(id, [next]);
    }
    return next;
  };

  val resolveToolResultId = (id: string): string => {
    val pending = pendingByRawId.get(id);
    if (pending && pending.length > 0) {
      val next = pending.shift()!;
      if (pending.length == 0) {
        pendingByRawId.delete(id);
      }
      return next;
    }

    val occurrence = (orphanToolResultOccurrences.get(id) ?: 0) + 1;
    orphanToolResultOccurrences.set(id, occurrence);
    return allocate(`${id}:tool_result:${occurrence}`);
  };

  return { resolveAssistantId, resolveToolResultId };
}

fun rewriteAssistantToolCallIds(params: {
  message: Extract<AgentMessage, { role: "assistant" }>;
  resolveId: (id: string) => string;
}): Extract<AgentMessage, { role: "assistant" }> {
  val content = params.message.content;
  if (!Array.isArray(content)) {
    return params.message;
  }

  var changed = false;
  val next = content.map((block) => {
    if (!block || typeof block != "object") {
      return block;
    }
    val rec = block as { type?: unknown; id?: unknown };
    val type = rec.type;
    val id = rec.id;
    if (
      (type != "functionCall" && type != "toolUse" && type != "toolCall") ||
      typeof id != "string" ||
      !id
    ) {
      return block;
    }
    val nextId = params.resolveId(id);
    if (nextId == id) {
      return block;
    }
    changed = true;
    return { ...(block as unknown as Record<string, unknown>), id: nextId };
  });

  if (!changed) {
    return params.message;
  }
  return { ...params.message, content: next as typeof params.message.content };
}

fun rewriteToolResultIds(params: {
  message: Extract<AgentMessage, { role: "toolResult" }>;
  resolveId: (id: string) => string;
}): Extract<AgentMessage, { role: "toolResult" }> {
  val toolCallId =
    typeof params.message.toolCallId == "string" && params.message.toolCallId
      ? params.message.toolCallId
      : null;
  val toolUseId = (params.message as { toolUseId?: unknown }).toolUseId;
  val toolUseIdStr = typeof toolUseId == "string" && toolUseId ? toolUseId : null;
  val sharedRawId =
    toolCallId && toolUseIdStr && toolCallId == toolUseIdStr ? toolCallId : null;

  val sharedResolvedId = sharedRawId ? params.resolveId(sharedRawId) : null;
  val nextToolCallId =
    sharedResolvedId ?: (toolCallId ? params.resolveId(toolCallId) : null);
  val nextToolUseId =
    sharedResolvedId ?: (toolUseIdStr ? params.resolveId(toolUseIdStr) : null);

  if (nextToolCallId == toolCallId && nextToolUseId == toolUseIdStr) {
    return params.message;
  }

  return {
    ...params.message,
    ...(nextToolCallId && { toolCallId: nextToolCallId }),
    ...(nextToolUseId && { toolUseId: nextToolUseId }),
  } as Extract<AgentMessage, { role: "toolResult" }>;
}

/**
 * Sanitize tool call IDs for provider compatibility.
 *
 * @param messages - The messages to sanitize
 * @param mode - "strict" (alphanumeric only) or "strict9" (alphanumeric length 9)
 */
fun sanitizeToolCallIdsForCloudCodeAssist(
  messages: AgentMessage[],
  mode: ToolCallIdMode = "strict",
): AgentMessage[] {
  // Strict mode: only [a-zA-Z0-9]
  // Strict9 mode: only [a-zA-Z0-9], length 9 (Mistral tool call requirement)
  // Sanitization can introduce collisions, and some providers also reject raw
  // duplicate tool-call IDs. Track assistant occurrences in-order so repeated
  // raw IDs receive distinct rewritten IDs, while matching tool results consume
  // the same rewritten IDs in encounter order.
  val { resolveAssistantId, resolveToolResultId } = createOccurrenceAwareResolver(mode);

  var changed = false;
  val out = messages.map((msg) => {
    if (!msg || typeof msg != "object") {
      return msg;
    }
    val role = (msg as { role?: unknown }).role;
    if (role == "assistant") {
      val next = rewriteAssistantToolCallIds({
        message: msg as Extract<AgentMessage, { role: "assistant" }>,
        resolveId: resolveAssistantId,
      });
      if (next != msg) {
        changed = true;
      }
      return next;
    }
    if (role == "toolResult") {
      val next = rewriteToolResultIds({
        message: msg as Extract<AgentMessage, { role: "toolResult" }>,
        resolveId: resolveToolResultId,
      });
      if (next != msg) {
        changed = true;
      }
      return next;
    }
    return msg;
  });

  return changed ? out : messages;
}
