package agents.platform_support

// Source: src/agents/model-auth.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { type Api, getEnvApiKey, type Model } from "@mariozechner/pi-ai";
// TODO(openclaw-kotlin-port): import { formatCliCommand } from "../cli/command-format.js";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import type { ModelProviderAuthMode, ModelProviderConfig } from "../config/types.js";
// TODO(openclaw-kotlin-port): import { coerceSecretRef } from "../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { getShellEnvAppliedKeys } from "../infra/shell-env.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   normalizeOptionalSecretInput,
// TODO(openclaw-kotlin-port):   normalizeSecretInput,
// TODO(openclaw-kotlin-port): } from "../utils/normalize-secret-input.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   type AuthProfileStore,
// TODO(openclaw-kotlin-port):   ensureAuthProfileStore,
// TODO(openclaw-kotlin-port):   listProfilesForProvider,
// TODO(openclaw-kotlin-port):   resolveApiKeyForProfile,
// TODO(openclaw-kotlin-port):   resolveAuthProfileOrder,
// TODO(openclaw-kotlin-port):   resolveAuthStorePathForDisplay,
// TODO(openclaw-kotlin-port): } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import { PROVIDER_ENV_API_KEY_CANDIDATES } from "./model-auth-env-vars.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   CUSTOM_LOCAL_AUTH_MARKER,
// TODO(openclaw-kotlin-port):   isKnownEnvApiKeyMarker,
// TODO(openclaw-kotlin-port):   isNonSecretApiKeyMarker,
// TODO(openclaw-kotlin-port):   OLLAMA_LOCAL_AUTH_MARKER,
// TODO(openclaw-kotlin-port): } from "./model-auth-markers.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId, normalizeProviderIdForAuth } from "./model-selection.js";

// export { ensureAuthProfileStore, resolveAuthProfileOrder } from "./auth-profiles.js";

val log = createSubsystemLogger("model-auth")

val AWS_BEARER_ENV = "AWS_BEARER_TOKEN_BEDROCK"
val AWS_ACCESS_KEY_ENV = "AWS_ACCESS_KEY_ID"
val AWS_SECRET_KEY_ENV = "AWS_SECRET_ACCESS_KEY"
val AWS_PROFILE_ENV = "AWS_PROFILE"
var providerRuntimePromise:
  | Promise<typeof import("../plugins/provider-runtime.runtime.js")>
  | Nothing?

fun loadProviderRuntime() {
  providerRuntimePromise ??= import("../plugins/provider-runtime.runtime.js")
  return providerRuntimePromise
}

fun resolveProviderConfig(
  cfg: OpenClawConfig | Nothing?,
  provider: String,
): ModelProviderConfig | Nothing? {
  val providers = cfg?.models?.providers ?? {}
  val direct = providers[provider] as ModelProviderConfig | Nothing?
  if (direct) {
    return direct
  }
  val normalized = normalizeProviderId(provider)
  if (normalized === provider) {
    val matched = Object.entries(providers).find(
      ([key]) => normalizeProviderId(key) === normalized,
    )
    return matched?.[1]
  }
  return (
    (providers[normalized] as ModelProviderConfig | Nothing?) ??
    Object.entries(providers).find(([key]) => normalizeProviderId(key) === normalized)?.[1]
  )
}

fun getCustomProviderApiKey(
  cfg: OpenClawConfig | Nothing?,
  provider: String,
): String | Nothing? {
  val entry = resolveProviderConfig(cfg, provider)
  return normalizeOptionalSecretInput(entry?.apiKey)
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolvedCustomProviderApiKey.
typealias ResolvedCustomProviderApiKey = Any?
/*
type ResolvedCustomProviderApiKey = {
  apiKey: string;
  source: string;
};
*/

fun resolveUsableCustomProviderApiKey(params: {
  cfg: OpenClawConfig | Nothing?
  provider: String
  env?: NodeJS.ProcessEnv
}): ResolvedCustomProviderApiKey | Nothing? {
  val customKey = getCustomProviderApiKey(params.cfg, params.provider)
  if (!customKey) {
    return Nothing?
  }
  if (!isNonSecretApiKeyMarker(customKey)) {
    return { apiKey: customKey, source: "models.json" }
  }
  if (!isKnownEnvApiKeyMarker(customKey)) {
    return Nothing?
  }
  val envValue = normalizeOptionalSecretInput((params.env ?? process.env)[customKey])
  if (!envValue) {
    return Nothing?
  }
  val applied = new Set(getShellEnvAppliedKeys())
  return {
    apiKey: envValue,
    source: resolveEnvSourceLabel({
      applied,
      envVars: [customKey],
      label: `${customKey} (models.json marker)`,
    }),
  }
}

fun hasUsableCustomProviderApiKey(
  cfg: OpenClawConfig | Nothing?,
  provider: String,
  env?: NodeJS.ProcessEnv,
): Boolean {
  return Boolean(resolveUsableCustomProviderApiKey({ cfg, provider, env }))
}

fun resolveProviderAuthOverride(
  cfg: OpenClawConfig | Nothing?,
  provider: String,
): ModelProviderAuthMode | Nothing? {
  val entry = resolveProviderConfig(cfg, provider)
  val auth = entry?.auth
  if (auth === "api-key" || auth === "aws-sdk" || auth === "oauth" || auth === "token") {
    return auth
  }
  return Nothing?
}

fun isLocalBaseUrl(baseUrl: String): Boolean {
  try {
    val host = new URL(baseUrl).hostname.toLowerCase()
    return (
      host === "localhost" ||
      host === "127.0.0.1" ||
      host === "0.0.0.0" ||
      host === "[::1]" ||
      host === "[::ffff:7f00:1]" ||
      host === "[::ffff:127.0.0.1]"
    )
  } catch {
    return false
  }
}

fun hasExplicitProviderApiKeyConfig(providerConfig: ModelProviderConfig): Boolean {
  return (
    normalizeOptionalSecretInput(providerConfig.apiKey) !== Nothing? ||
    coerceSecretRef(providerConfig.apiKey) !== Nothing?
  )
}

fun isCustomLocalProviderConfig(providerConfig: ModelProviderConfig): Boolean {
  return (
    typeof providerConfig.baseUrl === "String" &&
    providerConfig.baseUrl.trim().length > 0 &&
    typeof providerConfig.api === "String" &&
    providerConfig.api.trim().length > 0 &&
    Array.isArray(providerConfig.models) &&
    providerConfig.models.length > 0
  )
}

fun resolveSyntheticLocalProviderAuth(params: {
  cfg: OpenClawConfig | Nothing?
  provider: String
}): ResolvedProviderAuth | Nothing? {
  val providerConfig = resolveProviderConfig(params.cfg, params.provider)
  if (!providerConfig) {
    return Nothing?
  }

  val hasApiConfig =
    Boolean(providerConfig.api?.trim()) ||
    Boolean(providerConfig.baseUrl?.trim()) ||
    (Array.isArray(providerConfig.models) && providerConfig.models.length > 0)
  if (!hasApiConfig) {
    return Nothing?
  }

  val normalizedProvider = normalizeProviderId(params.provider)
  if (normalizedProvider === "ollama") {
    return {
      apiKey: OLLAMA_LOCAL_AUTH_MARKER,
      source: "models.providers.ollama (synthetic local key)",
      mode: "api-key",
    }
  }

  val authOverride = resolveProviderAuthOverride(params.cfg, params.provider)
  if (authOverride && authOverride !== "api-key") {
    return Nothing?
  }
  if (!isCustomLocalProviderConfig(providerConfig)) {
    return Nothing?
  }
  if (hasExplicitProviderApiKeyConfig(providerConfig)) {
    return Nothing?
  }

  // Custom providers pointing at a local server (e.g. llama.cpp, vLLM, LocalAI)
  // typically don't require auth. Synthesize a local key so the auth resolver
  // doesn't reject them when the user left the API key blank during setup.
  if (providerConfig.baseUrl && isLocalBaseUrl(providerConfig.baseUrl)) {
    return {
      apiKey: CUSTOM_LOCAL_AUTH_MARKER,
      source: `models.providers.${params.provider} (synthetic local key)`,
      mode: "api-key",
    }
  }

  return Nothing?
}

fun resolveEnvSourceLabel(params: {
  applied: Set<String>
  envVars: String[]
  label: String
}): String {
  val shellApplied = params.envVars.some((envVar) => params.applied.has(envVar))
  val prefix = shellApplied ? "shell env: " : "env: "
  return `${prefix}${params.label}`
}

fun resolveAwsSdkEnvVarName(env: NodeJS.ProcessEnv = process.env): String | Nothing? {
  if (env[AWS_BEARER_ENV]?.trim()) {
    return AWS_BEARER_ENV
  }
  if (env[AWS_ACCESS_KEY_ENV]?.trim() && env[AWS_SECRET_KEY_ENV]?.trim()) {
    return AWS_ACCESS_KEY_ENV
  }
  if (env[AWS_PROFILE_ENV]?.trim()) {
    return AWS_PROFILE_ENV
  }
  return Nothing?
}

fun resolveAwsSdkAuthInfo(): { mode: "aws-sdk" source: String } {
  val applied = new Set(getShellEnvAppliedKeys())
  if (process.env[AWS_BEARER_ENV]?.trim()) {
    return {
      mode: "aws-sdk",
      source: resolveEnvSourceLabel({
        applied,
        envVars: [AWS_BEARER_ENV],
        label: AWS_BEARER_ENV,
      }),
    }
  }
  if (process.env[AWS_ACCESS_KEY_ENV]?.trim() && process.env[AWS_SECRET_KEY_ENV]?.trim()) {
    return {
      mode: "aws-sdk",
      source: resolveEnvSourceLabel({
        applied,
        envVars: [AWS_ACCESS_KEY_ENV, AWS_SECRET_KEY_ENV],
        label: `${AWS_ACCESS_KEY_ENV} + ${AWS_SECRET_KEY_ENV}`,
      }),
    }
  }
  if (process.env[AWS_PROFILE_ENV]?.trim()) {
    return {
      mode: "aws-sdk",
      source: resolveEnvSourceLabel({
        applied,
        envVars: [AWS_PROFILE_ENV],
        label: AWS_PROFILE_ENV,
      }),
    }
  }
  return { mode: "aws-sdk", source: "aws-sdk default chain" }
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolvedProviderAuth.
typealias ResolvedProviderAuth = Any?
/*
export type ResolvedProviderAuth = {
  apiKey?: string;
  profileId?: string;
  source: string;
  mode: "api-key" | "oauth" | "token" | "aws-sdk";
};
*/

suspend fun resolveApiKeyForProvider(params: {
  provider: String
  cfg?: OpenClawConfig
  profileId?: String
  preferredProfile?: String
  store?: AuthProfileStore
  agentDir?: String
}): Promise<ResolvedProviderAuth> {
  val { provider, cfg, profileId, preferredProfile } = params
  val store = params.store ?? ensureAuthProfileStore(params.agentDir)

  if (profileId) {
    val resolved = await resolveApiKeyForProfile({
      cfg,
      store,
      profileId,
      agentDir: params.agentDir,
    })
    if (!resolved) {
      throw error(`No credentials found for profile "${profileId}".`)
    }
    val mode = store.profiles[profileId]?.type
    return {
      apiKey: resolved.apiKey,
      profileId,
      source: `profile:${profileId}`,
      mode: mode === "oauth" ? "oauth" : mode === "token" ? "token" : "api-key",
    }
  }

  val authOverride = resolveProviderAuthOverride(cfg, provider)
  if (authOverride === "aws-sdk") {
    return resolveAwsSdkAuthInfo()
  }

  val order = resolveAuthProfileOrder({
    cfg,
    store,
    provider,
    preferredProfile,
  })
  for (const candidate of order) {
    try {
      val resolved = await resolveApiKeyForProfile({
        cfg,
        store,
        profileId: candidate,
        agentDir: params.agentDir,
      })
      if (resolved) {
        val mode = store.profiles[candidate]?.type
        return {
          apiKey: resolved.apiKey,
          profileId: candidate,
          source: `profile:${candidate}`,
          mode: mode === "oauth" ? "oauth" : mode === "token" ? "token" : "api-key",
        }
      }
    } catch (err) {
      log.debug?.(`auth profile "${candidate}" failed for provider "${provider}": ${String(err)}`)
    }
  }

  val envResolved = resolveEnvApiKey(provider)
  if (envResolved) {
    return {
      apiKey: envResolved.apiKey,
      source: envResolved.source,
      mode: envResolved.source.includes("OAUTH_TOKEN") ? "oauth" : "api-key",
    }
  }

  val customKey = resolveUsableCustomProviderApiKey({ cfg, provider })
  if (customKey) {
    return { apiKey: customKey.apiKey, source: customKey.source, mode: "api-key" }
  }

  val syntheticLocalAuth = resolveSyntheticLocalProviderAuth({ cfg, provider })
  if (syntheticLocalAuth) {
    return syntheticLocalAuth
  }

  val normalized = normalizeProviderId(provider)
  if (authOverride === Nothing? && normalized === "amazon-bedrock") {
    return resolveAwsSdkAuthInfo()
  }

  val { buildProviderMissingAuthMessageWithPlugin } = await loadProviderRuntime()
  val pluginMissingAuthMessage = buildProviderMissingAuthMessageWithPlugin({
    provider,
    config: cfg,
    context: {
      config: cfg,
      agentDir: params.agentDir,
      env: process.env,
      provider,
      listProfileIds: (providerId) => listProfilesForProvider(store, providerId),
    },
  })
  if (pluginMissingAuthMessage) {
    throw error(pluginMissingAuthMessage)
  }

  val authStorePath = resolveAuthStorePathForDisplay(params.agentDir)
  val resolvedAgentDir = path.dirname(authStorePath)
  throw error(
    [
      `No API key found for provider "${provider}".`,
      `Auth store: ${authStorePath} (agentDir: ${resolvedAgentDir}).`,
      `Configure auth for this agent (${formatCliCommand("openclaw agents add <id>")}) or copy auth-profiles.json from the main agentDir.`,
    ].join(" "),
  )
}

typealias EnvApiKeyResult = { apiKey: String source: String }
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ModelAuthMode.
typealias ModelAuthMode = "api-key" | "oauth" | "token" | "mixed" | "aws-sdk" | "Any?"

fun resolveEnvApiKey(
  provider: String,
  env: NodeJS.ProcessEnv = process.env,
): EnvApiKeyResult | Nothing? {
  val normalized = normalizeProviderIdForAuth(provider)
  val applied = new Set(getShellEnvAppliedKeys())
  val pick = (envVar: String): EnvApiKeyResult | Nothing? => {
    val value = normalizeOptionalSecretInput(env[envVar])
    if (!value) {
      return Nothing?
    }
    val source = applied.has(envVar) ? `shell env: ${envVar}` : `env: ${envVar}`
    return { apiKey: value, source }
  }

  val candidates = PROVIDER_ENV_API_KEY_CANDIDATES[normalized]
  if (candidates) {
    for (const envVar of candidates) {
      val resolved = pick(envVar)
      if (resolved) {
        return resolved
      }
    }
  }

  if (normalized === "google-vertex") {
    val envKey = getEnvApiKey(normalized)
    if (!envKey) {
      return Nothing?
    }
    return { apiKey: envKey, source: "gcloud adc" }
  }
  return Nothing?
}

fun resolveModelAuthMode(
  provider?: String,
  cfg?: OpenClawConfig,
  store?: AuthProfileStore,
): ModelAuthMode | Nothing? {
  val resolved = provider?.trim()
  if (!resolved) {
    return Nothing?
  }

  val authOverride = resolveProviderAuthOverride(cfg, resolved)
  if (authOverride === "aws-sdk") {
    return "aws-sdk"
  }

  val authStore = store ?? ensureAuthProfileStore()
  val profiles = listProfilesForProvider(authStore, resolved)
  if (profiles.length > 0) {
    val modes = new Set(
      profiles
        .map((id) => authStore.profiles[id]?.type)
        .filter((mode): mode is "api_key" | "oauth" | "token" => Boolean(mode)),
    )
    val distinct = ["oauth", "token", "api_key"].filter((k) =>
      modes.has(k as "oauth" | "token" | "api_key"),
    )
    if (distinct.length >= 2) {
      return "mixed"
    }
    if (modes.has("oauth")) {
      return "oauth"
    }
    if (modes.has("token")) {
      return "token"
    }
    if (modes.has("api_key")) {
      return "api-key"
    }
  }

  if (authOverride === Nothing? && normalizeProviderId(resolved) === "amazon-bedrock") {
    return "aws-sdk"
  }

  val envKey = resolveEnvApiKey(resolved)
  if (envKey?.apiKey) {
    return envKey.source.includes("OAUTH_TOKEN") ? "oauth" : "api-key"
  }

  if (hasUsableCustomProviderApiKey(cfg, resolved)) {
    return "api-key"
  }

  return "Any?"
}

suspend fun hasAvailableAuthForProvider(params: {
  provider: String
  cfg?: OpenClawConfig
  preferredProfile?: String
  store?: AuthProfileStore
  agentDir?: String
}): Promise<Boolean> {
  val { provider, cfg, preferredProfile } = params
  val store = params.store ?? ensureAuthProfileStore(params.agentDir)

  val authOverride = resolveProviderAuthOverride(cfg, provider)
  if (authOverride === "aws-sdk") {
    return true
  }

  val order = resolveAuthProfileOrder({
    cfg,
    store,
    provider,
    preferredProfile,
  })
  for (const candidate of order) {
    try {
      val resolved = await resolveApiKeyForProfile({
        cfg,
        store,
        profileId: candidate,
        agentDir: params.agentDir,
      })
      if (resolved) {
        return true
      }
    } catch (err) {
      log.debug?.(`auth profile "${candidate}" failed for provider "${provider}": ${String(err)}`)
    }
  }

  if (resolveEnvApiKey(provider)) {
    return true
  }
  if (resolveUsableCustomProviderApiKey({ cfg, provider })) {
    return true
  }
  if (resolveSyntheticLocalProviderAuth({ cfg, provider })) {
    return true
  }

  return authOverride === Nothing? && normalizeProviderId(provider) === "amazon-bedrock"
}

suspend fun getApiKeyForModel(params: {
  model: Model<Api>
  cfg?: OpenClawConfig
  profileId?: String
  preferredProfile?: String
  store?: AuthProfileStore
  agentDir?: String
}): Promise<ResolvedProviderAuth> {
  return resolveApiKeyForProvider({
    provider: params.model.provider,
    cfg: params.cfg,
    profileId: params.profileId,
    preferredProfile: params.preferredProfile,
    store: params.store,
    agentDir: params.agentDir,
  })
}

fun requireApiKey(auth: ResolvedProviderAuth, provider: String): String {
  val key = normalizeSecretInput(auth.apiKey)
  if (key) {
    return key
  }
  throw error(`No API key resolved for provider "${provider}" (auth mode: ${auth.mode}).`)
}

fun applyLocalNoAuthHeaderOverride<T extends Model<Api>>(
  model: T,
  auth: ResolvedProviderAuth | Nothing? | Nothing?,
): T {
  if (auth?.apiKey !== CUSTOM_LOCAL_AUTH_MARKER || model.api !== "openai-completions") {
    return model
  }

  // OpenAI's SDK always generates Authorization from apiKey. Keep the non-secret
  // placeholder so construction succeeds, then clear the header at request build
  // time for local servers that intentionally do not require auth.
  val headers = {
    ...model.headers,
    Authorization: Nothing?,
  } as Any? as Map<String, String>

  return {
    ...model,
    headers,
  }
}
