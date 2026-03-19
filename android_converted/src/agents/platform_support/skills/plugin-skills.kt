package agents.platform_support.skills

// Source: src/agents/skills/plugin-skills.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   normalizePluginsConfig,
// TODO(openclaw-kotlin-port):   resolveEffectiveEnableState,
// TODO(openclaw-kotlin-port):   resolveMemorySlotDecision,
// TODO(openclaw-kotlin-port): } from "../../plugins/config-state.js";
// TODO(openclaw-kotlin-port): import { loadPluginManifestRegistry } from "../../plugins/manifest-registry.js";
// TODO(openclaw-kotlin-port): import { isPathInsideWithRealpath } from "../../security/scan-paths.js";

val log = createSubsystemLogger("skills")

fun resolvePluginSkillDirs(params: {
  workspaceDir: String | Nothing?
  config?: OpenClawConfig
}): String[] {
  val workspaceDir = (params.workspaceDir ?? "").trim()
  if (!workspaceDir) {
    return []
  }
  val registry = loadPluginManifestRegistry({
    workspaceDir,
    config: params.config,
  })
  if (registry.plugins.length === 0) {
    return []
  }
  val normalizedPlugins = normalizePluginsConfig(params.config?.plugins)
  val acpEnabled = params.config?.acp?.enabled !== false
  val memorySlot = normalizedPlugins.slots.memory
  var selectedMemoryPluginId: String | Nothing? = Nothing?
  val seen = mutableSetOf<String>()
  val resolved: String[] = []

  for (const record of registry.plugins) {
    if (!record.skills || record.skills.length === 0) {
      continue
    }
    val enableState = resolveEffectiveEnableState({
      id: record.id,
      origin: record.origin,
      config: normalizedPlugins,
      rootConfig: params.config,
    })
    if (!enableState.enabled) {
      continue
    }
    // ACP router skills should not be attached when ACP is explicitly disabled.
    if (!acpEnabled && record.id === "acpx") {
      continue
    }
    val memoryDecision = resolveMemorySlotDecision({
      id: record.id,
      kind: record.kind,
      slot: memorySlot,
      selectedId: selectedMemoryPluginId,
    })
    if (!memoryDecision.enabled) {
      continue
    }
    if (memoryDecision.selected && record.kind === "memory") {
      selectedMemoryPluginId = record.id
    }
    for (const raw of record.skills) {
      val trimmed = raw.trim()
      if (!trimmed) {
        continue
      }
      val candidate = path.resolve(record.rootDir, trimmed)
      if (!fs.existsSync(candidate)) {
        log.warn(`plugin skill path not found (${record.id}): ${candidate}`)
        continue
      }
      if (!isPathInsideWithRealpath(record.rootDir, candidate, { requireRealpath: true })) {
        log.warn(`plugin skill path escapes plugin root (${record.id}): ${candidate}`)
        continue
      }
      if (seen.has(candidate)) {
        continue
      }
      seen.add(candidate)
      resolved.push(candidate)
    }
  }

  return resolved
}
