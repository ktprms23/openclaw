@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-helpers.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// export type {
// AgentToAgentPolicy,
// SessionAccessAction,
// SessionAccessResult,
// SessionToolsVisibility,
// } from "./sessions-access.js";
// export {
// createAgentToAgentPolicy,
// createSessionVisibilityGuard,
// resolveEffectiveSessionToolsVisibility,
// resolveSandboxSessionToolsVisibility,
// resolveSandboxedSessionToolContext,
// resolveSessionToolsVisibility,
// } from "./sessions-access.js";
// TODO(port-deps): import { resolveSandboxedSessionToolContext } from "./sessions-access.js";
// export type { SessionReferenceResolution } from "./sessions-resolution.js";
// export {
// isRequesterSpawnedSessionVisible,
// isResolvedSessionVisibleToRequester,
// listSpawnedSessionKeys,
// looksLikeSessionId,
// looksLikeSessionKey,
// resolveDisplaySessionKey,
// resolveInternalSessionKey,
// resolveMainSessionAlias,
// resolveSessionReference,
// resolveVisibleSessionReference,
// shouldResolveSessionIdInput,
// shouldVerifyRequesterSpawnedSessionVisibility,
// } from "./sessions-resolution.js";
// TODO(port-deps): import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO(port-deps): import { extractTextFromChatContent } from "../../shared/chat-content.js";
// TODO(port-deps): import { sanitizeUserFacingText } from "../pi-embedded-helpers.js";
// TODO(port-deps): import {
// TODO(port-deps): stripDowngradedToolCallText,
// TODO(port-deps): stripMinimaxToolCallXml,
// TODO(port-deps): stripModelSpecialTokens,
// TODO(port-deps): stripThinkingTagsFromText,
// TODO(port-deps): } from "../pi-embedded-utils.js";

typealias SessionKind = Any /* TODO: translate TypeScript alias */

typealias SessionListDeliveryContext = Any /* TODO: translate TypeScript alias */

typealias SessionListRow = Any /* TODO: translate TypeScript alias */

fun normalizeKey(value?: string) {
  val trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

fun resolveSessionToolContext(opts?: {
  agentSessionKey?: string;
  sandboxed?: boolean;
  config?: OpenClawConfig;
}) {
  val cfg = opts?.config ?: loadConfig();
  return {
    cfg,
    ...resolveSandboxedSessionToolContext({
      cfg,
      agentSessionKey: opts?.agentSessionKey,
      sandboxed: opts?.sandboxed,
    }),
  };
}

fun classifySessionKind(params: {
  key: string;
  gatewayKind?: string | null;
  alias: string;
  mainKey: string;
}): SessionKind {
  val key = params.key;
  if (key == params.alias || key == params.mainKey) {
    return "main";
  }
  if (key.startsWith("cron:")) {
    return "cron";
  }
  if (key.startsWith("hook:")) {
    return "hook";
  }
  if (key.startsWith("node-") || key.startsWith("node:")) {
    return "node";
  }
  if (params.gatewayKind == "group") {
    return "group";
  }
  if (key.includes(":group:") || key.includes(":channel:")) {
    return "group";
  }
  return "other";
}

fun deriveChannel(params: {
  key: string;
  kind: SessionKind;
  channel?: string | null;
  lastChannel?: string | null;
}): string {
  if (params.kind == "cron" || params.kind == "hook" || params.kind == "node") {
    return "internal";
  }
  val channel = normalizeKey(params.channel ?: null);
  if (channel) {
    return channel;
  }
  val lastChannel = normalizeKey(params.lastChannel ?: null);
  if (lastChannel) {
    return lastChannel;
  }
  val parts = params.key.split(":").filter(Boolean);
  if (parts.length >= 3 && (parts[1] == "group" || parts[1] == "channel")) {
    return parts[0];
  }
  return "unknown";
}

fun stripToolMessages(messages: unknown[]): unknown[] {
  return messages.filter((msg) => {
    if (!msg || typeof msg != "object") {
      return true;
    }
    val role = (msg as { role?: unknown }).role;
    return role != "toolResult" && role != "tool";
  });
}

/**
 * Sanitize text content to strip tool call markers and thinking tags.
 * This ensures user-facing text doesn't leak internal tool representations.
 */
fun sanitizeTextContent(text: string): string {
  if (!text) {
    return text;
  }
  return stripThinkingTagsFromText(
    stripDowngradedToolCallText(stripModelSpecialTokens(stripMinimaxToolCallXml(text))),
  );
}

fun extractAssistantText(message: unknown): string | null {
  if (!message || typeof message != "object") {
    return null;
  }
  if ((message as { role?: unknown }).role != "assistant") {
    return null;
  }
  val content = (message as { content?: unknown }).content;
  if (!Array.isArray(content)) {
    return null;
  }
  val joined =
    extractTextFromChatContent(content, {
      sanitizeText: sanitizeTextContent,
      joinWith: "",
      normalizeText: (text) => text.trim(),
    }) ?: "";
  val stopReason = (message as { stopReason?: unknown }).stopReason;
  // Gate on stopReason only — a non-error response with a stale/background errorMessage
  // should not have its content rewritten with error templates (#13935).
  val errorContext = stopReason == "error";

  return joined ? sanitizeUserFacingText(joined, { errorContext }) : null;
}
