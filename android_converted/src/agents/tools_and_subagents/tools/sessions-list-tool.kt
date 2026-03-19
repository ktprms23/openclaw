@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-list-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import path from "node:path";
// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { type OpenClawConfig, loadConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): resolveSessionFilePath,
// TODO(port-deps): resolveSessionFilePathOptions,
// TODO(port-deps): resolveStorePath,
// TODO(port-deps): } from "../../config/sessions.js";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { resolveAgentIdFromSessionKey } from "../../routing/session-key.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readStringArrayParam } from "./common.js";
// TODO(port-deps): import {
// TODO(port-deps): createSessionVisibilityGuard,
// TODO(port-deps): createAgentToAgentPolicy,
// TODO(port-deps): classifySessionKind,
// TODO(port-deps): deriveChannel,
// TODO(port-deps): resolveDisplaySessionKey,
// TODO(port-deps): resolveEffectiveSessionToolsVisibility,
// TODO(port-deps): resolveInternalSessionKey,
// TODO(port-deps): resolveSandboxedSessionToolContext,
// TODO(port-deps): type SessionListRow,
// TODO(port-deps): stripToolMessages,
// TODO(port-deps): } from "./sessions-helpers.js";

val SessionsListToolSchema = Type.Object({
  kinds: Type.Optional(Type.Array(Type.String())),
  limit: Type.Optional(Type.Number({ minimum: 1 })),
  activeMinutes: Type.Optional(Type.Number({ minimum: 1 })),
  messageLimit: Type.Optional(Type.Number({ minimum: 0 })),
});

fun createSessionsListTool(opts?: {
  agentSessionKey?: string;
  sandboxed?: boolean;
  config?: OpenClawConfig;
}): AnyAgentTool {
  return {
    label: "Sessions",
    name: "sessions_list",
    description: "List sessions with optional filters and last messages.",
    parameters: SessionsListToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val cfg = opts?.config ?: loadConfig();
      val { mainKey, alias, requesterInternalKey, restrictToSpawned } =
        resolveSandboxedSessionToolContext({
          cfg,
          agentSessionKey: opts?.agentSessionKey,
          sandboxed: opts?.sandboxed,
        });
      val effectiveRequesterKey = requesterInternalKey ?: alias;
      val visibility = resolveEffectiveSessionToolsVisibility({
        cfg,
        sandboxed: opts?.sandboxed == true,
      });

      val kindsRaw = readStringArrayParam(params, "kinds")?.map((value) =>
        value.trim().toLowerCase(),
      );
      val allowedKindsList = (kindsRaw ?: []).filter((value) =>
        ["main", "group", "cron", "hook", "node", "other"].includes(value),
      );
      val allowedKinds = allowedKindsList.length ? new Set(allowedKindsList) : null;

      val limit =
        typeof params.limit == "number" && Number.isFinite(params.limit)
          ? Math.max(1, Math.floor(params.limit))
          : null;
      val activeMinutes =
        typeof params.activeMinutes == "number" && Number.isFinite(params.activeMinutes)
          ? Math.max(1, Math.floor(params.activeMinutes))
          : null;
      val messageLimitRaw =
        typeof params.messageLimit == "number" && Number.isFinite(params.messageLimit)
          ? Math.max(0, Math.floor(params.messageLimit))
          : 0;
      val messageLimit = Math.min(messageLimitRaw, 20);

      val list = await callGateway<{ sessions: Array<SessionListRow>; path: string }>({
        method: "sessions.list",
        params: {
          limit,
          activeMinutes,
          includeGlobal: !restrictToSpawned,
          includeUnknown: !restrictToSpawned,
          spawnedBy: restrictToSpawned ? effectiveRequesterKey : null,
        },
      });

      val sessions = Array.isArray(list?.sessions) ? list.sessions : [];
      val storePath = typeof list?.path == "string" ? list.path : null;
      val a2aPolicy = createAgentToAgentPolicy(cfg);
      val visibilityGuard = await createSessionVisibilityGuard({
        action: "list",
        requesterSessionKey: effectiveRequesterKey,
        visibility,
        a2aPolicy,
      });
      val rows: SessionListRow[] = [];
      val historyTargets: Array<{ row: SessionListRow; resolvedKey: string }> = [];

      for (val entry of sessions) {
        if (!entry || typeof entry != "object") {
          continue;
        }
        val key = typeof entry.key == "string" ? entry.key : "";
        if (!key) {
          continue;
        }
        val access = visibilityGuard.check(key);
        if (!access.allowed) {
          continue;
        }

        if (key == "unknown") {
          continue;
        }
        if (key == "global" && alias != "global") {
          continue;
        }

        val gatewayKind = typeof entry.kind == "string" ? entry.kind : null;
        val kind = classifySessionKind({ key, gatewayKind, alias, mainKey });
        if (allowedKinds && !allowedKinds.has(kind)) {
          continue;
        }

        val displayKey = resolveDisplaySessionKey({
          key,
          alias,
          mainKey,
        });

        val entryChannel = typeof entry.channel == "string" ? entry.channel : null;
        val deliveryContext =
          entry.deliveryContext && typeof entry.deliveryContext == "object"
            ? (entry.deliveryContext as Record<string, unknown>)
            : null;
        val deliveryChannel =
          typeof deliveryContext?.channel == "string" ? deliveryContext.channel : null;
        val deliveryTo = typeof deliveryContext?.to == "string" ? deliveryContext.to : null;
        val deliveryAccountId =
          typeof deliveryContext?.accountId == "string" ? deliveryContext.accountId : null;
        val lastChannel =
          deliveryChannel ??
          (typeof entry.lastChannel == "string" ? entry.lastChannel : null);
        val lastAccountId =
          deliveryAccountId ??
          (typeof entry.lastAccountId == "string" ? entry.lastAccountId : null);
        val derivedChannel = deriveChannel({
          key,
          kind,
          channel: entryChannel,
          lastChannel,
        });

        val sessionId = typeof entry.sessionId == "string" ? entry.sessionId : null;
        val sessionFileRaw = (entry as { sessionFile?: unknown }).sessionFile;
        val sessionFile = typeof sessionFileRaw == "string" ? sessionFileRaw : null;
        var transcriptPath: string | null;
        if (sessionId) {
          try {
            val agentId = resolveAgentIdFromSessionKey(key);
            val trimmedStorePath = storePath?.trim();
            var effectiveStorePath: string | null;
            if (trimmedStorePath && trimmedStorePath != "(multiple)") {
              if (trimmedStorePath.includes("{agentId}") || trimmedStorePath.startsWith("~")) {
                effectiveStorePath = resolveStorePath(trimmedStorePath, { agentId });
              } else if (path.isAbsolute(trimmedStorePath)) {
                effectiveStorePath = trimmedStorePath;
              }
            }
            val filePathOpts = resolveSessionFilePathOptions({
              agentId,
              storePath: effectiveStorePath,
            });
            transcriptPath = resolveSessionFilePath(
              sessionId,
              sessionFile ? { sessionFile } : null,
              filePathOpts,
            );
          } catch {
            transcriptPath = null;
          }
        }

        val row: SessionListRow = {
          key: displayKey,
          kind,
          channel: derivedChannel,
          label: typeof entry.label == "string" ? entry.label : null,
          displayName: typeof entry.displayName == "string" ? entry.displayName : null,
          deliveryContext:
            deliveryChannel || deliveryTo || deliveryAccountId
              ? {
                  channel: deliveryChannel,
                  to: deliveryTo,
                  accountId: deliveryAccountId,
                }
              : null,
          updatedAt: typeof entry.updatedAt == "number" ? entry.updatedAt : null,
          sessionId,
          model: typeof entry.model == "string" ? entry.model : null,
          contextTokens: typeof entry.contextTokens == "number" ? entry.contextTokens : null,
          totalTokens: typeof entry.totalTokens == "number" ? entry.totalTokens : null,
          thinkingLevel: typeof entry.thinkingLevel == "string" ? entry.thinkingLevel : null,
          verboseLevel: typeof entry.verboseLevel == "string" ? entry.verboseLevel : null,
          systemSent: typeof entry.systemSent == "boolean" ? entry.systemSent : null,
          abortedLastRun:
            typeof entry.abortedLastRun == "boolean" ? entry.abortedLastRun : null,
          sendPolicy: typeof entry.sendPolicy == "string" ? entry.sendPolicy : null,
          lastChannel,
          lastTo: deliveryTo ?: (typeof entry.lastTo == "string" ? entry.lastTo : null),
          lastAccountId,
          transcriptPath,
        };
        if (messageLimit > 0) {
          val resolvedKey = resolveInternalSessionKey({
            key: displayKey,
            alias,
            mainKey,
          });
          historyTargets.push({ row, resolvedKey });
        }
        rows.push(row);
      }

      if (messageLimit > 0 && historyTargets.length > 0) {
        val maxConcurrent = Math.min(4, historyTargets.length);
        var index = 0;
        val worker = async () => {
          while (true) {
            val next = index;
            index += 1;
            if (next >= historyTargets.length) {
              return;
            }
            val target = historyTargets[next];
            val history = await callGateway<{ messages: Array<unknown> }>({
              method: "chat.history",
              params: { sessionKey: target.resolvedKey, limit: messageLimit },
            });
            val rawMessages = Array.isArray(history?.messages) ? history.messages : [];
            val filtered = stripToolMessages(rawMessages);
            target.row.messages =
              filtered.length > messageLimit ? filtered.slice(-messageLimit) : filtered;
          }
        };
        await Promise.all(Array.from({ length: maxConcurrent }, () => worker()));
      }

      return jsonResult({
        count: rows.length,
        sessions: rows,
      });
    },
  };
}
