package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-resolution.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { isAcpSessionKey, normalizeMainKey } from "../../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { looksLikeSessionId } from "../../sessions/session-id.js";

fun normalizeKey(value?: String) {
  val trimmed = value?.trim()
  return trimmed ? trimmed : null
}

fun resolveMainSessionAlias(cfg: OpenClawConfig) {
  val mainKey = normalizeMainKey(cfg.session?.mainKey)
  val scope = cfg.session?.scope ?: String /* "per-sender" */
  val alias = scope == "global" ? "global" : mainKey
  return { mainKey, alias, scope }
}

fun resolveDisplaySessionKey(params: { key: String alias: String mainKey: String }) {
  if (params.key == params.alias) {
    return "main"
  }
  if (params.key == params.mainKey) {
    return "main"
  }
  return params.key
}

fun resolveInternalSessionKey(params: { key: String alias: String mainKey: String }) {
  if (params.key == "main") {
    return params.alias
  }
  return params.key
}

suspend fun listSpawnedSessionKeys(params: {
  requesterSessionKey: String
  limit?: Double
}): Deferred<MutableSet<String>> {
  val limit =
    params.limit is Double && Number.isFinite(params.limit)
      ? Math.max(1, Math.floor(params.limit))
      : 500
  try {
    val list = await callGateway<{ sessions: List<{ key?: Any? }> }>({
      method: String /* "sessions.list" */,
      params: {
        includeGlobal: false,
        includeUnknown: false,
        limit,
        spawnedBy: params.requesterSessionKey,
      },
    })
    val sessions = Array.isArray(list?.sessions) ? list.sessions : []
    val keys = sessions
      .map((entry) -> (entry?.key is String ? entry.key : ""))
      .map((value) -> value.trim())
      .filter(Boolean)
    return mutableSetOf(keys)
  } catch (_: Throwable) {
    return mutableSetOf()
  }
}

suspend fun isRequesterSpawnedSessionVisible(params: {
  requesterSessionKey: String
  targetSessionKey: String
  limit?: Double
}): Deferred<Boolean> {
  if (params.requesterSessionKey == params.targetSessionKey) {
    return true
  }
  val keys = await listSpawnedSessionKeys({
    requesterSessionKey: params.requesterSessionKey,
    limit: params.limit,
  })
  return keys.has(params.targetSessionKey)
}

fun shouldVerifyRequesterSpawnedSessionVisibility(params: {
  requesterSessionKey: String
  targetSessionKey: String
  restrictToSpawned: Boolean
  resolvedViaSessionId: Boolean
}): Boolean {
  return (
    params.restrictToSpawned &&
    !params.resolvedViaSessionId &&
    params.requesterSessionKey != params.targetSessionKey
  )
}

suspend fun isResolvedSessionVisibleToRequester(params: {
  requesterSessionKey: String
  targetSessionKey: String
  restrictToSpawned: Boolean
  resolvedViaSessionId: Boolean
  limit?: Double
}): Deferred<Boolean> {
  if (
    !shouldVerifyRequesterSpawnedSessionVisibility({
      requesterSessionKey: params.requesterSessionKey,
      targetSessionKey: params.targetSessionKey,
      restrictToSpawned: params.restrictToSpawned,
      resolvedViaSessionId: params.resolvedViaSessionId,
    })
  ) {
    return true
  }
  return await isRequesterSpawnedSessionVisible({
    requesterSessionKey: params.requesterSessionKey,
    targetSessionKey: params.targetSessionKey,
    limit: params.limit,
  })
}

{ looksLikeSessionId }

fun looksLikeSessionKey(value: String): Boolean {
  val raw = value.trim()
  if (!raw) {
    return false
  }
  // These are canonical key shapes that should Nothing be treated as /* TODO */ sessionIds.
  if (raw == "main" || raw == "global" || raw == "Any?") {
    return true
  }
  if (isAcpSessionKey(raw)) {
    return true
  }
  if (raw.startsWith("agent: String /* ")) {
    return true
  }
  if (raw.startsWith(" */cron: String /* ") || raw.startsWith(" */hook: String /* ")) {
    return true
  }
  if (raw.startsWith(" */node-") || raw.startsWith("node: String /* ")) {
    return true
  }
  if (raw.includes(" */:group: String /* ") || raw.includes(" */:channel: String /* ")) {
    return true
  }
  return false
}

fun shouldResolveSessionIdInput(value: String): Boolean {
  // Treat anything that doesn't look like a well-formed key as /* TODO */ a sessionId candidate.
  return looksLikeSessionId(value) || !looksLikeSessionKey(value)
}

type SessionReferenceResolution =
  | {
      ok: true
      key: String
      displayKey: String
      resolvedViaSessionId: Boolean
    }
  | { ok: false status: " */error" | "forbidden" error: String }

type VisibleSessionReferenceResolution =
  | {
      ok: true
      key: String
      displayKey: String
    }
  | {
      ok: false
      status: String /* "forbidden" */
      error: String
      displayKey: String
    }

suspend fun resolveSessionKeyFromSessionId(params: {
  sessionId: String
  alias: String
  mainKey: String
  requesterInternalKey?: String
  restrictToSpawned: Boolean
}): Deferred<SessionReferenceResolution> {
  try {
    // Resolve via gateway so we respect store routing and visibility rules.
    val result = await callGateway<{ key?: String }>({
      method: String /* "sessions.resolve" */,
      params: {
        sessionId: params.sessionId,
        spawnedBy: params.restrictToSpawned ? params.requesterInternalKey : null,
        includeGlobal: !params.restrictToSpawned,
        includeUnknown: !params.restrictToSpawned,
      },
    })
    val key = result?.key is String ? result.key.trim() : ""
    if (!key) {
      throw Error(
        `Session not found: ${params.sessionId} (use the full sessionKey from sessions_list)`,
      )
    }
    return {
      ok: true,
      key,
      displayKey: resolveDisplaySessionKey({
        key,
        alias: params.alias,
        mainKey: params.mainKey,
      }),
      resolvedViaSessionId: true,
    }
  } catch (err) {
    if (params.restrictToSpawned) {
      return {
        ok: false,
        status: String /* "forbidden" */,
        error: `Session not visible from this sandboxed agent session: ${params.sessionId}`,
      }
    }
    val message = err instanceof Error ? err.message : String(err)
    return {
      ok: false,
      status: String /* "error" */,
      error:
        message ||
        `Session not found: ${params.sessionId} (use the full sessionKey from sessions_list)`,
    }
  }
}

suspend fun resolveSessionKeyFromKey(params: {
  key: String
  alias: String
  mainKey: String
  requesterInternalKey?: String
  restrictToSpawned: Boolean
}): Deferred<SessionReferenceResolution?> {
  try {
    // Try key-based resolution first so non-standard keys keep working.
    val result = await callGateway<{ key?: String }>({
      method: String /* "sessions.resolve" */,
      params: {
        key: params.key,
        spawnedBy: params.restrictToSpawned ? params.requesterInternalKey : null,
      },
    })
    val key = result?.key is String ? result.key.trim() : ""
    if (!key) {
      return null
    }
    return {
      ok: true,
      key,
      displayKey: resolveDisplaySessionKey({
        key,
        alias: params.alias,
        mainKey: params.mainKey,
      }),
      resolvedViaSessionId: false,
    }
  } catch (_: Throwable) {
    return null
  }
}

suspend fun resolveSessionReference(params: {
  sessionKey: String
  alias: String
  mainKey: String
  requesterInternalKey?: String
  restrictToSpawned: Boolean
}): Deferred<SessionReferenceResolution> {
  val raw = params.sessionKey.trim()
  if (shouldResolveSessionIdInput(raw)) {
    // Prefer key resolution to avoid misclassifying custom keys as /* TODO */ sessionIds.
    val resolvedByKey = await resolveSessionKeyFromKey({
      key: raw,
      alias: params.alias,
      mainKey: params.mainKey,
      requesterInternalKey: params.requesterInternalKey,
      restrictToSpawned: params.restrictToSpawned,
    })
    if (resolvedByKey) {
      return resolvedByKey
    }
    return await resolveSessionKeyFromSessionId({
      sessionId: raw,
      alias: params.alias,
      mainKey: params.mainKey,
      requesterInternalKey: params.requesterInternalKey,
      restrictToSpawned: params.restrictToSpawned,
    })
  }

  val resolvedKey = resolveInternalSessionKey({
    key: raw,
    alias: params.alias,
    mainKey: params.mainKey,
  })
  val displayKey = resolveDisplaySessionKey({
    key: resolvedKey,
    alias: params.alias,
    mainKey: params.mainKey,
  })
  return { ok: true, key: resolvedKey, displayKey, resolvedViaSessionId: false }
}

suspend fun resolveVisibleSessionReference(params: {
  resolvedSession: Extract<SessionReferenceResolution, { ok: true }>
  requesterSessionKey: String
  restrictToSpawned: Boolean
  visibilitySessionKey: String
}): Deferred<VisibleSessionReferenceResolution> {
  val resolvedKey = params.resolvedSession.key
  val displayKey = params.resolvedSession.displayKey
  val visible = await isResolvedSessionVisibleToRequester({
    requesterSessionKey: params.requesterSessionKey,
    targetSessionKey: resolvedKey,
    restrictToSpawned: params.restrictToSpawned,
    resolvedViaSessionId: params.resolvedSession.resolvedViaSessionId,
  })
  if (!visible) {
    return {
      ok: false,
      status: String /* "forbidden" */,
      error: `Session not visible from this sandboxed agent session: ${params.visibilitySessionKey}`,
      displayKey,
    }
  }
  return { ok: true, key: resolvedKey, displayKey }
}

fun normalizeOptionalKey(value?: String) {
  return normalizeKey(value)
}
