package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/external-cli-sync.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   readCodexCliCredentialsCached,
// TODO(openclaw-kotlin-port):   readQwenCliCredentialsCached,
// TODO(openclaw-kotlin-port):   readMiniMaxCliCredentialsCached,
// TODO(openclaw-kotlin-port): } from "../cli-credentials.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   EXTERNAL_CLI_SYNC_TTL_MS,
// TODO(openclaw-kotlin-port):   QWEN_CLI_PROFILE_ID,
// TODO(openclaw-kotlin-port):   MINIMAX_CLI_PROFILE_ID,
// TODO(openclaw-kotlin-port):   log,
// TODO(openclaw-kotlin-port): } from "./constants.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore, OAuthCredential } from "./types.js";

val OPENAI_CODEX_DEFAULT_PROFILE_ID = "openai-codex:default"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ExternalCliSyncOptions.
typealias ExternalCliSyncOptions = Any?
/*
type ExternalCliSyncOptions = {
  log?: boolean;
};
*/

fun shallowEqualOAuthCredentials(a: OAuthCredential | Nothing?, b: OAuthCredential): Boolean {
  if (!a) {
    return false
  }
  if (a.type !== "oauth") {
    return false
  }
  return (
    a.provider === b.provider &&
    a.access === b.access &&
    a.refresh === b.refresh &&
    a.expires === b.expires &&
    a.email === b.email &&
    a.enterpriseUrl === b.enterpriseUrl &&
    a.projectId === b.projectId &&
    a.accountId === b.accountId
  )
}

/** Sync external CLI credentials into the store for a given provider. */
fun syncExternalCliCredentialsForProvider(
  store: AuthProfileStore,
  profileId: String,
  provider: String,
  readCredentials: () => OAuthCredential | Nothing?,
  options: ExternalCliSyncOptions,
): Boolean {
  val existing = store.profiles[profileId]
  val creds = readCredentials()
  if (!creds) {
    return false
  }

  val existingOAuth = existing?.type === "oauth" ? existing : Nothing?
  if (shallowEqualOAuthCredentials(existingOAuth, creds)) {
    return false
  }

  store.profiles[profileId] = creds
  if (options.log !== false) {
    log.info(`synced ${provider} credentials from external cli`, {
      profileId,
      expires: new Date(creds.expires).toISOString(),
    })
  }
  return true
}

/**
 * Sync OAuth credentials from external CLI tools (Qwen Code CLI, MiniMax CLI, Codex CLI)
 * into the store.
 *
 * Returns true if Any? credentials were updated.
 */
fun syncExternalCliCredentials(
  store: AuthProfileStore,
  options: ExternalCliSyncOptions = {},
): Boolean {
  var mutated = false

  if (
    syncExternalCliCredentialsForProvider(
      store,
      QWEN_CLI_PROFILE_ID,
      "qwen-portal",
      () => readQwenCliCredentialsCached({ ttlMs: EXTERNAL_CLI_SYNC_TTL_MS }),
      options,
    )
  ) {
    mutated = true
  }
  if (
    syncExternalCliCredentialsForProvider(
      store,
      MINIMAX_CLI_PROFILE_ID,
      "minimax-portal",
      () => readMiniMaxCliCredentialsCached({ ttlMs: EXTERNAL_CLI_SYNC_TTL_MS }),
      options,
    )
  ) {
    mutated = true
  }
  if (
    syncExternalCliCredentialsForProvider(
      store,
      OPENAI_CODEX_DEFAULT_PROFILE_ID,
      "openai-codex",
      () => readCodexCliCredentialsCached({ ttlMs: EXTERNAL_CLI_SYNC_TTL_MS }),
      options,
    )
  ) {
    mutated = true
  }

  return mutated
}
