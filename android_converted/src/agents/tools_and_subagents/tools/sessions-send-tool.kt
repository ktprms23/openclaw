@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-send-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { normalizeAgentId, resolveAgentIdFromSessionKey } from "../../routing/session-key.js";
// TODO(port-deps): import { SESSION_LABEL_MAX_LENGTH } from "../../sessions/session-label.js";
// TODO(port-deps): import {
// TODO(port-deps): type GatewayMessageChannel,
// TODO(port-deps): INTERNAL_MESSAGE_CHANNEL,
// TODO(port-deps): } from "../../utils/message-channel.js";
// TODO(port-deps): import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import {
// TODO(port-deps): createSessionVisibilityGuard,
// TODO(port-deps): createAgentToAgentPolicy,
// TODO(port-deps): extractAssistantText,
// TODO(port-deps): resolveEffectiveSessionToolsVisibility,
// TODO(port-deps): resolveSessionReference,
// TODO(port-deps): resolveSessionToolContext,
// TODO(port-deps): resolveVisibleSessionReference,
// TODO(port-deps): stripToolMessages,
// TODO(port-deps): } from "./sessions-helpers.js";
// TODO(port-deps): import { buildAgentToAgentMessageContext, resolvePingPongTurns } from "./sessions-send-helpers.js";
// TODO(port-deps): import { runSessionsSendA2AFlow } from "./sessions-send-tool.a2a.js";

val SessionsSendToolSchema = Type.Object({
  sessionKey: Type.Optional(Type.String()),
  label: Type.Optional(Type.String({ minLength: 1, maxLength: SESSION_LABEL_MAX_LENGTH })),
  agentId: Type.Optional(Type.String({ minLength: 1, maxLength: 64 })),
  message: Type.String(),
  timeoutSeconds: Type.Optional(Type.Number({ minimum: 0 })),
});

suspend fun startAgentRun(params: {
  runId: string;
  sendParams: Record<string, unknown>;
  sessionKey: string;
}): Promise<{ ok: true; runId: string } | { ok: false; result: ReturnType<typeof jsonResult> }> {
  try {
    val response = await callGateway<{ runId: string }>({
      method: "agent",
      params: params.sendParams,
      timeoutMs: 10_000,
    });
    return {
      ok: true,
      runId: typeof response?.runId == "string" && response.runId ? response.runId : params.runId,
    };
  } catch (err) {
    val messageText =
      err instanceof Error ? err.message : typeof err == "string" ? err : "error";
    return {
      ok: false,
      result: jsonResult({
        runId: params.runId,
        status: "error",
        error: messageText,
        sessionKey: params.sessionKey,
      }),
    };
  }
}

fun createSessionsSendTool(opts?: {
  agentSessionKey?: string;
  agentChannel?: GatewayMessageChannel;
  sandboxed?: boolean;
  config?: OpenClawConfig;
}): AnyAgentTool {
  return {
    label: "Session Send",
    name: "sessions_send",
    description:
      "Send a message into another session. Use sessionKey or label to identify the target.",
    parameters: SessionsSendToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val message = readStringParam(params, "message", { required: true });
      val { cfg, mainKey, alias, effectiveRequesterKey, restrictToSpawned } =
        resolveSessionToolContext(opts);

      val a2aPolicy = createAgentToAgentPolicy(cfg);
      val sessionVisibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      });

      val sessionKeyParam = readStringParam(params, "sessionKey");
      val labelParam = readStringParam(params, "label")?.trim() || null;
      val labelAgentIdParam = readStringParam(params, "agentId")?.trim() || null;
      if (sessionKeyParam && labelParam) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: "error",
          error: "Provide either sessionKey or label (not both).",
        });
      }

      var sessionKey = sessionKeyParam;
      if (!sessionKey && labelParam) {
        val requesterAgentId = resolveAgentIdFromSessionKey(effectiveRequesterKey);
        val requestedAgentId = labelAgentIdParam
          ? normalizeAgentId(labelAgentIdParam)
          : null;

        if (restrictToSpawned && requestedAgentId && requestedAgentId != requesterAgentId) {
          return jsonResult({
            runId: crypto.randomUUID(),
            status: "forbidden",
            error: "Sandboxed sessions_send label lookup is limited to this agent",
          });
        }

        if (requesterAgentId && requestedAgentId && requestedAgentId != requesterAgentId) {
          if (!a2aPolicy.enabled) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: "forbidden",
              error:
                "Agent-to-agent messaging is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent sends.",
            });
          }
          if (!a2aPolicy.isAllowed(requesterAgentId, requestedAgentId)) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: "forbidden",
              error: "Agent-to-agent messaging denied by tools.agentToAgent.allow.",
            });
          }
        }

        val resolveParams: Record<string, unknown> = {
          label: labelParam,
          ...(requestedAgentId ? { agentId: requestedAgentId } : {}),
          ...(restrictToSpawned ? { spawnedBy: effectiveRequesterKey } : {}),
        };
        var resolvedKey = "";
        try {
          val resolved = await callGateway<{ key: string }>({
            method: "sessions.resolve",
            params: resolveParams,
            timeoutMs: 10_000,
          });
          resolvedKey = typeof resolved?.key == "string" ? resolved.key.trim() : "";
        } catch (err) {
          val msg = err instanceof Error ? err.message : String(err);
          if (restrictToSpawned) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: "forbidden",
              error: "Session not visible from this sandboxed agent session.",
            });
          }
          return jsonResult({
            runId: crypto.randomUUID(),
            status: "error",
            error: msg || `No session found with label: ${labelParam}`,
          });
        }

        if (!resolvedKey) {
          if (restrictToSpawned) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: "forbidden",
              error: "Session not visible from this sandboxed agent session.",
            });
          }
          return jsonResult({
            runId: crypto.randomUUID(),
            status: "error",
            error: `No session found with label: ${labelParam}`,
          });
        }
        sessionKey = resolvedKey;
      }

      if (!sessionKey) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: "error",
          error: "Either sessionKey or label is required",
        });
      }
      val resolvedSession = await resolveSessionReference({
        sessionKey,
        alias,
        mainKey,
        requesterInternalKey: effectiveRequesterKey,
        restrictToSpawned,
      });
      if (!resolvedSession.ok) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: resolvedSession.status,
          error: resolvedSession.error,
        });
      }
      val visibleSession = await resolveVisibleSessionReference({
        resolvedSession,
        requesterSessionKey: effectiveRequesterKey,
        restrictToSpawned,
        visibilitySessionKey: sessionKey,
      });
      if (!visibleSession.ok) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: visibleSession.status,
          error: visibleSession.error,
          sessionKey: visibleSession.displayKey,
        });
      }
      // Normalize sessionKey/sessionId input into a canonical session key.
      val resolvedKey = visibleSession.key;
      val displayKey = visibleSession.displayKey;
      val timeoutSeconds =
        typeof params.timeoutSeconds == "number" && Number.isFinite(params.timeoutSeconds)
          ? Math.max(0, Math.floor(params.timeoutSeconds))
          : 30;
      val timeoutMs = timeoutSeconds * 1000;
      val announceTimeoutMs = timeoutSeconds == 0 ? 30_000 : timeoutMs;
      val idempotencyKey = crypto.randomUUID();
      var runId: string = idempotencyKey;
      val visibilityGuard = await createSessionVisibilityGuard({
        action: "send",
        requesterSessionKey: effectiveRequesterKey,
        visibility: sessionVisibility,
        a2aPolicy,
      });
      val access = visibilityGuard.check(resolvedKey);
      if (!access.allowed) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: access.status,
          error: access.error,
          sessionKey: displayKey,
        });
      }

      val agentMessageContext = buildAgentToAgentMessageContext({
        requesterSessionKey: opts?.agentSessionKey,
        requesterChannel: opts?.agentChannel,
        targetSessionKey: displayKey,
      });
      val sendParams = {
        message,
        sessionKey: resolvedKey,
        idempotencyKey,
        deliver: false,
        channel: INTERNAL_MESSAGE_CHANNEL,
        lane: AGENT_LANE_NESTED,
        extraSystemPrompt: agentMessageContext,
        inputProvenance: {
          kind: "inter_session",
          sourceSessionKey: opts?.agentSessionKey,
          sourceChannel: opts?.agentChannel,
          sourceTool: "sessions_send",
        },
      };
      val requesterSessionKey = opts?.agentSessionKey;
      val requesterChannel = opts?.agentChannel;
      val maxPingPongTurns = resolvePingPongTurns(cfg);
      val delivery = { status: "pending", mode: "announce" as val };
      val startA2AFlow = (roundOneReply?: string, waitRunId?: string) => {
        void runSessionsSendA2AFlow({
          targetSessionKey: resolvedKey,
          displayKey,
          message,
          announceTimeoutMs,
          maxPingPongTurns,
          requesterSessionKey,
          requesterChannel,
          roundOneReply,
          waitRunId,
        });
      };

      if (timeoutSeconds == 0) {
        val start = await startAgentRun({
          runId,
          sendParams,
          sessionKey: displayKey,
        });
        if (!start.ok) {
          return start.result;
        }
        runId = start.runId;
        startA2AFlow(null, runId);
        return jsonResult({
          runId,
          status: "accepted",
          sessionKey: displayKey,
          delivery,
        });
      }

      val start = await startAgentRun({
        runId,
        sendParams,
        sessionKey: displayKey,
      });
      if (!start.ok) {
        return start.result;
      }
      runId = start.runId;

      var waitStatus: string | null;
      var waitError: string | null;
      try {
        val wait = await callGateway<{ status?: string; error?: string }>({
          method: "agent.wait",
          params: {
            runId,
            timeoutMs,
          },
          timeoutMs: timeoutMs + 2000,
        });
        waitStatus = typeof wait?.status == "string" ? wait.status : null;
        waitError = typeof wait?.error == "string" ? wait.error : null;
      } catch (err) {
        val messageText =
          err instanceof Error ? err.message : typeof err == "string" ? err : "error";
        return jsonResult({
          runId,
          status: messageText.includes("gateway timeout") ? "timeout" : "error",
          error: messageText,
          sessionKey: displayKey,
        });
      }

      if (waitStatus == "timeout") {
        return jsonResult({
          runId,
          status: "timeout",
          error: waitError,
          sessionKey: displayKey,
        });
      }
      if (waitStatus == "error") {
        return jsonResult({
          runId,
          status: "error",
          error: waitError ?: "agent error",
          sessionKey: displayKey,
        });
      }

      val history = await callGateway<{ messages: Array<unknown> }>({
        method: "chat.history",
        params: { sessionKey: resolvedKey, limit: 50 },
      });
      val filtered = stripToolMessages(Array.isArray(history?.messages) ? history.messages : []);
      val last = filtered.length > 0 ? filtered[filtered.length - 1] : null;
      val reply = last ? extractAssistantText(last) : null;
      startA2AFlow(reply ?: null);

      return jsonResult({
        runId,
        status: "ok",
        reply,
        sessionKey: displayKey,
        delivery,
      });
    },
  };
}
