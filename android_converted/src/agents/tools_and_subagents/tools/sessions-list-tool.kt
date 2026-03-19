package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-list-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import path from "node:path";
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  resolveSessionFilePath,
  resolveSessionFilePathOptions,
  resolveStorePath,
} from "../../config/sessions.js"
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { resolveAgentIdFromSessionKey } from "../../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readStringArrayParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import {
  createSessionVisibilityGuard,
  createAgentToAgentPolicy,
  classifySessionKind,
  deriveChannel,
  resolveDisplaySessionKey,
  resolveEffectiveSessionToolsVisibility,
  resolveInternalSessionKey,
  resolveSandboxedSessionToolContext,
  type SessionListRow,
  stripToolMessages,
} from "./sessions-helpers.js"

val SessionsListToolSchema = Type.Object({
  kinds: Type.Optional(Type.Array(Type.String())),
  limit: Type.Optional(Type.Number({ minimum: 1 })),
  activeMinutes: Type.Optional(Type.Number({ minimum: 1 })),
  messageLimit: Type.Optional(Type.Number({ minimum: 0 })),
})

fun createSessionsListTool(opts?: {
  agentSessionKey?: String
  sandboxed?: Boolean
  config?: OpenClawConfig
}): AnyAgentTool {
  return {
    label: String /* "Sessions" */,
    name: String /* "sessions_list" */,
    description: String /* "List sessions with optional filters and last messages." */,
    parameters: SessionsListToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val cfg = opts?.config ?: loadConfig()
      val { mainKey, alias, requesterInternalKey, restrictToSpawned } =
        resolveSandboxedSessionToolContext({
          cfg,
          agentSessionKey: opts?.agentSessionKey,
          sandboxed: opts?.sandboxed,
        })
      val effectiveRequesterKey = requesterInternalKey ?: alias
      val visibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      })

      val kindsRaw = readStringArrayParam(params, "kinds")?.map((value) ->
        value.trim().toLowerCase(),
      )
      val allowedKindsList = (kindsRaw ?: []).filter((value) ->
        ["main", "group", "cron", "hook", "node", "other"].includes(value),
      )
      val allowedKinds = allowedKindsList.length ? mutableSetOf(allowedKindsList) : null

      val limit =
        params.limit is Double && Number.isFinite(params.limit)
          ? Math.max(1, Math.floor(params.limit))
          : null
      val activeMinutes =
        params.activeMinutes is Double && Number.isFinite(params.activeMinutes)
          ? Math.max(1, Math.floor(params.activeMinutes))
          : null
      val messageLimitRaw =
        params.messageLimit is Double && Number.isFinite(params.messageLimit)
          ? Math.max(0, Math.floor(params.messageLimit))
          : 0
      val messageLimit = Math.min(messageLimitRaw, 20)

      val list = await callGateway<{ sessions: List<SessionListRow> path: String }>({
        method: String /* "sessions.list" */,
        params: {
          limit,
          activeMinutes,
          includeGlobal: !restrictToSpawned,
          includeUnknown: !restrictToSpawned,
          spawnedBy: restrictToSpawned ? effectiveRequesterKey : null,
        },
      })

      val sessions = Array.isArray(list?.sessions) ? list.sessions : []
      val storePath = list?.path is String ? list.path : null
      val a2aPolicy = createAgentToAgentPolicy(cfg)
      val visibilityGuard = await createSessionVisibilityGuard({
        action: String /* "list" */,
        requesterSessionKey: effectiveRequesterKey,
        visibility,
        a2aPolicy,
      })
      val rows: List<SessionListRow> = []
      val historyTargets: List<{ row: SessionListRow resolvedKey: String }> = []

      for (val entry of sessions) {
        if (!entry || typeof entry != "object") {
          continue
        }
        val key = entry.key is String ? entry.key : ""
        if (!key) {
          continue
        }
        val access = visibilityGuard.check(key)
        if (!access.allowed) {
          continue
        }

        if (key == "Any?") {
          continue
        }
        if (key == "global" && alias != "global") {
          continue
        }

        val gatewayKind = entry.kind is String ? entry.kind : null
        val kind = classifySessionKind({ key, gatewayKind, alias, mainKey })
        if (allowedKinds && !allowedKinds.has(kind)) {
          continue
        }

        val displayKey = resolveDisplaySessionKey({
          key,
          alias,
          mainKey,
        })

        val entryChannel = entry.channel is String ? entry.channel : null
        val deliveryContext =
          entry.deliveryContext && typeof entry.deliveryContext == "object"
            ? (entry.deliveryContext as /* TODO */ MutableMap<String, Any?>)
            : null
        val deliveryChannel =
          deliveryContext?.channel is String ? deliveryContext.channel : null
        val deliveryTo = deliveryContext?.to is String ? deliveryContext.to : null
        val deliveryAccountId =
          deliveryContext?.accountId is String ? deliveryContext.accountId : null
        val lastChannel =
          deliveryChannel ??
          (entry.lastChannel is String ? entry.lastChannel : null)
        val lastAccountId =
          deliveryAccountId ??
          (entry.lastAccountId is String ? entry.lastAccountId : null)
        val derivedChannel = deriveChannel({
          key,
          kind,
          channel: entryChannel,
          lastChannel,
        })

        val sessionId = entry.sessionId is String ? entry.sessionId : null
        val sessionFileRaw = (entry as /* TODO */ { sessionFile?: Any? }).sessionFile
        val sessionFile = sessionFileRaw is String ? sessionFileRaw : null
        var transcriptPath: String?
        if (sessionId) {
          try {
            val agentId = resolveAgentIdFromSessionKey(key)
            val trimmedStorePath = storePath?.trim()
            var effectiveStorePath: String?
            if (trimmedStorePath && trimmedStorePath != "(multiple)") {
              if (trimmedStorePath.includes("{agentId}") || trimmedStorePath.startsWith("~")) {
                effectiveStorePath = resolveStorePath(trimmedStorePath, { agentId })
              } else if (path.isAbsolute(trimmedStorePath)) {
                effectiveStorePath = trimmedStorePath
              }
            }
            val filePathOpts = resolveSessionFilePathOptions({
              agentId,
              storePath: effectiveStorePath,
            })
            transcriptPath = resolveSessionFilePath(
              sessionId,
              sessionFile ? { sessionFile } : null,
              filePathOpts,
            )
          } catch (_: Throwable) {
            transcriptPath = null
          }
        }

        val row: SessionListRow = {
          key: displayKey,
          kind,
          channel: derivedChannel,
          label: entry.label is String ? entry.label : null,
          displayName: entry.displayName is String ? entry.displayName : null,
          deliveryContext:
            deliveryChannel || deliveryTo || deliveryAccountId
              ? {
                  channel: deliveryChannel,
                  to: deliveryTo,
                  accountId: deliveryAccountId,
                }
              : null,
          updatedAt: entry.updatedAt is Double ? entry.updatedAt : null,
          sessionId,
          model: entry.model is String ? entry.model : null,
          contextTokens: entry.contextTokens is Double ? entry.contextTokens : null,
          totalTokens: entry.totalTokens is Double ? entry.totalTokens : null,
          thinkingLevel: entry.thinkingLevel is String ? entry.thinkingLevel : null,
          verboseLevel: entry.verboseLevel is String ? entry.verboseLevel : null,
          systemSent: entry.systemSent is Boolean ? entry.systemSent : null,
          abortedLastRun:
            entry.abortedLastRun is Boolean ? entry.abortedLastRun : null,
          sendPolicy: entry.sendPolicy is String ? entry.sendPolicy : null,
          lastChannel,
          lastTo: deliveryTo ?: (entry.lastTo is String ? entry.lastTo : null),
          lastAccountId,
          transcriptPath,
        }
        if (messageLimit > 0) {
          val resolvedKey = resolveInternalSessionKey({
            key: displayKey,
            alias,
            mainKey,
          })
          historyTargets.push({ row, resolvedKey })
        }
        rows.push(row)
      }

      if (messageLimit > 0 && historyTargets.length > 0) {
        val maxConcurrent = Math.min(4, historyTargets.length)
        var index = 0
        val worker = async () {
          while (true) {
            val next = index
            index += 1
            if (next >= historyTargets.length) {
              return
            }
            val target = historyTargets[next]
            val history = await callGateway<{ messages: List<Any?> }>({
              method: String /* "chat.history" */,
              params: { sessionKey: target.resolvedKey, limit: messageLimit },
            })
            val rawMessages = Array.isArray(history?.messages) ? history.messages : []
            val filtered = stripToolMessages(rawMessages)
            target.row.messages =
              filtered.length > messageLimit ? filtered.slice(-messageLimit) : filtered
          }
        }
        await Promise.all(Array.from({ length: maxConcurrent }, () -> worker()))
      }

      return jsonResult({
        count: rows.length,
        sessions: rows,
      })
    },
  }
}
