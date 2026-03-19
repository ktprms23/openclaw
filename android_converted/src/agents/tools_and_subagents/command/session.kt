package agents.tools_and_subagents.command

// Converted from src/agents/command/session.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import type { MsgContext } from "../../auto-reply/templating.js";
// TODO: TypeScript import retained for manual wiring: import {
  normalizeThinkLevel,
  normalizeVerboseLevel,
  type ThinkLevel,
  type VerboseLevel,
} from "../../auto-reply/thinking.js"
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  evaluateSessionFreshness,
  loadSessionStore,
  resolveAgentIdFromSessionKey,
  resolveChannelResetConfig,
  resolveExplicitAgentSessionKey,
  resolveSessionResetPolicy,
  resolveSessionResetType,
  resolveSessionKey,
  resolveStorePath,
  type SessionEntry,
} from "../../config/sessions.js"
// TODO: TypeScript import retained for manual wiring: import { normalizeMainKey } from "../../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { listAgentIds } from "../agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import { clearBootstrapSnapshotOnSessionRollover } from "../bootstrap-cache.js";

data class SessionResolution(
    val sessionId: String,
    val sessionKey: String?,
    val sessionEntry: SessionEntry?,
    val sessionStore: MutableMap<String, SessionEntry>?,
    val storePath: String,
    val isNewSession: Boolean,
    val persistedThinking: ThinkLevel?,
    val persistedVerbose: VerboseLevel?,
)

data class SessionKeyResolution(
    val sessionKey: String?,
    val sessionStore: MutableMap<String, SessionEntry>,
    val storePath: String,
)

fun resolveSessionKeyForRequest(opts: {
  cfg: OpenClawConfig
  to?: String
  sessionId?: String
  sessionKey?: String
  agentId?: String
}): SessionKeyResolution {
  val sessionCfg = opts.cfg.session
  val scope = sessionCfg?.scope ?: String /* "per-sender" */
  val mainKey = normalizeMainKey(sessionCfg?.mainKey)
  val explicitSessionKey =
    opts.sessionKey?.trim() ||
    resolveExplicitAgentSessionKey({
      cfg: opts.cfg,
      agentId: opts.agentId,
    })
  val storeAgentId = resolveAgentIdFromSessionKey(explicitSessionKey)
  val storePath = resolveStorePath(sessionCfg?.store, {
    agentId: storeAgentId,
  })
  val sessionStore = loadSessionStore(storePath)

  val ctx: MsgContext? = opts.to?.trim() ? { From: opts.to } : null
  var sessionKey: String? =
    explicitSessionKey ?: (ctx ? resolveSessionKey(scope, ctx, mainKey) : null)

  // If a session id was provided, prefer to re-use its entry (by id) even when no key was derived.
  if (
    !explicitSessionKey &&
    opts.sessionId &&
    (!sessionKey || sessionStore[sessionKey]?.sessionId != opts.sessionId)
  ) {
    val foundKey = Object.keys(sessionStore).find(
      (key) -> sessionStore[key]?.sessionId == opts.sessionId,
    )
    if (foundKey) {
      sessionKey = foundKey
    }
  }

  // When sessionId was provided but not found in the primary store, search all agent stores.
  // Sessions created under a specific agent live in that agent's store file the primary
  // store (derived from the default agent) won't contain them.
  // Also covers the case where --to derived a sessionKey that doesn't match the requested sessionId.
  if (
    opts.sessionId &&
    !explicitSessionKey &&
    (!sessionKey || sessionStore[sessionKey]?.sessionId != opts.sessionId)
  ) {
    val allAgentIds = listAgentIds(opts.cfg)
    for (val agentId of allAgentIds) {
      if (agentId == storeAgentId) {
        continue
      }
      val altStorePath = resolveStorePath(sessionCfg?.store, { agentId })
      val altStore = loadSessionStore(altStorePath)
      val foundKey = Object.keys(altStore).find(
        (key) -> altStore[key]?.sessionId == opts.sessionId,
      )
      if (foundKey) {
        return { sessionKey: foundKey, sessionStore: altStore, storePath: altStorePath }
      }
    }
  }

  return { sessionKey, sessionStore, storePath }
}

fun resolveSession(opts: {
  cfg: OpenClawConfig
  to?: String
  sessionId?: String
  sessionKey?: String
  agentId?: String
}): SessionResolution {
  val sessionCfg = opts.cfg.session
  val { sessionKey, sessionStore, storePath } = resolveSessionKeyForRequest({
    cfg: opts.cfg,
    to: opts.to,
    sessionId: opts.sessionId,
    sessionKey: opts.sessionKey,
    agentId: opts.agentId,
  })
  val now = Date.now()

  val sessionEntry = sessionKey ? sessionStore[sessionKey] : null

  val resetType = resolveSessionResetType({ sessionKey })
  val channelReset = resolveChannelResetConfig({
    sessionCfg,
    channel: sessionEntry?.lastChannel ?: sessionEntry?.channel,
  })
  val resetPolicy = resolveSessionResetPolicy({
    sessionCfg,
    resetType,
    resetOverride: channelReset,
  })
  val fresh = sessionEntry
    ? evaluateSessionFreshness({ updatedAt: sessionEntry.updatedAt, now, policy: resetPolicy })
        .fresh
    : false
  val sessionId =
    opts.sessionId?.trim() || (fresh ? sessionEntry?.sessionId : null) || crypto.randomUUID()
  val isNewSession = !fresh && !opts.sessionId

  clearBootstrapSnapshotOnSessionRollover({
    sessionKey,
    previousSessionId: isNewSession ? sessionEntry?.sessionId : null,
  })

  val persistedThinking =
    fresh && sessionEntry?.thinkingLevel
      ? normalizeThinkLevel(sessionEntry.thinkingLevel)
      : null
  val persistedVerbose =
    fresh && sessionEntry?.verboseLevel
      ? normalizeVerboseLevel(sessionEntry.verboseLevel)
      : null

  return {
    sessionId,
    sessionKey,
    sessionEntry,
    sessionStore,
    storePath,
    isNewSession,
    persistedThinking,
    persistedVerbose,
  }
}
