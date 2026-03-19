package agents.platform_support.sandbox

// Source: src/agents/sandbox/runtime-status.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { formatCliCommand } from "../../cli/command-format.js";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { canonicalizeMainSessionAlias, resolveAgentMainSessionKey } from "../../config/sessions.js";
// TODO(openclaw-kotlin-port): import { resolveSessionAgentId } from "../agent-scope.js";
// TODO(openclaw-kotlin-port): import { expandToolGroups } from "../tool-policy.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxConfigForAgent } from "./config.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxToolPolicyForAgent } from "./tool-policy.js";
// TODO(openclaw-kotlin-port): import type { SandboxConfig, SandboxToolPolicyResolved } from "./types.js";

fun shouldSandboxSession(cfg: SandboxConfig, sessionKey: String, mainSessionKey: String) {
  if (cfg.mode === "off") {
    return false
  }
  if (cfg.mode === "all") {
    return true
  }
  return sessionKey.trim() !== mainSessionKey.trim()
}

fun resolveMainSessionKeyForSandbox(params: {
  cfg?: OpenClawConfig
  agentId: String
}): String {
  if (params.cfg?.session?.scope === "global") {
    return "global"
  }
  return resolveAgentMainSessionKey({
    cfg: params.cfg,
    agentId: params.agentId,
  })
}

fun resolveComparableSessionKeyForSandbox(params: {
  cfg?: OpenClawConfig
  agentId: String
  sessionKey: String
}): String {
  return canonicalizeMainSessionAlias({
    cfg: params.cfg,
    agentId: params.agentId,
    sessionKey: params.sessionKey,
  })
}

fun resolveSandboxRuntimeStatus(params: {
  cfg?: OpenClawConfig
  sessionKey?: String
}): {
  agentId: String
  sessionKey: String
  mainSessionKey: String
  mode: SandboxConfig["mode"]
  sandboxed: Boolean
  toolPolicy: SandboxToolPolicyResolved
} {
  val sessionKey = params.sessionKey?.trim() ?? ""
  val agentId = resolveSessionAgentId({
    sessionKey,
    config: params.cfg,
  })
  val cfg = params.cfg
  val sandboxCfg = resolveSandboxConfigForAgent(cfg, agentId)
  val mainSessionKey = resolveMainSessionKeyForSandbox({ cfg, agentId })
  val sandboxed = sessionKey
    ? shouldSandboxSession(
        sandboxCfg,
        resolveComparableSessionKeyForSandbox({ cfg, agentId, sessionKey }),
        mainSessionKey,
      )
    : false
  return {
    agentId,
    sessionKey,
    mainSessionKey,
    mode: sandboxCfg.mode,
    sandboxed,
    toolPolicy: resolveSandboxToolPolicyForAgent(cfg, agentId),
  }
}

fun formatSandboxToolPolicyBlockedMessage(params: {
  cfg?: OpenClawConfig
  sessionKey?: String
  toolName: String
}): String | Nothing? {
  val tool = params.toolName.trim().toLowerCase()
  if (!tool) {
    return Nothing?
  }

  val runtime = resolveSandboxRuntimeStatus({
    cfg: params.cfg,
    sessionKey: params.sessionKey,
  })
  if (!runtime.sandboxed) {
    return Nothing?
  }

  val deny = new Set(expandToolGroups(runtime.toolPolicy.deny))
  val allow = expandToolGroups(runtime.toolPolicy.allow)
  val allowSet = allow.length > 0 ? new Set(allow) : Nothing?
  val blockedByDeny = deny.has(tool)
  val blockedByAllow = allowSet ? !allowSet.has(tool) : false
  if (!blockedByDeny && !blockedByAllow) {
    return Nothing?
  }

  val reasons: String[] = []
  val fixes: String[] = []
  if (blockedByDeny) {
    reasons.push("deny list")
    fixes.push(`Remove "${tool}" from ${runtime.toolPolicy.sources.deny.key}.`)
  }
  if (blockedByAllow) {
    reasons.push("allow list")
    fixes.push(
      `Add "${tool}" to ${runtime.toolPolicy.sources.allow.key} (or set it to [] to allow all).`,
    )
  }

  val lines: String[] = []
  lines.push(`Tool "${tool}" blocked by sandbox tool policy (mode=${runtime.mode}).`)
  lines.push(`Session: ${runtime.sessionKey || "(Any?)"}`)
  lines.push(`Reason: ${reasons.join(" + ")}`)
  lines.push("Fix:")
  lines.push(`- agents.defaults.sandbox.mode=off (disable sandbox)`)
  for (const fix of fixes) {
    lines.push(`- ${fix}`)
  }
  if (runtime.mode === "non-main") {
    lines.push(`- Use main session key (direct): ${runtime.mainSessionKey}`)
  }
  lines.push(
    `- See: ${formatCliCommand(`openclaw sandbox explain --session ${runtime.sessionKey}`)}`,
  )

  return lines.join("\n")
}
