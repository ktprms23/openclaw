package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-send-tool.a2a.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { formatErrorMessage } from "../../infra/errors.js";
// TODO: TypeScript import retained for manual wiring: import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO: TypeScript import retained for manual wiring: import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO: TypeScript import retained for manual wiring: import { readLatestAssistantReply, runAgentStep } from "./agent-step.js";
// TODO: TypeScript import retained for manual wiring: import { resolveAnnounceTarget } from "./sessions-announce-target.js";
// TODO: TypeScript import retained for manual wiring: import {
  buildAgentToAgentAnnounceContext,
  buildAgentToAgentReplyContext,
  isAnnounceSkip,
  isReplySkip,
} from "./sessions-send-helpers.js"

val log = createSubsystemLogger("agents/sessions-send")

suspend fun runSessionsSendA2AFlow(params: {
  targetSessionKey: String
  displayKey: String
  message: String
  announceTimeoutMs: Double
  maxPingPongTurns: Double
  requesterSessionKey?: String
  requesterChannel?: GatewayMessageChannel
  roundOneReply?: String
  waitRunId?: String
}) {
  val runContextId = params.waitRunId ?: String /* "Any?" */
  try {
    var primaryReply = params.roundOneReply
    var latestReply = params.roundOneReply
    if (!primaryReply && params.waitRunId) {
      val waitMs = Math.min(params.announceTimeoutMs, 60_000)
      val wait = await callGateway<{ status: String }>({
        method: String /* "agent.wait" */,
        params: {
          runId: params.waitRunId,
          timeoutMs: waitMs,
        },
        timeoutMs: waitMs + 2000,
      })
      if (wait?.status == "ok") {
        primaryReply = await readLatestAssistantReply({
          sessionKey: params.targetSessionKey,
        })
        latestReply = primaryReply
      }
    }
    if (!latestReply) {
      return
    }

    val announceTarget = await resolveAnnounceTarget({
      sessionKey: params.targetSessionKey,
      displayKey: params.displayKey,
    })
    val targetChannel = announceTarget?.channel ?: String /* "Any?" */

    if (
      params.maxPingPongTurns > 0 &&
      params.requesterSessionKey &&
      params.requesterSessionKey != params.targetSessionKey
    ) {
      var currentSessionKey = params.requesterSessionKey
      var nextSessionKey = params.targetSessionKey
      var incomingMessage = latestReply
      for (var turn = 1 turn <= params.maxPingPongTurns turn += 1) {
        val currentRole =
          currentSessionKey == params.requesterSessionKey ? "requester" : String /* "target" */
        val replyPrompt = buildAgentToAgentReplyContext({
          requesterSessionKey: params.requesterSessionKey,
          requesterChannel: params.requesterChannel,
          targetSessionKey: params.displayKey,
          targetChannel,
          currentRole,
          turn,
          maxTurns: params.maxPingPongTurns,
        })
        val replyText = await runAgentStep({
          sessionKey: currentSessionKey,
          message: incomingMessage,
          extraSystemPrompt: replyPrompt,
          timeoutMs: params.announceTimeoutMs,
          lane: AGENT_LANE_NESTED,
          sourceSessionKey: nextSessionKey,
          sourceChannel:
            nextSessionKey == params.requesterSessionKey ? params.requesterChannel : targetChannel,
          sourceTool: String /* "sessions_send" */,
        })
        if (!replyText || isReplySkip(replyText)) {
          break
        }
        latestReply = replyText
        incomingMessage = replyText
        val swap = currentSessionKey
        currentSessionKey = nextSessionKey
        nextSessionKey = swap
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
    })
    val announceReply = await runAgentStep({
      sessionKey: params.targetSessionKey,
      message: String /* "Agent-to-agent announce step." */,
      extraSystemPrompt: announcePrompt,
      timeoutMs: params.announceTimeoutMs,
      lane: AGENT_LANE_NESTED,
      sourceSessionKey: params.requesterSessionKey,
      sourceChannel: params.requesterChannel,
      sourceTool: String /* "sessions_send" */,
    })
    if (announceTarget && announceReply && announceReply.trim() && !isAnnounceSkip(announceReply)) {
      try {
        await callGateway({
          method: String /* "send" */,
          params: {
            to: announceTarget.to,
            message: announceReply.trim(),
            channel: announceTarget.channel,
            accountId: announceTarget.accountId,
            idempotencyKey: crypto.randomUUID(),
          },
          timeoutMs: 10_000,
        })
      } catch (err) {
        log.warn("sessions_send announce delivery failed", {
          runId: runContextId,
          channel: announceTarget.channel,
          to: announceTarget.to,
          error: formatErrorMessage(err),
        })
      }
    }
  } catch (err) {
    log.warn("sessions_send announce flow failed", {
      runId: runContextId,
      error: formatErrorMessage(err),
    })
  }
}
