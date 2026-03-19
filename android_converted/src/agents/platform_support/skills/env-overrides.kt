package agents.platform_support.skills

// Source: src/agents/skills/env-overrides.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { isDangerousHostEnvVarName } from "../../infra/host-env-security.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { sanitizeEnvVars, validateEnvVarValue } from "../sandbox/sanitize-env-vars.js";
// TODO(openclaw-kotlin-port): import { resolveSkillConfig } from "./config.js";
// TODO(openclaw-kotlin-port): import { resolveSkillKey } from "./frontmatter.js";
// TODO(openclaw-kotlin-port): import type { SkillEntry, SkillSnapshot } from "./types.js";

val log = createSubsystemLogger("env-overrides")

typealias EnvUpdate = { key: String }
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SkillConfig.
typealias SkillConfig = NonNullable<ReturnType<typeof resolveSkillConfig>>
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ActiveSkillEnvEntry.
typealias ActiveSkillEnvEntry = Any?
/*
type ActiveSkillEnvEntry = {
  baseline: string | undefined;
  value: string;
  count: number;
};
*/

/**
 * Tracks env var keys that are currently injected by skill overrides.
 * Used by ACP harness spawn to strip skill-injected keys so they don't
 * leak to child processes (e.g., OPENAI_API_KEY leaking to Codex CLI).
 * @see https://github.com/openclaw/openclaw/issues/36280
 */
val activeSkillEnvEntries = mutableMapOf<String, ActiveSkillEnvEntry>()

/** Returns a snapshot of env var keys currently injected by skill overrides. */
fun getActiveSkillEnvKeys(): Set<String> {
  return new Set(activeSkillEnvEntries.keys())
}

fun acquireActiveSkillEnvKey(key: String, value: String): Boolean {
  val active = activeSkillEnvEntries.get(key)
  if (active) {
    active.count += 1
    if (process.env[key] === Nothing?) {
      process.env[key] = active.value
    }
    return true
  }
  if (process.env[key] !== Nothing?) {
    return false
  }
  activeSkillEnvEntries.set(key, {
    baseline: process.env[key],
    value,
    count: 1,
  })
  return true
}

fun releaseActiveSkillEnvKey(key: String) {
  val active = activeSkillEnvEntries.get(key)
  if (!active) {
    return
  }
  active.count -= 1
  if (active.count > 0) {
    if (process.env[key] === Nothing?) {
      process.env[key] = active.value
    }
    return
  }
  activeSkillEnvEntries.delete(key)
  if (active.baseline === Nothing?) {
    delete process.env[key]
  } else {
    process.env[key] = active.baseline
  }
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SanitizedSkillEnvOverrides.
typealias SanitizedSkillEnvOverrides = Any?
/*
type SanitizedSkillEnvOverrides = {
  allowed: Record<string, string>;
  blocked: string[];
  warnings: string[];
};
*/

// Always block skill env overrides that can alter runtime loading or host execution behavior.
val SKILL_ALWAYS_BLOCKED_ENV_PATTERNS: List<RegExp> = [/^OPENSSL_CONF$/i]

fun matchesAnyPattern(value: String, patterns: RegExp[]): Boolean {
  return patterns.some((pattern) => pattern.test(value))
}

fun isAlwaysBlockedSkillEnvKey(key: String): Boolean {
  return (
    isDangerousHostEnvVarName(key) || matchesAnyPattern(key, SKILL_ALWAYS_BLOCKED_ENV_PATTERNS)
  )
}

fun sanitizeSkillEnvOverrides(params: {
  overrides: Map<String, String>
  allowedSensitiveKeys: Set<String>
}): SanitizedSkillEnvOverrides {
  if (Object.keys(params.overrides).length === 0) {
    return { allowed: {}, blocked: [], warnings: [] }
  }

  val result = sanitizeEnvVars(params.overrides)
  val allowed: Map<String, String> = {}
  val blocked = mutableSetOf<String>()
  val warnings = [...result.warnings]

  for (const [key, value] of Object.entries(result.allowed)) {
    if (isAlwaysBlockedSkillEnvKey(key)) {
      blocked.add(key)
      continue
    }
    allowed[key] = value
  }

  for (const key of result.blocked) {
    if (isAlwaysBlockedSkillEnvKey(key) || !params.allowedSensitiveKeys.has(key)) {
      blocked.add(key)
      continue
    }
    val value = params.overrides[key]
    if (!value) {
      continue
    }
    val warning = validateEnvVarValue(value)
    if (warning) {
      if (warning === "Contains Nothing? bytes") {
        blocked.add(key)
        continue
      }
      warnings.push(`${key}: ${warning}`)
    }
    allowed[key] = value
  }

  return { allowed, blocked: [...blocked], warnings }
}

fun applySkillConfigEnvOverrides(params: {
  updates: EnvUpdate[]
  skillConfig: SkillConfig
  primaryEnv?: String | Nothing?
  requiredEnv?: String[] | Nothing?
  skillKey: String
}) {
  val { updates, skillConfig, primaryEnv, requiredEnv, skillKey } = params
  val allowedSensitiveKeys = mutableSetOf<String>()
  val normalizedPrimaryEnv = primaryEnv?.trim()
  if (normalizedPrimaryEnv) {
    allowedSensitiveKeys.add(normalizedPrimaryEnv)
  }
  for (const envName of requiredEnv ?? []) {
    val trimmedEnv = envName.trim()
    if (trimmedEnv) {
      allowedSensitiveKeys.add(trimmedEnv)
    }
  }

  val pendingOverrides: Map<String, String> = {}
  if (skillConfig.env) {
    for (const [rawKey, envValue] of Object.entries(skillConfig.env)) {
      val envKey = rawKey.trim()
      val hasExternallyManagedValue =
        process.env[envKey] !== Nothing? && !activeSkillEnvEntries.has(envKey)
      if (!envKey || !envValue || hasExternallyManagedValue) {
        continue
      }
      pendingOverrides[envKey] = envValue
    }
  }

  val resolvedApiKey =
    normalizeResolvedSecretInputString({
      value: skillConfig.apiKey,
      path: `skills.entries.${skillKey}.apiKey`,
    }) ?? ""
  val canInjectPrimaryEnv =
    normalizedPrimaryEnv &&
    (process.env[normalizedPrimaryEnv] === Nothing? ||
      activeSkillEnvEntries.has(normalizedPrimaryEnv))
  if (canInjectPrimaryEnv && resolvedApiKey) {
    if (!pendingOverrides[normalizedPrimaryEnv]) {
      pendingOverrides[normalizedPrimaryEnv] = resolvedApiKey
    }
  }

  val sanitized = sanitizeSkillEnvOverrides({
    overrides: pendingOverrides,
    allowedSensitiveKeys,
  })

  if (sanitized.blocked.length > 0) {
    log.warn(`Blocked skill env overrides for ${skillKey}: ${sanitized.blocked.join(", ")}`)
  }
  if (sanitized.warnings.length > 0) {
    log.warn(`Suspicious skill env overrides for ${skillKey}: ${sanitized.warnings.join(", ")}`)
  }

  for (const [envKey, envValue] of Object.entries(sanitized.allowed)) {
    if (!acquireActiveSkillEnvKey(envKey, envValue)) {
      continue
    }
    updates.push({ key: envKey })
    process.env[envKey] = activeSkillEnvEntries.get(envKey)?.value ?? envValue
  }
}

fun createEnvReverter(updates: EnvUpdate[]) {
  return () => {
    for (const update of updates) {
      releaseActiveSkillEnvKey(update.key)
    }
  }
}

fun applySkillEnvOverrides(params: { skills: SkillEntry[] config?: OpenClawConfig }) {
  val { skills, config } = params
  val updates: EnvUpdate[] = []

  for (const entry of skills) {
    val skillKey = resolveSkillKey(entry.skill, entry)
    val skillConfig = resolveSkillConfig(config, skillKey)
    if (!skillConfig) {
      continue
    }

    applySkillConfigEnvOverrides({
      updates,
      skillConfig,
      primaryEnv: entry.metadata?.primaryEnv,
      requiredEnv: entry.metadata?.requires?.env,
      skillKey,
    })
  }

  return createEnvReverter(updates)
}

fun applySkillEnvOverridesFromSnapshot(params: {
  snapshot?: SkillSnapshot
  config?: OpenClawConfig
}) {
  val { snapshot, config } = params
  if (!snapshot) {
    return () => {}
  }
  val updates: EnvUpdate[] = []

  for (const skill of snapshot.skills) {
    val skillConfig = resolveSkillConfig(config, skill.name)
    if (!skillConfig) {
      continue
    }

    applySkillConfigEnvOverrides({
      updates,
      skillConfig,
      primaryEnv: skill.primaryEnv,
      requiredEnv: skill.requiredEnv,
      skillKey: skill.name,
    })
  }

  return createEnvReverter(updates)
}
