@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/command/session.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import type { MsgContext } from "../../auto-reply/templating.js";
// TODO(port-deps): import {
// TODO(port-deps): normalizeThinkLevel,
// TODO(port-deps): normalizeVerboseLevel,
// TODO(port-deps): type ThinkLevel,
// TODO(port-deps): type VerboseLevel,
// TODO(port-deps): } from "../../auto-reply/thinking.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): evaluateSessionFreshness,
// TODO(port-deps): loadSessionStore,
// TODO(port-deps): resolveAgentIdFromSessionKey,
// TODO(port-deps): resolveChannelResetConfig,
// TODO(port-deps): resolveExplicitAgentSessionKey,
// TODO(port-deps): resolveSessionResetPolicy,
// TODO(port-deps): resolveSessionResetType,
// TODO(port-deps): resolveSessionKey,
// TODO(port-deps): resolveStorePath,
// TODO(port-deps): type SessionEntry,
// TODO(port-deps): } from "../../config/sessions.js";
// TODO(port-deps): import { normalizeMainKey } from "../../routing/session-key.js";
// TODO(port-deps): import { listAgentIds } from "../agent-scope.js";
// TODO(port-deps): import { clearBootstrapSnapshotOnSessionRollover } from "../bootstrap-cache.js";

typealias SessionResolution = Any /* TODO: translate TypeScript alias */

typealias SessionKeyResolution = Any /* TODO: translate TypeScript alias */

fun resolveSessionKeyForRequest(opts: {
  cfg: OpenClawConfig;
  to?: string;
  sessionId?: string;
  sessionKey?: string;
  agentId?: string;
}): SessionKeyResolution {
  val sessionCfg = opts.cfg.session;
  val scope = sessionCfg?.scope ?: "per-sender";
  val mainKey = normalizeMainKey(sessionCfg?.mainKey);
  val explicitSessionKey =
    opts.sessionKey?.trim() ||
    resolveExplicitAgentSessionKey({
      cfg: opts.cfg,
      agentId: opts.agentId,
    });
  val storeAgentId = resolveAgentIdFromSessionKey(explicitSessionKey);
  val storePath = resolveStorePath(sessionCfg?.store, {
    agentId: storeAgentId,
  });
  val sessionStore = loadSessionStore(storePath);

  val ctx: MsgContext | null = opts.to?.trim() ? { From: opts.to } : null;
  var sessionKey: string | null =
    explicitSessionKey ?: (ctx ? resolveSessionKey(scope, ctx, mainKey) : null);

  // If a session id was provided, prefer to re-use its entry (by id) even when no key was derived.
  if (
    !explicitSessionKey &&
    opts.sessionId &&
    (!sessionKey || sessionStore[sessionKey]?.sessionId != opts.sessionId)
  ) {
    val foundKey = Object.keys(sessionStore).find(
      (key) => sessionStore[key]?.sessionId == opts.sessionId,
    );
    if (foundKey) {
      sessionKey = foundKey;
    }
  }

  // When sessionId was provided but not found in the primary store, search all agent stores.
  // Sessions created under a specific agent live in that agent's store file; the primary
  // store (derived from the default agent) won't contain them.
  // Also covers the case where --to derived a sessionKey that doesn't match the requested sessionId.
  if (
    opts.sessionId &&
    !explicitSessionKey &&
    (!sessionKey || sessionStore[sessionKey]?.sessionId != opts.sessionId)
  ) {
    val allAgentIds = listAgentIds(opts.cfg);
    for (val agentId of allAgentIds) {
      if (agentId == storeAgentId) {
        continue;
      }
      val altStorePath = resolveStorePath(sessionCfg?.store, { agentId });
      val altStore = loadSessionStore(altStorePath);
      val foundKey = Object.keys(altStore).find(
        (key) => altStore[key]?.sessionId == opts.sessionId,
      );
      if (foundKey) {
        return { sessionKey: foundKey, sessionStore: altStore, storePath: altStorePath };
      }
    }
  }

  return { sessionKey, sessionStore, storePath };
}

fun resolveSession(opts: {
  cfg: OpenClawConfig;
  to?: string;
  sessionId?: string;
  sessionKey?: string;
  agentId?: string;
}): SessionResolution {
  val sessionCfg = opts.cfg.session;
  val { sessionKey, sessionStore, storePath } = resolveSessionKeyForRequest({
    cfg: opts.cfg,
    to: opts.to,
    sessionId: opts.sessionId,
    sessionKey: opts.sessionKey,
    agentId: opts.agentId,
  });
  val now = Date.now();

  val sessionEntry = sessionKey ? sessionStore[sessionKey] : null;

  val resetType = resolveSessionResetType({ sessionKey });
  val channelReset = resolveChannelResetConfig({
    sessionCfg,
    channel: sessionEntry?.lastChannel ?: sessionEntry?.channel,
  });
  val resetPolicy = resolveSessionResetPolicy({
    sessionCfg,
    resetType,
    resetOverride: channelReset,
  });
  val fresh = sessionEntry
    ? evaluateSessionFreshness({ updatedAt: sessionEntry.updatedAt, now, policy: resetPolicy })
        .fresh
    : false;
  val sessionId =
    opts.sessionId?.trim() || (fresh ? sessionEntry?.sessionId : null) || crypto.randomUUID();
  val isNewSession = !fresh && !opts.sessionId;

  clearBootstrapSnapshotOnSessionRollover({
    sessionKey,
    previousSessionId: isNewSession ? sessionEntry?.sessionId : null,
  });

  val persistedThinking =
    fresh && sessionEntry?.thinkingLevel
      ? normalizeThinkLevel(sessionEntry.thinkingLevel)
      : null;
  val persistedVerbose =
    fresh && sessionEntry?.verboseLevel
      ? normalizeVerboseLevel(sessionEntry.verboseLevel)
      : null;

  return {
    sessionId,
    sessionKey,
    sessionEntry,
    sessionStore,
    storePath,
    isNewSession,
    persistedThinking,
    persistedVerbose,
  };
}
