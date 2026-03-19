package agents.platform_support.sandbox

// Source: src/agents/sandbox/tool-policy.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { resolveAgentConfig } from "../agent-scope.js";
// TODO(openclaw-kotlin-port): import { compileGlobPatterns, matchesAnyGlobPattern } from "../glob-pattern.js";
// TODO(openclaw-kotlin-port): import { expandToolGroups } from "../tool-policy.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_TOOL_ALLOW, DEFAULT_TOOL_DENY } from "./constants.js";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   SandboxToolPolicy,
// TODO(openclaw-kotlin-port):   SandboxToolPolicyResolved,
// TODO(openclaw-kotlin-port):   SandboxToolPolicySource,
// TODO(openclaw-kotlin-port): } from "./types.js";

fun normalizeGlob(value: String) {
  return value.trim().toLowerCase()
}

fun isToolAllowed(policy: SandboxToolPolicy, name: String) {
  val normalized = normalizeGlob(name)
  val deny = compileGlobPatterns({
    raw: expandToolGroups(policy.deny ?? []),
    normalize: normalizeGlob,
  })
  if (matchesAnyGlobPattern(normalized, deny)) {
    return false
  }
  val allow = compileGlobPatterns({
    raw: expandToolGroups(policy.allow ?? []),
    normalize: normalizeGlob,
  })
  if (allow.length === 0) {
    return true
  }
  return matchesAnyGlobPattern(normalized, allow)
}

fun resolveSandboxToolPolicyForAgent(
  cfg?: OpenClawConfig,
  agentId?: String,
): SandboxToolPolicyResolved {
  val agentConfig = cfg && agentId ? resolveAgentConfig(cfg, agentId) : Nothing?
  val agentAllow = agentConfig?.tools?.sandbox?.tools?.allow
  val agentDeny = agentConfig?.tools?.sandbox?.tools?.deny
  val globalAllow = cfg?.tools?.sandbox?.tools?.allow
  val globalDeny = cfg?.tools?.sandbox?.tools?.deny

  val allowSource = Array.isArray(agentAllow)
    ? ({
        source: "agent",
        key: "agents.list[].tools.sandbox.tools.allow",
      } satisfies SandboxToolPolicySource)
    : Array.isArray(globalAllow)
      ? ({
          source: "global",
          key: "tools.sandbox.tools.allow",
        } satisfies SandboxToolPolicySource)
      : ({
          source: "default",
          key: "tools.sandbox.tools.allow",
        } satisfies SandboxToolPolicySource)

  val denySource = Array.isArray(agentDeny)
    ? ({
        source: "agent",
        key: "agents.list[].tools.sandbox.tools.deny",
      } satisfies SandboxToolPolicySource)
    : Array.isArray(globalDeny)
      ? ({
          source: "global",
          key: "tools.sandbox.tools.deny",
        } satisfies SandboxToolPolicySource)
      : ({
          source: "default",
          key: "tools.sandbox.tools.deny",
        } satisfies SandboxToolPolicySource)

  val deny = Array.isArray(agentDeny)
    ? agentDeny
    : Array.isArray(globalDeny)
      ? globalDeny
      : [...DEFAULT_TOOL_DENY]
  val allow = Array.isArray(agentAllow)
    ? agentAllow
    : Array.isArray(globalAllow)
      ? globalAllow
      : [...DEFAULT_TOOL_ALLOW]

  val expandedDeny = expandToolGroups(deny)
  var expandedAllow = expandToolGroups(allow)

  // `image` is essential for multimodal workflows always include it in sandboxed
  // sessions unless explicitly denied.
  if (
    // Empty allowlist means "allow all" for `isToolAllowed`, so don't inject a
    // single tool that would accidentally turn it into an explicit allowlist.
    expandedAllow.length > 0 &&
    !expandedDeny.map((v) => v.toLowerCase()).includes("image") &&
    !expandedAllow.map((v) => v.toLowerCase()).includes("image")
  ) {
    expandedAllow = [...expandedAllow, "image"]
  }

  return {
    allow: expandedAllow,
    deny: expandedDeny,
    sources: {
      allow: allowSource,
      deny: denySource,
    },
  }
}
