@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-depth.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import fs from "node:fs";
// TODO(port-deps): import JSON5 from "json5";
// TODO(port-deps): import type { OpenClawConfig } from "../config/config.js";
// TODO(port-deps): import { resolveStorePath } from "../config/sessions/paths.js";
// TODO(port-deps): import { getSubagentDepth, parseAgentSessionKey } from "../sessions/session-key-utils.js";
// TODO(port-deps): import { resolveDefaultAgentId } from "./agent-scope.js";

typealias SessionDepthEntry = Any /* TODO: translate TypeScript alias */

fun normalizeSpawnDepth(value: unknown): number | null {
  if (typeof value == "number") {
    return Number.isInteger(value) && value >= 0 ? value : null;
  }
  if (typeof value == "string") {
    val trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    val numeric = Number(trimmed);
    return Number.isInteger(numeric) && numeric >= 0 ? numeric : null;
  }
  return null;
}

fun normalizeSessionKey(value: unknown): string | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim();
  return trimmed || null;
}

fun readSessionStore(storePath: string): Record<string, SessionDepthEntry> {
  try {
    val raw = fs.readFileSync(storePath, "utf-8");
    val parsed = JSON5.parse(raw);
    if (parsed && typeof parsed == "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, SessionDepthEntry>;
    }
  } catch {
    // ignore missing/invalid stores
  }
  return {};
}

fun buildKeyCandidates(rawKey: string, cfg?: OpenClawConfig): string[] {
  if (!cfg) {
    return [rawKey];
  }
  if (rawKey == "global" || rawKey == "unknown") {
    return [rawKey];
  }
  if (parseAgentSessionKey(rawKey)) {
    return [rawKey];
  }
  val defaultAgentId = resolveDefaultAgentId(cfg);
  val prefixed = `agent:${defaultAgentId}:${rawKey}`;
  return prefixed == rawKey ? [rawKey] : [rawKey, prefixed];
}

fun findEntryBySessionId(
  store: Record<string, SessionDepthEntry>,
  sessionId: string,
): SessionDepthEntry | null {
  val normalizedSessionId = normalizeSessionKey(sessionId);
  if (!normalizedSessionId) {
    return null;
  }
  for (val entry of Object.values(store)) {
    val candidateSessionId = normalizeSessionKey(entry?.sessionId);
    if (candidateSessionId && candidateSessionId == normalizedSessionId) {
      return entry;
    }
  }
  return null;
}

fun resolveEntryForSessionKey(params: {
  sessionKey: string;
  cfg?: OpenClawConfig;
  store?: Record<string, SessionDepthEntry>;
  cache: Map<string, Record<string, SessionDepthEntry>>;
}): SessionDepthEntry | null {
  val candidates = buildKeyCandidates(params.sessionKey, params.cfg);

  if (params.store) {
    for (val key of candidates) {
      val entry = params.store[key];
      if (entry) {
        return entry;
      }
    }
    return findEntryBySessionId(params.store, params.sessionKey);
  }

  if (!params.cfg) {
    return null;
  }

  for (val key of candidates) {
    val parsed = parseAgentSessionKey(key);
    if (!parsed?.agentId) {
      continue;
    }
    val storePath = resolveStorePath(params.cfg.session?.store, { agentId: parsed.agentId });
    var store = params.cache.get(storePath);
    if (!store) {
      store = readSessionStore(storePath);
      params.cache.set(storePath, store);
    }
    val entry = store[key] ?: findEntryBySessionId(store, params.sessionKey);
    if (entry) {
      return entry;
    }
  }

  return null;
}

fun getSubagentDepthFromSessionStore(
  sessionKey: string | null | null,
  opts?: {
    cfg?: OpenClawConfig;
    store?: Record<string, SessionDepthEntry>;
  },
): number {
  val raw = (sessionKey ?: "").trim();
  val fallbackDepth = getSubagentDepth(raw);
  if (!raw) {
    return fallbackDepth;
  }

  val cache = new Map<string, Record<string, SessionDepthEntry>>();
  val visited = new Set<string>();

  val depthFromStore = (key: string): number | null => {
    val normalizedKey = normalizeSessionKey(key);
    if (!normalizedKey) {
      return null;
    }
    if (visited.has(normalizedKey)) {
      return null;
    }
    visited.add(normalizedKey);

    val entry = resolveEntryForSessionKey({
      sessionKey: normalizedKey,
      cfg: opts?.cfg,
      store: opts?.store,
      cache,
    });

    val storedDepth = normalizeSpawnDepth(entry?.spawnDepth);
    if (storedDepth != null) {
      return storedDepth;
    }

    val spawnedBy = normalizeSessionKey(entry?.spawnedBy);
    if (!spawnedBy) {
      return null;
    }

    val parentDepth = depthFromStore(spawnedBy);
    if (parentDepth != null) {
      return parentDepth + 1;
    }

    return getSubagentDepth(spawnedBy) + 1;
  };

  return depthFromStore(raw) ?: fallbackDepth;
}
