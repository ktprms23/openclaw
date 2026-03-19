@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-policy-match.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { compileGlobPatterns, matchesAnyGlobPattern } from "./glob-pattern.js";
// TODO(port-deps): import type { SandboxToolPolicy } from "./sandbox/types.js";
// TODO(port-deps): import { expandToolGroups, normalizeToolName } from "./tool-policy.js";

fun makeToolPolicyMatcher(policy: SandboxToolPolicy) {
  val deny = compileGlobPatterns({
    raw: expandToolGroups(policy.deny ?: []),
    normalize: normalizeToolName,
  });
  val allow = compileGlobPatterns({
    raw: expandToolGroups(policy.allow ?: []),
    normalize: normalizeToolName,
  });
  return (name: string) => {
    val normalized = normalizeToolName(name);
    if (matchesAnyGlobPattern(normalized, deny)) {
      return false;
    }
    if (allow.length == 0) {
      return true;
    }
    if (matchesAnyGlobPattern(normalized, allow)) {
      return true;
    }
    if (normalized == "apply_patch" && matchesAnyGlobPattern("exec", allow)) {
      return true;
    }
    return false;
  };
}

fun isToolAllowedByPolicyName(name: string, policy?: SandboxToolPolicy): boolean {
  if (!policy) {
    return true;
  }
  return makeToolPolicyMatcher(policy)(name);
}

fun isToolAllowedByPolicies(
  name: string,
  policies: Array<SandboxToolPolicy | null>,
) {
  return policies.every((policy) => isToolAllowedByPolicyName(name, policy));
}
