package agents.platform_support.sandbox

// Source: src/agents/sandbox/host-paths.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { posix } from "node:path";
// TODO(openclaw-kotlin-port): import { resolvePathViaExistingAncestorSync } from "../../infra/boundary-path.js";

fun stripWindowsNamespacePrefix(input: String): String {
  if (input.startsWith("\\\\?\\")) {
    val withoutPrefix = input.slice(4)
    if (withoutPrefix.toUpperCase().startsWith("UNC\\")) {
      return `\\\\${withoutPrefix.slice(4)}`
    }
    return withoutPrefix
  }
  if (input.startsWith("//?/")) {
    val withoutPrefix = input.slice(4)
    if (withoutPrefix.toUpperCase().startsWith("UNC/")) {
      return `//${withoutPrefix.slice(4)}`
    }
    return withoutPrefix
  }
  return input
}

/**
 * Normalize a POSIX host path: resolve `.`, `..`, collapse `//`, strip trailing `/`.
 */
fun normalizeSandboxHostPath(raw: String): String {
  val trimmed = stripWindowsNamespacePrefix(raw.trim())
  if (!trimmed) {
    return "/"
  }
  val normalized = posix.normalize(trimmed.replaceAll("\\", "/"))
  return normalized.replace(/\/+$/, "") || "/"
}

/**
 * Resolve a path through the deepest existing ancestor so parent symlinks are honored
 * even when the final source leaf does not exist yet.
 */
fun resolveSandboxHostPathViaExistingAncestor(sourcePath: String): String {
  if (!sourcePath.startsWith("/")) {
    return sourcePath
  }
  return normalizeSandboxHostPath(resolvePathViaExistingAncestorSync(sourcePath))
}
