package agents.platform_support.sandbox

// Source: src/agents/sandbox/shared.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { normalizeAgentId } from "../../routing/session-key.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { resolveAgentIdFromSessionKey } from "../agent-scope.js";
// TODO(openclaw-kotlin-port): import { hashTextSha256 } from "./hash.js";

fun slugifySessionKey(value: String) {
  val trimmed = value.trim() || "session"
  val hash = hashTextSha256(trimmed).slice(0, 8)
  val safe = trimmed
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
  val base = safe.slice(0, 32) || "session"
  return `${base}-${hash}`
}

fun resolveSandboxWorkspaceDir(root: String, sessionKey: String) {
  val resolvedRoot = resolveUserPath(root)
  val slug = slugifySessionKey(sessionKey)
  return path.join(resolvedRoot, slug)
}

fun resolveSandboxScopeKey(scope: "session" | "agent" | "shared", sessionKey: String) {
  val trimmed = sessionKey.trim() || "main"
  if (scope === "shared") {
    return "shared"
  }
  if (scope === "session") {
    return trimmed
  }
  val agentId = resolveAgentIdFromSessionKey(trimmed)
  return `agent:${agentId}`
}

fun resolveSandboxAgentId(scopeKey: String): String | Nothing? {
  val trimmed = scopeKey.trim()
  if (!trimmed || trimmed === "shared") {
    return Nothing?
  }
  val parts = trimmed.split(":").filter(Boolean)
  if (parts[0] === "agent" && parts[1]) {
    return normalizeAgentId(parts[1])
  }
  return resolveAgentIdFromSessionKey(trimmed)
}
