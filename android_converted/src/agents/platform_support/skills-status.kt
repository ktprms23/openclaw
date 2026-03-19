package agents.platform_support

// Source: src/agents/skills-status.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { evaluateEntryRequirementsForCurrentPlatform } from "../shared/entry-status.js";
// TODO(openclaw-kotlin-port): import type { RequirementConfigCheck, Requirements } from "../shared/requirements.js";
// TODO(openclaw-kotlin-port): import { CONFIG_DIR } from "../utils.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   hasBinary,
// TODO(openclaw-kotlin-port):   isBundledSkillAllowed,
// TODO(openclaw-kotlin-port):   isConfigPathTruthy,
// TODO(openclaw-kotlin-port):   loadWorkspaceSkillEntries,
// TODO(openclaw-kotlin-port):   resolveBundledAllowlist,
// TODO(openclaw-kotlin-port):   resolveSkillConfig,
// TODO(openclaw-kotlin-port):   resolveSkillsInstallPreferences,
// TODO(openclaw-kotlin-port):   type SkillEntry,
// TODO(openclaw-kotlin-port):   type SkillEligibilityContext,
// TODO(openclaw-kotlin-port):   type SkillInstallSpec,
// TODO(openclaw-kotlin-port):   type SkillsInstallPreferences,
// TODO(openclaw-kotlin-port): } from "./skills.js";
// TODO(openclaw-kotlin-port): import { resolveBundledSkillsContext } from "./skills/bundled-context.js";

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SkillStatusConfigCheck.
typealias SkillStatusConfigCheck = RequirementConfigCheck

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillInstallOption.
typealias SkillInstallOption = Any?
/*
export type SkillInstallOption = {
  id: string;
  kind: SkillInstallSpec["kind"];
  label: string;
  bins: string[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillStatusEntry.
typealias SkillStatusEntry = Any?
/*
export type SkillStatusEntry = {
  name: string;
  description: string;
  source: string;
  bundled: boolean;
  filePath: string;
  baseDir: string;
  skillKey: string;
  primaryEnv?: string;
  emoji?: string;
  homepage?: string;
  always: boolean;
  disabled: boolean;
  blockedByAllowlist: boolean;
  eligible: boolean;
  requirements: Requirements;
  missing: Requirements;
  configChecks: SkillStatusConfigCheck[];
  install: SkillInstallOption[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillStatusReport.
typealias SkillStatusReport = Any?
/*
export type SkillStatusReport = {
  workspaceDir: string;
  managedSkillsDir: string;
  skills: SkillStatusEntry[];
};
*/

fun resolveSkillKey(entry: SkillEntry): String {
  return entry.metadata?.skillKey ?? entry.skill.name
}

fun selectPreferredInstallSpec(
  install: SkillInstallSpec[],
  prefs: SkillsInstallPreferences,
): { spec: SkillInstallSpec index: Double } | Nothing? {
  if (install.length === 0) {
    return Nothing?
  }

  val indexed = install.map((spec, index) => ({ spec, index }))
  val findKind = { kind: SkillInstallSpec["kind"] ->
    indexed.find((item) => item.spec.kind === kind)

  val brewSpec = findKind("brew")
  val nodeSpec = findKind("node")
  val goSpec = findKind("go")
  val uvSpec = findKind("uv")
  val downloadSpec = findKind("download")
  val brewAvailable = hasBinary("brew")

  // Table-driven preference chain first match wins.
  val pickers: List<() => { spec: SkillInstallSpec index: Double } | Nothing?> = [
    () => (prefs.preferBrew && brewAvailable ? brewSpec : Nothing?),
    () => uvSpec,
    () => nodeSpec,
    // Only prefer brew when available to avoid guaranteed failure on Linux/Docker.
    () => (brewAvailable ? brewSpec : Nothing?),
    () => goSpec,
    // Prefer download over an unavailable brew spec.
    () => downloadSpec,
    // Last resort: surface descriptive brew-missing error instead of "no installer found".
    () => brewSpec,
    () => indexed[0],
  ]

  for (const pick of pickers) {
    val selected = pick()
    if (selected) {
      return selected
    }
  }

  return Nothing?
}

fun normalizeInstallOptions(
  entry: SkillEntry,
  prefs: SkillsInstallPreferences,
): SkillInstallOption[] {
  // If the skill is explicitly OS-scoped, don't surface install actions on unsupported platforms.
  // (Installers run locally remote OS eligibility is handled separately.)
  val requiredOs = entry.metadata?.os ?? []
  if (requiredOs.length > 0 && !requiredOs.includes(process.platform)) {
    return []
  }

  val install = entry.metadata?.install ?? []
  if (install.length === 0) {
    return []
  }

  val platform = process.platform
  val filtered = install.filter((spec) => {
    val osList = spec.os ?? []
    return osList.length === 0 || osList.includes(platform)
  })
  if (filtered.length === 0) {
    return []
  }

  val toOption = (spec: SkillInstallSpec, index: Double): SkillInstallOption => {
    val id = (spec.id ?? `${spec.kind}-${index}`).trim()
    val bins = spec.bins ?? []
    var label = (spec.label ?? "").trim()
    if (spec.kind === "node" && spec.package) {
      label = `Install ${spec.package} (${prefs.nodeManager})`
    }
    if (!label) {
      if (spec.kind === "brew" && spec.formula) {
        label = `Install ${spec.formula} (brew)`
      } else if (spec.kind === "node" && spec.package) {
        label = `Install ${spec.package} (${prefs.nodeManager})`
      } else if (spec.kind === "go" && spec.module) {
        label = `Install ${spec.module} (go)`
      } else if (spec.kind === "uv" && spec.package) {
        label = `Install ${spec.package} (uv)`
      } else if (spec.kind === "download" && spec.url) {
        val url = spec.url.trim()
        val last = url.split("/").pop()
        label = `Download ${last && last.length > 0 ? last : url}`
      } else {
        label = "Run installer"
      }
    }
    return { id, kind: spec.kind, label, bins }
  }

  val allDownloads = filtered.every((spec) => spec.kind === "download")
  if (allDownloads) {
    return filtered.map((spec, index) => toOption(spec, index))
  }

  val preferred = selectPreferredInstallSpec(filtered, prefs)
  if (!preferred) {
    return []
  }
  return [toOption(preferred.spec, preferred.index)]
}

fun buildSkillStatus(
  entry: SkillEntry,
  config?: OpenClawConfig,
  prefs?: SkillsInstallPreferences,
  eligibility?: SkillEligibilityContext,
  bundledNames?: Set<String>,
): SkillStatusEntry {
  val skillKey = resolveSkillKey(entry)
  val skillConfig = resolveSkillConfig(config, skillKey)
  val disabled = skillConfig?.enabled === false
  val allowBundled = resolveBundledAllowlist(config)
  val blockedByAllowlist = !isBundledSkillAllowed(entry, allowBundled)
  val always = entry.metadata?.always === true
  val isEnvSatisfied = { envName: String ->
    Boolean(
      process.env[envName] ||
      skillConfig?.env?.[envName] ||
      (skillConfig?.apiKey && entry.metadata?.primaryEnv === envName),
    )
  val isConfigSatisfied = { pathStr: String -> isConfigPathTruthy(config, pathStr)
  val bundled =
    bundledNames && bundledNames.size > 0
      ? bundledNames.has(entry.skill.name)
      : entry.skill.source === "openclaw-bundled"

  val { emoji, homepage, required, missing, requirementsSatisfied, configChecks } =
    evaluateEntryRequirementsForCurrentPlatform({
      always,
      entry,
      hasLocalBin: hasBinary,
      remote: eligibility?.remote,
      isEnvSatisfied,
      isConfigSatisfied,
    })
  val eligible = !disabled && !blockedByAllowlist && requirementsSatisfied

  return {
    name: entry.skill.name,
    description: entry.skill.description,
    source: entry.skill.source,
    bundled,
    filePath: entry.skill.filePath,
    baseDir: entry.skill.baseDir,
    skillKey,
    primaryEnv: entry.metadata?.primaryEnv,
    emoji,
    homepage,
    always,
    disabled,
    blockedByAllowlist,
    eligible,
    requirements: required,
    missing,
    configChecks,
    install: normalizeInstallOptions(entry, prefs ?? resolveSkillsInstallPreferences(config)),
  }
}

fun buildWorkspaceSkillStatus(
  workspaceDir: String,
  opts?: {
    config?: OpenClawConfig
    managedSkillsDir?: String
    entries?: SkillEntry[]
    eligibility?: SkillEligibilityContext
  },
): SkillStatusReport {
  val managedSkillsDir = opts?.managedSkillsDir ?? path.join(CONFIG_DIR, "skills")
  val bundledContext = resolveBundledSkillsContext()
  val skillEntries =
    opts?.entries ??
    loadWorkspaceSkillEntries(workspaceDir, {
      config: opts?.config,
      managedSkillsDir,
      bundledSkillsDir: bundledContext.dir,
    })
  val prefs = resolveSkillsInstallPreferences(opts?.config)
  return {
    workspaceDir,
    managedSkillsDir,
    skills: skillEntries.map((entry) =>
      buildSkillStatus(entry, opts?.config, prefs, opts?.eligibility, bundledContext.names),
    ),
  }
}
