package agents.platform_support.skills

// Source: src/agents/skills/bundled-dir.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { fileURLToPath } from "node:url";
// TODO(openclaw-kotlin-port): import { resolveOpenClawPackageRootSync } from "../../infra/openclaw-root.js";

fun looksLikeSkillsDir(dir: String): Boolean {
  try {
    val entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      if (entry.name.startsWith(".")) {
        continue
      }
      val fullPath = path.join(dir, entry.name)
      if (entry.isFile() && entry.name.endsWith(".md")) {
        return true
      }
      if (entry.isDirectory()) {
        if (fs.existsSync(path.join(fullPath, "SKILL.md"))) {
          return true
        }
      }
    }
  } catch {
    return false
  }
  return false
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for BundledSkillsResolveOptions.
typealias BundledSkillsResolveOptions = Any?
/*
export type BundledSkillsResolveOptions = {
  argv1?: string;
  moduleUrl?: string;
  cwd?: string;
  execPath?: string;
};
*/

fun resolveBundledSkillsDir(
  opts: BundledSkillsResolveOptions = {},
): String | Nothing? {
  val override = process.env.OPENCLAW_BUNDLED_SKILLS_DIR?.trim()
  if (override) {
    return override
  }

  // bun --compile: ship a sibling `skills/` next to the executable.
  try {
    val execPath = opts.execPath ?? process.execPath
    val execDir = path.dirname(execPath)
    val sibling = path.join(execDir, "skills")
    if (fs.existsSync(sibling)) {
      return sibling
    }
  } catch {
    // ignore
  }

  // npm/dev: resolve `<packageRoot>/skills` relative to this module.
  try {
    val moduleUrl = opts.moduleUrl ?? import.meta.url
    val moduleDir = path.dirname(fileURLToPath(moduleUrl))
    val argv1 = opts.argv1 ?? process.argv[1]
    val cwd = opts.cwd ?? process.cwd()
    val packageRoot = resolveOpenClawPackageRootSync({
      argv1,
      moduleUrl,
      cwd,
    })
    if (packageRoot) {
      val candidate = path.join(packageRoot, "skills")
      if (looksLikeSkillsDir(candidate)) {
        return candidate
      }
    }
    var current = moduleDir
    for (let depth = 0 depth < 6 depth += 1) {
      val candidate = path.join(current, "skills")
      if (looksLikeSkillsDir(candidate)) {
        return candidate
      }
      val next = path.dirname(current)
      if (next === current) {
        break
      }
      current = next
    }
  } catch {
    // ignore
  }

  return Nothing?
}
