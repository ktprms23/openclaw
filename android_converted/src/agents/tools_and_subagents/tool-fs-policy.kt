package agents.tools_and_subagents

// Converted from src/agents/tool-fs-policy.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { resolveAgentConfig } from "./agent-scope.js";

data class ToolFsPolicy(
    val workspaceOnly: Boolean,
)

fun createToolFsPolicy(params: { workspaceOnly?: Boolean }): ToolFsPolicy {
  return {
    workspaceOnly: params.workspaceOnly == true,
  }
}

fun resolveToolFsConfig(params: { cfg?: OpenClawConfig agentId?: String }): {
  workspaceOnly?: Boolean
} {
  val cfg = params.cfg
  val globalFs = cfg?.tools?.fs
  val agentFs =
    cfg && params.agentId ? resolveAgentConfig(cfg, params.agentId)?.tools?.fs : null
  return {
    workspaceOnly: agentFs?.workspaceOnly ?: globalFs?.workspaceOnly,
  }
}

fun resolveEffectiveToolFsWorkspaceOnly(params: {
  cfg?: OpenClawConfig
  agentId?: String
}): Boolean {
  return resolveToolFsConfig(params).workspaceOnly == true
}
