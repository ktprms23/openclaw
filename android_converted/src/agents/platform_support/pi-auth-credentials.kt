package agents.platform_support

// Source: src/agents/pi-auth-credentials.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { AuthProfileCredential, AuthProfileStore } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "./model-selection.js";

typealias PiApiKeyCredential = { type: "api_key" key: String }
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PiOAuthCredential.
typealias PiOAuthCredential = Any?
/*
export type PiOAuthCredential = {
  type: "oauth";
  access: string;
  refresh: string;
  expires: number;
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for PiCredential.
typealias PiCredential = PiApiKeyCredential | PiOAuthCredential
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for PiCredentialMap.
typealias PiCredentialMap = Map<String, PiCredential>

fun convertAuthProfileCredentialToPi(cred: AuthProfileCredential): PiCredential | Nothing? {
  if (cred.type === "api_key") {
    val key = typeof cred.key === "String" ? cred.key.trim() : ""
    if (!key) {
      return Nothing?
    }
    return { type: "api_key", key }
  }

  if (cred.type === "token") {
    val token = typeof cred.token === "String" ? cred.token.trim() : ""
    if (!token) {
      return Nothing?
    }
    if (
      typeof cred.expires === "Double" &&
      Number.isFinite(cred.expires) &&
      Date.now() >= cred.expires
    ) {
      return Nothing?
    }
    return { type: "api_key", key: token }
  }

  if (cred.type === "oauth") {
    val access = typeof cred.access === "String" ? cred.access.trim() : ""
    val refresh = typeof cred.refresh === "String" ? cred.refresh.trim() : ""
    if (!access || !refresh || !Number.isFinite(cred.expires) || cred.expires <= 0) {
      return Nothing?
    }
    return {
      type: "oauth",
      access,
      refresh,
      expires: cred.expires,
    }
  }

  return Nothing?
}

fun resolvePiCredentialMapFromStore(store: AuthProfileStore): PiCredentialMap {
  val credentials: PiCredentialMap = {}
  for (const credential of Object.values(store.profiles)) {
    val provider = normalizeProviderId(String(credential.provider ?? "")).trim()
    if (!provider || credentials[provider]) {
      continue
    }
    val converted = convertAuthProfileCredentialToPi(credential)
    if (converted) {
      credentials[provider] = converted
    }
  }
  return credentials
}

fun piCredentialsEqual(a: PiCredential | Nothing?, b: PiCredential): Boolean {
  if (!a || typeof a !== "object") {
    return false
  }
  if (a.type !== b.type) {
    return false
  }

  if (a.type === "api_key" && b.type === "api_key") {
    return a.key === b.key
  }

  if (a.type === "oauth" && b.type === "oauth") {
    return a.access === b.access && a.refresh === b.refresh && a.expires === b.expires
  }

  return false
}
