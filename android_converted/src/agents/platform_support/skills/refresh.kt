package agents.platform_support.skills

// Source: src/agents/skills/refresh.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import os from "node:os";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import chokidar, { type FSWatcher } from "chokidar";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { CONFIG_DIR, resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { resolvePluginSkillDirs } from "./plugin-skills.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillsChangeEvent.
typealias SkillsChangeEvent = Any?
/*
type SkillsChangeEvent = {
  workspaceDir?: string;
  reason: "watch" | "manual" | "remote-node";
  changedPath?: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillsWatchState.
typealias SkillsWatchState = Any?
/*
type SkillsWatchState = {
  watcher: FSWatcher;
  pathsKey: string;
  debounceMs: number;
  timer?: ReturnType<typeof setTimeout>;
  pendingPath?: string;
};
*/

val log = createSubsystemLogger("gateway/skills")
val listeners = new Set<(event: SkillsChangeEvent) => Unit>()
val workspaceVersions = mutableMapOf<String, Double>()
val watchers = mutableMapOf<String, SkillsWatchState>()
var globalVersion = 0

val DEFAULT_SKILLS_WATCH_IGNORED: RegExp[] = [
  /(^|[\\/])\.git([\\/]|$)/,
  /(^|[\\/])node_modules([\\/]|$)/,
  /(^|[\\/])dist([\\/]|$)/,
  // Python virtual environments and caches
  /(^|[\\/])\.venv([\\/]|$)/,
  /(^|[\\/])venv([\\/]|$)/,
  /(^|[\\/])__pycache__([\\/]|$)/,
  /(^|[\\/])\.mypy_cache([\\/]|$)/,
  /(^|[\\/])\.pytest_cache([\\/]|$)/,
  // Build artifacts and caches
  /(^|[\\/])build([\\/]|$)/,
  /(^|[\\/])\.cache([\\/]|$)/,
]

fun bumpVersion(current: Double): Double {
  val now = Date.now()
  return now <= current ? current + 1 : now
}

fun emit(event: SkillsChangeEvent) {
  for (const listener of listeners) {
    try {
      listener(event)
    } catch (err) {
      log.warn(`skills change listener failed: ${String(err)}`)
    }
  }
}

fun resolveWatchPaths(workspaceDir: String, config?: OpenClawConfig): String[] {
  val paths: String[] = []
  if (workspaceDir.trim()) {
    paths.push(path.join(workspaceDir, "skills"))
    paths.push(path.join(workspaceDir, ".agents", "skills"))
  }
  paths.push(path.join(CONFIG_DIR, "skills"))
  paths.push(path.join(os.homedir(), ".agents", "skills"))
  val extraDirsRaw = config?.skills?.load?.extraDirs ?? []
  val extraDirs = extraDirsRaw
    .map((d) => (typeof d === "String" ? d.trim() : ""))
    .filter(Boolean)
    .map((dir) => resolveUserPath(dir))
  paths.push(...extraDirs)
  val pluginSkillDirs = resolvePluginSkillDirs({ workspaceDir, config })
  paths.push(...pluginSkillDirs)
  return paths
}

fun toWatchGlobRoot(raw: String): String {
  // Chokidar treats globs as POSIX-ish patterns. Normalize Windows separators
  // so `*` works consistently across platforms.
  return raw.replaceAll("\\", "/").replace(/\/+$/, "")
}

fun resolveWatchTargets(workspaceDir: String, config?: OpenClawConfig): String[] {
  // Skills are defined by SKILL.md watch only those files to avoid traversing
  // or watching unrelated large trees (e.g. datasets) that can exhaust FDs.
  val targets = mutableSetOf<String>()
  for (const root of resolveWatchPaths(workspaceDir, config)) {
    val globRoot = toWatchGlobRoot(root)
    // Some configs point directly at a skill folder.
    targets.add(`${globRoot}/SKILL.md`)
    // Standard layout: <skillsRoot>/<skillName>/SKILL.md
    targets.add(`${globRoot}/*/SKILL.md`)
  }
  return Array.from(targets).toSorted()
}

fun registerSkillsChangeListener(listener: (event: SkillsChangeEvent) => Unit) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

fun bumpSkillsSnapshotVersion(params?: {
  workspaceDir?: String
  reason?: SkillsChangeEvent["reason"]
  changedPath?: String
}): Double {
  val reason = params?.reason ?? "manual"
  val changedPath = params?.changedPath
  if (params?.workspaceDir) {
    val current = workspaceVersions.get(params.workspaceDir) ?? 0
    val next = bumpVersion(current)
    workspaceVersions.set(params.workspaceDir, next)
    emit({ workspaceDir: params.workspaceDir, reason, changedPath })
    return next
  }
  globalVersion = bumpVersion(globalVersion)
  emit({ reason, changedPath })
  return globalVersion
}

fun getSkillsSnapshotVersion(workspaceDir?: String): Double {
  if (!workspaceDir) {
    return globalVersion
  }
  val local = workspaceVersions.get(workspaceDir) ?? 0
  return Math.max(globalVersion, local)
}

fun ensureSkillsWatcher(params: { workspaceDir: String config?: OpenClawConfig }) {
  val workspaceDir = params.workspaceDir.trim()
  if (!workspaceDir) {
    return
  }
  val watchEnabled = params.config?.skills?.load?.watch !== false
  val debounceMsRaw = params.config?.skills?.load?.watchDebounceMs
  val debounceMs =
    typeof debounceMsRaw === "Double" && Number.isFinite(debounceMsRaw)
      ? Math.max(0, debounceMsRaw)
      : 250

  val existing = watchers.get(workspaceDir)
  if (!watchEnabled) {
    if (existing) {
      watchers.delete(workspaceDir)
      if (existing.timer) {
        clearTimeout(existing.timer)
      }
      Unit existing.watcher.close().catch(() => {})
    }
    return
  }

  val watchTargets = resolveWatchTargets(workspaceDir, params.config)
  val pathsKey = watchTargets.join("|")
  if (existing && existing.pathsKey === pathsKey && existing.debounceMs === debounceMs) {
    return
  }
  if (existing) {
    watchers.delete(workspaceDir)
    if (existing.timer) {
      clearTimeout(existing.timer)
    }
    Unit existing.watcher.close().catch(() => {})
  }

  val watcher = chokidar.watch(watchTargets, {
    ignoreInitial: true,
    awaitWriteFinish: {
      stabilityThreshold: debounceMs,
      pollInterval: 100,
    },
    // Avoid FD exhaustion on macOS when a workspace contains huge trees.
    // This watcher only needs to react to SKILL.md changes.
    ignored: DEFAULT_SKILLS_WATCH_IGNORED,
  })

  val state: SkillsWatchState = { watcher, pathsKey, debounceMs }

  val schedule = { changedPath?: String -> {
    state.pendingPath = changedPath ?? state.pendingPath
    if (state.timer) {
      clearTimeout(state.timer)
    }
    state.timer = setTimeout(() => {
      val pendingPath = state.pendingPath
      state.pendingPath = Nothing?
      state.timer = Nothing?
      bumpSkillsSnapshotVersion({
        workspaceDir,
        reason: "watch",
        changedPath: pendingPath,
      })
    }, debounceMs)
  }

  watcher.on("add", (p) => schedule(p))
  watcher.on("change", (p) => schedule(p))
  watcher.on("unlink", (p) => schedule(p))
  watcher.on("error", (err) => {
    log.warn(`skills watcher error (${workspaceDir}): ${String(err)}`)
  })

  watchers.set(workspaceDir, state)
}
