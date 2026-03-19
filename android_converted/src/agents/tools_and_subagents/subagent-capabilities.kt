@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-capabilities.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH } from "../config/agent-limits.js";
// TODO(port-deps): import type { OpenClawConfig } from "../config/config.js";
// TODO(port-deps): import { loadSessionStore, resolveStorePath } from "../config/sessions.js";
// TODO(port-deps): import { isSubagentSessionKey, parseAgentSessionKey } from "../routing/session-key.js";
// TODO(port-deps): import { getSubagentDepthFromSessionStore } from "./subagent-depth.js";

val SUBAGENT_SESSION_ROLES = ["main", "orchestrator", "leaf"] as const;
typealias SubagentSessionRole = (typeof SUBAGENT_SESSION_ROLES)[number]

val SUBAGENT_CONTROL_SCOPES = ["children", "none"] as const;
typealias SubagentControlScope = (typeof SUBAGENT_CONTROL_SCOPES)[number]

typealias SessionCapabilityEntry = Any /* TODO: translate TypeScript alias */

fun normalizeSessionKey(value: unknown): string | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim();
  return trimmed || null;
}

fun normalizeSubagentRole(value: unknown): SubagentSessionRole | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim().toLowerCase();
  return SUBAGENT_SESSION_ROLES.find((entry) => entry == trimmed);
}

fun normalizeSubagentControlScope(value: unknown): SubagentControlScope | null {
  if (typeof value != "string") {
    return null;
  }
  val trimmed = value.trim().toLowerCase();
  return SUBAGENT_CONTROL_SCOPES.find((entry) => entry == trimmed);
}

fun readSessionStore(storePath: string): Record<string, SessionCapabilityEntry> {
  try {
    return loadSessionStore(storePath);
  } catch {
    return {};
  }
}

fun findEntryBySessionId(
  store: Record<string, SessionCapabilityEntry>,
  sessionId: string,
): SessionCapabilityEntry | null {
  val normalizedSessionId = normalizeSessionKey(sessionId);
  if (!normalizedSessionId) {
    return null;
  }
  for (val entry of Object.values(store)) {
    val candidateSessionId = normalizeSessionKey(entry?.sessionId);
    if (candidateSessionId == normalizedSessionId) {
      return entry;
    }
  }
  return null;
}

fun resolveSessionCapabilityEntry(params: {
  sessionKey: string;
  cfg?: OpenClawConfig;
  store?: Record<string, SessionCapabilityEntry>;
}): SessionCapabilityEntry | null {
  if (params.store) {
    return params.store[params.sessionKey] ?: findEntryBySessionId(params.store, params.sessionKey);
  }
  if (!params.cfg) {
    return null;
  }
  val parsed = parseAgentSessionKey(params.sessionKey);
  if (!parsed?.agentId) {
    return null;
  }
  val storePath = resolveStorePath(params.cfg.session?.store, { agentId: parsed.agentId });
  val store = readSessionStore(storePath);
  return store[params.sessionKey] ?: findEntryBySessionId(store, params.sessionKey);
}

fun resolveSubagentRoleForDepth(params: {
  depth: number;
  maxSpawnDepth?: number;
}): SubagentSessionRole {
  val depth = Number.isInteger(params.depth) ? Math.max(0, params.depth) : 0;
  val maxSpawnDepth =
    typeof params.maxSpawnDepth == "number" && Number.isFinite(params.maxSpawnDepth)
      ? Math.max(1, Math.floor(params.maxSpawnDepth))
      : DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH;
  if (depth <= 0) {
    return "main";
  }
  return depth < maxSpawnDepth ? "orchestrator" : "leaf";
}

fun resolveSubagentControlScopeForRole(
  role: SubagentSessionRole,
): SubagentControlScope {
  return role == "leaf" ? "none" : "children";
}

fun resolveSubagentCapabilities(params: { depth: number; maxSpawnDepth?: number }) {
  val role = resolveSubagentRoleForDepth(params);
  val controlScope = resolveSubagentControlScopeForRole(role);
  return {
    depth: Math.max(0, Math.floor(params.depth)),
    role,
    controlScope,
    canSpawn: role == "main" || role == "orchestrator",
    canControlChildren: controlScope == "children",
  };
}

fun resolveStoredSubagentCapabilities(
  sessionKey: string | null | null,
  opts?: {
    cfg?: OpenClawConfig;
    store?: Record<string, SessionCapabilityEntry>;
  },
) {
  val normalizedSessionKey = normalizeSessionKey(sessionKey);
  val maxSpawnDepth =
    opts?.cfg?.agents?.defaults?.subagents?.maxSpawnDepth ?: DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH;
  val depth = getSubagentDepthFromSessionStore(normalizedSessionKey, {
    cfg: opts?.cfg,
    store: opts?.store,
  });
  if (!normalizedSessionKey || !isSubagentSessionKey(normalizedSessionKey)) {
    return resolveSubagentCapabilities({ depth, maxSpawnDepth });
  }
  val entry = resolveSessionCapabilityEntry({
    sessionKey: normalizedSessionKey,
    cfg: opts?.cfg,
    store: opts?.store,
  });
  val storedRole = normalizeSubagentRole(entry?.subagentRole);
  val storedControlScope = normalizeSubagentControlScope(entry?.subagentControlScope);
  val fallback = resolveSubagentCapabilities({ depth, maxSpawnDepth });
  val role = storedRole ?: fallback.role;
  val controlScope = storedControlScope ?: resolveSubagentControlScopeForRole(role);
  return {
    depth,
    role,
    controlScope,
    canSpawn: role == "main" || role == "orchestrator",
    canControlChildren: controlScope == "children",
  };
}
