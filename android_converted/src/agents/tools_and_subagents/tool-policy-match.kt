package agents.tools_and_subagents

// Converted from src/agents/tool-policy-match.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { compileGlobPatterns, matchesAnyGlobPattern } from "./glob-pattern.js";
// TODO: TypeScript import retained for manual wiring: import type { SandboxToolPolicy } from "./sandbox/types.js";
// TODO: TypeScript import retained for manual wiring: import { expandToolGroups, normalizeToolName } from "./tool-policy.js";

fun makeToolPolicyMatcher(policy: SandboxToolPolicy) {
  val deny = compileGlobPatterns({
    raw: expandToolGroups(policy.deny ?: []),
    normalize: normalizeToolName,
  })
  val allow = compileGlobPatterns({
    raw: expandToolGroups(policy.allow ?: []),
    normalize: normalizeToolName,
  })
  return (name: String) {
    val normalized = normalizeToolName(name)
    if (matchesAnyGlobPattern(normalized, deny)) {
      return false
    }
    if (allow.length == 0) {
      return true
    }
    if (matchesAnyGlobPattern(normalized, allow)) {
      return true
    }
    if (normalized == "apply_patch" && matchesAnyGlobPattern("exec", allow)) {
      return true
    }
    return false
  }
}

fun isToolAllowedByPolicyName(name: String, policy?: SandboxToolPolicy): Boolean {
  if (!policy) {
    return true
  }
  return makeToolPolicyMatcher(policy)(name)
}

fun isToolAllowedByPolicies(
  name: String,
  policies: List<SandboxToolPolicy?>,
) {
  return policies.every((policy) -> isToolAllowedByPolicyName(name, policy))
}
