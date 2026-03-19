package agents.platform_support.skills

// Source: src/agents/skills/workspace.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import os from "node:os";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   formatSkillsForPrompt,
// TODO(openclaw-kotlin-port):   loadSkillsFromDir,
// TODO(openclaw-kotlin-port):   type Skill,
// TODO(openclaw-kotlin-port): } from "@mariozechner/pi-coding-agent";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { isPathInside } from "../../infra/path-guards.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { CONFIG_DIR, resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxPath } from "../sandbox-paths.js";
// TODO(openclaw-kotlin-port): import { resolveBundledSkillsDir } from "./bundled-dir.js";
// TODO(openclaw-kotlin-port): import { shouldIncludeSkill } from "./config.js";
// TODO(openclaw-kotlin-port): import { normalizeSkillFilter } from "./filter.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   parseFrontmatter,
// TODO(openclaw-kotlin-port):   resolveOpenClawMetadata,
// TODO(openclaw-kotlin-port):   resolveSkillInvocationPolicy,
// TODO(openclaw-kotlin-port): } from "./frontmatter.js";
// TODO(openclaw-kotlin-port): import { resolvePluginSkillDirs } from "./plugin-skills.js";
// TODO(openclaw-kotlin-port): import { serializeByKey } from "./serialize.js";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   ParsedSkillFrontmatter,
// TODO(openclaw-kotlin-port):   SkillEligibilityContext,
// TODO(openclaw-kotlin-port):   SkillCommandSpec,
// TODO(openclaw-kotlin-port):   SkillEntry,
// TODO(openclaw-kotlin-port):   SkillSnapshot,
// TODO(openclaw-kotlin-port): } from "./types.js";

val fsp = fs.promises
val skillsLogger = createSubsystemLogger("skills")
val skillCommandDebugOnce = mutableSetOf<String>()

/**
 * Replace the user's home directory prefix with `~` in skill file paths
 * to reduce system prompt token usage. Models understand `~` expansion,
 * and the read tool resolves `~` to the home directory.
 *
 * Example: `/Users/alice/.bun/.../skills/github/SKILL.md`
 *       → `~/.bun/.../skills/github/SKILL.md`
 *
 * Saves ~5–6 tokens per skill path × N skills ≈ 400–600 tokens total.
 */
fun compactSkillPaths(skills: Skill[]): Skill[] {
  val home = os.homedir()
  if (!home) return skills
  val prefix = home.endsWith(path.sep) ? home : home + path.sep
  return skills.map((s) => ({
    ...s,
    filePath: s.filePath.startsWith(prefix) ? "~/" + s.filePath.slice(prefix.length) : s.filePath,
  }))
}

fun debugSkillCommandOnce(
  messageKey: String,
  message: String,
  meta?: Map<String, Any?>,
) {
  if (skillCommandDebugOnce.has(messageKey)) {
    return
  }
  skillCommandDebugOnce.add(messageKey)
  skillsLogger.debug(message, meta)
}

fun filterSkillEntries(
  entries: SkillEntry[],
  config?: OpenClawConfig,
  skillFilter?: String[],
  eligibility?: SkillEligibilityContext,
): SkillEntry[] {
  var filtered = entries.filter((entry) => shouldIncludeSkill({ entry, config, eligibility }))
  // If skillFilter is provided, only include skills in the filter list.
  if (skillFilter !== Nothing?) {
    val normalized = normalizeSkillFilter(skillFilter) ?? []
    val label = normalized.length > 0 ? normalized.join(", ") : "(none)"
    skillsLogger.debug(`Applying skill filter: ${label}`)
    filtered =
      normalized.length > 0
        ? filtered.filter((entry) => normalized.includes(entry.skill.name))
        : []
    skillsLogger.debug(
      `After skill filter: ${filtered.map((entry) => entry.skill.name).join(", ") || "(none)"}`,
    )
  }
  return filtered
}

val SKILL_COMMAND_MAX_LENGTH = 32
val SKILL_COMMAND_FALLBACK = "skill"
// Discord command descriptions must be ≤100 characters
val SKILL_COMMAND_DESCRIPTION_MAX_LENGTH = 100

val DEFAULT_MAX_CANDIDATES_PER_ROOT = 300
val DEFAULT_MAX_SKILLS_LOADED_PER_SOURCE = 200
val DEFAULT_MAX_SKILLS_IN_PROMPT = 150
val DEFAULT_MAX_SKILLS_PROMPT_CHARS = 30_000
val DEFAULT_MAX_SKILL_FILE_BYTES = 256_000

fun sanitizeSkillCommandName(raw: String): String {
  val normalized = raw
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "")
  val trimmed = normalized.slice(0, SKILL_COMMAND_MAX_LENGTH)
  return trimmed || SKILL_COMMAND_FALLBACK
}

fun resolveUniqueSkillCommandName(base: String, used: Set<String>): String {
  val normalizedBase = base.toLowerCase()
  if (!used.has(normalizedBase)) {
    return base
  }
  for (let index = 2 index < 1000 index += 1) {
    val suffix = `_${index}`
    val maxBaseLength = Math.max(1, SKILL_COMMAND_MAX_LENGTH - suffix.length)
    val trimmedBase = base.slice(0, maxBaseLength)
    val candidate = `${trimmedBase}${suffix}`
    val candidateKey = candidate.toLowerCase()
    if (!used.has(candidateKey)) {
      return candidate
    }
  }
  val fallback = `${base.slice(0, Math.max(1, SKILL_COMMAND_MAX_LENGTH - 2))}_x`
  return fallback
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolvedSkillsLimits.
typealias ResolvedSkillsLimits = Any?
/*
type ResolvedSkillsLimits = {
  maxCandidatesPerRoot: number;
  maxSkillsLoadedPerSource: number;
  maxSkillsInPrompt: number;
  maxSkillsPromptChars: number;
  maxSkillFileBytes: number;
};
*/

fun resolveSkillsLimits(config?: OpenClawConfig): ResolvedSkillsLimits {
  val limits = config?.skills?.limits
  return {
    maxCandidatesPerRoot: limits?.maxCandidatesPerRoot ?? DEFAULT_MAX_CANDIDATES_PER_ROOT,
    maxSkillsLoadedPerSource:
      limits?.maxSkillsLoadedPerSource ?? DEFAULT_MAX_SKILLS_LOADED_PER_SOURCE,
    maxSkillsInPrompt: limits?.maxSkillsInPrompt ?? DEFAULT_MAX_SKILLS_IN_PROMPT,
    maxSkillsPromptChars: limits?.maxSkillsPromptChars ?? DEFAULT_MAX_SKILLS_PROMPT_CHARS,
    maxSkillFileBytes: limits?.maxSkillFileBytes ?? DEFAULT_MAX_SKILL_FILE_BYTES,
  }
}

fun listChildDirectories(dir: String): String[] {
  try {
    val entries = fs.readdirSync(dir, { withFileTypes: true })
    val dirs: String[] = []
    for (const entry of entries) {
      if (entry.name.startsWith(".")) continue
      if (entry.name === "node_modules") continue
      val fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        dirs.push(entry.name)
        continue
      }
      if (entry.isSymbolicLink()) {
        try {
          if (fs.statSync(fullPath).isDirectory()) {
            dirs.push(entry.name)
          }
        } catch {
          // ignore broken symlinks
        }
      }
    }
    return dirs
  } catch {
    return []
  }
}

fun tryRealpath(filePath: String): String | Nothing? {
  try {
    return fs.realpathSync(filePath)
  } catch {
    return Nothing?
  }
}

fun warnEscapedSkillPath(params: {
  source: String
  rootDir: String
  candidatePath: String
  candidateRealPath: String
}) {
  skillsLogger.warn("Skipping skill path that resolves outside its configured root.", {
    source: params.source,
    rootDir: params.rootDir,
    path: params.candidatePath,
    realPath: params.candidateRealPath,
  })
}

fun resolveContainedSkillPath(params: {
  source: String
  rootDir: String
  rootRealPath: String
  candidatePath: String
}): String | Nothing? {
  val candidateRealPath = tryRealpath(params.candidatePath)
  if (!candidateRealPath) {
    return Nothing?
  }
  if (isPathInside(params.rootRealPath, candidateRealPath)) {
    return candidateRealPath
  }
  warnEscapedSkillPath({
    source: params.source,
    rootDir: params.rootDir,
    candidatePath: path.resolve(params.candidatePath),
    candidateRealPath,
  })
  return Nothing?
}

fun filterLoadedSkillsInsideRoot(params: {
  skills: Skill[]
  source: String
  rootDir: String
  rootRealPath: String
}): Skill[] {
  return params.skills.filter((skill) => {
    val baseDirRealPath = resolveContainedSkillPath({
      source: params.source,
      rootDir: params.rootDir,
      rootRealPath: params.rootRealPath,
      candidatePath: skill.baseDir,
    })
    if (!baseDirRealPath) {
      return false
    }
    val skillFileRealPath = resolveContainedSkillPath({
      source: params.source,
      rootDir: params.rootDir,
      rootRealPath: params.rootRealPath,
      candidatePath: skill.filePath,
    })
    return Boolean(skillFileRealPath)
  })
}

fun resolveNestedSkillsRoot(
  dir: String,
  opts?: {
    maxEntriesToScan?: Double
  },
): { baseDir: String note?: String } {
  val nested = path.join(dir, "skills")
  try {
    if (!fs.existsSync(nested) || !fs.statSync(nested).isDirectory()) {
      return { baseDir: dir }
    }
  } catch {
    return { baseDir: dir }
  }

  // Heuristic: if `dir/skills/*/SKILL.md` exists for Any? entry, treat `dir/skills` as the real root.
  // Note: don't stop at 25, but keep a cap to avoid pathological scans.
  val nestedDirs = listChildDirectories(nested)
  val scanLimit = Math.max(0, opts?.maxEntriesToScan ?? 100)
  val toScan = scanLimit === 0 ? [] : nestedDirs.slice(0, Math.min(nestedDirs.length, scanLimit))

  for (const name of toScan) {
    val skillMd = path.join(nested, name, "SKILL.md")
    if (fs.existsSync(skillMd)) {
      return { baseDir: nested, note: `Detected nested skills root at ${nested}` }
    }
  }
  return { baseDir: dir }
}

fun unwrapLoadedSkills(loaded: Any?): Skill[] {
  if (Array.isArray(loaded)) {
    return loaded as Skill[]
  }
  if (loaded && typeof loaded === "object" && "skills" in loaded) {
    val skills = (loaded as { skills?: Any? }).skills
    if (Array.isArray(skills)) {
      return skills as Skill[]
    }
  }
  return []
}

fun loadSkillEntries(
  workspaceDir: String,
  opts?: {
    config?: OpenClawConfig
    managedSkillsDir?: String
    bundledSkillsDir?: String
  },
): SkillEntry[] {
  val limits = resolveSkillsLimits(opts?.config)

  val loadSkills = (params: { dir: String source: String }): Skill[] => {
    val rootDir = path.resolve(params.dir)
    val rootRealPath = tryRealpath(rootDir) ?? rootDir
    val resolved = resolveNestedSkillsRoot(params.dir, {
      maxEntriesToScan: limits.maxCandidatesPerRoot,
    })
    val baseDir = resolved.baseDir
    val baseDirRealPath = resolveContainedSkillPath({
      source: params.source,
      rootDir,
      rootRealPath,
      candidatePath: baseDir,
    })
    if (!baseDirRealPath) {
      return []
    }

    // If the root itself is a skill directory, just load it directly (but enforce size cap).
    val rootSkillMd = path.join(baseDir, "SKILL.md")
    if (fs.existsSync(rootSkillMd)) {
      val rootSkillRealPath = resolveContainedSkillPath({
        source: params.source,
        rootDir,
        rootRealPath: baseDirRealPath,
        candidatePath: rootSkillMd,
      })
      if (!rootSkillRealPath) {
        return []
      }
      try {
        val size = fs.statSync(rootSkillRealPath).size
        if (size > limits.maxSkillFileBytes) {
          skillsLogger.warn("Skipping skills root due to oversized SKILL.md.", {
            dir: baseDir,
            filePath: rootSkillMd,
            size,
            maxSkillFileBytes: limits.maxSkillFileBytes,
          })
          return []
        }
      } catch {
        return []
      }

      val loaded = loadSkillsFromDir({ dir: baseDir, source: params.source })
      return filterLoadedSkillsInsideRoot({
        skills: unwrapLoadedSkills(loaded),
        source: params.source,
        rootDir,
        rootRealPath: baseDirRealPath,
      })
    }

    val childDirs = listChildDirectories(baseDir)
    val suspicious = childDirs.length > limits.maxCandidatesPerRoot

    val maxCandidates = Math.max(0, limits.maxSkillsLoadedPerSource)
    val limitedChildren = childDirs.slice().sort().slice(0, maxCandidates)

    if (suspicious) {
      skillsLogger.warn("Skills root looks suspiciously large, truncating discovery.", {
        dir: params.dir,
        baseDir,
        childDirCount: childDirs.length,
        maxCandidatesPerRoot: limits.maxCandidatesPerRoot,
        maxSkillsLoadedPerSource: limits.maxSkillsLoadedPerSource,
      })
    } else if (childDirs.length > maxCandidates) {
      skillsLogger.warn("Skills root has many entries, truncating discovery.", {
        dir: params.dir,
        baseDir,
        childDirCount: childDirs.length,
        maxSkillsLoadedPerSource: limits.maxSkillsLoadedPerSource,
      })
    }

    val loadedSkills: Skill[] = []

    // Only consider immediate subfolders that look like skills (have SKILL.md) and are under size cap.
    for (const name of limitedChildren) {
      val skillDir = path.join(baseDir, name)
      val skillDirRealPath = resolveContainedSkillPath({
        source: params.source,
        rootDir,
        rootRealPath: baseDirRealPath,
        candidatePath: skillDir,
      })
      if (!skillDirRealPath) {
        continue
      }
      val skillMd = path.join(skillDir, "SKILL.md")
      if (!fs.existsSync(skillMd)) {
        continue
      }
      val skillMdRealPath = resolveContainedSkillPath({
        source: params.source,
        rootDir,
        rootRealPath: baseDirRealPath,
        candidatePath: skillMd,
      })
      if (!skillMdRealPath) {
        continue
      }
      try {
        val size = fs.statSync(skillMdRealPath).size
        if (size > limits.maxSkillFileBytes) {
          skillsLogger.warn("Skipping skill due to oversized SKILL.md.", {
            skill: name,
            filePath: skillMd,
            size,
            maxSkillFileBytes: limits.maxSkillFileBytes,
          })
          continue
        }
      } catch {
        continue
      }

      val loaded = loadSkillsFromDir({ dir: skillDir, source: params.source })
      loadedSkills.push(
        ...filterLoadedSkillsInsideRoot({
          skills: unwrapLoadedSkills(loaded),
          source: params.source,
          rootDir,
          rootRealPath: baseDirRealPath,
        }),
      )

      if (loadedSkills.length >= limits.maxSkillsLoadedPerSource) {
        break
      }
    }

    if (loadedSkills.length > limits.maxSkillsLoadedPerSource) {
      return loadedSkills
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .slice(0, limits.maxSkillsLoadedPerSource)
    }

    return loadedSkills
  }

  val managedSkillsDir = opts?.managedSkillsDir ?? path.join(CONFIG_DIR, "skills")
  val workspaceSkillsDir = path.resolve(workspaceDir, "skills")
  val bundledSkillsDir = opts?.bundledSkillsDir ?? resolveBundledSkillsDir()
  val extraDirsRaw = opts?.config?.skills?.load?.extraDirs ?? []
  val extraDirs = extraDirsRaw
    .map((d) => (typeof d === "String" ? d.trim() : ""))
    .filter(Boolean)
  val pluginSkillDirs = resolvePluginSkillDirs({
    workspaceDir,
    config: opts?.config,
  })
  val mergedExtraDirs = [...extraDirs, ...pluginSkillDirs]

  val bundledSkills = bundledSkillsDir
    ? loadSkills({
        dir: bundledSkillsDir,
        source: "openclaw-bundled",
      })
    : []
  val extraSkills = mergedExtraDirs.flatMap((dir) => {
    val resolved = resolveUserPath(dir)
    return loadSkills({
      dir: resolved,
      source: "openclaw-extra",
    })
  })
  val managedSkills = loadSkills({
    dir: managedSkillsDir,
    source: "openclaw-managed",
  })
  val personalAgentsSkillsDir = path.resolve(os.homedir(), ".agents", "skills")
  val personalAgentsSkills = loadSkills({
    dir: personalAgentsSkillsDir,
    source: "agents-skills-personal",
  })
  val projectAgentsSkillsDir = path.resolve(workspaceDir, ".agents", "skills")
  val projectAgentsSkills = loadSkills({
    dir: projectAgentsSkillsDir,
    source: "agents-skills-project",
  })
  val workspaceSkills = loadSkills({
    dir: workspaceSkillsDir,
    source: "openclaw-workspace",
  })

  val merged = mutableMapOf<String, Skill>()
  // Precedence: extra < bundled < managed < agents-skills-personal < agents-skills-project < workspace
  for (const skill of extraSkills) {
    merged.set(skill.name, skill)
  }
  for (const skill of bundledSkills) {
    merged.set(skill.name, skill)
  }
  for (const skill of managedSkills) {
    merged.set(skill.name, skill)
  }
  for (const skill of personalAgentsSkills) {
    merged.set(skill.name, skill)
  }
  for (const skill of projectAgentsSkills) {
    merged.set(skill.name, skill)
  }
  for (const skill of workspaceSkills) {
    merged.set(skill.name, skill)
  }

  val skillEntries: SkillEntry[] = Array.from(merged.values()).map((skill) => {
    var frontmatter: ParsedSkillFrontmatter = {}
    try {
      val raw = fs.readFileSync(skill.filePath, "utf-8")
      frontmatter = parseFrontmatter(raw)
    } catch {
      // ignore malformed skills
    }
    return {
      skill,
      frontmatter,
      metadata: resolveOpenClawMetadata(frontmatter),
      invocation: resolveSkillInvocationPolicy(frontmatter),
    }
  })
  return skillEntries
}

fun escapeXml(str: String): String {
  return str
    .replace(/&/g, "&amp")
    .replace(/</g, "&lt")
    .replace(/>/g, "&gt")
    .replace(/"/g, "&quot")
    .replace(/'/g, "&apos")
}

/**
 * Compact skill catalog: name + location only (no description).
 * Used as a fallback when the full format exceeds the char budget,
 * preserving awareness of all skills before resorting to dropping.
 */
fun formatSkillsCompact(skills: Skill[]): String {
  val visible = skills.filter((s) => !s.disableModelInvocation)
  if (visible.length === 0) return ""
  val lines = [
    "\n\nThe following skills provide specialized instructions for specific tasks.",
    "Use the read tool to load a skill's file when the task matches its name.",
    "When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.",
    "",
    "<available_skills>",
  ]
  for (const skill of visible) {
    lines.push("  <skill>")
    lines.push(`    <name>${escapeXml(skill.name)}</name>`)
    lines.push(`    <location>${escapeXml(skill.filePath)}</location>`)
    lines.push("  </skill>")
  }
  lines.push("</available_skills>")
  return lines.join("\n")
}

// Budget reserved for the compact-mode warning line prepended by the caller.
val COMPACT_WARNING_OVERHEAD = 150

fun applySkillsPromptLimits(params: { skills: Skill[] config?: OpenClawConfig }): {
  skillsForPrompt: Skill[]
  truncated: Boolean
  compact: Boolean
} {
  val limits = resolveSkillsLimits(params.config)
  val total = params.skills.length
  val byCount = params.skills.slice(0, Math.max(0, limits.maxSkillsInPrompt))

  var skillsForPrompt = byCount
  var truncated = total > byCount.length
  var compact = false

  val fitsFull = (skills: Skill[]): Boolean =>
    formatSkillsForPrompt(skills).length <= limits.maxSkillsPromptChars

  // Reserve space for the warning line the caller prepends in compact mode.
  val compactBudget = limits.maxSkillsPromptChars - COMPACT_WARNING_OVERHEAD
  val fitsCompact = (skills: Skill[]): Boolean =>
    formatSkillsCompact(skills).length <= compactBudget

  if (!fitsFull(skillsForPrompt)) {
    // Full format exceeds budget. Try compact (name + location, no description)
    // to preserve awareness of all skills before dropping Any?.
    if (fitsCompact(skillsForPrompt)) {
      compact = true
      // No skills dropped — only format downgraded. Preserve existing truncated state.
    } else {
      // Compact still too large — binary search the largest prefix that fits.
      compact = true
      var lo = 0
      var hi = skillsForPrompt.length
      while (lo < hi) {
        val mid = Math.ceil((lo + hi) / 2)
        if (fitsCompact(skillsForPrompt.slice(0, mid))) {
          lo = mid
        } else {
          hi = mid - 1
        }
      }
      skillsForPrompt = skillsForPrompt.slice(0, lo)
      truncated = true
    }
  }

  return { skillsForPrompt, truncated, compact }
}

fun buildWorkspaceSkillSnapshot(
  workspaceDir: String,
  opts?: WorkspaceSkillBuildOptions & { snapshotVersion?: Double },
): SkillSnapshot {
  val { eligible, prompt, resolvedSkills } = resolveWorkspaceSkillPromptState(workspaceDir, opts)
  val skillFilter = normalizeSkillFilter(opts?.skillFilter)
  return {
    prompt,
    skills: eligible.map((entry) => ({
      name: entry.skill.name,
      primaryEnv: entry.metadata?.primaryEnv,
      requiredEnv: entry.metadata?.requires?.env?.slice(),
    })),
    ...(skillFilter === Nothing? ? {} : { skillFilter }),
    resolvedSkills,
    version: opts?.snapshotVersion,
  }
}

fun buildWorkspaceSkillsPrompt(
  workspaceDir: String,
  opts?: WorkspaceSkillBuildOptions,
): String {
  return resolveWorkspaceSkillPromptState(workspaceDir, opts).prompt
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for WorkspaceSkillBuildOptions.
typealias WorkspaceSkillBuildOptions = Any?
/*
type WorkspaceSkillBuildOptions = {
  config?: OpenClawConfig;
  managedSkillsDir?: string;
  bundledSkillsDir?: string;
  entries?: SkillEntry[];
  /** If provided, only include skills with these names */
  skillFilter?: string[];
  eligibility?: SkillEligibilityContext;
};
*/

fun resolveWorkspaceSkillPromptState(
  workspaceDir: String,
  opts?: WorkspaceSkillBuildOptions,
): {
  eligible: SkillEntry[]
  prompt: String
  resolvedSkills: Skill[]
} {
  val skillEntries = opts?.entries ?? loadSkillEntries(workspaceDir, opts)
  val eligible = filterSkillEntries(
    skillEntries,
    opts?.config,
    opts?.skillFilter,
    opts?.eligibility,
  )
  val promptEntries = eligible.filter(
    (entry) => entry.invocation?.disableModelInvocation !== true,
  )
  val remoteNote = opts?.eligibility?.remote?.note?.trim()
  val resolvedSkills = promptEntries.map((entry) => entry.skill)
  // Derive prompt-facing skills with compacted paths (e.g. ~/...) once.
  // Budget checks and final render both use this same representation so the
  // tier decision is based on the exact strings that end up in the prompt.
  // resolvedSkills keeps canonical paths for snapshot / runtime consumers.
  val promptSkills = compactSkillPaths(resolvedSkills)
  val { skillsForPrompt, truncated, compact } = applySkillsPromptLimits({
    skills: promptSkills,
    config: opts?.config,
  })
  val truncationNote = truncated
    ? `⚠️ Skills truncated: included ${skillsForPrompt.length} of ${resolvedSkills.length}${compact ? " (compact format, descriptions omitted)" : ""}. Run \`openclaw skills check\` to audit.`
    : compact
      ? `⚠️ Skills catalog using compact format (descriptions omitted). Run \`openclaw skills check\` to audit.`
      : ""
  val prompt = [
    remoteNote,
    truncationNote,
    compact ? formatSkillsCompact(skillsForPrompt) : formatSkillsForPrompt(skillsForPrompt),
  ]
    .filter(Boolean)
    .join("\n")
  return { eligible, prompt, resolvedSkills }
}

fun resolveSkillsPromptForRun(params: {
  skillsSnapshot?: SkillSnapshot
  entries?: SkillEntry[]
  config?: OpenClawConfig
  workspaceDir: String
}): String {
  val snapshotPrompt = params.skillsSnapshot?.prompt?.trim()
  if (snapshotPrompt) {
    return snapshotPrompt
  }
  if (params.entries && params.entries.length > 0) {
    val prompt = buildWorkspaceSkillsPrompt(params.workspaceDir, {
      entries: params.entries,
      config: params.config,
    })
    return prompt.trim() ? prompt : ""
  }
  return ""
}

fun loadWorkspaceSkillEntries(
  workspaceDir: String,
  opts?: {
    config?: OpenClawConfig
    managedSkillsDir?: String
    bundledSkillsDir?: String
  },
): SkillEntry[] {
  return loadSkillEntries(workspaceDir, opts)
}

fun resolveUniqueSyncedSkillDirName(base: String, used: Set<String>): String {
  if (!used.has(base)) {
    used.add(base)
    return base
  }
  for (let index = 2 index < 10_000 index += 1) {
    val candidate = `${base}-${index}`
    if (!used.has(candidate)) {
      used.add(candidate)
      return candidate
    }
  }
  var fallbackIndex = 10_000
  var fallback = `${base}-${fallbackIndex}`
  while (used.has(fallback)) {
    fallbackIndex += 1
    fallback = `${base}-${fallbackIndex}`
  }
  used.add(fallback)
  return fallback
}

fun resolveSyncedSkillDestinationPath(params: {
  targetSkillsDir: String
  entry: SkillEntry
  usedDirNames: Set<String>
}): String | Nothing? {
  val sourceDirName = path.basename(params.entry.skill.baseDir).trim()
  if (!sourceDirName || sourceDirName === "." || sourceDirName === "..") {
    return Nothing?
  }
  val uniqueDirName = resolveUniqueSyncedSkillDirName(sourceDirName, params.usedDirNames)
  return resolveSandboxPath({
    filePath: uniqueDirName,
    cwd: params.targetSkillsDir,
    root: params.targetSkillsDir,
  }).resolved
}

suspend fun syncSkillsToWorkspace(params: {
  sourceWorkspaceDir: String
  targetWorkspaceDir: String
  config?: OpenClawConfig
  managedSkillsDir?: String
  bundledSkillsDir?: String
}) {
  val sourceDir = resolveUserPath(params.sourceWorkspaceDir)
  val targetDir = resolveUserPath(params.targetWorkspaceDir)
  if (sourceDir === targetDir) {
    return
  }

  await serializeByKey(`syncSkills:${targetDir}`, async () => {
    val targetSkillsDir = path.join(targetDir, "skills")

    val entries = loadSkillEntries(sourceDir, {
      config: params.config,
      managedSkillsDir: params.managedSkillsDir,
      bundledSkillsDir: params.bundledSkillsDir,
    })

    await fsp.rm(targetSkillsDir, { recursive: true, force: true })
    await fsp.mkdir(targetSkillsDir, { recursive: true })

    val usedDirNames = mutableSetOf<String>()
    for (const entry of entries) {
      var dest: String | Nothing? = Nothing?
      try {
        dest = resolveSyncedSkillDestinationPath({
          targetSkillsDir,
          entry,
          usedDirNames,
        })
      } catch (error) {
        val message = error instanceof Error ? error.message : JSON.stringify(error)
        skillsLogger.warn(`Failed to resolve safe destination for ${entry.skill.name}: ${message}`)
        continue
      }
      if (!dest) {
        skillsLogger.warn(
          `Failed to resolve safe destination for ${entry.skill.name}: invalid source directory name`,
        )
        continue
      }
      try {
        await fsp.cp(entry.skill.baseDir, dest, {
          recursive: true,
          force: true,
        })
      } catch (error) {
        val message = error instanceof Error ? error.message : JSON.stringify(error)
        skillsLogger.warn(`Failed to copy ${entry.skill.name} to sandbox: ${message}`)
      }
    }
  })
}

fun filterWorkspaceSkillEntries(
  entries: SkillEntry[],
  config?: OpenClawConfig,
): SkillEntry[] {
  return filterSkillEntries(entries, config)
}

fun buildWorkspaceSkillCommandSpecs(
  workspaceDir: String,
  opts?: {
    config?: OpenClawConfig
    managedSkillsDir?: String
    bundledSkillsDir?: String
    entries?: SkillEntry[]
    skillFilter?: String[]
    eligibility?: SkillEligibilityContext
    reservedNames?: Set<String>
  },
): SkillCommandSpec[] {
  val skillEntries = opts?.entries ?? loadSkillEntries(workspaceDir, opts)
  val eligible = filterSkillEntries(
    skillEntries,
    opts?.config,
    opts?.skillFilter,
    opts?.eligibility,
  )
  val userInvocable = eligible.filter((entry) => entry.invocation?.userInvocable !== false)
  val used = mutableSetOf<String>()
  for (const reserved of opts?.reservedNames ?? []) {
    used.add(reserved.toLowerCase())
  }

  val specs: SkillCommandSpec[] = []
  for (const entry of userInvocable) {
    val rawName = entry.skill.name
    val base = sanitizeSkillCommandName(rawName)
    if (base !== rawName) {
      debugSkillCommandOnce(
        `sanitize:${rawName}:${base}`,
        `Sanitized skill command name "${rawName}" to "/${base}".`,
        { rawName, sanitized: `/${base}` },
      )
    }
    val unique = resolveUniqueSkillCommandName(base, used)
    if (unique !== base) {
      debugSkillCommandOnce(
        `dedupe:${rawName}:${unique}`,
        `De-duplicated skill command name for "${rawName}" to "/${unique}".`,
        { rawName, deduped: `/${unique}` },
      )
    }
    used.add(unique.toLowerCase())
    val rawDescription = entry.skill.description?.trim() || rawName
    val description =
      rawDescription.length > SKILL_COMMAND_DESCRIPTION_MAX_LENGTH
        ? rawDescription.slice(0, SKILL_COMMAND_DESCRIPTION_MAX_LENGTH - 1) + "…"
        : rawDescription
    val dispatch = { ( -> {
      val kindRaw = (
        entry.frontmatter?.["command-dispatch"] ??
        entry.frontmatter?.["command_dispatch"] ??
        ""
      )
        .trim()
        .toLowerCase()
      if (!kindRaw) {
        return Nothing?
      }
      if (kindRaw !== "tool") {
        return Nothing?
      }

      val toolName = (
        entry.frontmatter?.["command-tool"] ??
        entry.frontmatter?.["command_tool"] ??
        ""
      ).trim()
      if (!toolName) {
        debugSkillCommandOnce(
          `dispatch:missingTool:${rawName}`,
          `Skill command "/${unique}" requested tool dispatch but did not provide command-tool. Ignoring dispatch.`,
          { skillName: rawName, command: unique },
        )
        return Nothing?
      }

      val argModeRaw = (
        entry.frontmatter?.["command-arg-mode"] ??
        entry.frontmatter?.["command_arg_mode"] ??
        ""
      )
        .trim()
        .toLowerCase()
      val argMode = !argModeRaw || argModeRaw === "raw" ? "raw" : Nothing?
      if (!argMode) {
        debugSkillCommandOnce(
          `dispatch:badArgMode:${rawName}:${argModeRaw}`,
          `Skill command "/${unique}" requested tool dispatch but has Any? command-arg-mode. Falling back to raw.`,
          { skillName: rawName, command: unique, argMode: argModeRaw },
        )
      }

      return { kind: "tool", toolName, argMode: "raw" } /* as const */
    })()

    specs.push({
      name: unique,
      skillName: rawName,
      description,
      ...(dispatch ? { dispatch } : {}),
    })
  }
  return specs
}
