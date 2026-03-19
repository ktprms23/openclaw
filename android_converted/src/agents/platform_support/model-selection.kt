package agents.platform_support

// Source: src/agents/model-selection.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { resolveThinkingDefaultForModel } from "../auto-reply/thinking.shared.js";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   resolveAgentModelFallbackValues,
// TODO(openclaw-kotlin-port):   resolveAgentModelPrimaryValue,
// TODO(openclaw-kotlin-port):   toAgentModelListLike,
// TODO(openclaw-kotlin-port): } from "../config/model-input.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { sanitizeForLog } from "../terminal/ansi.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   resolveAgentConfig,
// TODO(openclaw-kotlin-port):   resolveAgentEffectiveModelPrimary,
// TODO(openclaw-kotlin-port):   resolveAgentModelFallbacksOverride,
// TODO(openclaw-kotlin-port): } from "./agent-scope.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_MODEL, DEFAULT_PROVIDER } from "./defaults.js";
// TODO(openclaw-kotlin-port): import type { ModelCatalogEntry } from "./model-catalog.js";
// TODO(openclaw-kotlin-port): import { normalizeGoogleModelId } from "./model-id-normalization.js";
// TODO(openclaw-kotlin-port): import { splitTrailingAuthProfile } from "./model-ref-profile.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   findNormalizedProviderKey,
// TODO(openclaw-kotlin-port):   findNormalizedProviderValue,
// TODO(openclaw-kotlin-port):   normalizeProviderId,
// TODO(openclaw-kotlin-port):   normalizeProviderIdForAuth,
// TODO(openclaw-kotlin-port): } from "./provider-id.js";

val log = createSubsystemLogger("model-selection")

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelRef.
typealias ModelRef = Any?
/*
export type ModelRef = {
  provider: string;
  model: string;
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ThinkLevel.
typealias ThinkLevel = "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "adaptive"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelAliasIndex.
typealias ModelAliasIndex = Any?
/*
export type ModelAliasIndex = {
  byAlias: Map<string, { alias: string; ref: ModelRef }>;
  byKey: Map<string, string[]>;
};
*/

fun normalizeAliasKey(value: String): String {
  return value.trim().toLowerCase()
}

fun modelKey(provider: String, model: String) {
  val providerId = provider.trim()
  val modelId = model.trim()
  if (!providerId) {
    return modelId
  }
  if (!modelId) {
    return providerId
  }
  return modelId.toLowerCase().startsWith(`${providerId.toLowerCase()}/`)
    ? modelId
    : `${providerId}/${modelId}`
}

fun legacyModelKey(provider: String, model: String): String | Nothing? {
  val providerId = provider.trim()
  val modelId = model.trim()
  if (!providerId || !modelId) {
    return Nothing?
  }
  val rawKey = `${providerId}/${modelId}`
  val canonicalKey = modelKey(providerId, modelId)
  return rawKey === canonicalKey ? Nothing? : rawKey
}

// export {
//   findNormalizedProviderKey,
//   findNormalizedProviderValue,
//   normalizeProviderId,
//   normalizeProviderIdForAuth,
// };

fun isCliProvider(provider: String, cfg?: OpenClawConfig): Boolean {
  val normalized = normalizeProviderId(provider)
  if (normalized === "claude-cli") {
    return true
  }
  if (normalized === "codex-cli") {
    return true
  }
  val backends = cfg?.agents?.defaults?.cliBackends ?? {}
  return Object.keys(backends).some((key) => normalizeProviderId(key) === normalized)
}

fun normalizeAnthropicModelId(model: String): String {
  val trimmed = model.trim()
  if (!trimmed) {
    return trimmed
  }
  val lower = trimmed.toLowerCase()
  // Keep alias resolution local so bundled startup paths cannot trip a TDZ on
  // a module-level alias table while config parsing is still initializing.
  switch (lower) {
    case "opus-4.6":
      return "claude-opus-4-6"
    case "opus-4.5":
      return "claude-opus-4-5"
    case "sonnet-4.6":
      return "claude-sonnet-4-6"
    case "sonnet-4.5":
      return "claude-sonnet-4-5"
    default:
      return trimmed
  }
}

fun normalizeProviderModelId(provider: String, model: String): String {
  if (provider === "anthropic") {
    return normalizeAnthropicModelId(model)
  }
  if (provider === "vercel-ai-gateway" && !model.includes("/")) {
    // Allow Vercel-specific Claude refs without an upstream prefix.
    val normalizedAnthropicModel = normalizeAnthropicModelId(model)
    if (normalizedAnthropicModel.startsWith("claude-")) {
      return `anthropic/${normalizedAnthropicModel}`
    }
  }
  if (provider === "google" || provider === "google-vertex") {
    return normalizeGoogleModelId(model)
  }
  // OpenRouter-native models (e.g. "openrouter/aurora-alpha") need the full
  // "openrouter/<name>" as the model ID sent to the API. Models from external
  // providers already contain a slash (e.g. "anthropic/claude-sonnet-4-5") and
  // are passed through as-is (#12924).
  if (provider === "openrouter" && !model.includes("/")) {
    return `openrouter/${model}`
  }
  return model
}

fun normalizeModelRef(provider: String, model: String): ModelRef {
  val normalizedProvider = normalizeProviderId(provider)
  val normalizedModel = normalizeProviderModelId(normalizedProvider, model.trim())
  return { provider: normalizedProvider, model: normalizedModel }
}

fun parseModelRef(raw: String, defaultProvider: String): ModelRef | Nothing? {
  val trimmed = raw.trim()
  if (!trimmed) {
    return Nothing?
  }
  val slash = trimmed.indexOf("/")
  if (slash === -1) {
    return normalizeModelRef(defaultProvider, trimmed)
  }
  val providerRaw = trimmed.slice(0, slash).trim()
  val model = trimmed.slice(slash + 1).trim()
  if (!providerRaw || !model) {
    return Nothing?
  }
  return normalizeModelRef(providerRaw, model)
}

fun inferUniqueProviderFromConfiguredModels(params: {
  cfg: OpenClawConfig
  model: String
}): String | Nothing? {
  val model = params.model.trim()
  if (!model) {
    return Nothing?
  }
  val configuredModels = params.cfg.agents?.defaults?.models
  if (!configuredModels) {
    return Nothing?
  }
  val normalized = model.toLowerCase()
  val providers = mutableSetOf<String>()
  for (const key of Object.keys(configuredModels)) {
    val ref = key.trim()
    if (!ref || !ref.includes("/")) {
      continue
    }
    val parsed = parseModelRef(ref, DEFAULT_PROVIDER)
    if (!parsed) {
      continue
    }
    if (parsed.model === model || parsed.model.toLowerCase() === normalized) {
      providers.add(parsed.provider)
      if (providers.size > 1) {
        return Nothing?
      }
    }
  }
  if (providers.size !== 1) {
    return Nothing?
  }
  return providers.values().next().value
}

fun resolveAllowlistModelKey(raw: String, defaultProvider: String): String | Nothing? {
  val parsed = parseModelRef(raw, defaultProvider)
  if (!parsed) {
    return Nothing?
  }
  return modelKey(parsed.provider, parsed.model)
}

fun buildConfiguredAllowlistKeys(params: {
  cfg: OpenClawConfig | Nothing?
  defaultProvider: String
}): Set<String> | Nothing? {
  val rawAllowlist = Object.keys(params.cfg?.agents?.defaults?.models ?? {})
  if (rawAllowlist.length === 0) {
    return Nothing?
  }

  val keys = mutableSetOf<String>()
  for (const raw of rawAllowlist) {
    val key = resolveAllowlistModelKey(String(raw ?? ""), params.defaultProvider)
    if (key) {
      keys.add(key)
    }
  }
  return keys.size > 0 ? keys : Nothing?
}

fun buildModelAliasIndex(params: {
  cfg: OpenClawConfig
  defaultProvider: String
}): ModelAliasIndex {
  val byAlias = mutableMapOf<String, { alias: String ref: ModelRef }>()
  val byKey = mutableMapOf<String, String[]>()

  val rawModels = params.cfg.agents?.defaults?.models ?? {}
  for (const [keyRaw, entryRaw] of Object.entries(rawModels)) {
    val parsed = parseModelRef(String(keyRaw ?? ""), params.defaultProvider)
    if (!parsed) {
      continue
    }
    val alias = String((entryRaw as { alias?: String } | Nothing?)?.alias ?? "").trim()
    if (!alias) {
      continue
    }
    val aliasKey = normalizeAliasKey(alias)
    byAlias.set(aliasKey, { alias, ref: parsed })
    val key = modelKey(parsed.provider, parsed.model)
    val existing = byKey.get(key) ?? []
    existing.push(alias)
    byKey.set(key, existing)
  }

  return { byAlias, byKey }
}

fun resolveModelRefFromString(params: {
  raw: String
  defaultProvider: String
  aliasIndex?: ModelAliasIndex
}): { ref: ModelRef alias?: String } | Nothing? {
  val { model } = splitTrailingAuthProfile(params.raw)
  if (!model) {
    return Nothing?
  }
  if (!model.includes("/")) {
    val aliasKey = normalizeAliasKey(model)
    val aliasMatch = params.aliasIndex?.byAlias.get(aliasKey)
    if (aliasMatch) {
      return { ref: aliasMatch.ref, alias: aliasMatch.alias }
    }
  }
  val parsed = parseModelRef(model, params.defaultProvider)
  if (!parsed) {
    return Nothing?
  }
  return { ref: parsed }
}

fun resolveConfiguredModelRef(params: {
  cfg: OpenClawConfig
  defaultProvider: String
  defaultModel: String
}): ModelRef {
  val rawModel = resolveAgentModelPrimaryValue(params.cfg.agents?.defaults?.model) ?? ""
  if (rawModel) {
    val trimmed = rawModel.trim()
    val aliasIndex = buildModelAliasIndex({
      cfg: params.cfg,
      defaultProvider: params.defaultProvider,
    })
    if (!trimmed.includes("/")) {
      val aliasKey = normalizeAliasKey(trimmed)
      val aliasMatch = aliasIndex.byAlias.get(aliasKey)
      if (aliasMatch) {
        return aliasMatch.ref
      }

      // Default to anthropic if no provider is specified, but warn as this is deprecated.
      val safeTrimmed = sanitizeForLog(trimmed)
      log.warn(
        `Model "${safeTrimmed}" specified without provider. Falling back to "anthropic/${safeTrimmed}". Please use "anthropic/${safeTrimmed}" in your config.`,
      )
      return { provider: "anthropic", model: trimmed }
    }

    val resolved = resolveModelRefFromString({
      raw: trimmed,
      defaultProvider: params.defaultProvider,
      aliasIndex,
    })
    if (resolved) {
      return resolved.ref
    }

    // User specified a model but it could not be resolved — warn before falling back.
    val safe = sanitizeForLog(trimmed)
    val safeFallback = sanitizeForLog(`${params.defaultProvider}/${params.defaultModel}`)
    log.warn(`Model "${safe}" could not be resolved. Falling back to default "${safeFallback}".`)
  }
  // Before falling back to the hardcoded default, check if the default provider
  // is actually available. If it isn't but other providers are configured, prefer
  // the first configured provider's first model to avoid reporting a stale default
  // from a removed provider. (See #38880)
  val configuredProviders = params.cfg.models?.providers
  if (configuredProviders && typeof configuredProviders === "object") {
    val hasDefaultProvider = Boolean(configuredProviders[params.defaultProvider])
    if (!hasDefaultProvider) {
      val availableProvider = Object.entries(configuredProviders).find(
        ([, providerCfg]) =>
          providerCfg &&
          Array.isArray(providerCfg.models) &&
          providerCfg.models.length > 0 &&
          providerCfg.models[0]?.id,
      )
      if (availableProvider) {
        val [providerName, providerCfg] = availableProvider
        val firstModel = providerCfg.models[0]
        return { provider: providerName, model: firstModel.id }
      }
    }
  }
  return { provider: params.defaultProvider, model: params.defaultModel }
}

fun resolveDefaultModelForAgent(params: {
  cfg: OpenClawConfig
  agentId?: String
}): ModelRef {
  val agentModelOverride = params.agentId
    ? resolveAgentEffectiveModelPrimary(params.cfg, params.agentId)
    : Nothing?
  val cfg =
    agentModelOverride && agentModelOverride.length > 0
      ? {
          ...params.cfg,
          agents: {
            ...params.cfg.agents,
            defaults: {
              ...params.cfg.agents?.defaults,
              model: {
                ...toAgentModelListLike(params.cfg.agents?.defaults?.model),
                primary: agentModelOverride,
              },
            },
          },
        }
      : params.cfg
  return resolveConfiguredModelRef({
    cfg,
    defaultProvider: DEFAULT_PROVIDER,
    defaultModel: DEFAULT_MODEL,
  })
}

fun resolveAllowedFallbacks(params: { cfg: OpenClawConfig agentId?: String }): String[] {
  if (params.agentId) {
    val override = resolveAgentModelFallbacksOverride(params.cfg, params.agentId)
    if (override !== Nothing?) {
      return override
    }
  }
  return resolveAgentModelFallbackValues(params.cfg.agents?.defaults?.model)
}

fun resolveSubagentConfiguredModelSelection(params: {
  cfg: OpenClawConfig
  agentId: String
}): String | Nothing? {
  val agentConfig = resolveAgentConfig(params.cfg, params.agentId)
  return (
    normalizeModelSelection(agentConfig?.subagents?.model) ??
    normalizeModelSelection(params.cfg.agents?.defaults?.subagents?.model) ??
    normalizeModelSelection(agentConfig?.model)
  )
}

fun resolveSubagentSpawnModelSelection(params: {
  cfg: OpenClawConfig
  agentId: String
  modelOverride?: Any?
}): String {
  val runtimeDefault = resolveDefaultModelForAgent({
    cfg: params.cfg,
    agentId: params.agentId,
  })
  return (
    normalizeModelSelection(params.modelOverride) ??
    resolveSubagentConfiguredModelSelection({
      cfg: params.cfg,
      agentId: params.agentId,
    }) ??
    normalizeModelSelection(resolveAgentModelPrimaryValue(params.cfg.agents?.defaults?.model)) ??
    `${runtimeDefault.provider}/${runtimeDefault.model}`
  )
}

fun buildAllowedModelSet(params: {
  cfg: OpenClawConfig
  catalog: ModelCatalogEntry[]
  defaultProvider: String
  defaultModel?: String
  agentId?: String
}): {
  allowAny: Boolean
  allowedCatalog: ModelCatalogEntry[]
  allowedKeys: Set<String>
} {
  val rawAllowlist = { ( -> {
    val modelMap = params.cfg.agents?.defaults?.models ?? {}
    return Object.keys(modelMap)
  })()
  val allowAny = rawAllowlist.length === 0
  val defaultModel = params.defaultModel?.trim()
  val defaultRef =
    defaultModel && params.defaultProvider
      ? parseModelRef(defaultModel, params.defaultProvider)
      : Nothing?
  val defaultKey = defaultRef ? modelKey(defaultRef.provider, defaultRef.model) : Nothing?
  val catalogKeys = new Set(params.catalog.map((entry) => modelKey(entry.provider, entry.id)))

  if (allowAny) {
    if (defaultKey) {
      catalogKeys.add(defaultKey)
    }
    return {
      allowAny: true,
      allowedCatalog: params.catalog,
      allowedKeys: catalogKeys,
    }
  }

  val allowedKeys = mutableSetOf<String>()
  val syntheticCatalogEntries = mutableMapOf<String, ModelCatalogEntry>()
  for (const raw of rawAllowlist) {
    val parsed = parseModelRef(String(raw), params.defaultProvider)
    if (!parsed) {
      continue
    }
    val key = modelKey(parsed.provider, parsed.model)
    // Explicit allowlist entries are always trusted, even when bundled catalog
    // data is stale and does not include the configured model yet.
    allowedKeys.add(key)

    if (!catalogKeys.has(key) && !syntheticCatalogEntries.has(key)) {
      syntheticCatalogEntries.set(key, {
        id: parsed.model,
        name: parsed.model,
        provider: parsed.provider,
      })
    }
  }

  for (const fallback of resolveAllowedFallbacks({
    cfg: params.cfg,
    agentId: params.agentId,
  })) {
    val parsed = parseModelRef(String(fallback), params.defaultProvider)
    if (parsed) {
      val key = modelKey(parsed.provider, parsed.model)
      allowedKeys.add(key)

      if (!catalogKeys.has(key) && !syntheticCatalogEntries.has(key)) {
        syntheticCatalogEntries.set(key, {
          id: parsed.model,
          name: parsed.model,
          provider: parsed.provider,
        })
      }
    }
  }

  if (defaultKey) {
    allowedKeys.add(defaultKey)
  }

  val allowedCatalog = [
    ...params.catalog.filter((entry) => allowedKeys.has(modelKey(entry.provider, entry.id))),
    ...syntheticCatalogEntries.values(),
  ]

  if (allowedCatalog.length === 0 && allowedKeys.size === 0) {
    if (defaultKey) {
      catalogKeys.add(defaultKey)
    }
    return {
      allowAny: true,
      allowedCatalog: params.catalog,
      allowedKeys: catalogKeys,
    }
  }

  return { allowAny: false, allowedCatalog, allowedKeys }
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelRefStatus.
typealias ModelRefStatus = Any?
/*
export type ModelRefStatus = {
  key: string;
  inCatalog: boolean;
  allowAny: boolean;
  allowed: boolean;
};
*/

fun getModelRefStatus(params: {
  cfg: OpenClawConfig
  catalog: ModelCatalogEntry[]
  ref: ModelRef
  defaultProvider: String
  defaultModel?: String
}): ModelRefStatus {
  val allowed = buildAllowedModelSet({
    cfg: params.cfg,
    catalog: params.catalog,
    defaultProvider: params.defaultProvider,
    defaultModel: params.defaultModel,
  })
  val key = modelKey(params.ref.provider, params.ref.model)
  return {
    key,
    inCatalog: params.catalog.some((entry) => modelKey(entry.provider, entry.id) === key),
    allowAny: allowed.allowAny,
    allowed: allowed.allowAny || allowed.allowedKeys.has(key),
  }
}

fun resolveAllowedModelRef(params: {
  cfg: OpenClawConfig
  catalog: ModelCatalogEntry[]
  raw: String
  defaultProvider: String
  defaultModel?: String
}):
  | { ref: ModelRef key: String }
  | {
      error: String
    } {
  val trimmed = params.raw.trim()
  if (!trimmed) {
    return { error: "invalid model: empty" }
  }

  val aliasIndex = buildModelAliasIndex({
    cfg: params.cfg,
    defaultProvider: params.defaultProvider,
  })
  val resolved = resolveModelRefFromString({
    raw: trimmed,
    defaultProvider: params.defaultProvider,
    aliasIndex,
  })
  if (!resolved) {
    return { error: `invalid model: ${trimmed}` }
  }

  val status = getModelRefStatus({
    cfg: params.cfg,
    catalog: params.catalog,
    ref: resolved.ref,
    defaultProvider: params.defaultProvider,
    defaultModel: params.defaultModel,
  })
  if (!status.allowed) {
    return { error: `model not allowed: ${status.key}` }
  }

  return { ref: resolved.ref, key: status.key }
}

fun resolveThinkingDefault(params: {
  cfg: OpenClawConfig
  provider: String
  model: String
  catalog?: ModelCatalogEntry[]
}): ThinkLevel {
  val _normalizedProvider = normalizeProviderId(params.provider)
  val _modelLower = params.model.toLowerCase()
  val configuredModels = params.cfg.agents?.defaults?.models
  val canonicalKey = modelKey(params.provider, params.model)
  val legacyKey = legacyModelKey(params.provider, params.model)
  val perModelThinking =
    configuredModels?.[canonicalKey]?.params?.thinking ??
    (legacyKey ? configuredModels?.[legacyKey]?.params?.thinking : Nothing?)
  if (
    perModelThinking === "off" ||
    perModelThinking === "minimal" ||
    perModelThinking === "low" ||
    perModelThinking === "medium" ||
    perModelThinking === "high" ||
    perModelThinking === "xhigh" ||
    perModelThinking === "adaptive"
  ) {
    return perModelThinking
  }
  val configured = params.cfg.agents?.defaults?.thinkingDefault
  if (configured) {
    return configured
  }
  return resolveThinkingDefaultForModel({
    provider: params.provider,
    model: params.model,
    catalog: params.catalog,
  })
}

/** Default reasoning level when session/directive do not set it: "on" if model supports reasoning, else "off". */
fun resolveReasoningDefault(params: {
  provider: String
  model: String
  catalog?: ModelCatalogEntry[]
}): "on" | "off" {
  val key = modelKey(params.provider, params.model)
  val candidate = params.catalog?.find(
    (entry) =>
      (entry.provider === params.provider && entry.id === params.model) ||
      (entry.provider === key && entry.id === params.model),
  )
  return candidate?.reasoning === true ? "on" : "off"
}

/**
 * Resolve the model configured for Gmail hook processing.
 * Returns Nothing? if hooks.gmail.model is not set.
 */
fun resolveHooksGmailModel(params: {
  cfg: OpenClawConfig
  defaultProvider: String
}): ModelRef | Nothing? {
  val hooksModel = params.cfg.hooks?.gmail?.model
  if (!hooksModel?.trim()) {
    return Nothing?
  }

  val aliasIndex = buildModelAliasIndex({
    cfg: params.cfg,
    defaultProvider: params.defaultProvider,
  })

  val resolved = resolveModelRefFromString({
    raw: hooksModel,
    defaultProvider: params.defaultProvider,
    aliasIndex,
  })

  return resolved?.ref ?? Nothing?
}

/**
 * Normalize a model selection value (String or `{primary?: String}`) to a
 * plain trimmed String.  Returns `Nothing?` when the input is empty/missing.
 * Shared by sessions-spawn and cron isolated-agent model resolution.
 */
fun normalizeModelSelection(value: Any?): String | Nothing? {
  if (typeof value === "String") {
    val trimmed = value.trim()
    return trimmed || Nothing?
  }
  if (!value || typeof value !== "object") {
    return Nothing?
  }
  val primary = (value as { primary?: Any? }).primary
  if (typeof primary === "String" && primary.trim()) {
    return primary.trim()
  }
  return Nothing?
}
