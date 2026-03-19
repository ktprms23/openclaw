package agents.platform_support.sandbox

// Source: src/agents/sandbox/fs-bridge-shell-command-plans.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { AnchoredSandboxEntry, PathSafetyCheck } from "./fs-bridge-path-safety.js";
// TODO(openclaw-kotlin-port): import type { SandboxResolvedFsPath } from "./fs-paths.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxFsCommandPlan.
typealias SandboxFsCommandPlan = Any?
/*
export type SandboxFsCommandPlan = {
  checks: PathSafetyCheck[];
  script: string;
  args?: string[];
  stdin?: Buffer | string;
  recheckBeforeCommand?: boolean;
  allowFailure?: boolean;
};
*/

fun buildStatPlan(
  target: SandboxResolvedFsPath,
  anchoredTarget: AnchoredSandboxEntry,
): SandboxFsCommandPlan {
  return {
    checks: [{ target, options: { action: "stat files" } }],
    script: 'set -eu\ncd -- "$1"\nstat -c "%F|%s|%Y" -- "$2"',
    args: [anchoredTarget.canonicalParentPath, anchoredTarget.basename],
    allowFailure: true,
  }
}
