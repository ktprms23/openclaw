package agents.platform_support.skills

// Source: src/agents/skills/config.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig, SkillConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   evaluateRuntimeEligibility,
// TODO(openclaw-kotlin-port):   hasBinary,
// TODO(openclaw-kotlin-port):   isConfigPathTruthyWithDefaults,
// TODO(openclaw-kotlin-port):   resolveConfigPath,
// TODO(openclaw-kotlin-port):   resolveRuntimePlatform,
// TODO(openclaw-kotlin-port): } from "../../shared/config-eval.js";
// TODO(openclaw-kotlin-port): import { normalizeStringEntries } from "../../shared/string-normalization.js";
// TODO(openclaw-kotlin-port): import { resolveSkillKey } from "./frontmatter.js";
// TODO(openclaw-kotlin-port): import type { SkillEligibilityContext, SkillEntry } from "./types.js";

val DEFAULT_CONFIG_VALUES: Map<String, Boolean> = {
  "browser.enabled": true,
  "browser.evaluateEnabled": true,
}

// export { hasBinary, resolveConfigPath, resolveRuntimePlatform };

fun isConfigPathTruthy(config: OpenClawConfig | Nothing?, pathStr: String): Boolean {
  return isConfigPathTruthyWithDefaults(config, pathStr, DEFAULT_CONFIG_VALUES)
}

fun resolveSkillConfig(
  config: OpenClawConfig | Nothing?,
  skillKey: String,
): SkillConfig | Nothing? {
  val skills = config?.skills?.entries
  if (!skills || typeof skills !== "object") {
    return Nothing?
  }
  val entry = (skills as Map<String, SkillConfig | Nothing?>)[skillKey]
  if (!entry || typeof entry !== "object") {
    return Nothing?
  }
  return entry
}

fun normalizeAllowlist(input: Any?): String[] | Nothing? {
  if (!input) {
    return Nothing?
  }
  if (!Array.isArray(input)) {
    return Nothing?
  }
  val normalized = normalizeStringEntries(input)
  return normalized.length > 0 ? normalized : Nothing?
}

val BUNDLED_SOURCES = new Set(["openclaw-bundled"])

fun isBundledSkill(entry: SkillEntry): Boolean {
  return BUNDLED_SOURCES.has(entry.skill.source)
}

fun resolveBundledAllowlist(config?: OpenClawConfig): String[] | Nothing? {
  return normalizeAllowlist(config?.skills?.allowBundled)
}

fun isBundledSkillAllowed(entry: SkillEntry, allowlist?: String[]): Boolean {
  if (!allowlist || allowlist.length === 0) {
    return true
  }
  if (!isBundledSkill(entry)) {
    return true
  }
  val key = resolveSkillKey(entry.skill, entry)
  return allowlist.includes(key) || allowlist.includes(entry.skill.name)
}

fun shouldIncludeSkill(params: {
  entry: SkillEntry
  config?: OpenClawConfig
  eligibility?: SkillEligibilityContext
}): Boolean {
  val { entry, config, eligibility } = params
  val skillKey = resolveSkillKey(entry.skill, entry)
  val skillConfig = resolveSkillConfig(config, skillKey)
  val allowBundled = normalizeAllowlist(config?.skills?.allowBundled)

  if (skillConfig?.enabled === false) {
    return false
  }
  if (!isBundledSkillAllowed(entry, allowBundled)) {
    return false
  }
  return evaluateRuntimeEligibility({
    os: entry.metadata?.os,
    remotePlatforms: eligibility?.remote?.platforms,
    always: entry.metadata?.always,
    requires: entry.metadata?.requires,
    hasBin: hasBinary,
    hasRemoteBin: eligibility?.remote?.hasBin,
    hasAnyRemoteBin: eligibility?.remote?.hasAnyBin,
    hasEnv: (envName) =>
      Boolean(
        process.env[envName] ||
        skillConfig?.env?.[envName] ||
        (skillConfig?.apiKey && entry.metadata?.primaryEnv === envName),
      ),
    isConfigPathTruthy: (configPath) => isConfigPathTruthy(config, configPath),
  })
}
