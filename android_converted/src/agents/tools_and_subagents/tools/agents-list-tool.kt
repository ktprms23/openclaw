package agents.tools_and_subagents.tools

// Converted from src/agents/tools/agents-list-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  DEFAULT_AGENT_ID,
  normalizeAgentId,
  parseAgentSessionKey,
} from "../../routing/session-key.js"
// TODO: TypeScript import retained for manual wiring: import { resolveAgentConfig } from "../agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { resolveInternalSessionKey, resolveMainSessionAlias } from "./sessions-helpers.js";

val AgentsListToolSchema = Type.Object({})

data class AgentListEntry(
    val id: String,
    val name: String?,
    val configured: Boolean,
)

fun createAgentsListTool(opts?: {
  agentSessionKey?: String
  /** Explicit agent ID override for cron/hook sessions. */
  requesterAgentIdOverride?: String
}): AnyAgentTool {
  return {
    label: String /* "Agents" */,
    name: String /* "agents_list" */,
    description:
      'List OpenClaw agent ids you can target with `sessions_spawn` when `runtime="subagent"` (based on subagent allowlists).',
    parameters: AgentsListToolSchema,
    execute: async () {
      val cfg = loadConfig()
      val { mainKey, alias } = resolveMainSessionAlias(cfg)
      val requesterInternalKey =
        opts?.agentSessionKey is String && opts.agentSessionKey.trim()
          ? resolveInternalSessionKey({
              key: opts.agentSessionKey,
              alias,
              mainKey,
            })
          : alias
      val requesterAgentId = normalizeAgentId(
        opts?.requesterAgentIdOverride ??
          parseAgentSessionKey(requesterInternalKey)?.agentId ??
          DEFAULT_AGENT_ID,
      )

      val allowAgents = resolveAgentConfig(cfg, requesterAgentId)?.subagents?.allowAgents ?: []
      val allowAny = allowAgents.some((value) -> value.trim() == "*")
      val allowSet = mutableSetOf(
        allowAgents
          .filter((value) -> value.trim() && value.trim() != "*")
          .map((value) -> normalizeAgentId(value)),
      )

      val configuredAgents = Array.isArray(cfg.agents?.list) ? cfg.agents?.list : []
      val configuredIds = configuredAgents.map((entry) -> normalizeAgentId(entry.id))
      val configuredNameMap = new MutableMap<String, String>()
      for (val entry of configuredAgents) {
        val name = entry?.name?.trim() ?: ""
        if (!name) {
          continue
        }
        configuredNameMap.set(normalizeAgentId(entry.id), name)
      }

      val allowed = new MutableSet<String>()
      allowed.add(requesterAgentId)
      if (allowAny) {
        for (val id of configuredIds) {
          allowed.add(id)
        }
      } else {
        for (val id of allowSet) {
          allowed.add(id)
        }
      }

      val all = Array.from(allowed)
      val rest = all
        .filter((id) -> id != requesterAgentId)
        .toSorted((a, b) -> a.localeCompare(b))
      val ordered = [requesterAgentId, ...rest]
      val agents: List<AgentListEntry> = ordered.map((id) -> ({
        id,
        name: configuredNameMap.get(id),
        configured: configuredIds.includes(id),
      }))

      return jsonResult({
        requester: requesterAgentId,
        allowAny,
        agents,
      })
    },
  }
}
