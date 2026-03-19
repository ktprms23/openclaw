package agents.platform_support

// Source: src/agents/model-auth-markers.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { SecretRefSource } from "../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { listKnownProviderEnvApiKeyNames } from "./model-auth-env-vars.js";

val MINIMAX_OAUTH_MARKER = "minimax-oauth"
val OAUTH_API_KEY_MARKER_PREFIX = "oauth:"
val QWEN_OAUTH_MARKER = "qwen-oauth"
val OLLAMA_LOCAL_AUTH_MARKER = "ollama-local"
val CUSTOM_LOCAL_AUTH_MARKER = "custom-local"
val NON_ENV_SECRETREF_MARKER = "secretref-managed" // pragma: allowlist secret
val SECRETREF_ENV_HEADER_MARKER_PREFIX = "secretref-env:" // pragma: allowlist secret

val AWS_SDK_ENV_MARKERS = new Set([
  "AWS_BEARER_TOKEN_BEDROCK",
  "AWS_ACCESS_KEY_ID",
  "AWS_PROFILE",
])

// Legacy marker names kept for backward compatibility with existing models.json files.
val LEGACY_ENV_API_KEY_MARKERS = [
  "GOOGLE_API_KEY",
  "DEEPSEEK_API_KEY",
  "PERPLEXITY_API_KEY",
  "FIREWORKS_API_KEY",
  "NOVITA_API_KEY",
  "AZURE_OPENAI_API_KEY",
  "AZURE_API_KEY",
  "MINIMAX_CODE_PLAN_KEY",
]

val KNOWN_ENV_API_KEY_MARKERS = new Set([
  ...listKnownProviderEnvApiKeyNames(),
  ...LEGACY_ENV_API_KEY_MARKERS,
  ...AWS_SDK_ENV_MARKERS,
])

fun isAwsSdkAuthMarker(value: String): Boolean {
  return AWS_SDK_ENV_MARKERS.has(value.trim())
}

fun isKnownEnvApiKeyMarker(value: String): Boolean {
  val trimmed = value.trim()
  return KNOWN_ENV_API_KEY_MARKERS.has(trimmed) && !isAwsSdkAuthMarker(trimmed)
}

fun resolveOAuthApiKeyMarker(providerId: String): String {
  return `${OAUTH_API_KEY_MARKER_PREFIX}${providerId.trim()}`
}

fun isOAuthApiKeyMarker(value: String): Boolean {
  return value.trim().startsWith(OAUTH_API_KEY_MARKER_PREFIX)
}

fun resolveNonEnvSecretRefApiKeyMarker(_source: SecretRefSource): String {
  return NON_ENV_SECRETREF_MARKER
}

fun resolveNonEnvSecretRefHeaderValueMarker(_source: SecretRefSource): String {
  return NON_ENV_SECRETREF_MARKER
}

fun resolveEnvSecretRefHeaderValueMarker(envVarName: String): String {
  return `${SECRETREF_ENV_HEADER_MARKER_PREFIX}${envVarName.trim()}`
}

fun isSecretRefHeaderValueMarker(value: String): Boolean {
  val trimmed = value.trim()
  return (
    trimmed === NON_ENV_SECRETREF_MARKER || trimmed.startsWith(SECRETREF_ENV_HEADER_MARKER_PREFIX)
  )
}

fun isNonSecretApiKeyMarker(
  value: String,
  opts?: { includeEnvVarName?: Boolean },
): Boolean {
  val trimmed = value.trim()
  if (!trimmed) {
    return false
  }
  val isKnownMarker =
    trimmed === MINIMAX_OAUTH_MARKER ||
    trimmed === QWEN_OAUTH_MARKER ||
    isOAuthApiKeyMarker(trimmed) ||
    trimmed === OLLAMA_LOCAL_AUTH_MARKER ||
    trimmed === CUSTOM_LOCAL_AUTH_MARKER ||
    trimmed === NON_ENV_SECRETREF_MARKER ||
    isAwsSdkAuthMarker(trimmed)
  if (isKnownMarker) {
    return true
  }
  if (opts?.includeEnvVarName === false) {
    return false
  }
  // Do not treat arbitrary ALL_CAPS values as markers only recognize the
  // known env-var markers we intentionally persist for compatibility.
  return KNOWN_ENV_API_KEY_MARKERS.has(trimmed)
}
