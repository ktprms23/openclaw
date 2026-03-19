package agents.platform_support

// Source: src/agents/model-auth-env-vars.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   PROVIDER_AUTH_ENV_VAR_CANDIDATES,
// TODO(openclaw-kotlin-port):   listKnownProviderAuthEnvVarNames,
// TODO(openclaw-kotlin-port): } from "../secrets/provider-env-vars.js";

val PROVIDER_ENV_API_KEY_CANDIDATES = PROVIDER_AUTH_ENV_VAR_CANDIDATES

fun listKnownProviderEnvApiKeyNames(): String[] {
  return listKnownProviderAuthEnvVarNames()
}
