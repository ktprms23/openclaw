@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/message-action-discovery-input.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun buildEmbeddedMessageActionDiscoveryInput(/* parameters preserved from TypeScript source */): Unit {
    TODO("Port runtime function buildEmbeddedMessageActionDiscoveryInput from src/agents/pi-embedded-runner/message-action-discovery-input.ts")
}

/*
Original imports retained for mapping context:
import type { OpenClawConfig } from "../../config/config.js";
*/

/*
Original TypeScript reference:
import type { OpenClawConfig } from "../../config/config.js";

export function buildEmbeddedMessageActionDiscoveryInput(params: {
  cfg?: OpenClawConfig;
  channel: string;
  currentChannelId?: string | null;
  currentThreadTs?: string | null;
  currentMessageId?: string | number | null;
  accountId?: string | null;
  sessionKey?: string | null;
  sessionId?: string | null;
  agentId?: string | null;
  senderId?: string | null;
}) {
  return {
    cfg: params.cfg,
    channel: params.channel,
    currentChannelId: params.currentChannelId ?? undefined,
    currentThreadTs: params.currentThreadTs ?? undefined,
    currentMessageId: params.currentMessageId ?? undefined,
    accountId: params.accountId ?? undefined,
    sessionKey: params.sessionKey ?? undefined,
    sessionId: params.sessionId ?? undefined,
    agentId: params.agentId ?? undefined,
    requesterSenderId: params.senderId ?? undefined,
  };
}

*/
