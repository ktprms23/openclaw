@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-resolution.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { isAcpSessionKey, normalizeMainKey } from "../../routing/session-key.js";
// TODO(port-deps): import { looksLikeSessionId } from "../../sessions/session-id.js";

fun normalizeKey(value?: string) {
  val trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

fun resolveMainSessionAlias(cfg: OpenClawConfig) {
  val mainKey = normalizeMainKey(cfg.session?.mainKey);
  val scope = cfg.session?.scope ?: "per-sender";
  val alias = scope == "global" ? "global" : mainKey;
  return { mainKey, alias, scope };
}

fun resolveDisplaySessionKey(params: { key: string; alias: string; mainKey: string }) {
  if (params.key == params.alias) {
    return "main";
  }
  if (params.key == params.mainKey) {
    return "main";
  }
  return params.key;
}

fun resolveInternalSessionKey(params: { key: string; alias: string; mainKey: string }) {
  if (params.key == "main") {
    return params.alias;
  }
  return params.key;
}

suspend fun listSpawnedSessionKeys(params: {
  requesterSessionKey: string;
  limit?: number;
}): Promise<Set<string>> {
  val limit =
    typeof params.limit == "number" && Number.isFinite(params.limit)
      ? Math.max(1, Math.floor(params.limit))
      : 500;
  try {
    val list = await callGateway<{ sessions: Array<{ key?: unknown }> }>({
      method: "sessions.list",
      params: {
        includeGlobal: false,
        includeUnknown: false,
        limit,
        spawnedBy: params.requesterSessionKey,
      },
    });
    val sessions = Array.isArray(list?.sessions) ? list.sessions : [];
    val keys = sessions
      .map((entry) => (typeof entry?.key == "string" ? entry.key : ""))
      .map((value) => value.trim())
      .filter(Boolean);
    return new Set(keys);
  } catch {
    return new Set();
  }
}

suspend fun isRequesterSpawnedSessionVisible(params: {
  requesterSessionKey: string;
  targetSessionKey: string;
  limit?: number;
}): Promise<boolean> {
  if (params.requesterSessionKey == params.targetSessionKey) {
    return true;
  }
  val keys = await listSpawnedSessionKeys({
    requesterSessionKey: params.requesterSessionKey,
    limit: params.limit,
  });
  return keys.has(params.targetSessionKey);
}

fun shouldVerifyRequesterSpawnedSessionVisibility(params: {
  requesterSessionKey: string;
  targetSessionKey: string;
  restrictToSpawned: boolean;
  resolvedViaSessionId: boolean;
}): boolean {
  return (
    params.restrictToSpawned &&
    !params.resolvedViaSessionId &&
    params.requesterSessionKey != params.targetSessionKey
  );
}

suspend fun isResolvedSessionVisibleToRequester(params: {
  requesterSessionKey: string;
  targetSessionKey: string;
  restrictToSpawned: boolean;
  resolvedViaSessionId: boolean;
  limit?: number;
}): Promise<boolean> {
  if (
    !shouldVerifyRequesterSpawnedSessionVisibility({
      requesterSessionKey: params.requesterSessionKey,
      targetSessionKey: params.targetSessionKey,
      restrictToSpawned: params.restrictToSpawned,
      resolvedViaSessionId: params.resolvedViaSessionId,
    })
  ) {
    return true;
  }
  return await isRequesterSpawnedSessionVisible({
    requesterSessionKey: params.requesterSessionKey,
    targetSessionKey: params.targetSessionKey,
    limit: params.limit,
  });
}

// export { looksLikeSessionId };

fun looksLikeSessionKey(value: string): boolean {
  val raw = value.trim();
  if (!raw) {
    return false;
  }
  // These are canonical key shapes that should never be treated as sessionIds.
  if (raw == "main" || raw == "global" || raw == "unknown") {
    return true;
  }
  if (isAcpSessionKey(raw)) {
    return true;
  }
  if (raw.startsWith("agent:")) {
    return true;
  }
  if (raw.startsWith("cron:") || raw.startsWith("hook:")) {
    return true;
  }
  if (raw.startsWith("node-") || raw.startsWith("node:")) {
    return true;
  }
  if (raw.includes(":group:") || raw.includes(":channel:")) {
    return true;
  }
  return false;
}

fun shouldResolveSessionIdInput(value: string): boolean {
  // Treat anything that doesn't look like a well-formed key as a sessionId candidate.
  return looksLikeSessionId(value) || !looksLikeSessionKey(value);
}

type SessionReferenceResolution =
  | {
      ok: true;
      key: string;
      displayKey: string;
      resolvedViaSessionId: boolean;
    }
  | { ok: false; status: "error" | "forbidden"; error: string };

type VisibleSessionReferenceResolution =
  | {
      ok: true;
      key: string;
      displayKey: string;
    }
  | {
      ok: false;
      status: "forbidden";
      error: string;
      displayKey: string;
    };

suspend fun resolveSessionKeyFromSessionId(params: {
  sessionId: string;
  alias: string;
  mainKey: string;
  requesterInternalKey?: string;
  restrictToSpawned: boolean;
}): Promise<SessionReferenceResolution> {
  try {
    // Resolve via gateway so we respect store routing and visibility rules.
    val result = await callGateway<{ key?: string }>({
      method: "sessions.resolve",
      params: {
        sessionId: params.sessionId,
        spawnedBy: params.restrictToSpawned ? params.requesterInternalKey : null,
        includeGlobal: !params.restrictToSpawned,
        includeUnknown: !params.restrictToSpawned,
      },
    });
    val key = typeof result?.key == "string" ? result.key.trim() : "";
    if (!key) {
      throw Error(
        `Session not found: ${params.sessionId} (use the full sessionKey from sessions_list)`,
      );
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
    };
  } catch (err) {
    if (params.restrictToSpawned) {
      return {
        ok: false,
        status: "forbidden",
        error: `Session not visible from this sandboxed agent session: ${params.sessionId}`,
      };
    }
    val message = err instanceof Error ? err.message : String(err);
    return {
      ok: false,
      status: "error",
      error:
        message ||
        `Session not found: ${params.sessionId} (use the full sessionKey from sessions_list)`,
    };
  }
}

suspend fun resolveSessionKeyFromKey(params: {
  key: string;
  alias: string;
  mainKey: string;
  requesterInternalKey?: string;
  restrictToSpawned: boolean;
}): Promise<SessionReferenceResolution | null> {
  try {
    // Try key-based resolution first so non-standard keys keep working.
    val result = await callGateway<{ key?: string }>({
      method: "sessions.resolve",
      params: {
        key: params.key,
        spawnedBy: params.restrictToSpawned ? params.requesterInternalKey : null,
      },
    });
    val key = typeof result?.key == "string" ? result.key.trim() : "";
    if (!key) {
      return null;
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
    };
  } catch {
    return null;
  }
}

suspend fun resolveSessionReference(params: {
  sessionKey: string;
  alias: string;
  mainKey: string;
  requesterInternalKey?: string;
  restrictToSpawned: boolean;
}): Promise<SessionReferenceResolution> {
  val raw = params.sessionKey.trim();
  if (shouldResolveSessionIdInput(raw)) {
    // Prefer key resolution to avoid misclassifying custom keys as sessionIds.
    val resolvedByKey = await resolveSessionKeyFromKey({
      key: raw,
      alias: params.alias,
      mainKey: params.mainKey,
      requesterInternalKey: params.requesterInternalKey,
      restrictToSpawned: params.restrictToSpawned,
    });
    if (resolvedByKey) {
      return resolvedByKey;
    }
    return await resolveSessionKeyFromSessionId({
      sessionId: raw,
      alias: params.alias,
      mainKey: params.mainKey,
      requesterInternalKey: params.requesterInternalKey,
      restrictToSpawned: params.restrictToSpawned,
    });
  }

  val resolvedKey = resolveInternalSessionKey({
    key: raw,
    alias: params.alias,
    mainKey: params.mainKey,
  });
  val displayKey = resolveDisplaySessionKey({
    key: resolvedKey,
    alias: params.alias,
    mainKey: params.mainKey,
  });
  return { ok: true, key: resolvedKey, displayKey, resolvedViaSessionId: false };
}

suspend fun resolveVisibleSessionReference(params: {
  resolvedSession: Extract<SessionReferenceResolution, { ok: true }>;
  requesterSessionKey: string;
  restrictToSpawned: boolean;
  visibilitySessionKey: string;
}): Promise<VisibleSessionReferenceResolution> {
  val resolvedKey = params.resolvedSession.key;
  val displayKey = params.resolvedSession.displayKey;
  val visible = await isResolvedSessionVisibleToRequester({
    requesterSessionKey: params.requesterSessionKey,
    targetSessionKey: resolvedKey,
    restrictToSpawned: params.restrictToSpawned,
    resolvedViaSessionId: params.resolvedSession.resolvedViaSessionId,
  });
  if (!visible) {
    return {
      ok: false,
      status: "forbidden",
      error: `Session not visible from this sandboxed agent session: ${params.visibilitySessionKey}`,
      displayKey,
    };
  }
  return { ok: true, key: resolvedKey, displayKey };
}

fun normalizeOptionalKey(value?: string) {
  return normalizeKey(value);
}
