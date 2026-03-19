package agents.platform_support.skills

// Source: src/agents/skills/types.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { Skill } from "@mariozechner/pi-coding-agent";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillInstallSpec.
typealias SkillInstallSpec = Any?
/*
export type SkillInstallSpec = {
  id?: string;
  kind: "brew" | "node" | "go" | "uv" | "download";
  label?: string;
  bins?: string[];
  os?: string[];
  formula?: string;
  package?: string;
  module?: string;
  url?: string;
  archive?: string;
  extract?: boolean;
  stripComponents?: number;
  targetDir?: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OpenClawSkillMetadata.
typealias OpenClawSkillMetadata = Any?
/*
export type OpenClawSkillMetadata = {
  always?: boolean;
  skillKey?: string;
  primaryEnv?: string;
  emoji?: string;
  homepage?: string;
  os?: string[];
  requires?: {
    bins?: string[];
    anyBins?: string[];
    env?: string[];
    config?: string[];
  };
  install?: SkillInstallSpec[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillInvocationPolicy.
typealias SkillInvocationPolicy = Any?
/*
export type SkillInvocationPolicy = {
  userInvocable: boolean;
  disableModelInvocation: boolean;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillCommandDispatchSpec.
typealias SkillCommandDispatchSpec = Any?
/*
export type SkillCommandDispatchSpec = {
  kind: "tool";
  /** Name of the tool to invoke (AnyAgentTool.name). */
  toolName: string;
  /**
   * How to forward user-provided args to the tool.
   * - raw: forward the raw args string (no core parsing).
   */
  argMode?: "raw";
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillCommandSpec.
typealias SkillCommandSpec = Any?
/*
export type SkillCommandSpec = {
  name: string;
  skillName: string;
  description: string;
  /** Optional deterministic dispatch behavior for this command. */
  dispatch?: SkillCommandDispatchSpec;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillsInstallPreferences.
typealias SkillsInstallPreferences = Any?
/*
export type SkillsInstallPreferences = {
  preferBrew: boolean;
  nodeManager: "npm" | "pnpm" | "yarn" | "bun";
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ParsedSkillFrontmatter.
typealias ParsedSkillFrontmatter = Map<String, String>

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillEntry.
typealias SkillEntry = Any?
/*
export type SkillEntry = {
  skill: Skill;
  frontmatter: ParsedSkillFrontmatter;
  metadata?: OpenClawSkillMetadata;
  invocation?: SkillInvocationPolicy;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillEligibilityContext.
typealias SkillEligibilityContext = Any?
/*
export type SkillEligibilityContext = {
  remote?: {
    platforms: string[];
    hasBin: (bin: string) => boolean;
    hasAnyBin: (bins: string[]) => boolean;
    note?: string;
  };
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillSnapshot.
typealias SkillSnapshot = Any?
/*
export type SkillSnapshot = {
  prompt: string;
  skills: Array<{ name: string; primaryEnv?: string; requiredEnv?: string[] }>;
  /** Normalized agent-level filter used to build this snapshot; undefined means unrestricted. */
  skillFilter?: string[];
  resolvedSkills?: Skill[];
  version?: number;
};
*/
