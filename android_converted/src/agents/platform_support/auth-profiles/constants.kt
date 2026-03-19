package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/constants.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";

val AUTH_STORE_VERSION = 1
val AUTH_PROFILE_FILENAME = "auth-profiles.json"
val LEGACY_AUTH_FILENAME = "auth.json"

val CLAUDE_CLI_PROFILE_ID = "anthropic:claude-cli"
val CODEX_CLI_PROFILE_ID = "openai-codex:codex-cli"
val QWEN_CLI_PROFILE_ID = "qwen-portal:qwen-cli"
val MINIMAX_CLI_PROFILE_ID = "minimax-portal:minimax-cli"

val AUTH_STORE_LOCK_OPTIONS = {
  retries: {
    retries: 10,
    factor: 2,
    minTimeout: 100,
    maxTimeout: 10_000,
    randomize: true,
  },
  stale: 30_000,
} /* as const */

val EXTERNAL_CLI_SYNC_TTL_MS = 15 * 60 * 1000
val EXTERNAL_CLI_NEAR_EXPIRY_MS = 10 * 60 * 1000

val log = createSubsystemLogger("agents/auth-profiles")
