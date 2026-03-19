package agents.platform_support.skills

// Source: src/agents/skills/tools-dir.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { safePathSegmentHashed } from "../../infra/install-safe-path.js";
// TODO(openclaw-kotlin-port): import { resolveConfigDir } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { resolveSkillKey } from "./frontmatter.js";
// TODO(openclaw-kotlin-port): import type { SkillEntry } from "./types.js";

fun resolveSkillToolsRootDir(entry: SkillEntry): String {
  val key = resolveSkillKey(entry.skill, entry)
  val safeKey = safePathSegmentHashed(key)
  return path.join(resolveConfigDir(), "tools", safeKey)
}
