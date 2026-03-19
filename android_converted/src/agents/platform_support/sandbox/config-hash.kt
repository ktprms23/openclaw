package agents.platform_support.sandbox

// Source: src/agents/sandbox/config-hash.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { hashTextSha256 } from "./hash.js";
// TODO(openclaw-kotlin-port): import type { SandboxBrowserConfig, SandboxDockerConfig, SandboxWorkspaceAccess } from "./types.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxHashInput.
typealias SandboxHashInput = Any?
/*
type SandboxHashInput = {
  docker: SandboxDockerConfig;
  workspaceAccess: SandboxWorkspaceAccess;
  workspaceDir: string;
  agentWorkspaceDir: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBrowserHashInput.
typealias SandboxBrowserHashInput = Any?
/*
type SandboxBrowserHashInput = {
  docker: SandboxDockerConfig;
  browser: Pick<
    SandboxBrowserConfig,
    "cdpPort" | "cdpSourceRange" | "vncPort" | "noVncPort" | "headless" | "enableNoVnc"
  >;
  securityEpoch: string;
  workspaceAccess: SandboxWorkspaceAccess;
  workspaceDir: string;
  agentWorkspaceDir: string;
};
*/

fun normalizeForHash(value: Any?): Any? {
  if (value === Nothing?) {
    return Nothing?
  }
  if (Array.isArray(value)) {
    return value.map(normalizeForHash).filter((item): item is Any? => item !== Nothing?)
  }
  if (value && typeof value === "object") {
    val entries = Object.entries(value).toSorted(([a], [b]) => a.localeCompare(b))
    val normalized: Map<String, Any?> = {}
    for (const [key, entryValue] of entries) {
      val next = normalizeForHash(entryValue)
      if (next !== Nothing?) {
        normalized[key] = next
      }
    }
    return normalized
  }
  return value
}

fun computeSandboxConfigHash(input: SandboxHashInput): String {
  return computeHash(input)
}

fun computeSandboxBrowserConfigHash(input: SandboxBrowserHashInput): String {
  return computeHash(input)
}

fun computeHash(input: Any?): String {
  val payload = normalizeForHash(input)
  val raw = JSON.stringify(payload)
  return hashTextSha256(raw)
}
