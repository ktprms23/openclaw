package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-send-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeAgentId, resolveAgentIdFromSessionKey } from "../../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { SESSION_LABEL_MAX_LENGTH } from "../../sessions/session-label.js";
// TODO: TypeScript import retained for manual wiring: import {
  type GatewayMessageChannel,
  INTERNAL_MESSAGE_CHANNEL,
} from "../../utils/message-channel.js"
// TODO: TypeScript import retained for manual wiring: import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import {
  createSessionVisibilityGuard,
  createAgentToAgentPolicy,
  extractAssistantText,
  resolveEffectiveSessionToolsVisibility,
  resolveSessionReference,
  resolveSessionToolContext,
  resolveVisibleSessionReference,
  stripToolMessages,
} from "./sessions-helpers.js"
// TODO: TypeScript import retained for manual wiring: import { buildAgentToAgentMessageContext, resolvePingPongTurns } from "./sessions-send-helpers.js";
// TODO: TypeScript import retained for manual wiring: import { runSessionsSendA2AFlow } from "./sessions-send-tool.a2a.js";

val SessionsSendToolSchema = Type.Object({
  sessionKey: Type.Optional(Type.String()),
  label: Type.Optional(Type.String({ minLength: 1, maxLength: SESSION_LABEL_MAX_LENGTH })),
  agentId: Type.Optional(Type.String({ minLength: 1, maxLength: 64 })),
  message: Type.String(),
  timeoutSeconds: Type.Optional(Type.Number({ minimum: 0 })),
})

suspend fun startAgentRun(params: {
  runId: String
  sendParams: MutableMap<String, Any?>
  sessionKey: String
}): Deferred<{ ok: true runId: String } | { ok: false result: ReturnType<typeof jsonResult> }> {
  try {
    val response = await callGateway<{ runId: String }>({
      method: String /* "agent" */,
      params: params.sendParams,
      timeoutMs: 10_000,
    })
    return {
      ok: true,
      runId: response?.runId is String && response.runId ? response.runId : params.runId,
    }
  } catch (err) {
    val messageText =
      err instanceof Error ? err.message : err is String ? err : String /* "error" */
    return {
      ok: false,
      result: jsonResult({
        runId: params.runId,
        status: String /* "error" */,
        error: messageText,
        sessionKey: params.sessionKey,
      }),
    }
  }
}

fun createSessionsSendTool(opts?: {
  agentSessionKey?: String
  agentChannel?: GatewayMessageChannel
  sandboxed?: Boolean
  config?: OpenClawConfig
}): AnyAgentTool {
  return {
    label: String /* "Session Send" */,
    name: String /* "sessions_send" */,
    description: String /* "Send a message into another session. Use sessionKey or label to identify the target." */,
    parameters: SessionsSendToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val message = readStringParam(params, "message", { required: true })
      val { cfg, mainKey, alias, effectiveRequesterKey, restrictToSpawned } =
        resolveSessionToolContext(opts)

      val a2aPolicy = createAgentToAgentPolicy(cfg)
      val sessionVisibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      })

      val sessionKeyParam = readStringParam(params, "sessionKey")
      val labelParam = readStringParam(params, "label")?.trim() || null
      val labelAgentIdParam = readStringParam(params, "agentId")?.trim() || null
      if (sessionKeyParam && labelParam) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: String /* "error" */,
          error: String /* "Provide either sessionKey or label (not both)." */,
        })
      }

      var sessionKey = sessionKeyParam
      if (!sessionKey && labelParam) {
        val requesterAgentId = resolveAgentIdFromSessionKey(effectiveRequesterKey)
        val requestedAgentId = labelAgentIdParam
          ? normalizeAgentId(labelAgentIdParam)
          : null

        if (restrictToSpawned && requestedAgentId && requestedAgentId != requesterAgentId) {
          return jsonResult({
            runId: crypto.randomUUID(),
            status: String /* "forbidden" */,
            error: String /* "Sandboxed sessions_send label lookup is limited to this agent" */,
          })
        }

        if (requesterAgentId && requestedAgentId && requestedAgentId != requesterAgentId) {
          if (!a2aPolicy.enabled) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: String /* "forbidden" */,
              error: String /* "Agent-to-agent messaging is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent sends." */,
            })
          }
          if (!a2aPolicy.isAllowed(requesterAgentId, requestedAgentId)) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: String /* "forbidden" */,
              error: String /* "Agent-to-agent messaging denied by tools.agentToAgent.allow." */,
            })
          }
        }

        val resolveParams: MutableMap<String, Any?> = {
          label: labelParam,
          ...(requestedAgentId ? { agentId: requestedAgentId } : {}),
          ...(restrictToSpawned ? { spawnedBy: effectiveRequesterKey } : {}),
        }
        var resolvedKey = ""
        try {
          val resolved = await callGateway<{ key: String }>({
            method: String /* "sessions.resolve" */,
            params: resolveParams,
            timeoutMs: 10_000,
          })
          resolvedKey = resolved?.key is String ? resolved.key.trim() : ""
        } catch (err) {
          val msg = err instanceof Error ? err.message : String(err)
          if (restrictToSpawned) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: String /* "forbidden" */,
              error: String /* "Session not visible from this sandboxed agent session." */,
            })
          }
          return jsonResult({
            runId: crypto.randomUUID(),
            status: String /* "error" */,
            error: msg || `No session found with label: ${labelParam}`,
          })
        }

        if (!resolvedKey) {
          if (restrictToSpawned) {
            return jsonResult({
              runId: crypto.randomUUID(),
              status: String /* "forbidden" */,
              error: String /* "Session not visible from this sandboxed agent session." */,
            })
          }
          return jsonResult({
            runId: crypto.randomUUID(),
            status: String /* "error" */,
            error: `No session found with label: ${labelParam}`,
          })
        }
        sessionKey = resolvedKey
      }

      if (!sessionKey) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: String /* "error" */,
          error: String /* "Either sessionKey or label is required" */,
        })
      }
      val resolvedSession = await resolveSessionReference({
        sessionKey,
        alias,
        mainKey,
        requesterInternalKey: effectiveRequesterKey,
        restrictToSpawned,
      })
      if (!resolvedSession.ok) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: resolvedSession.status,
          error: resolvedSession.error,
        })
      }
      val visibleSession = await resolveVisibleSessionReference({
        resolvedSession,
        requesterSessionKey: effectiveRequesterKey,
        restrictToSpawned,
        visibilitySessionKey: sessionKey,
      })
      if (!visibleSession.ok) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: visibleSession.status,
          error: visibleSession.error,
          sessionKey: visibleSession.displayKey,
        })
      }
      // Normalize sessionKey/sessionId input into a canonical session key.
      val resolvedKey = visibleSession.key
      val displayKey = visibleSession.displayKey
      val timeoutSeconds =
        params.timeoutSeconds is Double && Number.isFinite(params.timeoutSeconds)
          ? Math.max(0, Math.floor(params.timeoutSeconds))
          : 30
      val timeoutMs = timeoutSeconds * 1000
      val announceTimeoutMs = timeoutSeconds == 0 ? 30_000 : timeoutMs
      val idempotencyKey = crypto.randomUUID()
      var runId: String = idempotencyKey
      val visibilityGuard = await createSessionVisibilityGuard({
        action: String /* "send" */,
        requesterSessionKey: effectiveRequesterKey,
        visibility: sessionVisibility,
        a2aPolicy,
      })
      val access = visibilityGuard.check(resolvedKey)
      if (!access.allowed) {
        return jsonResult({
          runId: crypto.randomUUID(),
          status: access.status,
          error: access.error,
          sessionKey: displayKey,
        })
      }

      val agentMessageContext = buildAgentToAgentMessageContext({
        requesterSessionKey: opts?.agentSessionKey,
        requesterChannel: opts?.agentChannel,
        targetSessionKey: displayKey,
      })
      val sendParams = {
        message,
        sessionKey: resolvedKey,
        idempotencyKey,
        deliver: false,
        channel: INTERNAL_MESSAGE_CHANNEL,
        lane: AGENT_LANE_NESTED,
        extraSystemPrompt: agentMessageContext,
        inputProvenance: {
          kind: String /* "inter_session" */,
          sourceSessionKey: opts?.agentSessionKey,
          sourceChannel: opts?.agentChannel,
          sourceTool: String /* "sessions_send" */,
        },
      }
      val requesterSessionKey = opts?.agentSessionKey
      val requesterChannel = opts?.agentChannel
      val maxPingPongTurns = resolvePingPongTurns(cfg)
      val delivery = { status: String /* "pending" */, mode: String /* "announce" */ as /* TODO */ val }
      val startA2AFlow = { roundOneReply?: String, waitRunId?: String ->
        Unit runSessionsSendA2AFlow({
          targetSessionKey: resolvedKey,
          displayKey,
          message,
          announceTimeoutMs,
          maxPingPongTurns,
          requesterSessionKey,
          requesterChannel,
          roundOneReply,
          waitRunId,
        })
      }

      if (timeoutSeconds == 0) {
        val start = await startAgentRun({
          runId,
          sendParams,
          sessionKey: displayKey,
        })
        if (!start.ok) {
          return start.result
        }
        runId = start.runId
        startA2AFlow(null, runId)
        return jsonResult({
          runId,
          status: String /* "accepted" */,
          sessionKey: displayKey,
          delivery,
        })
      }

      val start = await startAgentRun({
        runId,
        sendParams,
        sessionKey: displayKey,
      })
      if (!start.ok) {
        return start.result
      }
      runId = start.runId

      var waitStatus: String?
      var waitError: String?
      try {
        val wait = await callGateway<{ status?: String error?: String }>({
          method: String /* "agent.wait" */,
          params: {
            runId,
            timeoutMs,
          },
          timeoutMs: timeoutMs + 2000,
        })
        waitStatus = wait?.status is String ? wait.status : null
        waitError = wait?.error is String ? wait.error : null
      } catch (err) {
        val messageText =
          err instanceof Error ? err.message : err is String ? err : String /* "error" */
        return jsonResult({
          runId,
          status: messageText.includes("gateway timeout") ? "timeout" : String /* "error" */,
          error: messageText,
          sessionKey: displayKey,
        })
      }

      if (waitStatus == "timeout") {
        return jsonResult({
          runId,
          status: String /* "timeout" */,
          error: waitError,
          sessionKey: displayKey,
        })
      }
      if (waitStatus == "error") {
        return jsonResult({
          runId,
          status: String /* "error" */,
          error: waitError ?: String /* "agent error" */,
          sessionKey: displayKey,
        })
      }

      val history = await callGateway<{ messages: List<Any?> }>({
        method: String /* "chat.history" */,
        params: { sessionKey: resolvedKey, limit: 50 },
      })
      val filtered = stripToolMessages(Array.isArray(history?.messages) ? history.messages : [])
      val last = filtered.length > 0 ? filtered[filtered.length - 1] : null
      val reply = last ? extractAssistantText(last) : null
      startA2AFlow(reply ?: null)

      return jsonResult({
        runId,
        status: String /* "ok" */,
        reply,
        sessionKey: displayKey,
        delivery,
      })
    },
  }
}
