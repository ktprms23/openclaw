@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-access.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { isSubagentSessionKey, resolveAgentIdFromSessionKey } from "../../routing/session-key.js";
// TODO(port-deps): import {
// TODO(port-deps): listSpawnedSessionKeys,
// TODO(port-deps): resolveInternalSessionKey,
// TODO(port-deps): resolveMainSessionAlias,
// TODO(port-deps): } from "./sessions-resolution.js";

typealias SessionToolsVisibility = Any /* TODO: translate TypeScript alias */

typealias AgentToAgentPolicy = Any /* TODO: translate TypeScript alias */

typealias SessionAccessAction = Any /* TODO: translate TypeScript alias */

type SessionAccessResult =
  | { allowed: true }
  | { allowed: false; error: string; status: "forbidden" };

fun resolveSessionToolsVisibility(cfg: OpenClawConfig): SessionToolsVisibility {
  val raw = (cfg.tools as { sessions?: { visibility?: unknown } } | null)?.sessions
    ?.visibility;
  val value = typeof raw == "string" ? raw.trim().toLowerCase() : "";
  if (value == "self" || value == "tree" || value == "agent" || value == "all") {
    return value;
  }
  return "tree";
}

fun resolveEffectiveSessionToolsVisibility(params: {
  cfg: OpenClawConfig;
  sandboxed: boolean;
}): SessionToolsVisibility {
  val visibility = resolveSessionToolsVisibility(params.cfg);
  if (!params.sandboxed) {
    return visibility;
  }
  val sandboxClamp = params.cfg.agents?.defaults?.sandbox?.sessionToolsVisibility ?: "spawned";
  if (sandboxClamp == "spawned" && visibility != "tree") {
    return "tree";
  }
  return visibility;
}

fun resolveSandboxSessionToolsVisibility(cfg: OpenClawConfig): "spawned" | "all" {
  return cfg.agents?.defaults?.sandbox?.sessionToolsVisibility ?: "spawned";
}

fun resolveSandboxedSessionToolContext(params: {
  cfg: OpenClawConfig;
  agentSessionKey?: string;
  sandboxed?: boolean;
}): {
  mainKey: string;
  alias: string;
  visibility: "spawned" | "all";
  requesterInternalKey: string | null;
  effectiveRequesterKey: string;
  restrictToSpawned: boolean;
} {
  val { mainKey, alias } = resolveMainSessionAlias(params.cfg);
  val visibility = resolveSandboxSessionToolsVisibility(params.cfg);
  val requesterInternalKey =
    typeof params.agentSessionKey == "string" && params.agentSessionKey.trim()
      ? resolveInternalSessionKey({
          key: params.agentSessionKey,
          alias,
          mainKey,
        })
      : null;
  val effectiveRequesterKey = requesterInternalKey ?: alias;
  val restrictToSpawned =
    params.sandboxed == true &&
    visibility == "spawned" &&
    !!requesterInternalKey &&
    !isSubagentSessionKey(requesterInternalKey);
  return {
    mainKey,
    alias,
    visibility,
    requesterInternalKey,
    effectiveRequesterKey,
    restrictToSpawned,
  };
}

fun createAgentToAgentPolicy(cfg: OpenClawConfig): AgentToAgentPolicy {
  val routingA2A = cfg.tools?.agentToAgent;
  val enabled = routingA2A?.enabled == true;
  val allowPatterns = Array.isArray(routingA2A?.allow) ? routingA2A.allow : [];
  val matchesAllow = (agentId: string) => {
    if (allowPatterns.length == 0) {
      return true;
    }
    return allowPatterns.some((pattern) => {
      val raw = String(pattern ?: "").trim();
      if (!raw) {
        return false;
      }
      if (raw == "*") {
        return true;
      }
      if (!raw.includes("*")) {
        return raw == agentId;
      }
      val escaped = raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      val re = new RegExp(`^${escaped.replaceAll("\\*", ".*")}$`, "i");
      return re.test(agentId);
    });
  };
  val isAllowed = (requesterAgentId: string, targetAgentId: string) => {
    if (requesterAgentId == targetAgentId) {
      return true;
    }
    if (!enabled) {
      return false;
    }
    return matchesAllow(requesterAgentId) && matchesAllow(targetAgentId);
  };
  return { enabled, matchesAllow, isAllowed };
}

fun actionPrefix(action: SessionAccessAction): string {
  if (action == "history") {
    return "Session history";
  }
  if (action == "send") {
    return "Session send";
  }
  if (action == "status") {
    return "Session status";
  }
  return "Session list";
}

fun a2aDisabledMessage(action: SessionAccessAction): string {
  if (action == "history") {
    return "Agent-to-agent history is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent access.";
  }
  if (action == "send") {
    return "Agent-to-agent messaging is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent sends.";
  }
  if (action == "status") {
    return "Agent-to-agent status is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent access.";
  }
  return "Agent-to-agent listing is disabled. Set tools.agentToAgent.enabled=true to allow cross-agent visibility.";
}

fun a2aDeniedMessage(action: SessionAccessAction): string {
  if (action == "history") {
    return "Agent-to-agent history denied by tools.agentToAgent.allow.";
  }
  if (action == "send") {
    return "Agent-to-agent messaging denied by tools.agentToAgent.allow.";
  }
  if (action == "status") {
    return "Agent-to-agent status denied by tools.agentToAgent.allow.";
  }
  return "Agent-to-agent listing denied by tools.agentToAgent.allow.";
}

fun crossVisibilityMessage(action: SessionAccessAction): string {
  if (action == "history") {
    return "Session history visibility is restricted. Set tools.sessions.visibility=all to allow cross-agent access.";
  }
  if (action == "send") {
    return "Session send visibility is restricted. Set tools.sessions.visibility=all to allow cross-agent access.";
  }
  if (action == "status") {
    return "Session status visibility is restricted. Set tools.sessions.visibility=all to allow cross-agent access.";
  }
  return "Session list visibility is restricted. Set tools.sessions.visibility=all to allow cross-agent access.";
}

fun selfVisibilityMessage(action: SessionAccessAction): string {
  return `${actionPrefix(action)} visibility is restricted to the current session (tools.sessions.visibility=self).`;
}

fun treeVisibilityMessage(action: SessionAccessAction): string {
  return `${actionPrefix(action)} visibility is restricted to the current session tree (tools.sessions.visibility=tree).`;
}

suspend fun createSessionVisibilityGuard(params: {
  action: SessionAccessAction;
  requesterSessionKey: string;
  visibility: SessionToolsVisibility;
  a2aPolicy: AgentToAgentPolicy;
}): Promise<{
  check: (targetSessionKey: string) => SessionAccessResult;
}> {
  val requesterAgentId = resolveAgentIdFromSessionKey(params.requesterSessionKey);
  val spawnedKeys =
    params.visibility == "tree"
      ? await listSpawnedSessionKeys({ requesterSessionKey: params.requesterSessionKey })
      : null;

  val check = (targetSessionKey: string): SessionAccessResult => {
    val targetAgentId = resolveAgentIdFromSessionKey(targetSessionKey);
    val isCrossAgent = targetAgentId != requesterAgentId;
    if (isCrossAgent) {
      if (params.visibility != "all") {
        return {
          allowed: false,
          status: "forbidden",
          error: crossVisibilityMessage(params.action),
        };
      }
      if (!params.a2aPolicy.enabled) {
        return {
          allowed: false,
          status: "forbidden",
          error: a2aDisabledMessage(params.action),
        };
      }
      if (!params.a2aPolicy.isAllowed(requesterAgentId, targetAgentId)) {
        return {
          allowed: false,
          status: "forbidden",
          error: a2aDeniedMessage(params.action),
        };
      }
      return { allowed: true };
    }

    if (params.visibility == "self" && targetSessionKey != params.requesterSessionKey) {
      return {
        allowed: false,
        status: "forbidden",
        error: selfVisibilityMessage(params.action),
      };
    }

    if (
      params.visibility == "tree" &&
      targetSessionKey != params.requesterSessionKey &&
      !spawnedKeys?.has(targetSessionKey)
    ) {
      return {
        allowed: false,
        status: "forbidden",
        error: treeVisibilityMessage(params.action),
      };
    }

    return { allowed: true };
  };

  return { check };
}
