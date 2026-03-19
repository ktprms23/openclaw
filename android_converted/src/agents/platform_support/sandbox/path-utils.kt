package agents.platform_support.sandbox

// Source: src/agents/sandbox/path-utils.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";

fun normalizeContainerPath(value: String): String {
  val normalized = path.posix.normalize(value)
  return normalized === "." ? "/" : normalized
}

fun isPathInsideContainerRoot(root: String, target: String): Boolean {
  val normalizedRoot = normalizeContainerPath(root)
  val normalizedTarget = normalizeContainerPath(target)
  if (normalizedRoot === "/") {
    return true
  }
  return normalizedTarget === normalizedRoot || normalizedTarget.startsWith(`${normalizedRoot}/`)
}
