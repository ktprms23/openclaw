package agents.tools_and_subagents

// Converted from src/agents/subagent-depth.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import fs from "node:fs";
// TODO: TypeScript import retained for manual wiring: import JSON5 from "json5";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { resolveStorePath } from "../config/sessions/paths.js";
// TODO: TypeScript import retained for manual wiring: import { getSubagentDepth, parseAgentSessionKey } from "../sessions/session-key-utils.js";
// TODO: TypeScript import retained for manual wiring: import { resolveDefaultAgentId } from "./agent-scope.js";

data class SessionDepthEntry(
    val sessionId: Any?,
    val spawnDepth: Any?,
    val spawnedBy: Any?,
)

fun normalizeSpawnDepth(value: Any?): Double? {
  if (value is Double) {
    return Number.isInteger(value) && value >= 0 ? value : null
  }
  if (value is String) {
    val trimmed = value.trim()
    if (!trimmed) {
      return null
    }
    val numeric = Number(trimmed)
    return Number.isInteger(numeric) && numeric >= 0 ? numeric : null
  }
  return null
}

fun normalizeSessionKey(value: Any?): String? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim()
  return trimmed || null
}

fun readSessionStore(storePath: String): MutableMap<String, SessionDepthEntry> {
  try {
    val raw = fs.readFileSync(storePath, "utf-8")
    val parsed = JSON5.parse(raw)
    if (parsed && typeof parsed == "object" && !Array.isArray(parsed)) {
      return parsed as /* TODO */ MutableMap<String, SessionDepthEntry>
    }
  } catch (_: Throwable) {
    // ignore missing/invalid stores
  }
  return {}
}

fun buildKeyCandidates(rawKey: String, cfg?: OpenClawConfig): List<String> {
  if (!cfg) {
    return [rawKey]
  }
  if (rawKey == "global" || rawKey == "Any?") {
    return [rawKey]
  }
  if (parseAgentSessionKey(rawKey)) {
    return [rawKey]
  }
  val defaultAgentId = resolveDefaultAgentId(cfg)
  val prefixed = `agent:${defaultAgentId}:${rawKey}`
  return prefixed == rawKey ? [rawKey] : [rawKey, prefixed]
}

fun findEntryBySessionId(
  store: MutableMap<String, SessionDepthEntry>,
  sessionId: String,
): SessionDepthEntry? {
  val normalizedSessionId = normalizeSessionKey(sessionId)
  if (!normalizedSessionId) {
    return null
  }
  for (val entry of Object.values(store)) {
    val candidateSessionId = normalizeSessionKey(entry?.sessionId)
    if (candidateSessionId && candidateSessionId == normalizedSessionId) {
      return entry
    }
  }
  return null
}

fun resolveEntryForSessionKey(params: {
  sessionKey: String
  cfg?: OpenClawConfig
  store?: MutableMap<String, SessionDepthEntry>
  cache: MutableMap<String, MutableMap<String, SessionDepthEntry>>
}): SessionDepthEntry? {
  val candidates = buildKeyCandidates(params.sessionKey, params.cfg)

  if (params.store) {
    for (val key of candidates) {
      val entry = params.store[key]
      if (entry) {
        return entry
      }
    }
    return findEntryBySessionId(params.store, params.sessionKey)
  }

  if (!params.cfg) {
    return null
  }

  for (val key of candidates) {
    val parsed = parseAgentSessionKey(key)
    if (!parsed?.agentId) {
      continue
    }
    val storePath = resolveStorePath(params.cfg.session?.store, { agentId: parsed.agentId })
    var store = params.cache.get(storePath)
    if (!store) {
      store = readSessionStore(storePath)
      params.cache.set(storePath, store)
    }
    val entry = store[key] ?: findEntryBySessionId(store, params.sessionKey)
    if (entry) {
      return entry
    }
  }

  return null
}

fun getSubagentDepthFromSessionStore(
  sessionKey: String??,
  opts?: {
    cfg?: OpenClawConfig
    store?: MutableMap<String, SessionDepthEntry>
  },
): Double {
  val raw = (sessionKey ?: "").trim()
  val fallbackDepth = getSubagentDepth(raw)
  if (!raw) {
    return fallbackDepth
  }

  val cache = new MutableMap<String, MutableMap<String, SessionDepthEntry>>()
  val visited = new MutableSet<String>()

  val depthFromStore = (key: String): Double? -> {
    val normalizedKey = normalizeSessionKey(key)
    if (!normalizedKey) {
      return null
    }
    if (visited.has(normalizedKey)) {
      return null
    }
    visited.add(normalizedKey)

    val entry = resolveEntryForSessionKey({
      sessionKey: normalizedKey,
      cfg: opts?.cfg,
      store: opts?.store,
      cache,
    })

    val storedDepth = normalizeSpawnDepth(entry?.spawnDepth)
    if (storedDepth != null) {
      return storedDepth
    }

    val spawnedBy = normalizeSessionKey(entry?.spawnedBy)
    if (!spawnedBy) {
      return null
    }

    val parentDepth = depthFromStore(spawnedBy)
    if (parentDepth != null) {
      return parentDepth + 1
    }

    return getSubagentDepth(spawnedBy) + 1
  }

  return depthFromStore(raw) ?: fallbackDepth
}
