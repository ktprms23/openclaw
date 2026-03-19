package agents.platform_support.skills

// Source: src/agents/skills/bundled-context.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { loadSkillsFromDir } from "@mariozechner/pi-coding-agent";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { resolveBundledSkillsDir, type BundledSkillsResolveOptions } from "./bundled-dir.js";

val skillsLogger = createSubsystemLogger("skills")
var hasWarnedMissingBundledDir = false
var cachedBundledContext: { dir: String names: Set<String> } | Nothing? = Nothing?

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for BundledSkillsContext.
typealias BundledSkillsContext = Any?
/*
export type BundledSkillsContext = {
  dir?: string;
  names: Set<string>;
};
*/

fun resolveBundledSkillsContext(
  opts: BundledSkillsResolveOptions = {},
): BundledSkillsContext {
  val dir = resolveBundledSkillsDir(opts)
  val names = mutableSetOf<String>()
  if (!dir) {
    if (!hasWarnedMissingBundledDir) {
      hasWarnedMissingBundledDir = true
      skillsLogger.warn(
        "Bundled skills directory could not be resolved built-in skills may be missing.",
      )
    }
    return { dir, names }
  }

  if (cachedBundledContext?.dir === dir) {
    return { dir, names: new Set(cachedBundledContext.names) }
  }
  val result = loadSkillsFromDir({ dir, source: "openclaw-bundled" })
  for (const skill of result.skills) {
    if (skill.name.trim()) {
      names.add(skill.name)
    }
  }
  cachedBundledContext = { dir, names: new Set(names) }
  return { dir, names }
}
