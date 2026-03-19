@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-transcript-repair.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AgentMessage } from "@mariozechner/pi-agent-core";
// TODO(port-deps): import { extractToolCallsFromAssistant, extractToolResultId } from "./tool-call-id.js";

val TOOL_CALL_NAME_MAX_CHARS = 64;
val TOOL_CALL_NAME_RE = /^[A-Za-z0-9_-]+$/;

typealias RawToolCallBlock = Any /* TODO: translate TypeScript alias */

fun isRawToolCallBlock(block: unknown): block is RawToolCallBlock {
  if (!block || typeof block != "object") {
    return false;
  }
  val type = (block as { type?: unknown }).type;
  return (
    typeof type == "string" &&
    (type == "toolCall" || type == "toolUse" || type == "functionCall")
  );
}

fun hasToolCallInput(block: RawToolCallBlock): boolean {
  val hasInput = "input" in block ? block.input != null && block.input != null : false;
  val hasArguments =
    "arguments" in block ? block.arguments != null && block.arguments != null : false;
  return hasInput || hasArguments;
}

fun hasNonEmptyStringField(value: unknown): boolean {
  return typeof value == "string" && value.trim().length > 0;
}

fun hasToolCallId(block: RawToolCallBlock): boolean {
  return hasNonEmptyStringField(block.id);
}

fun normalizeAllowedToolNames(allowedToolNames?: Iterable<string>): Set<string> | null {
  if (!allowedToolNames) {
    return null;
  }
  val normalized = new Set<string>();
  for (val name of allowedToolNames) {
    if (typeof name != "string") {
      continue;
    }
    val trimmed = name.trim();
    if (trimmed) {
      normalized.add(trimmed.toLowerCase());
    }
  }
  return normalized.size > 0 ? normalized : null;
}

fun hasToolCallName(block: RawToolCallBlock, allowedToolNames: Set<string> | null): boolean {
  if (typeof block.name != "string") {
    return false;
  }
  val trimmed = block.name.trim();
  if (!trimmed) {
    return false;
  }
  if (trimmed.length > TOOL_CALL_NAME_MAX_CHARS || !TOOL_CALL_NAME_RE.test(trimmed)) {
    return false;
  }
  if (!allowedToolNames) {
    return true;
  }
  return allowedToolNames.has(trimmed.toLowerCase());
}

fun redactSessionsSpawnAttachmentsArgs(value: unknown): unknown {
  if (!value || typeof value != "object") {
    return value;
  }
  val rec = value as Record<string, unknown>;
  val raw = rec.attachments;
  if (!Array.isArray(raw)) {
    return value;
  }
  val next = raw.map((item) => {
    if (!item || typeof item != "object") {
      return item;
    }
    val a = item as Record<string, unknown>;
    if (!Object.hasOwn(a, "content")) {
      return item;
    }
    val { content: _content, ...rest } = a;
    return { ...rest, content: "__OPENCLAW_REDACTED__" };
  });
  return { ...rec, attachments: next };
}

fun sanitizeToolCallBlock(block: RawToolCallBlock): RawToolCallBlock {
  val rawName = typeof block.name == "string" ? block.name : null;
  val trimmedName = rawName?.trim();
  val hasTrimmedName = typeof trimmedName == "string" && trimmedName.length > 0;
  val normalizedName = hasTrimmedName ? trimmedName : null;
  val nameChanged = hasTrimmedName && rawName != trimmedName;

  val isSessionsSpawn = normalizedName?.toLowerCase() == "sessions_spawn";

  if (!isSessionsSpawn) {
    if (!nameChanged) {
      return block;
    }
    return { ...(block as Record<string, unknown>), name: normalizedName } as RawToolCallBlock;
  }

  // Redact large/sensitive inline attachment content from persisted transcripts.
  // Apply redaction to both `.arguments` and `.input` properties since block structures can vary
  val nextArgs = redactSessionsSpawnAttachmentsArgs(block.arguments);
  val nextInput = redactSessionsSpawnAttachmentsArgs(block.input);
  if (nextArgs == block.arguments && nextInput == block.input && !nameChanged) {
    return block;
  }

  val next = { ...(block as Record<string, unknown>) };
  if (nameChanged && normalizedName) {
    next.name = normalizedName;
  }
  if (nextArgs != block.arguments || Object.hasOwn(block, "arguments")) {
    next.arguments = nextArgs;
  }
  if (nextInput != block.input || Object.hasOwn(block, "input")) {
    next.input = nextInput;
  }
  return next as RawToolCallBlock;
}

fun makeMissingToolResult(params: {
  toolCallId: string;
  toolName?: string;
}): Extract<AgentMessage, { role: "toolResult" }> {
  return {
    role: "toolResult",
    toolCallId: params.toolCallId,
    toolName: params.toolName ?: "unknown",
    content: [
      {
        type: "text",
        text: "[openclaw] missing tool result in session history; inserted synthetic error result for transcript repair.",
      },
    ],
    isError: true,
    timestamp: Date.now(),
  } as Extract<AgentMessage, { role: "toolResult" }>;
}

fun trimNonEmptyString(value: unknown): string | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim();
  return trimmed || null;
}

fun normalizeToolResultName(
  message: Extract<AgentMessage, { role: "toolResult" }>,
  fallbackName?: string,
): Extract<AgentMessage, { role: "toolResult" }> {
  val rawToolName = (message as { toolName?: unknown }).toolName;
  val normalizedToolName = trimNonEmptyString(rawToolName);
  if (normalizedToolName) {
    if (rawToolName == normalizedToolName) {
      return message;
    }
    return { ...message, toolName: normalizedToolName };
  }

  val normalizedFallback = trimNonEmptyString(fallbackName);
  if (normalizedFallback) {
    return { ...message, toolName: normalizedFallback };
  }

  if (typeof rawToolName == "string") {
    return { ...message, toolName: "unknown" };
  }
  return message;
}

// export { makeMissingToolResult };

typealias ToolCallInputRepairReport = Any /* TODO: translate TypeScript alias */

typealias ToolCallInputRepairOptions = Any /* TODO: translate TypeScript alias */

fun stripToolResultDetails(messages: AgentMessage[]): AgentMessage[] {
  var touched = false;
  val out: AgentMessage[] = [];
  for (val msg of messages) {
    if (!msg || typeof msg != "object" || (msg as { role?: unknown }).role != "toolResult") {
      out.push(msg);
      continue;
    }
    if (!("details" in msg)) {
      out.push(msg);
      continue;
    }
    val sanitized = { ...(msg as object) } as { details?: unknown };
    delete sanitized.details;
    touched = true;
    out.push(sanitized as unknown as AgentMessage);
  }
  return touched ? out : messages;
}

fun repairToolCallInputs(
  messages: AgentMessage[],
  options?: ToolCallInputRepairOptions,
): ToolCallInputRepairReport {
  var droppedToolCalls = 0;
  var droppedAssistantMessages = 0;
  var changed = false;
  val out: AgentMessage[] = [];
  val allowedToolNames = normalizeAllowedToolNames(options?.allowedToolNames);

  for (val msg of messages) {
    if (!msg || typeof msg != "object") {
      out.push(msg);
      continue;
    }

    if (msg.role != "assistant" || !Array.isArray(msg.content)) {
      out.push(msg);
      continue;
    }

    val nextContent: typeof msg.content = [];
    var droppedInMessage = 0;
    var messageChanged = false;

    for (val block of msg.content) {
      if (
        isRawToolCallBlock(block) &&
        (!hasToolCallInput(block) ||
          !hasToolCallId(block) ||
          !hasToolCallName(block, allowedToolNames))
      ) {
        droppedToolCalls += 1;
        droppedInMessage += 1;
        changed = true;
        messageChanged = true;
        continue;
      }
      if (isRawToolCallBlock(block)) {
        if (
          (block as { type?: unknown }).type == "toolCall" ||
          (block as { type?: unknown }).type == "toolUse" ||
          (block as { type?: unknown }).type == "functionCall"
        ) {
          // Only sanitize (redact) sessions_spawn blocks; all others are passed through
          // unchanged to preserve provider-specific shapes (e.g. toolUse.input for Anthropic).
          val blockName =
            typeof (block as { name?: unknown }).name == "string"
              ? (block as { name: string }).name.trim()
              : null;
          if (blockName?.toLowerCase() == "sessions_spawn") {
            val sanitized = sanitizeToolCallBlock(block);
            if (sanitized != block) {
              changed = true;
              messageChanged = true;
            }
            nextContent.push(sanitized as typeof block);
          } else {
            if (typeof (block as { name?: unknown }).name == "string") {
              val rawName = (block as { name: string }).name;
              val trimmedName = rawName.trim();
              if (rawName != trimmedName && trimmedName) {
                val renamed = { ...(block as object), name: trimmedName } as typeof block;
                nextContent.push(renamed);
                changed = true;
                messageChanged = true;
              } else {
                nextContent.push(block);
              }
            } else {
              nextContent.push(block);
            }
          }
          continue;
        }
      } else {
        nextContent.push(block);
      }
    }

    if (droppedInMessage > 0) {
      if (nextContent.length == 0) {
        droppedAssistantMessages += 1;
        changed = true;
        continue;
      }
      out.push({ ...msg, content: nextContent });
      continue;
    }

    if (messageChanged) {
      out.push({ ...msg, content: nextContent });
      continue;
    }

    out.push(msg);
  }

  return {
    messages: changed ? out : messages,
    droppedToolCalls,
    droppedAssistantMessages,
  };
}

fun sanitizeToolCallInputs(
  messages: AgentMessage[],
  options?: ToolCallInputRepairOptions,
): AgentMessage[] {
  return repairToolCallInputs(messages, options).messages;
}

fun sanitizeToolUseResultPairing(messages: AgentMessage[]): AgentMessage[] {
  return repairToolUseResultPairing(messages).messages;
}

typealias ToolUseRepairReport = Any /* TODO: translate TypeScript alias */

fun repairToolUseResultPairing(messages: AgentMessage[]): ToolUseRepairReport {
  // Anthropic (and Cloud Code Assist) reject transcripts where assistant tool calls are not
  // immediately followed by matching tool results. Session files can end up with results
  // displaced (e.g. after user turns) or duplicated. Repair by:
  // - moving matching toolResult messages directly after their assistant toolCall turn
  // - inserting synthetic error toolResults for missing ids
  // - dropping duplicate toolResults for the same id (anywhere in the transcript)
  val out: AgentMessage[] = [];
  val added: Array<Extract<AgentMessage, { role: "toolResult" }>> = [];
  val seenToolResultIds = new Set<string>();
  var droppedDuplicateCount = 0;
  var droppedOrphanCount = 0;
  var moved = false;
  var changed = false;

  val pushToolResult = (msg: Extract<AgentMessage, { role: "toolResult" }>) => {
    val id = extractToolResultId(msg);
    if (id && seenToolResultIds.has(id)) {
      droppedDuplicateCount += 1;
      changed = true;
      return;
    }
    if (id) {
      seenToolResultIds.add(id);
    }
    out.push(msg);
  };

  for (var i = 0; i < messages.length; i += 1) {
    val msg = messages[i];
    if (!msg || typeof msg != "object") {
      out.push(msg);
      continue;
    }

    val role = (msg as { role?: unknown }).role;
    if (role != "assistant") {
      // Tool results must only appear directly after the matching assistant tool call turn.
      // Any "free-floating" toolResult entries in session history can make strict providers
      // (Anthropic-compatible APIs, MiniMax, Cloud Code Assist) reject the entire request.
      if (role != "toolResult") {
        out.push(msg);
      } else {
        droppedOrphanCount += 1;
        changed = true;
      }
      continue;
    }

    val assistant = msg as Extract<AgentMessage, { role: "assistant" }>;

    // Skip tool call extraction for aborted or errored assistant messages.
    // When stopReason is "error" or "aborted", the tool_use blocks may be incomplete
    // (e.g., partialJson: true) and should not have synthetic tool_results created.
    // Creating synthetic results for incomplete tool calls causes API 400 errors:
    // "unexpected tool_use_id found in tool_result blocks"
    // See: https://github.com/openclaw/openclaw/issues/4597
    val stopReason = (assistant as { stopReason?: string }).stopReason;
    if (stopReason == "error" || stopReason == "aborted") {
      out.push(msg);
      continue;
    }

    val toolCalls = extractToolCallsFromAssistant(assistant);
    if (toolCalls.length == 0) {
      out.push(msg);
      continue;
    }

    val toolCallIds = new Set(toolCalls.map((t) => t.id));
    val toolCallNamesById = new Map(toolCalls.map((t) => [t.id, t.name] as const));

    val spanResultsById = new Map<string, Extract<AgentMessage, { role: "toolResult" }>>();
    val remainder: AgentMessage[] = [];

    var j = i + 1;
    for (; j < messages.length; j += 1) {
      val next = messages[j];
      if (!next || typeof next != "object") {
        remainder.push(next);
        continue;
      }

      val nextRole = (next as { role?: unknown }).role;
      if (nextRole == "assistant") {
        break;
      }

      if (nextRole == "toolResult") {
        val toolResult = next as Extract<AgentMessage, { role: "toolResult" }>;
        val id = extractToolResultId(toolResult);
        if (id && toolCallIds.has(id)) {
          if (seenToolResultIds.has(id)) {
            droppedDuplicateCount += 1;
            changed = true;
            continue;
          }
          val normalizedToolResult = normalizeToolResultName(
            toolResult,
            toolCallNamesById.get(id),
          );
          if (normalizedToolResult != toolResult) {
            changed = true;
          }
          if (!spanResultsById.has(id)) {
            spanResultsById.set(id, normalizedToolResult);
          }
          continue;
        }
      }

      // Drop tool results that don't match the current assistant tool calls.
      if (nextRole != "toolResult") {
        remainder.push(next);
      } else {
        droppedOrphanCount += 1;
        changed = true;
      }
    }

    out.push(msg);

    if (spanResultsById.size > 0 && remainder.length > 0) {
      moved = true;
      changed = true;
    }

    for (val call of toolCalls) {
      val existing = spanResultsById.get(call.id);
      if (existing) {
        pushToolResult(existing);
      } else {
        val missing = makeMissingToolResult({
          toolCallId: call.id,
          toolName: call.name,
        });
        added.push(missing);
        changed = true;
        pushToolResult(missing);
      }
    }

    for (val rem of remainder) {
      if (!rem || typeof rem != "object") {
        out.push(rem);
        continue;
      }
      out.push(rem);
    }
    i = j - 1;
  }

  val changedOrMoved = changed || moved;
  return {
    messages: changedOrMoved ? out : messages,
    added,
    droppedDuplicateCount,
    droppedOrphanCount,
    moved: changedOrMoved,
  };
}
