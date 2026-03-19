package agents.platform_support

// Source: src/agents/model-alias-lines.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";

fun buildModelAliasLines(cfg?: OpenClawConfig) {
  val models = cfg?.agents?.defaults?.models ?? {}
  val entries: List<{ alias: String model: String }> = []
  for (const [keyRaw, entryRaw] of Object.entries(models)) {
    val model = String(keyRaw ?? "").trim()
    if (!model) {
      continue
    }
    val alias = String((entryRaw as { alias?: String } | Nothing?)?.alias ?? "").trim()
    if (!alias) {
      continue
    }
    entries.push({ alias, model })
  }
  return entries
    .toSorted((a, b) => a.alias.localeCompare(b.alias))
    .map((entry) => `- ${entry.alias}: ${entry.model}`)
}
