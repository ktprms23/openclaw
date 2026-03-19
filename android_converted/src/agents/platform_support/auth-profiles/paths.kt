package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/paths.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { saveJsonFile } from "../../infra/json-file.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { resolveOpenClawAgentDir } from "../agent-paths.js";
// TODO(openclaw-kotlin-port): import { AUTH_PROFILE_FILENAME, AUTH_STORE_VERSION, LEGACY_AUTH_FILENAME } from "./constants.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore } from "./types.js";

fun resolveAuthStorePath(agentDir?: String): String {
  val resolved = resolveUserPath(agentDir ?? resolveOpenClawAgentDir())
  return path.join(resolved, AUTH_PROFILE_FILENAME)
}

fun resolveLegacyAuthStorePath(agentDir?: String): String {
  val resolved = resolveUserPath(agentDir ?? resolveOpenClawAgentDir())
  return path.join(resolved, LEGACY_AUTH_FILENAME)
}

fun resolveAuthStorePathForDisplay(agentDir?: String): String {
  val pathname = resolveAuthStorePath(agentDir)
  return pathname.startsWith("~") ? pathname : resolveUserPath(pathname)
}

fun ensureAuthStoreFile(pathname: String) {
  if (fs.existsSync(pathname)) {
    return
  }
  val payload: AuthProfileStore = {
    version: AUTH_STORE_VERSION,
    profiles: {},
  }
  saveJsonFile(pathname, payload)
}
