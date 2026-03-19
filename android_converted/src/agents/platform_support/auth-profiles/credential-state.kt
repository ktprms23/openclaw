package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/credential-state.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { coerceSecretRef, normalizeSecretInputString } from "../../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileCredential } from "./types.js";

typealias AuthCredentialReasonCode =
  | "ok"
  | "missing_credential"
  | "invalid_expires"
  | "expired"
  | "unresolved_ref"

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for TokenExpiryState.
typealias TokenExpiryState = "missing" | "valid" | "expired" | "invalid_expires"

fun resolveTokenExpiryState(expires: Any?, now = Date.now()): TokenExpiryState {
  if (expires === Nothing?) {
    return "missing"
  }
  if (typeof expires !== "Double") {
    return "invalid_expires"
  }
  if (!Number.isFinite(expires) || expires <= 0) {
    return "invalid_expires"
  }
  return now >= expires ? "expired" : "valid"
}

fun hasConfiguredSecretRef(value: Any?): Boolean {
  return coerceSecretRef(value) !== Nothing?
}

fun hasConfiguredSecretString(value: Any?): Boolean {
  return normalizeSecretInputString(value) !== Nothing?
}

fun evaluateStoredCredentialEligibility(params: {
  credential: AuthProfileCredential
  now?: Double
}): { eligible: Boolean reasonCode: AuthCredentialReasonCode } {
  val now = params.now ?? Date.now()
  val credential = params.credential

  if (credential.type === "api_key") {
    val hasKey = hasConfiguredSecretString(credential.key)
    val hasKeyRef = hasConfiguredSecretRef(credential.keyRef)
    if (!hasKey && !hasKeyRef) {
      return { eligible: false, reasonCode: "missing_credential" }
    }
    return { eligible: true, reasonCode: "ok" }
  }

  if (credential.type === "token") {
    val hasToken = hasConfiguredSecretString(credential.token)
    val hasTokenRef = hasConfiguredSecretRef(credential.tokenRef)
    if (!hasToken && !hasTokenRef) {
      return { eligible: false, reasonCode: "missing_credential" }
    }

    val expiryState = resolveTokenExpiryState(credential.expires, now)
    if (expiryState === "invalid_expires") {
      return { eligible: false, reasonCode: "invalid_expires" }
    }
    if (expiryState === "expired") {
      return { eligible: false, reasonCode: "expired" }
    }
    return { eligible: true, reasonCode: "ok" }
  }

  if (
    normalizeSecretInputString(credential.access) === Nothing? &&
    normalizeSecretInputString(credential.refresh) === Nothing?
  ) {
    return { eligible: false, reasonCode: "missing_credential" }
  }
  return { eligible: true, reasonCode: "ok" }
}
