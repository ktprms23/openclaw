package agents.platform_support.sandbox

// Source: src/agents/sandbox/workspace-mounts.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { SANDBOX_AGENT_WORKSPACE_MOUNT } from "./constants.js";
// TODO(openclaw-kotlin-port): import type { SandboxWorkspaceAccess } from "./types.js";

fun mainWorkspaceMountSuffix(access: SandboxWorkspaceAccess): "" | ":ro" {
  return access === "rw" ? "" : ":ro"
}

fun agentWorkspaceMountSuffix(access: SandboxWorkspaceAccess): "" | ":ro" {
  return access === "ro" ? ":ro" : ""
}

fun appendWorkspaceMountArgs(params: {
  args: String[]
  workspaceDir: String
  agentWorkspaceDir: String
  workdir: String
  workspaceAccess: SandboxWorkspaceAccess
}) {
  val { args, workspaceDir, agentWorkspaceDir, workdir, workspaceAccess } = params

  args.push("-v", `${workspaceDir}:${workdir}${mainWorkspaceMountSuffix(workspaceAccess)}`)
  if (workspaceAccess !== "none" && workspaceDir !== agentWorkspaceDir) {
    args.push(
      "-v",
      `${agentWorkspaceDir}:${SANDBOX_AGENT_WORKSPACE_MOUNT}${agentWorkspaceMountSuffix(workspaceAccess)}`,
    )
  }
}
