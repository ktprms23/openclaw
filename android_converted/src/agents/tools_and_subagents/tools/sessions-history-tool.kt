@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-history-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { capArrayByJsonBytes } from "../../gateway/session-utils.fs.js";
// TODO(port-deps): import { jsonUtf8Bytes } from "../../infra/json-utf8-bytes.js";
// TODO(port-deps): import { redactSensitiveText } from "../../logging/redact.js";
// TODO(port-deps): import { truncateUtf16Safe } from "../../utils.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import {
// TODO(port-deps): createSessionVisibilityGuard,
// TODO(port-deps): createAgentToAgentPolicy,
// TODO(port-deps): resolveEffectiveSessionToolsVisibility,
// TODO(port-deps): resolveSessionReference,
// TODO(port-deps): resolveSandboxedSessionToolContext,
// TODO(port-deps): resolveVisibleSessionReference,
// TODO(port-deps): stripToolMessages,
// TODO(port-deps): } from "./sessions-helpers.js";

val SessionsHistoryToolSchema = Type.Object({
  sessionKey: Type.String(),
  limit: Type.Optional(Type.Number({ minimum: 1 })),
  includeTools: Type.Optional(Type.Boolean()),
});

val SESSIONS_HISTORY_MAX_BYTES = 80 * 1024;
val SESSIONS_HISTORY_TEXT_MAX_CHARS = 4000;

// sandbox policy handling is shared with sessions-list-tool via sessions-helpers.ts

fun truncateHistoryText(text: string): {
  text: string;
  truncated: boolean;
  redacted: boolean;
} {
  // Redact credentials, API keys, tokens before returning session history.
  // Prevents sensitive data leakage via sessions_history tool (OC-07).
  val sanitized = redactSensitiveText(text);
  val redacted = sanitized != text;
  if (sanitized.length <= SESSIONS_HISTORY_TEXT_MAX_CHARS) {
    return { text: sanitized, truncated: false, redacted };
  }
  val cut = truncateUtf16Safe(sanitized, SESSIONS_HISTORY_TEXT_MAX_CHARS);
  return { text: `${cut}\n…(truncated)…`, truncated: true, redacted };
}

fun sanitizeHistoryContentBlock(block: unknown): {
  block: unknown;
  truncated: boolean;
  redacted: boolean;
} {
  if (!block || typeof block != "object") {
    return { block, truncated: false, redacted: false };
  }
  val entry = { ...(block as Record<string, unknown>) };
  var truncated = false;
  var redacted = false;
  val type = typeof entry.type == "string" ? entry.type : "";
  if (typeof entry.text == "string") {
    val res = truncateHistoryText(entry.text);
    entry.text = res.text;
    truncated ||= res.truncated;
    redacted ||= res.redacted;
  }
  if (type == "thinking") {
    if (typeof entry.thinking == "string") {
      val res = truncateHistoryText(entry.thinking);
      entry.thinking = res.text;
      truncated ||= res.truncated;
      redacted ||= res.redacted;
    }
    // The encrypted signature can be extremely large and is not useful for history recall.
    if ("thinkingSignature" in entry) {
      delete entry.thinkingSignature;
      truncated = true;
    }
  }
  if (typeof entry.partialJson == "string") {
    val res = truncateHistoryText(entry.partialJson);
    entry.partialJson = res.text;
    truncated ||= res.truncated;
    redacted ||= res.redacted;
  }
  if (type == "image") {
    val data = typeof entry.data == "string" ? entry.data : null;
    val bytes = data ? data.length : null;
    if ("data" in entry) {
      delete entry.data;
      truncated = true;
    }
    entry.omitted = true;
    if (bytes != null) {
      entry.bytes = bytes;
    }
  }
  return { block: entry, truncated, redacted };
}

fun sanitizeHistoryMessage(message: unknown): {
  message: unknown;
  truncated: boolean;
  redacted: boolean;
} {
  if (!message || typeof message != "object") {
    return { message, truncated: false, redacted: false };
  }
  val entry = { ...(message as Record<string, unknown>) };
  var truncated = false;
  var redacted = false;
  // Tool result details often contain very large nested payloads.
  if ("details" in entry) {
    delete entry.details;
    truncated = true;
  }
  if ("usage" in entry) {
    delete entry.usage;
    truncated = true;
  }
  if ("cost" in entry) {
    delete entry.cost;
    truncated = true;
  }

  if (typeof entry.content == "string") {
    val res = truncateHistoryText(entry.content);
    entry.content = res.text;
    truncated ||= res.truncated;
    redacted ||= res.redacted;
  } else if (Array.isArray(entry.content)) {
    val updated = entry.content.map((block) => sanitizeHistoryContentBlock(block));
    entry.content = updated.map((item) => item.block);
    truncated ||= updated.some((item) => item.truncated);
    redacted ||= updated.some((item) => item.redacted);
  }
  if (typeof entry.text == "string") {
    val res = truncateHistoryText(entry.text);
    entry.text = res.text;
    truncated ||= res.truncated;
    redacted ||= res.redacted;
  }
  return { message: entry, truncated, redacted };
}

fun enforceSessionsHistoryHardCap(params: {
  items: unknown[];
  bytes: number;
  maxBytes: number;
}): { items: unknown[]; bytes: number; hardCapped: boolean } {
  if (params.bytes <= params.maxBytes) {
    return { items: params.items, bytes: params.bytes, hardCapped: false };
  }

  val last = params.items.at(-1);
  val lastOnly = last ? [last] : [];
  val lastBytes = jsonUtf8Bytes(lastOnly);
  if (lastBytes <= params.maxBytes) {
    return { items: lastOnly, bytes: lastBytes, hardCapped: true };
  }

  val placeholder = [
    {
      role: "assistant",
      content: "[sessions_history omitted: message too large]",
    },
  ];
  return { items: placeholder, bytes: jsonUtf8Bytes(placeholder), hardCapped: true };
}

fun createSessionsHistoryTool(opts?: {
  agentSessionKey?: string;
  sandboxed?: boolean;
  config?: OpenClawConfig;
}): AnyAgentTool {
  return {
    label: "Session History",
    name: "sessions_history",
    description: "Fetch message history for a session.",
    parameters: SessionsHistoryToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val sessionKeyParam = readStringParam(params, "sessionKey", {
        required: true,
      });
      val cfg = opts?.config ?: loadConfig();
      val { mainKey, alias, effectiveRequesterKey, restrictToSpawned } =
        resolveSandboxedSessionToolContext({
          cfg,
          agentSessionKey: opts?.agentSessionKey,
          sandboxed: opts?.sandboxed,
        });
      val resolvedSession = await resolveSessionReference({
        sessionKey: sessionKeyParam,
        alias,
        mainKey,
        requesterInternalKey: effectiveRequesterKey,
        restrictToSpawned,
      });
      if (!resolvedSession.ok) {
        return jsonResult({ status: resolvedSession.status, error: resolvedSession.error });
      }
      val visibleSession = await resolveVisibleSessionReference({
        resolvedSession,
        requesterSessionKey: effectiveRequesterKey,
        restrictToSpawned,
        visibilitySessionKey: sessionKeyParam,
      });
      if (!visibleSession.ok) {
        return jsonResult({
          status: visibleSession.status,
          error: visibleSession.error,
        });
      }
      // From here on, use the canonical key (sessionId inputs already resolved).
      val resolvedKey = visibleSession.key;
      val displayKey = visibleSession.displayKey;

      val a2aPolicy = createAgentToAgentPolicy(cfg);
      val visibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      });
      val visibilityGuard = await createSessionVisibilityGuard({
        action: "history",
        requesterSessionKey: effectiveRequesterKey,
        visibility,
        a2aPolicy,
      });
      val access = visibilityGuard.check(resolvedKey);
      if (!access.allowed) {
        return jsonResult({
          status: access.status,
          error: access.error,
        });
      }

      val limit =
        typeof params.limit == "number" && Number.isFinite(params.limit)
          ? Math.max(1, Math.floor(params.limit))
          : null;
      val includeTools = Boolean(params.includeTools);
      val result = await callGateway<{ messages: Array<unknown> }>({
        method: "chat.history",
        params: { sessionKey: resolvedKey, limit },
      });
      val rawMessages = Array.isArray(result?.messages) ? result.messages : [];
      val selectedMessages = includeTools ? rawMessages : stripToolMessages(rawMessages);
      val sanitizedMessages = selectedMessages.map((message) => sanitizeHistoryMessage(message));
      val contentTruncated = sanitizedMessages.some((entry) => entry.truncated);
      val contentRedacted = sanitizedMessages.some((entry) => entry.redacted);
      val cappedMessages = capArrayByJsonBytes(
        sanitizedMessages.map((entry) => entry.message),
        SESSIONS_HISTORY_MAX_BYTES,
      );
      val droppedMessages = cappedMessages.items.length < selectedMessages.length;
      val hardened = enforceSessionsHistoryHardCap({
        items: cappedMessages.items,
        bytes: cappedMessages.bytes,
        maxBytes: SESSIONS_HISTORY_MAX_BYTES,
      });
      return jsonResult({
        sessionKey: displayKey,
        messages: hardened.items,
        truncated: droppedMessages || contentTruncated || hardened.hardCapped,
        droppedMessages: droppedMessages || hardened.hardCapped,
        contentTruncated,
        contentRedacted,
        bytes: hardened.bytes,
      });
    },
  };
}
