@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-fs-policy.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../config/config.js";
// TODO(port-deps): import { resolveAgentConfig } from "./agent-scope.js";

typealias ToolFsPolicy = Any /* TODO: translate TypeScript alias */

fun createToolFsPolicy(params: { workspaceOnly?: boolean }): ToolFsPolicy {
  return {
    workspaceOnly: params.workspaceOnly == true,
  };
}

fun resolveToolFsConfig(params: { cfg?: OpenClawConfig; agentId?: string }): {
  workspaceOnly?: boolean;
} {
  val cfg = params.cfg;
  val globalFs = cfg?.tools?.fs;
  val agentFs =
    cfg && params.agentId ? resolveAgentConfig(cfg, params.agentId)?.tools?.fs : null;
  return {
    workspaceOnly: agentFs?.workspaceOnly ?: globalFs?.workspaceOnly,
  };
}

fun resolveEffectiveToolFsWorkspaceOnly(params: {
  cfg?: OpenClawConfig;
  agentId?: string;
}): boolean {
  return resolveToolFsConfig(params).workspaceOnly == true;
}
