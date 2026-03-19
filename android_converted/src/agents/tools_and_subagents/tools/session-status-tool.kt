@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/session-status-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { normalizeGroupActivation } from "../../auto-reply/group-activation.js";
// TODO(port-deps): import { getFollowupQueueDepth, resolveQueueSettings } from "../../auto-reply/reply/queue.js";
// TODO(port-deps): import { buildStatusMessage } from "../../auto-reply/status.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): loadSessionStore,
// TODO(port-deps): resolveStorePath,
// TODO(port-deps): type SessionEntry,
// TODO(port-deps): updateSessionStore,
// TODO(port-deps): } from "../../config/sessions.js";
// TODO(port-deps): import { loadCombinedSessionStoreForGateway } from "../../gateway/session-utils.js";
// TODO(port-deps): import {
// TODO(port-deps): formatUsageWindowSummary,
// TODO(port-deps): loadProviderUsageSummary,
// TODO(port-deps): resolveUsageProviderId,
// TODO(port-deps): } from "../../infra/provider-usage.js";
// TODO(port-deps): import {
// TODO(port-deps): buildAgentMainSessionKey,
// TODO(port-deps): DEFAULT_AGENT_ID,
// TODO(port-deps): parseAgentSessionKey,
// TODO(port-deps): resolveAgentIdFromSessionKey,
// TODO(port-deps): } from "../../routing/session-key.js";
// TODO(port-deps): import { applyModelOverrideToSessionEntry } from "../../sessions/model-overrides.js";
// TODO(port-deps): import { resolvePreferredSessionKeyForSessionIdMatches } from "../../sessions/session-id-resolution.js";
// TODO(port-deps): import { resolveAgentDir } from "../agent-scope.js";
// TODO(port-deps): import { formatUserTime, resolveUserTimeFormat, resolveUserTimezone } from "../date-time.js";
// TODO(port-deps): import { resolveModelAuthLabel } from "../model-auth-label.js";
// TODO(port-deps): import { loadModelCatalog } from "../model-catalog.js";
// TODO(port-deps): import {
// TODO(port-deps): buildAllowedModelSet,
// TODO(port-deps): buildModelAliasIndex,
// TODO(port-deps): modelKey,
// TODO(port-deps): resolveDefaultModelForAgent,
// TODO(port-deps): resolveModelRefFromString,
// TODO(port-deps): } from "../model-selection.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { readStringParam } from "./common.js";
// TODO(port-deps): import {
// TODO(port-deps): createSessionVisibilityGuard,
// TODO(port-deps): shouldResolveSessionIdInput,
// TODO(port-deps): createAgentToAgentPolicy,
// TODO(port-deps): resolveEffectiveSessionToolsVisibility,
// TODO(port-deps): resolveInternalSessionKey,
// TODO(port-deps): resolveSandboxedSessionToolContext,
// TODO(port-deps): } from "./sessions-helpers.js";

val SessionStatusToolSchema = Type.Object({
  sessionKey: Type.Optional(Type.String()),
  model: Type.Optional(Type.String()),
});

fun resolveSessionEntry(params: {
  store: Record<string, SessionEntry>;
  keyRaw: string;
  alias: string;
  mainKey: string;
}): { key: string; entry: SessionEntry } | null {
  val keyRaw = params.keyRaw.trim();
  if (!keyRaw) {
    return null;
  }
  val internal = resolveInternalSessionKey({
    key: keyRaw,
    alias: params.alias,
    mainKey: params.mainKey,
  });

  val candidates = new Set<string>([keyRaw, internal]);
  if (!keyRaw.startsWith("agent:")) {
    candidates.add(`agent:${DEFAULT_AGENT_ID}:${keyRaw}`);
    candidates.add(`agent:${DEFAULT_AGENT_ID}:${internal}`);
  }
  if (keyRaw == "main") {
    candidates.add(
      buildAgentMainSessionKey({
        agentId: DEFAULT_AGENT_ID,
        mainKey: params.mainKey,
      }),
    );
  }

  for (val key of candidates) {
    val entry = params.store[key];
    if (entry) {
      return { key, entry };
    }
  }

  return null;
}

fun resolveSessionKeyFromSessionId(params: {
  cfg: OpenClawConfig;
  sessionId: string;
  agentId?: string;
}): string | null {
  val trimmed = params.sessionId.trim();
  if (!trimmed) {
    return null;
  }
  val { store } = loadCombinedSessionStoreForGateway(params.cfg);
  val matches = Object.entries(store).filter(
    (entry): entry is [string, SessionEntry] =>
      entry[1]?.sessionId == trimmed &&
      (!params.agentId || resolveAgentIdFromSessionKey(entry[0]) == params.agentId),
  );
  return resolvePreferredSessionKeyForSessionIdMatches(matches, trimmed) ?: null;
}

suspend fun resolveModelOverride(params: {
  cfg: OpenClawConfig;
  raw: string;
  sessionEntry?: SessionEntry;
  agentId: string;
}): Promise<
  | { kind: "reset" }
  | {
      kind: "set";
      provider: string;
      model: string;
      isDefault: boolean;
    }
> {
  val raw = params.raw.trim();
  if (!raw) {
    return { kind: "reset" };
  }
  if (raw.toLowerCase() == "default") {
    return { kind: "reset" };
  }

  val configDefault = resolveDefaultModelForAgent({
    cfg: params.cfg,
    agentId: params.agentId,
  });
  val currentProvider = params.sessionEntry?.providerOverride?.trim() || configDefault.provider;
  val currentModel = params.sessionEntry?.modelOverride?.trim() || configDefault.model;

  val aliasIndex = buildModelAliasIndex({
    cfg: params.cfg,
    defaultProvider: currentProvider,
  });
  val catalog = await loadModelCatalog({ config: params.cfg });
  val allowed = buildAllowedModelSet({
    cfg: params.cfg,
    catalog,
    defaultProvider: currentProvider,
    defaultModel: currentModel,
    agentId: params.agentId,
  });

  val resolved = resolveModelRefFromString({
    raw,
    defaultProvider: currentProvider,
    aliasIndex,
  });
  if (!resolved) {
    throw Error(`Unrecognized model "${raw}".`);
  }
  val key = modelKey(resolved.ref.provider, resolved.ref.model);
  if (allowed.allowedKeys.size > 0 && !allowed.allowedKeys.has(key)) {
    throw Error(`Model "${key}" is not allowed.`);
  }
  val isDefault =
    resolved.ref.provider == configDefault.provider && resolved.ref.model == configDefault.model;
  return {
    kind: "set",
    provider: resolved.ref.provider,
    model: resolved.ref.model,
    isDefault,
  };
}

fun createSessionStatusTool(opts?: {
  agentSessionKey?: string;
  config?: OpenClawConfig;
  sandboxed?: boolean;
}): AnyAgentTool {
  return {
    label: "Session Status",
    name: "session_status",
    description:
      "Show a /status-equivalent session status card (usage + time + cost when available). Use for model-use questions (📊 session_status). Optional: set per-session model override (model=default resets overrides).",
    parameters: SessionStatusToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val cfg = opts?.config ?: loadConfig();
      val { mainKey, alias, effectiveRequesterKey } = resolveSandboxedSessionToolContext({
        cfg,
        agentSessionKey: opts?.agentSessionKey,
        sandboxed: opts?.sandboxed,
      });
      val a2aPolicy = createAgentToAgentPolicy(cfg);
      val requesterAgentId = resolveAgentIdFromSessionKey(
        opts?.agentSessionKey ?: effectiveRequesterKey,
      );
      val visibilityRequesterKey = effectiveRequesterKey.trim();
      val usesLegacyMainAlias = alias == mainKey;
      val isLegacyMainVisibilityKey = (sessionKey: string) => {
        val trimmed = sessionKey.trim();
        return usesLegacyMainAlias && (trimmed == "main" || trimmed == mainKey);
      };
      val resolveVisibilityMainSessionKey = (sessionAgentId: string) => {
        val requesterParsed = parseAgentSessionKey(visibilityRequesterKey);
        if (
          resolveAgentIdFromSessionKey(visibilityRequesterKey) == sessionAgentId &&
          (requesterParsed?.rest == mainKey || isLegacyMainVisibilityKey(visibilityRequesterKey))
        ) {
          return visibilityRequesterKey;
        }
        return buildAgentMainSessionKey({
          agentId: sessionAgentId,
          mainKey,
        });
      };
      val normalizeVisibilityTargetSessionKey = (sessionKey: string, sessionAgentId: string) => {
        val trimmed = sessionKey.trim();
        if (!trimmed) {
          return trimmed;
        }
        if (trimmed.startsWith("agent:")) {
          val parsed = parseAgentSessionKey(trimmed);
          if (parsed?.rest == mainKey) {
            return resolveVisibilityMainSessionKey(sessionAgentId);
          }
          return trimmed;
        }
        // Preserve legacy bare main keys for requester tree checks.
        if (isLegacyMainVisibilityKey(trimmed)) {
          return resolveVisibilityMainSessionKey(sessionAgentId);
        }
        return trimmed;
      };
      val visibilityGuard =
        opts?.sandboxed == true
          ? await createSessionVisibilityGuard({
              action: "status",
              requesterSessionKey: visibilityRequesterKey,
              visibility: resolveEffectiveSessionToolsVisibility({
                cfg,
                sandboxed: true,
              }),
              a2aPolicy,
            })
          : null;

      val requestedKeyParam = readStringParam(params, "sessionKey");
      var requestedKeyRaw = requestedKeyParam ?: opts?.agentSessionKey;
      if (!requestedKeyRaw?.trim()) {
        throw Error("sessionKey required");
      }
      val ensureAgentAccess = (targetAgentId: string) => {
        if (targetAgentId == requesterAgentId) {
          return;
        }
        // Gate cross-agent access behind tools.agentToAgent settings.
        if (!a2aPolicy.enabled) {
          throw Error(
            "Agent-to-agent status is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent access.",
          );
        }
        if (!a2aPolicy.isAllowed(requesterAgentId, targetAgentId)) {
          throw Error("Agent-to-agent session status denied by tools.agentToAgent.allow.");
        }
      };

      if (requestedKeyRaw.startsWith("agent:")) {
        val requestedAgentId = resolveAgentIdFromSessionKey(requestedKeyRaw);
        ensureAgentAccess(requestedAgentId);
        val access = visibilityGuard?.check(
          normalizeVisibilityTargetSessionKey(requestedKeyRaw, requestedAgentId),
        );
        if (access && !access.allowed) {
          throw Error(access.error);
        }
      }

      val isExplicitAgentKey = requestedKeyRaw.startsWith("agent:");
      var agentId = isExplicitAgentKey
        ? resolveAgentIdFromSessionKey(requestedKeyRaw)
        : requesterAgentId;
      var storePath = resolveStorePath(cfg.session?.store, { agentId });
      var store = loadSessionStore(storePath);

      // Resolve against the requester-scoped store first to avoid leaking default agent data.
      var resolved = resolveSessionEntry({
        store,
        keyRaw: requestedKeyRaw,
        alias,
        mainKey,
      });

      if (!resolved && shouldResolveSessionIdInput(requestedKeyRaw)) {
        val resolvedKey = resolveSessionKeyFromSessionId({
          cfg,
          sessionId: requestedKeyRaw,
          agentId: a2aPolicy.enabled ? null : requesterAgentId,
        });
        if (resolvedKey) {
          // If resolution points at another agent, enforce A2A policy before switching stores.
          ensureAgentAccess(resolveAgentIdFromSessionKey(resolvedKey));
          requestedKeyRaw = resolvedKey;
          agentId = resolveAgentIdFromSessionKey(resolvedKey);
          storePath = resolveStorePath(cfg.session?.store, { agentId });
          store = loadSessionStore(storePath);
          resolved = resolveSessionEntry({
            store,
            keyRaw: requestedKeyRaw,
            alias,
            mainKey,
          });
        }
      }

      if (!resolved) {
        val kind = shouldResolveSessionIdInput(requestedKeyRaw) ? "sessionId" : "sessionKey";
        throw Error(`Unknown ${kind}: ${requestedKeyRaw}`);
      }

      if (visibilityGuard && !requestedKeyRaw.startsWith("agent:")) {
        val access = visibilityGuard.check(
          normalizeVisibilityTargetSessionKey(resolved.key, agentId),
        );
        if (!access.allowed) {
          throw Error(access.error);
        }
      }

      val configured = resolveDefaultModelForAgent({ cfg, agentId });
      val modelRaw = readStringParam(params, "model");
      var changedModel = false;
      if (typeof modelRaw == "string") {
        val selection = await resolveModelOverride({
          cfg,
          raw: modelRaw,
          sessionEntry: resolved.entry,
          agentId,
        });
        val nextEntry: SessionEntry = { ...resolved.entry };
        val applied = applyModelOverrideToSessionEntry({
          entry: nextEntry,
          selection:
            selection.kind == "reset"
              ? {
                  provider: configured.provider,
                  model: configured.model,
                  isDefault: true,
                }
              : {
                  provider: selection.provider,
                  model: selection.model,
                  isDefault: selection.isDefault,
                },
        });
        if (applied.updated) {
          store[resolved.key] = nextEntry;
          await updateSessionStore(storePath, (nextStore) => {
            nextStore[resolved.key] = nextEntry;
          });
          resolved.entry = nextEntry;
          changedModel = true;
        }
      }

      val agentDir = resolveAgentDir(cfg, agentId);
      val providerForCard = resolved.entry.providerOverride?.trim() || configured.provider;
      val usageProvider = resolveUsageProviderId(providerForCard);
      var usageLine: string | null;
      if (usageProvider) {
        try {
          val usageSummary = await loadProviderUsageSummary({
            timeoutMs: 3500,
            providers: [usageProvider],
            agentDir,
          });
          val snapshot = usageSummary.providers.find((entry) => entry.provider == usageProvider);
          if (snapshot) {
            val formatted = formatUsageWindowSummary(snapshot, {
              now: Date.now(),
              maxWindows: 2,
              includeResets: true,
            });
            if (formatted && !formatted.startsWith("error:")) {
              usageLine = `📊 Usage: ${formatted}`;
            }
          }
        } catch {
          // ignore
        }
      }

      val isGroup =
        resolved.entry.chatType == "group" ||
        resolved.entry.chatType == "channel" ||
        resolved.key.includes(":group:") ||
        resolved.key.includes(":channel:");
      val groupActivation = isGroup
        ? (normalizeGroupActivation(resolved.entry.groupActivation) ?: "mention")
        : null;

      val queueSettings = resolveQueueSettings({
        cfg,
        channel: resolved.entry.channel ?: resolved.entry.lastChannel ?: "unknown",
        sessionEntry: resolved.entry,
      });
      val queueKey = resolved.key ?: resolved.entry.sessionId;
      val queueDepth = queueKey ? getFollowupQueueDepth(queueKey) : 0;
      val queueOverrides = Boolean(
        resolved.entry.queueDebounceMs ?: resolved.entry.queueCap ?: resolved.entry.queueDrop,
      );

      val userTimezone = resolveUserTimezone(cfg.agents?.defaults?.userTimezone);
      val userTimeFormat = resolveUserTimeFormat(cfg.agents?.defaults?.timeFormat);
      val userTime = formatUserTime(new Date(), userTimezone, userTimeFormat);
      val timeLine = userTime
        ? `🕒 Time: ${userTime} (${userTimezone})`
        : `🕒 Time zone: ${userTimezone}`;

      val agentDefaults = cfg.agents?.defaults ?: {};
      val defaultLabel = `${configured.provider}/${configured.model}`;
      val agentModel =
        typeof agentDefaults.model == "object" && agentDefaults.model
          ? { ...agentDefaults.model, primary: defaultLabel }
          : { primary: defaultLabel };
      val statusText = buildStatusMessage({
        config: cfg,
        agent: {
          ...agentDefaults,
          model: agentModel,
        },
        agentId,
        sessionEntry: resolved.entry,
        sessionKey: resolved.key,
        sessionStorePath: storePath,
        groupActivation,
        modelAuth: resolveModelAuthLabel({
          provider: providerForCard,
          cfg,
          sessionEntry: resolved.entry,
          agentDir,
        }),
        usageLine,
        timeLine,
        queue: {
          mode: queueSettings.mode,
          depth: queueDepth,
          debounceMs: queueSettings.debounceMs,
          cap: queueSettings.cap,
          dropPolicy: queueSettings.dropPolicy,
          showDetails: queueOverrides,
        },
        includeTranscriptUsage: true,
      });

      return {
        content: [{ type: "text", text: statusText }],
        details: {
          ok: true,
          sessionKey: resolved.key,
          changedModel,
          statusText,
        },
      };
    },
  };
}
