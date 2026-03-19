@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-send-tool.a2a.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { formatErrorMessage } from "../../infra/errors.js";
// TODO(port-deps): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(port-deps): import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO(port-deps): import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO(port-deps): import { readLatestAssistantReply, runAgentStep } from "./agent-step.js";
// TODO(port-deps): import { resolveAnnounceTarget } from "./sessions-announce-target.js";
// TODO(port-deps): import {
// TODO(port-deps): buildAgentToAgentAnnounceContext,
// TODO(port-deps): buildAgentToAgentReplyContext,
// TODO(port-deps): isAnnounceSkip,
// TODO(port-deps): isReplySkip,
// TODO(port-deps): } from "./sessions-send-helpers.js";

val log = createSubsystemLogger("agents/sessions-send");

suspend fun runSessionsSendA2AFlow(params: {
  targetSessionKey: string;
  displayKey: string;
  message: string;
  announceTimeoutMs: number;
  maxPingPongTurns: number;
  requesterSessionKey?: string;
  requesterChannel?: GatewayMessageChannel;
  roundOneReply?: string;
  waitRunId?: string;
}) {
  val runContextId = params.waitRunId ?: "unknown";
  try {
    var primaryReply = params.roundOneReply;
    var latestReply = params.roundOneReply;
    if (!primaryReply && params.waitRunId) {
      val waitMs = Math.min(params.announceTimeoutMs, 60_000);
      val wait = await callGateway<{ status: string }>({
        method: "agent.wait",
        params: {
          runId: params.waitRunId,
          timeoutMs: waitMs,
        },
        timeoutMs: waitMs + 2000,
      });
      if (wait?.status == "ok") {
        primaryReply = await readLatestAssistantReply({
          sessionKey: params.targetSessionKey,
        });
        latestReply = primaryReply;
      }
    }
    if (!latestReply) {
      return;
    }

    val announceTarget = await resolveAnnounceTarget({
      sessionKey: params.targetSessionKey,
      displayKey: params.displayKey,
    });
    val targetChannel = announceTarget?.channel ?: "unknown";

    if (
      params.maxPingPongTurns > 0 &&
      params.requesterSessionKey &&
      params.requesterSessionKey != params.targetSessionKey
    ) {
      var currentSessionKey = params.requesterSessionKey;
      var nextSessionKey = params.targetSessionKey;
      var incomingMessage = latestReply;
      for (var turn = 1; turn <= params.maxPingPongTurns; turn += 1) {
        val currentRole =
          currentSessionKey == params.requesterSessionKey ? "requester" : "target";
        val replyPrompt = buildAgentToAgentReplyContext({
          requesterSessionKey: params.requesterSessionKey,
          requesterChannel: params.requesterChannel,
          targetSessionKey: params.displayKey,
          targetChannel,
          currentRole,
          turn,
          maxTurns: params.maxPingPongTurns,
        });
        val replyText = await runAgentStep({
          sessionKey: currentSessionKey,
          message: incomingMessage,
          extraSystemPrompt: replyPrompt,
          timeoutMs: params.announceTimeoutMs,
          lane: AGENT_LANE_NESTED,
          sourceSessionKey: nextSessionKey,
          sourceChannel:
            nextSessionKey == params.requesterSessionKey ? params.requesterChannel : targetChannel,
          sourceTool: "sessions_send",
        });
        if (!replyText || isReplySkip(replyText)) {
          break;
        }
        latestReply = replyText;
        incomingMessage = replyText;
        val swap = currentSessionKey;
        currentSessionKey = nextSessionKey;
        nextSessionKey = swap;
      }
    }

    val announcePrompt = buildAgentToAgentAnnounceContext({
      requesterSessionKey: params.requesterSessionKey,
      requesterChannel: params.requesterChannel,
      targetSessionKey: params.displayKey,
      targetChannel,
      originalMessage: params.message,
      roundOneReply: primaryReply,
      latestReply,
    });
    val announceReply = await runAgentStep({
      sessionKey: params.targetSessionKey,
      message: "Agent-to-agent announce step.",
      extraSystemPrompt: announcePrompt,
      timeoutMs: params.announceTimeoutMs,
      lane: AGENT_LANE_NESTED,
      sourceSessionKey: params.requesterSessionKey,
      sourceChannel: params.requesterChannel,
      sourceTool: "sessions_send",
    });
    if (announceTarget && announceReply && announceReply.trim() && !isAnnounceSkip(announceReply)) {
      try {
        await callGateway({
          method: "send",
          params: {
            to: announceTarget.to,
            message: announceReply.trim(),
            channel: announceTarget.channel,
            accountId: announceTarget.accountId,
            idempotencyKey: crypto.randomUUID(),
          },
          timeoutMs: 10_000,
        });
      } catch (err) {
        log.warn("sessions_send announce delivery failed", {
          runId: runContextId,
          channel: announceTarget.channel,
          to: announceTarget.to,
          error: formatErrorMessage(err),
        });
      }
    }
  } catch (err) {
    log.warn("sessions_send announce flow failed", {
      runId: runContextId,
      error: formatErrorMessage(err),
    });
  }
}
