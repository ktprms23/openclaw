package agents.tools_and_subagents.tools

// Converted from src/agents/tools/agent-step.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { INTERNAL_MESSAGE_CHANNEL } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO: TypeScript import retained for manual wiring: import { extractAssistantText, stripToolMessages } from "./sessions-helpers.js";

suspend fun readLatestAssistantReply(params: {
  sessionKey: String
  limit?: Double
}): Deferred<String?> {
  val history = await callGateway<{ messages: List<Any?> }>({
    method: String /* "chat.history" */,
    params: { sessionKey: params.sessionKey, limit: params.limit ?: 50 },
  })
  val filtered = stripToolMessages(Array.isArray(history?.messages) ? history.messages : [])
  for (var i = filtered.length - 1 i >= 0 i -= 1) {
    val candidate = filtered[i]
    if (!candidate || typeof candidate != "object") {
      continue
    }
    if ((candidate as /* TODO */ { role?: Any? }).role != "assistant") {
      continue
    }
    val text = extractAssistantText(candidate)
    if (!text?.trim()) {
      continue
    }
    return text
  }
  return null
}

suspend fun runAgentStep(params: {
  sessionKey: String
  message: String
  extraSystemPrompt: String
  timeoutMs: Double
  channel?: String
  lane?: String
  sourceSessionKey?: String
  sourceChannel?: String
  sourceTool?: String
}): Deferred<String?> {
  val stepIdem = crypto.randomUUID()
  val response = await callGateway<{ runId?: String }>({
    method: String /* "agent" */,
    params: {
      message: params.message,
      sessionKey: params.sessionKey,
      idempotencyKey: stepIdem,
      deliver: false,
      channel: params.channel ?: INTERNAL_MESSAGE_CHANNEL,
      lane: params.lane ?: AGENT_LANE_NESTED,
      extraSystemPrompt: params.extraSystemPrompt,
      inputProvenance: {
        kind: String /* "inter_session" */,
        sourceSessionKey: params.sourceSessionKey,
        sourceChannel: params.sourceChannel,
        sourceTool: params.sourceTool ?: String /* "sessions_send" */,
      },
    },
    timeoutMs: 10_000,
  })

  val stepRunId = response?.runId is String && response.runId ? response.runId : ""
  val resolvedRunId = stepRunId || stepIdem
  val stepWaitMs = Math.min(params.timeoutMs, 60_000)
  val wait = await callGateway<{ status?: String }>({
    method: String /* "agent.wait" */,
    params: {
      runId: resolvedRunId,
      timeoutMs: stepWaitMs,
    },
    timeoutMs: stepWaitMs + 2000,
  })
  if (wait?.status != "ok") {
    return null
  }
  return await readLatestAssistantReply({ sessionKey: params.sessionKey })
}
