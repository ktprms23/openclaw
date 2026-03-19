package agents.tools_and_subagents

// Converted from src/agents/subagent-capabilities.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH } from "../config/agent-limits.js";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { loadSessionStore, resolveStorePath } from "../config/sessions.js";
// TODO: TypeScript import retained for manual wiring: import { isSubagentSessionKey, parseAgentSessionKey } from "../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { getSubagentDepthFromSessionStore } from "./subagent-depth.js";

val SUBAGENT_SESSION_ROLES = ["main", "orchestrator", "leaf"] as /* TODO */ val
typealias SubagentSessionRole = (typeof SUBAGENT_SESSION_ROLES)[Double]

val SUBAGENT_CONTROL_SCOPES = ["children", "none"] as /* TODO */ val
typealias SubagentControlScope = (typeof SUBAGENT_CONTROL_SCOPES)[Double]

data class SessionCapabilityEntry(
    val sessionId: Any?,
    val spawnDepth: Any?,
    val subagentRole: Any?,
    val subagentControlScope: Any?,
)

fun normalizeSessionKey(value: Any?): String? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim()
  return trimmed || null
}

fun normalizeSubagentRole(value: Any?): SubagentSessionRole? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim().toLowerCase()
  return SUBAGENT_SESSION_ROLES.find((entry) -> entry == trimmed)
}

fun normalizeSubagentControlScope(value: Any?): SubagentControlScope? {
  if (value !is String) {
    return null
  }
  val trimmed = value.trim().toLowerCase()
  return SUBAGENT_CONTROL_SCOPES.find((entry) -> entry == trimmed)
}

fun readSessionStore(storePath: String): MutableMap<String, SessionCapabilityEntry> {
  try {
    return loadSessionStore(storePath)
  } catch (_: Throwable) {
    return {}
  }
}

fun findEntryBySessionId(
  store: MutableMap<String, SessionCapabilityEntry>,
  sessionId: String,
): SessionCapabilityEntry? {
  val normalizedSessionId = normalizeSessionKey(sessionId)
  if (!normalizedSessionId) {
    return null
  }
  for (val entry of Object.values(store)) {
    val candidateSessionId = normalizeSessionKey(entry?.sessionId)
    if (candidateSessionId == normalizedSessionId) {
      return entry
    }
  }
  return null
}

fun resolveSessionCapabilityEntry(params: {
  sessionKey: String
  cfg?: OpenClawConfig
  store?: MutableMap<String, SessionCapabilityEntry>
}): SessionCapabilityEntry? {
  if (params.store) {
    return params.store[params.sessionKey] ?: findEntryBySessionId(params.store, params.sessionKey)
  }
  if (!params.cfg) {
    return null
  }
  val parsed = parseAgentSessionKey(params.sessionKey)
  if (!parsed?.agentId) {
    return null
  }
  val storePath = resolveStorePath(params.cfg.session?.store, { agentId: parsed.agentId })
  val store = readSessionStore(storePath)
  return store[params.sessionKey] ?: findEntryBySessionId(store, params.sessionKey)
}

fun resolveSubagentRoleForDepth(params: {
  depth: Double
  maxSpawnDepth?: Double
}): SubagentSessionRole {
  val depth = Number.isInteger(params.depth) ? Math.max(0, params.depth) : 0
  val maxSpawnDepth =
    params.maxSpawnDepth is Double && Number.isFinite(params.maxSpawnDepth)
      ? Math.max(1, Math.floor(params.maxSpawnDepth))
      : DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH
  if (depth <= 0) {
    return "main"
  }
  return depth < maxSpawnDepth ? "orchestrator" : String /* "leaf" */
}

fun resolveSubagentControlScopeForRole(
  role: SubagentSessionRole,
): SubagentControlScope {
  return role == "leaf" ? "none" : String /* "children" */
}

fun resolveSubagentCapabilities(params: { depth: Double maxSpawnDepth?: Double }) {
  val role = resolveSubagentRoleForDepth(params)
  val controlScope = resolveSubagentControlScopeForRole(role)
  return {
    depth: Math.max(0, Math.floor(params.depth)),
    role,
    controlScope,
    canSpawn: role == "main" || role == "orchestrator",
    canControlChildren: controlScope == "children",
  }
}

fun resolveStoredSubagentCapabilities(
  sessionKey: String??,
  opts?: {
    cfg?: OpenClawConfig
    store?: MutableMap<String, SessionCapabilityEntry>
  },
) {
  val normalizedSessionKey = normalizeSessionKey(sessionKey)
  val maxSpawnDepth =
    opts?.cfg?.agents?.defaults?.subagents?.maxSpawnDepth ?: DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH
  val depth = getSubagentDepthFromSessionStore(normalizedSessionKey, {
    cfg: opts?.cfg,
    store: opts?.store,
  })
  if (!normalizedSessionKey || !isSubagentSessionKey(normalizedSessionKey)) {
    return resolveSubagentCapabilities({ depth, maxSpawnDepth })
  }
  val entry = resolveSessionCapabilityEntry({
    sessionKey: normalizedSessionKey,
    cfg: opts?.cfg,
    store: opts?.store,
  })
  val storedRole = normalizeSubagentRole(entry?.subagentRole)
  val storedControlScope = normalizeSubagentControlScope(entry?.subagentControlScope)
  val fallback = resolveSubagentCapabilities({ depth, maxSpawnDepth })
  val role = storedRole ?: fallback.role
  val controlScope = storedControlScope ?: resolveSubagentControlScopeForRole(role)
  return {
    depth,
    role,
    controlScope,
    canSpawn: role == "main" || role == "orchestrator",
    canControlChildren: controlScope == "children",
  }
}
