@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/agents-list-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): DEFAULT_AGENT_ID,
// TODO(port-deps): normalizeAgentId,
// TODO(port-deps): parseAgentSessionKey,
// TODO(port-deps): } from "../../routing/session-key.js";
// TODO(port-deps): import { resolveAgentConfig } from "../agent-scope.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult } from "./common.js";
// TODO(port-deps): import { resolveInternalSessionKey, resolveMainSessionAlias } from "./sessions-helpers.js";

val AgentsListToolSchema = Type.Object({});

typealias AgentListEntry = Any /* TODO: translate TypeScript alias */

fun createAgentsListTool(opts?: {
  agentSessionKey?: string;
  /** Explicit agent ID override for cron/hook sessions. */
  requesterAgentIdOverride?: string;
}): AnyAgentTool {
  return {
    label: "Agents",
    name: "agents_list",
    description:
      'List OpenClaw agent ids you can target with `sessions_spawn` when `runtime="subagent"` (based on subagent allowlists).',
    parameters: AgentsListToolSchema,
    execute: async () => {
      val cfg = loadConfig();
      val { mainKey, alias } = resolveMainSessionAlias(cfg);
      val requesterInternalKey =
        typeof opts?.agentSessionKey == "string" && opts.agentSessionKey.trim()
          ? resolveInternalSessionKey({
              key: opts.agentSessionKey,
              alias,
              mainKey,
            })
          : alias;
      val requesterAgentId = normalizeAgentId(
        opts?.requesterAgentIdOverride ??
          parseAgentSessionKey(requesterInternalKey)?.agentId ??
          DEFAULT_AGENT_ID,
      );

      val allowAgents = resolveAgentConfig(cfg, requesterAgentId)?.subagents?.allowAgents ?: [];
      val allowAny = allowAgents.some((value) => value.trim() == "*");
      val allowSet = new Set(
        allowAgents
          .filter((value) => value.trim() && value.trim() != "*")
          .map((value) => normalizeAgentId(value)),
      );

      val configuredAgents = Array.isArray(cfg.agents?.list) ? cfg.agents?.list : [];
      val configuredIds = configuredAgents.map((entry) => normalizeAgentId(entry.id));
      val configuredNameMap = new Map<string, string>();
      for (val entry of configuredAgents) {
        val name = entry?.name?.trim() ?: "";
        if (!name) {
          continue;
        }
        configuredNameMap.set(normalizeAgentId(entry.id), name);
      }

      val allowed = new Set<string>();
      allowed.add(requesterAgentId);
      if (allowAny) {
        for (val id of configuredIds) {
          allowed.add(id);
        }
      } else {
        for (val id of allowSet) {
          allowed.add(id);
        }
      }

      val all = Array.from(allowed);
      val rest = all
        .filter((id) => id != requesterAgentId)
        .toSorted((a, b) => a.localeCompare(b));
      val ordered = [requesterAgentId, ...rest];
      val agents: AgentListEntry[] = ordered.map((id) => ({
        id,
        name: configuredNameMap.get(id),
        configured: configuredIds.includes(id),
      }));

      return jsonResult({
        requester: requesterAgentId,
        allowAny,
        agents,
      });
    },
  };
}
