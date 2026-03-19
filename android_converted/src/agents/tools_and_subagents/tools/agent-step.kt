@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/agent-step.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { INTERNAL_MESSAGE_CHANNEL } from "../../utils/message-channel.js";
// TODO(port-deps): import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO(port-deps): import { extractAssistantText, stripToolMessages } from "./sessions-helpers.js";

suspend fun readLatestAssistantReply(params: {
  sessionKey: string;
  limit?: number;
}): Promise<string | null> {
  val history = await callGateway<{ messages: Array<unknown> }>({
    method: "chat.history",
    params: { sessionKey: params.sessionKey, limit: params.limit ?: 50 },
  });
  val filtered = stripToolMessages(Array.isArray(history?.messages) ? history.messages : []);
  for (var i = filtered.length - 1; i >= 0; i -= 1) {
    val candidate = filtered[i];
    if (!candidate || typeof candidate != "object") {
      continue;
    }
    if ((candidate as { role?: unknown }).role != "assistant") {
      continue;
    }
    val text = extractAssistantText(candidate);
    if (!text?.trim()) {
      continue;
    }
    return text;
  }
  return null;
}

suspend fun runAgentStep(params: {
  sessionKey: string;
  message: string;
  extraSystemPrompt: string;
  timeoutMs: number;
  channel?: string;
  lane?: string;
  sourceSessionKey?: string;
  sourceChannel?: string;
  sourceTool?: string;
}): Promise<string | null> {
  val stepIdem = crypto.randomUUID();
  val response = await callGateway<{ runId?: string }>({
    method: "agent",
    params: {
      message: params.message,
      sessionKey: params.sessionKey,
      idempotencyKey: stepIdem,
      deliver: false,
      channel: params.channel ?: INTERNAL_MESSAGE_CHANNEL,
      lane: params.lane ?: AGENT_LANE_NESTED,
      extraSystemPrompt: params.extraSystemPrompt,
      inputProvenance: {
        kind: "inter_session",
        sourceSessionKey: params.sourceSessionKey,
        sourceChannel: params.sourceChannel,
        sourceTool: params.sourceTool ?: "sessions_send",
      },
    },
    timeoutMs: 10_000,
  });

  val stepRunId = typeof response?.runId == "string" && response.runId ? response.runId : "";
  val resolvedRunId = stepRunId || stepIdem;
  val stepWaitMs = Math.min(params.timeoutMs, 60_000);
  val wait = await callGateway<{ status?: string }>({
    method: "agent.wait",
    params: {
      runId: resolvedRunId,
      timeoutMs: stepWaitMs,
    },
    timeoutMs: stepWaitMs + 2000,
  });
  if (wait?.status != "ok") {
    return null;
  }
  return await readLatestAssistantReply({ sessionKey: params.sessionKey });
}
