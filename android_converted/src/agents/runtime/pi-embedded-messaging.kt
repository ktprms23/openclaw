@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-messaging.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

data class MessagingToolSend(
    val tool: String = TODO("Port default"),
    val provider: String = TODO("Port default"),
    val accountId: String? = TODO("Port default"),
    val to: String? = TODO("Port default"),
    val threadId: String? = TODO("Port default")
)

fun isMessagingTool(/* parameters preserved from TypeScript source */): Boolean {
    TODO("Port runtime function isMessagingTool from src/agents/pi-embedded-messaging.ts")
}

fun isMessagingToolSendAction(/* parameters preserved from TypeScript source */): Boolean {
    TODO("Port runtime function isMessagingToolSendAction from src/agents/pi-embedded-messaging.ts")
}

/*
Original imports retained for mapping context:
import { getChannelPlugin, normalizeChannelId } from "../channels/plugins/index.js";
*/

/*
Original TypeScript reference:
import { getChannelPlugin, normalizeChannelId } from "../channels/plugins/index.js";

export type MessagingToolSend = {
  tool: string;
  provider: string;
  accountId?: string;
  to?: string;
  threadId?: string;
};

const CORE_MESSAGING_TOOLS = new Set(["sessions_send", "message"]);

// Provider docking: any plugin with `actions` opts into messaging tool handling.
export function isMessagingTool(toolName: string): boolean {
  if (CORE_MESSAGING_TOOLS.has(toolName)) {
    return true;
  }
  const providerId = normalizeChannelId(toolName);
  return Boolean(providerId && getChannelPlugin(providerId)?.actions);
}

export function isMessagingToolSendAction(
  toolName: string,
  args: Record<string, unknown>,
): boolean {
  const action = typeof args.action === "string" ? args.action.trim() : "";
  if (toolName === "sessions_send") {
    return true;
  }
  if (toolName === "message") {
    return action === "send" || action === "thread-reply";
  }
  const providerId = normalizeChannelId(toolName);
  if (!providerId) {
    return false;
  }
  const plugin = getChannelPlugin(providerId);
  if (!plugin?.actions?.extractToolSend) {
    return false;
  }
  return Boolean(plugin.actions.extractToolSend({ args })?.to);
}

*/
