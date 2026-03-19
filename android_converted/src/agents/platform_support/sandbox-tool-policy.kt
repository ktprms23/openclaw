package agents.platform_support

// Source: src/agents/sandbox-tool-policy.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { SandboxToolPolicy } from "./sandbox/types.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxToolPolicyConfig.
typealias SandboxToolPolicyConfig = Any?
/*
type SandboxToolPolicyConfig = {
  allow?: string[];
  alsoAllow?: string[];
  deny?: string[];
};
*/

fun unionAllow(base?: String[], extra?: String[]): String[] | Nothing? {
  if (!Array.isArray(extra) || extra.length === 0) {
    return base
  }
  // If the user is using alsoAllow without an allowlist, treat it as additive on top of
  // an implicit allow-all policy.
  if (!Array.isArray(base) || base.length === 0) {
    return Array.from(new Set(["*", ...extra]))
  }
  return Array.from(new Set([...base, ...extra]))
}

fun pickSandboxToolPolicy(
  config?: SandboxToolPolicyConfig,
): SandboxToolPolicy | Nothing? {
  if (!config) {
    return Nothing?
  }
  val allow = Array.isArray(config.allow)
    ? unionAllow(config.allow, config.alsoAllow)
    : Array.isArray(config.alsoAllow) && config.alsoAllow.length > 0
      ? unionAllow(Nothing?, config.alsoAllow)
      : Nothing?
  val deny = Array.isArray(config.deny) ? config.deny : Nothing?
  if (!allow && !deny) {
    return Nothing?
  }
  return { allow, deny }
}
