package agents.platform_support

// Source: src/agents/skills.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import type { SkillsInstallPreferences } from "./skills/types.js";

// export {
//   hasBinary,
//   isBundledSkillAllowed,
//   isConfigPathTruthy,
//   resolveBundledAllowlist,
//   resolveConfigPath,
//   resolveRuntimePlatform,
//   resolveSkillConfig,
// } from "./skills/config.js";
// export {
//   applySkillEnvOverrides,
//   applySkillEnvOverridesFromSnapshot,
// } from "./skills/env-overrides.js";
// export type {
//   OpenClawSkillMetadata,
//   SkillEligibilityContext,
//   SkillCommandSpec,
//   SkillEntry,
//   SkillInstallSpec,
//   SkillSnapshot,
//   SkillsInstallPreferences,
// } from "./skills/types.js";
// export {
//   buildWorkspaceSkillSnapshot,
//   buildWorkspaceSkillsPrompt,
//   buildWorkspaceSkillCommandSpecs,
//   filterWorkspaceSkillEntries,
//   loadWorkspaceSkillEntries,
//   resolveSkillsPromptForRun,
//   syncSkillsToWorkspace,
// } from "./skills/workspace.js";

fun resolveSkillsInstallPreferences(config?: OpenClawConfig): SkillsInstallPreferences {
  val raw = config?.skills?.install
  val preferBrew = raw?.preferBrew ?? true
  val managerRaw = typeof raw?.nodeManager === "String" ? raw.nodeManager.trim() : ""
  val manager = managerRaw.toLowerCase()
  val nodeManager: SkillsInstallPreferences["nodeManager"] =
    manager === "pnpm" || manager === "yarn" || manager === "bun" || manager === "npm"
      ? manager
      : "npm"
  return { preferBrew, nodeManager }
}
