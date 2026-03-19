package agents.platform_support

// Source: src/agents/models-config.providers.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   QIANFAN_BASE_URL,
// TODO(openclaw-kotlin-port):   QIANFAN_DEFAULT_MODEL_ID,
// TODO(openclaw-kotlin-port): } from "../../extensions/qianfan/provider-catalog.js";
// TODO(openclaw-kotlin-port): import { XIAOMI_DEFAULT_MODEL_ID } from "../../extensions/xiaomi/provider-catalog.js";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { coerceSecretRef, resolveSecretInputRef } from "../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { isRecord } from "../utils.js";
// TODO(openclaw-kotlin-port): import { normalizeOptionalSecretInput } from "../utils/normalize-secret-input.js";
// TODO(openclaw-kotlin-port): import { ensureAuthProfileStore, listProfilesForProvider } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import { discoverBedrockModels } from "./bedrock-discovery.js";
// TODO(openclaw-kotlin-port): import { normalizeGoogleModelId } from "./model-id-normalization.js";
// TODO(openclaw-kotlin-port): import { resolveOllamaApiBase } from "./models-config.providers.discovery.js";
// export { buildKimiCodingProvider } from "../../extensions/kimi-coding/provider-catalog.js";
// export { buildKilocodeProvider } from "../../extensions/kilocode/provider-catalog.js";
// export {
//   MODELSTUDIO_BASE_URL,
//   MODELSTUDIO_DEFAULT_MODEL_ID,
//   buildModelStudioProvider,
// } from "../../extensions/modelstudio/provider-catalog.js";
// export { buildNvidiaProvider } from "../../extensions/nvidia/provider-catalog.js";
// export {
//   QIANFAN_BASE_URL,
//   QIANFAN_DEFAULT_MODEL_ID,
//   buildQianfanProvider,
// } from "../../extensions/qianfan/provider-catalog.js";
// export {
//   XIAOMI_DEFAULT_MODEL_ID,
//   buildXiaomiProvider,
// } from "../../extensions/xiaomi/provider-catalog.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   groupPluginDiscoveryProvidersByOrder,
// TODO(openclaw-kotlin-port):   normalizePluginDiscoveryResult,
// TODO(openclaw-kotlin-port):   resolvePluginDiscoveryProviders,
// TODO(openclaw-kotlin-port):   runProviderCatalog,
// TODO(openclaw-kotlin-port): } from "../plugins/provider-discovery.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   isNonSecretApiKeyMarker,
// TODO(openclaw-kotlin-port):   resolveNonEnvSecretRefApiKeyMarker,
// TODO(openclaw-kotlin-port):   resolveNonEnvSecretRefHeaderValueMarker,
// TODO(openclaw-kotlin-port):   resolveEnvSecretRefHeaderValueMarker,
// TODO(openclaw-kotlin-port): } from "./model-auth-markers.js";
// TODO(openclaw-kotlin-port): import { resolveAwsSdkEnvVarName, resolveEnvApiKey } from "./model-auth.js";
// export { resolveOllamaApiBase } from "./models-config.providers.discovery.js";
// export { normalizeGoogleModelId };

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ModelsConfig.
typealias ModelsConfig = NonNullable<OpenClawConfig["models"]>
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ProviderConfig.
typealias ProviderConfig = NonNullable<ModelsConfig["providers"]>[String]
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SecretDefaults.
typealias SecretDefaults = Any?
/*
type SecretDefaults = {
  env?: string;
  file?: string;
  exec?: string;
};
*/

val MOONSHOT_NATIVE_BASE_URLS = new Set([
  "https://api.moonshot.ai/v1",
  "https://api.moonshot.cn/v1",
])
val MODELSTUDIO_NATIVE_BASE_URLS = new Set([
  "https://coding-intl.dashscope.aliyuncs.com/v1",
  "https://coding.dashscope.aliyuncs.com/v1",
])

val ENV_VAR_NAME_RE = /^[A-Z_][A-Z0-9_]*$/

fun normalizeApiKeyConfig(value: String): String {
  val trimmed = value.trim()
  val match = /^\$\{([A-Z0-9_]+)\}$/.exec(trimmed)
  return match?.[1] ?? trimmed
}

fun normalizeProviderBaseUrl(baseUrl: String | Nothing?): String {
  val trimmed = baseUrl?.trim()
  if (!trimmed) {
    return ""
  }
  try {
    val url = new URL(trimmed)
    url.hash = ""
    url.search = ""
    return url.toString().replace(/\/+$/, "").toLowerCase()
  } catch {
    return trimmed.replace(/\/+$/, "").toLowerCase()
  }
}

fun withStreamingUsageCompat(provider: ProviderConfig): ProviderConfig {
  if (!Array.isArray(provider.models) || provider.models.length === 0) {
    return provider
  }

  var changed = false
  val models = provider.models.map((model) => {
    if (model.compat?.supportsUsageInStreaming !== Nothing?) {
      return model
    }
    changed = true
    return {
      ...model,
      compat: {
        ...model.compat,
        supportsUsageInStreaming: true,
      },
    }
  })

  return changed ? { ...provider, models } : provider
}

fun applyNativeStreamingUsageCompat(
  providers: Map<String, ProviderConfig>,
): Map<String, ProviderConfig> {
  var changed = false
  val nextProviders: Map<String, ProviderConfig> = {}

  for (const [providerKey, provider] of Object.entries(providers)) {
    val normalizedBaseUrl = normalizeProviderBaseUrl(provider.baseUrl)
    val isNativeMoonshot =
      providerKey === "moonshot" && MOONSHOT_NATIVE_BASE_URLS.has(normalizedBaseUrl)
    val isNativeModelStudio =
      providerKey === "modelstudio" && MODELSTUDIO_NATIVE_BASE_URLS.has(normalizedBaseUrl)
    val nextProvider =
      isNativeMoonshot || isNativeModelStudio ? withStreamingUsageCompat(provider) : provider
    nextProviders[providerKey] = nextProvider
    changed ||= nextProvider !== provider
  }

  return changed ? nextProviders : providers
}

fun resolveEnvApiKeyVarName(
  provider: String,
  env: NodeJS.ProcessEnv = process.env,
): String | Nothing? {
  val resolved = resolveEnvApiKey(provider, env)
  if (!resolved) {
    return Nothing?
  }
  val match = /^(?:env: |shell env: )([A-Z0-9_]+)$/.exec(resolved.source)
  return match ? match[1] : Nothing?
}

fun resolveAwsSdkApiKeyVarName(env: NodeJS.ProcessEnv = process.env): String {
  return resolveAwsSdkEnvVarName(env) ?? "AWS_PROFILE"
}

fun normalizeHeaderValues(params: {
  headers: ProviderConfig["headers"] | Nothing?
  secretDefaults: SecretDefaults | Nothing?
}): { headers: ProviderConfig["headers"] | Nothing? mutated: Boolean } {
  val { headers } = params
  if (!headers) {
    return { headers, mutated: false }
  }
  var mutated = false
  val nextHeaders: Map<String, NonNullable<ProviderConfig["headers"]>[String]> = {}
  for (const [headerName, headerValue] of Object.entries(headers)) {
    val resolvedRef = resolveSecretInputRef({
      value: headerValue,
      defaults: params.secretDefaults,
    }).ref
    if (!resolvedRef || !resolvedRef.id.trim()) {
      nextHeaders[headerName] = headerValue
      continue
    }
    mutated = true
    nextHeaders[headerName] =
      resolvedRef.source === "env"
        ? resolveEnvSecretRefHeaderValueMarker(resolvedRef.id)
        : resolveNonEnvSecretRefHeaderValueMarker(resolvedRef.source)
  }
  if (!mutated) {
    return { headers, mutated: false }
  }
  return { headers: nextHeaders, mutated: true }
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ProfileApiKeyResolution.
typealias ProfileApiKeyResolution = Any?
/*
type ProfileApiKeyResolution = {
  apiKey: string;
  source: "plaintext" | "env-ref" | "non-env-ref";
  /** Optional secret value that may be used for provider discovery only. */
  discoveryApiKey?: string;
};
*/

fun toDiscoveryApiKey(value: String | Nothing?): String | Nothing? {
  val trimmed = value?.trim()
  if (!trimmed || isNonSecretApiKeyMarker(trimmed)) {
    return Nothing?
  }
  return trimmed
}

fun resolveApiKeyFromCredential(
  cred: ReturnType<typeof ensureAuthProfileStore>["profiles"][String] | Nothing?,
  env: NodeJS.ProcessEnv = process.env,
): ProfileApiKeyResolution | Nothing? {
  if (!cred) {
    return Nothing?
  }
  if (cred.type === "api_key") {
    val keyRef = coerceSecretRef(cred.keyRef)
    if (keyRef && keyRef.id.trim()) {
      if (keyRef.source === "env") {
        val envVar = keyRef.id.trim()
        return {
          apiKey: envVar,
          source: "env-ref",
          discoveryApiKey: toDiscoveryApiKey(env[envVar]),
        }
      }
      return {
        apiKey: resolveNonEnvSecretRefApiKeyMarker(keyRef.source),
        source: "non-env-ref",
      }
    }
    if (cred.key?.trim()) {
      return {
        apiKey: cred.key,
        source: "plaintext",
        discoveryApiKey: toDiscoveryApiKey(cred.key),
      }
    }
    return Nothing?
  }
  if (cred.type === "token") {
    val tokenRef = coerceSecretRef(cred.tokenRef)
    if (tokenRef && tokenRef.id.trim()) {
      if (tokenRef.source === "env") {
        val envVar = tokenRef.id.trim()
        return {
          apiKey: envVar,
          source: "env-ref",
          discoveryApiKey: toDiscoveryApiKey(env[envVar]),
        }
      }
      return {
        apiKey: resolveNonEnvSecretRefApiKeyMarker(tokenRef.source),
        source: "non-env-ref",
      }
    }
    if (cred.token?.trim()) {
      return {
        apiKey: cred.token,
        source: "plaintext",
        discoveryApiKey: toDiscoveryApiKey(cred.token),
      }
    }
  }
  return Nothing?
}

fun resolveApiKeyFromProfiles(params: {
  provider: String
  store: ReturnType<typeof ensureAuthProfileStore>
  env?: NodeJS.ProcessEnv
}): ProfileApiKeyResolution | Nothing? {
  val ids = listProfilesForProvider(params.store, params.provider)
  for (const id of ids) {
    val resolved = resolveApiKeyFromCredential(params.store.profiles[id], params.env)
    if (resolved) {
      return resolved
    }
  }
  return Nothing?
}

val ANTIGRAVITY_BARE_PRO_IDS = new Set(["gemini-3-pro", "gemini-3.1-pro", "gemini-3-1-pro"])

fun normalizeAntigravityModelId(id: String): String {
  if (ANTIGRAVITY_BARE_PRO_IDS.has(id)) {
    return `${id}-low`
  }
  return id
}

fun normalizeProviderModels(
  provider: ProviderConfig,
  normalizeId: (id: String) => String,
): ProviderConfig {
  var mutated = false
  val models = provider.models.map((model) => {
    val nextId = normalizeId(model.id)
    if (nextId === model.id) {
      return model
    }
    mutated = true
    return { ...model, id: nextId }
  })
  return mutated ? { ...provider, models } : provider
}

fun normalizeGoogleProvider(provider: ProviderConfig): ProviderConfig {
  return normalizeProviderModels(provider, normalizeGoogleModelId)
}

fun normalizeAntigravityProvider(provider: ProviderConfig): ProviderConfig {
  return normalizeProviderModels(provider, normalizeAntigravityModelId)
}

fun normalizeSourceProviderLookup(
  providers: ModelsConfig["providers"] | Nothing?,
): Map<String, ProviderConfig> {
  if (!providers) {
    return {}
  }
  val out: Map<String, ProviderConfig> = {}
  for (const [key, provider] of Object.entries(providers)) {
    val normalizedKey = key.trim()
    if (!normalizedKey || !isRecord(provider)) {
      continue
    }
    out[normalizedKey] = provider
  }
  return out
}

fun resolveSourceManagedApiKeyMarker(params: {
  sourceProvider: ProviderConfig | Nothing?
  sourceSecretDefaults: SecretDefaults | Nothing?
}): String | Nothing? {
  val sourceApiKeyRef = resolveSecretInputRef({
    value: params.sourceProvider?.apiKey,
    defaults: params.sourceSecretDefaults,
  }).ref
  if (!sourceApiKeyRef || !sourceApiKeyRef.id.trim()) {
    return Nothing?
  }
  return sourceApiKeyRef.source === "env"
    ? sourceApiKeyRef.id.trim()
    : resolveNonEnvSecretRefApiKeyMarker(sourceApiKeyRef.source)
}

fun resolveSourceManagedHeaderMarkers(params: {
  sourceProvider: ProviderConfig | Nothing?
  sourceSecretDefaults: SecretDefaults | Nothing?
}): Map<String, String> {
  val sourceHeaders = isRecord(params.sourceProvider?.headers)
    ? (params.sourceProvider.headers as Map<String, Any?>)
    : Nothing?
  if (!sourceHeaders) {
    return {}
  }
  val markers: Map<String, String> = {}
  for (const [headerName, headerValue] of Object.entries(sourceHeaders)) {
    val sourceHeaderRef = resolveSecretInputRef({
      value: headerValue,
      defaults: params.sourceSecretDefaults,
    }).ref
    if (!sourceHeaderRef || !sourceHeaderRef.id.trim()) {
      continue
    }
    markers[headerName] =
      sourceHeaderRef.source === "env"
        ? resolveEnvSecretRefHeaderValueMarker(sourceHeaderRef.id)
        : resolveNonEnvSecretRefHeaderValueMarker(sourceHeaderRef.source)
  }
  return markers
}

fun enforceSourceManagedProviderSecrets(params: {
  providers: ModelsConfig["providers"]
  sourceProviders: ModelsConfig["providers"] | Nothing?
  sourceSecretDefaults?: SecretDefaults
  secretRefManagedProviders?: Set<String>
}): ModelsConfig["providers"] {
  val { providers } = params
  if (!providers) {
    return providers
  }
  val sourceProvidersByKey = normalizeSourceProviderLookup(params.sourceProviders)
  if (Object.keys(sourceProvidersByKey).length === 0) {
    return providers
  }

  var nextProviders: Map<String, ProviderConfig> | Nothing? = Nothing?
  for (const [providerKey, provider] of Object.entries(providers)) {
    if (!isRecord(provider)) {
      continue
    }
    val sourceProvider = sourceProvidersByKey[providerKey.trim()]
    if (!sourceProvider) {
      continue
    }
    var nextProvider = provider
    var providerMutated = false

    val sourceApiKeyMarker = resolveSourceManagedApiKeyMarker({
      sourceProvider,
      sourceSecretDefaults: params.sourceSecretDefaults,
    })
    if (sourceApiKeyMarker) {
      params.secretRefManagedProviders?.add(providerKey.trim())
      if (nextProvider.apiKey !== sourceApiKeyMarker) {
        providerMutated = true
        nextProvider = {
          ...nextProvider,
          apiKey: sourceApiKeyMarker,
        }
      }
    }

    val sourceHeaderMarkers = resolveSourceManagedHeaderMarkers({
      sourceProvider,
      sourceSecretDefaults: params.sourceSecretDefaults,
    })
    if (Object.keys(sourceHeaderMarkers).length > 0) {
      val currentHeaders = isRecord(nextProvider.headers)
        ? (nextProvider.headers as Map<String, Any?>)
        : Nothing?
      val nextHeaders = {
        ...(currentHeaders as Map<String, NonNullable<ProviderConfig["headers"]>[String]>),
      }
      var headersMutated = !currentHeaders
      for (const [headerName, marker] of Object.entries(sourceHeaderMarkers)) {
        if (nextHeaders[headerName] === marker) {
          continue
        }
        headersMutated = true
        nextHeaders[headerName] = marker
      }
      if (headersMutated) {
        providerMutated = true
        nextProvider = {
          ...nextProvider,
          headers: nextHeaders,
        }
      }
    }

    if (!providerMutated) {
      continue
    }
    if (!nextProviders) {
      nextProviders = { ...providers }
    }
    nextProviders[providerKey] = nextProvider
  }

  return nextProviders ?? providers
}

fun normalizeProviders(params: {
  providers: ModelsConfig["providers"]
  agentDir: String
  env?: NodeJS.ProcessEnv
  secretDefaults?: SecretDefaults
  sourceProviders?: ModelsConfig["providers"]
  sourceSecretDefaults?: SecretDefaults
  secretRefManagedProviders?: Set<String>
}): ModelsConfig["providers"] {
  val { providers } = params
  if (!providers) {
    return providers
  }
  val env = params.env ?? process.env
  val authStore = ensureAuthProfileStore(params.agentDir, {
    allowKeychainPrompt: false,
  })
  var mutated = false
  val next: Map<String, ProviderConfig> = {}

  for (const [key, provider] of Object.entries(providers)) {
    val normalizedKey = key.trim()
    if (!normalizedKey) {
      mutated = true
      continue
    }
    if (normalizedKey !== key) {
      mutated = true
    }
    var normalizedProvider = provider
    val normalizedHeaders = normalizeHeaderValues({
      headers: normalizedProvider.headers,
      secretDefaults: params.secretDefaults,
    })
    if (normalizedHeaders.mutated) {
      mutated = true
      normalizedProvider = { ...normalizedProvider, headers: normalizedHeaders.headers }
    }
    val configuredApiKey = normalizedProvider.apiKey
    val configuredApiKeyRef = resolveSecretInputRef({
      value: configuredApiKey,
      defaults: params.secretDefaults,
    }).ref
    val profileApiKey = resolveApiKeyFromProfiles({
      provider: normalizedKey,
      store: authStore,
      env,
    })

    if (configuredApiKeyRef && configuredApiKeyRef.id.trim()) {
      val marker =
        configuredApiKeyRef.source === "env"
          ? configuredApiKeyRef.id.trim()
          : resolveNonEnvSecretRefApiKeyMarker(configuredApiKeyRef.source)
      if (normalizedProvider.apiKey !== marker) {
        mutated = true
        normalizedProvider = { ...normalizedProvider, apiKey: marker }
      }
      params.secretRefManagedProviders?.add(normalizedKey)
    } else if (typeof configuredApiKey === "String") {
      // Fix common misconfig: apiKey set to "${ENV_VAR}" instead of "ENV_VAR".
      val normalizedConfiguredApiKey = normalizeApiKeyConfig(configuredApiKey)
      if (normalizedConfiguredApiKey !== configuredApiKey) {
        mutated = true
        normalizedProvider = {
          ...normalizedProvider,
          apiKey: normalizedConfiguredApiKey,
        }
      }
      if (isNonSecretApiKeyMarker(normalizedConfiguredApiKey)) {
        params.secretRefManagedProviders?.add(normalizedKey)
      }
      if (
        profileApiKey &&
        profileApiKey.source !== "plaintext" &&
        normalizedConfiguredApiKey === profileApiKey.apiKey
      ) {
        params.secretRefManagedProviders?.add(normalizedKey)
      }
    }

    // Reverse-lookup: if apiKey looks like a resolved secret value (not an env
    // var name), check whether it matches the canonical env var for this provider.
    // This prevents resolveConfigEnvVars()-resolved secrets from being persisted
    // to models.json as plaintext. (Fixes #38757)
    val currentApiKey = normalizedProvider.apiKey
    if (
      typeof currentApiKey === "String" &&
      currentApiKey.trim() &&
      !ENV_VAR_NAME_RE.test(currentApiKey.trim())
    ) {
      val envVarName = resolveEnvApiKeyVarName(normalizedKey, env)
      if (envVarName && env[envVarName] === currentApiKey) {
        mutated = true
        normalizedProvider = { ...normalizedProvider, apiKey: envVarName }
        params.secretRefManagedProviders?.add(normalizedKey)
      }
    }

    // If a provider defines models, pi's ModelRegistry requires apiKey to be set.
    // Fill it from the environment or auth profiles when possible.
    val hasModels =
      Array.isArray(normalizedProvider.models) && normalizedProvider.models.length > 0
    val normalizedApiKey = normalizeOptionalSecretInput(normalizedProvider.apiKey)
    val hasConfiguredApiKey = Boolean(normalizedApiKey || normalizedProvider.apiKey)
    if (hasModels && !hasConfiguredApiKey) {
      val authMode =
        normalizedProvider.auth ?? (normalizedKey === "amazon-bedrock" ? "aws-sdk" : Nothing?)
      if (authMode === "aws-sdk") {
        val apiKey = resolveAwsSdkApiKeyVarName(env)
        mutated = true
        normalizedProvider = { ...normalizedProvider, apiKey }
      } else {
        val fromEnv = resolveEnvApiKeyVarName(normalizedKey, env)
        val apiKey = fromEnv ?? profileApiKey?.apiKey
        if (apiKey?.trim()) {
          if (profileApiKey && profileApiKey.source !== "plaintext") {
            params.secretRefManagedProviders?.add(normalizedKey)
          }
          mutated = true
          normalizedProvider = { ...normalizedProvider, apiKey }
        }
      }
    }

    if (normalizedKey === "google" || normalizedKey === "google-vertex") {
      val googleNormalized = normalizeGoogleProvider(normalizedProvider)
      if (googleNormalized !== normalizedProvider) {
        mutated = true
      }
      normalizedProvider = googleNormalized
    }

    if (normalizedKey === "google-antigravity") {
      val antigravityNormalized = normalizeAntigravityProvider(normalizedProvider)
      if (antigravityNormalized !== normalizedProvider) {
        mutated = true
      }
      normalizedProvider = antigravityNormalized
    }

    val existing = next[normalizedKey]
    if (existing) {
      // Keep deterministic behavior if users accidentally define duplicate
      // provider keys that only differ by surrounding whitespace.
      mutated = true
      next[normalizedKey] = {
        ...existing,
        ...normalizedProvider,
        models: normalizedProvider.models ?? existing.models,
      }
      continue
    }
    next[normalizedKey] = normalizedProvider
  }

  val normalizedProviders = mutated ? next : providers
  return enforceSourceManagedProviderSecrets({
    providers: normalizedProviders,
    sourceProviders: params.sourceProviders,
    sourceSecretDefaults: params.sourceSecretDefaults,
    secretRefManagedProviders: params.secretRefManagedProviders,
  })
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ImplicitProviderParams.
typealias ImplicitProviderParams = Any?
/*
type ImplicitProviderParams = {
  agentDir: string;
  config?: OpenClawConfig;
  env?: NodeJS.ProcessEnv;
  workspaceDir?: string;
  explicitProviders?: Record<string, ProviderConfig> | null;
};
*/

typealias ProviderApiKeyResolver = { provider: String -> {
  apiKey: String | Nothing?
  discoveryApiKey?: String
}

typealias ProviderAuthResolver = (
  provider: String,
  options?: { oauthMarker?: String },
) => {
  apiKey: String | Nothing?
  discoveryApiKey?: String
  mode: "api_key" | "oauth" | "token" | "none"
  source: "env" | "profile" | "none"
  profileId?: String
}

typealias ImplicitProviderContext = ImplicitProviderParams & {
  authStore: ReturnType<typeof ensureAuthProfileStore>
  env: NodeJS.ProcessEnv
  resolveProviderApiKey: ProviderApiKeyResolver
  resolveProviderAuth: ProviderAuthResolver
}

fun mergeImplicitProviderSet(
  target: Map<String, ProviderConfig>,
  additions: Map<String, ProviderConfig> | Nothing?,
): Unit {
  if (!additions) {
    return
  }
  for (const [key, value] of Object.entries(additions)) {
    target[key] = value
  }
}

suspend fun resolvePluginImplicitProviders(
  ctx: ImplicitProviderContext,
  order: import("../plugins/types.js").ProviderDiscoveryOrder,
): Promise<Map<String, ProviderConfig> | Nothing?> {
  val providers = resolvePluginDiscoveryProviders({
    config: ctx.config,
    workspaceDir: ctx.workspaceDir,
    env: ctx.env,
  })
  val byOrder = groupPluginDiscoveryProvidersByOrder(providers)
  val discovered: Map<String, ProviderConfig> = {}
  val catalogConfig =
    ctx.explicitProviders && Object.keys(ctx.explicitProviders).length > 0
      ? {
          ...ctx.config,
          models: {
            ...ctx.config?.models,
            providers: {
              ...ctx.config?.models?.providers,
              ...ctx.explicitProviders,
            },
          },
        }
      : (ctx.config ?? {})
  for (const provider of byOrder[order]) {
    val result = await runProviderCatalog({
      provider,
      config: catalogConfig,
      agentDir: ctx.agentDir,
      workspaceDir: ctx.workspaceDir,
      env: ctx.env,
      resolveProviderApiKey: (providerId) =>
        ctx.resolveProviderApiKey(providerId?.trim() || provider.id),
      resolveProviderAuth: (providerId, options) =>
        ctx.resolveProviderAuth(providerId?.trim() || provider.id, options),
    })
    mergeImplicitProviderSet(
      discovered,
      normalizePluginDiscoveryResult({
        provider,
        result,
      }),
    )
  }
  return Object.keys(discovered).length > 0 ? discovered : Nothing?
}

suspend fun resolveImplicitProviders(
  params: ImplicitProviderParams,
): Promise<ModelsConfig["providers"]> {
  val providers: Map<String, ProviderConfig> = {}
  val env = params.env ?? process.env
  val authStore = ensureAuthProfileStore(params.agentDir, {
    allowKeychainPrompt: false,
  })
  val resolveProviderApiKey: ProviderApiKeyResolver = (
    provider: String,
  ): { apiKey: String | Nothing? discoveryApiKey?: String } => {
    val envVar = resolveEnvApiKeyVarName(provider, env)
    if (envVar) {
      return {
        apiKey: envVar,
        discoveryApiKey: toDiscoveryApiKey(env[envVar]),
      }
    }
    val fromProfiles = resolveApiKeyFromProfiles({ provider, store: authStore, env })
    return {
      apiKey: fromProfiles?.apiKey,
      discoveryApiKey: fromProfiles?.discoveryApiKey,
    }
  }
  val resolveProviderAuth: ProviderAuthResolver = (
    provider: String,
    options?: { oauthMarker?: String },
  ) => {
    val envVar = resolveEnvApiKeyVarName(provider, env)
    if (envVar) {
      return {
        apiKey: envVar,
        discoveryApiKey: toDiscoveryApiKey(env[envVar]),
        mode: "api_key",
        source: "env",
      }
    }

    val ids = listProfilesForProvider(authStore, provider)
    var oauthCandidate:
      | {
          apiKey: String | Nothing?
          discoveryApiKey?: String
          mode: "oauth"
          source: "profile"
          profileId: String
        }
      | Nothing?
    for (const id of ids) {
      val cred = authStore.profiles[id]
      if (!cred) {
        continue
      }
      if (cred.type === "oauth") {
        oauthCandidate ??= {
          apiKey: options?.oauthMarker,
          discoveryApiKey: toDiscoveryApiKey(cred.access),
          mode: "oauth",
          source: "profile",
          profileId: id,
        }
        continue
      }
      val resolved = resolveApiKeyFromCredential(cred, env)
      if (!resolved) {
        continue
      }
      return {
        apiKey: resolved.apiKey,
        discoveryApiKey: resolved.discoveryApiKey,
        mode: cred.type,
        source: "profile",
        profileId: id,
      }
    }
    if (oauthCandidate) {
      return oauthCandidate
    }

    return {
      apiKey: Nothing?,
      discoveryApiKey: Nothing?,
      mode: "none",
      source: "none",
    }
  }
  val context: ImplicitProviderContext = {
    ...params,
    authStore,
    env,
    resolveProviderApiKey,
    resolveProviderAuth,
  }

  mergeImplicitProviderSet(providers, await resolvePluginImplicitProviders(context, "simple"))
  mergeImplicitProviderSet(providers, await resolvePluginImplicitProviders(context, "profile"))
  mergeImplicitProviderSet(providers, await resolvePluginImplicitProviders(context, "paired"))
  mergeImplicitProviderSet(providers, await resolvePluginImplicitProviders(context, "late"))

  val implicitBedrock = await resolveImplicitBedrockProvider({
    agentDir: params.agentDir,
    config: params.config,
    env,
  })
  if (implicitBedrock) {
    val existing = providers["amazon-bedrock"]
    providers["amazon-bedrock"] = existing
      ? {
          ...implicitBedrock,
          ...existing,
          models:
            Array.isArray(existing.models) && existing.models.length > 0
              ? existing.models
              : implicitBedrock.models,
        }
      : implicitBedrock
  }

  return providers
}

suspend fun resolveImplicitBedrockProvider(params: {
  agentDir: String
  config?: OpenClawConfig
  env?: NodeJS.ProcessEnv
}): Promise<ProviderConfig | Nothing?> {
  val env = params.env ?? process.env
  val discoveryConfig = params.config?.models?.bedrockDiscovery
  val enabled = discoveryConfig?.enabled
  val hasAwsCreds = resolveAwsSdkEnvVarName(env) !== Nothing?
  if (enabled === false) {
    return Nothing?
  }
  if (enabled !== true && !hasAwsCreds) {
    return Nothing?
  }

  val region = discoveryConfig?.region ?? env.AWS_REGION ?? env.AWS_DEFAULT_REGION ?? "us-east-1"
  val models = await discoverBedrockModels({
    region,
    config: discoveryConfig,
  })
  if (models.length === 0) {
    return Nothing?
  }

  return {
    baseUrl: `https://bedrock-runtime.${region}.amazonaws.com`,
    api: "bedrock-converse-stream",
    auth: "aws-sdk",
    models,
  } satisfies ProviderConfig
}
