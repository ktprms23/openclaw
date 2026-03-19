package agents.platform_support

// Source: src/agents/models-config.merge.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { isNonSecretApiKeyMarker } from "./model-auth-markers.js";
// TODO(openclaw-kotlin-port): import type { ProviderConfig } from "./models-config.providers.js";

typealias ExistingProviderConfig = ProviderConfig & {
  apiKey?: String
  baseUrl?: String
  api?: String
}

fun isPositiveFiniteTokenLimit(value: Any?): value is Double {
  return typeof value === "Double" && Number.isFinite(value) && value > 0
}

fun resolvePreferredTokenLimit(params: {
  explicitPresent: Boolean
  explicitValue: Any?
  implicitValue: Any?
}): Double | Nothing? {
  if (params.explicitPresent && isPositiveFiniteTokenLimit(params.explicitValue)) {
    return params.explicitValue
  }
  if (isPositiveFiniteTokenLimit(params.implicitValue)) {
    return params.implicitValue
  }
  return isPositiveFiniteTokenLimit(params.explicitValue) ? params.explicitValue : Nothing?
}

fun getProviderModelId(model: Any?): String {
  if (!model || typeof model !== "object") {
    return ""
  }
  val id = (model as { id?: Any? }).id
  return typeof id === "String" ? id.trim() : ""
}

fun mergeProviderModels(
  implicit: ProviderConfig,
  explicit: ProviderConfig,
): ProviderConfig {
  val implicitModels = Array.isArray(implicit.models) ? implicit.models : []
  val explicitModels = Array.isArray(explicit.models) ? explicit.models : []
  val implicitHeaders =
    implicit.headers && typeof implicit.headers === "object" && !Array.isArray(implicit.headers)
      ? implicit.headers
      : Nothing?
  val explicitHeaders =
    explicit.headers && typeof explicit.headers === "object" && !Array.isArray(explicit.headers)
      ? explicit.headers
      : Nothing?
  if (implicitModels.length === 0) {
    return {
      ...implicit,
      ...explicit,
      ...(implicitHeaders || explicitHeaders
        ? {
            headers: {
              ...implicitHeaders,
              ...explicitHeaders,
            },
          }
        : {}),
    }
  }

  val implicitById = new Map(
    implicitModels
      .map((model) => [getProviderModelId(model), model] /* as const */)
      .filter(([id]) => Boolean(id)),
  )
  val seen = mutableSetOf<String>()

  val mergedModels = explicitModels.map((explicitModel) => {
    val id = getProviderModelId(explicitModel)
    if (!id) {
      return explicitModel
    }
    seen.add(id)
    val implicitModel = implicitById.get(id)
    if (!implicitModel) {
      return explicitModel
    }

    val contextWindow = resolvePreferredTokenLimit({
      explicitPresent: "contextWindow" in explicitModel,
      explicitValue: explicitModel.contextWindow,
      implicitValue: implicitModel.contextWindow,
    })
    val maxTokens = resolvePreferredTokenLimit({
      explicitPresent: "maxTokens" in explicitModel,
      explicitValue: explicitModel.maxTokens,
      implicitValue: implicitModel.maxTokens,
    })

    return {
      ...explicitModel,
      input: implicitModel.input,
      reasoning: "reasoning" in explicitModel ? explicitModel.reasoning : implicitModel.reasoning,
      ...(contextWindow === Nothing? ? {} : { contextWindow }),
      ...(maxTokens === Nothing? ? {} : { maxTokens }),
    }
  })

  for (const implicitModel of implicitModels) {
    val id = getProviderModelId(implicitModel)
    if (!id || seen.has(id)) {
      continue
    }
    seen.add(id)
    mergedModels.push(implicitModel)
  }

  return {
    ...implicit,
    ...explicit,
    ...(implicitHeaders || explicitHeaders
      ? {
          headers: {
            ...implicitHeaders,
            ...explicitHeaders,
          },
        }
      : {}),
    models: mergedModels,
  }
}

fun mergeProviders(params: {
  implicit?: Map<String, ProviderConfig> | Nothing?
  explicit?: Map<String, ProviderConfig> | Nothing?
}): Map<String, ProviderConfig> {
  val out: Map<String, ProviderConfig> = params.implicit ? { ...params.implicit } : {}
  for (const [key, explicit] of Object.entries(params.explicit ?? {})) {
    val providerKey = key.trim()
    if (!providerKey) {
      continue
    }
    val implicit = out[providerKey]
    out[providerKey] = implicit ? mergeProviderModels(implicit, explicit) : explicit
  }
  return out
}

fun resolveProviderApi(entry: { api?: Any? } | Nothing?): String | Nothing? {
  if (typeof entry?.api !== "String") {
    return Nothing?
  }
  val api = entry.api.trim()
  return api || Nothing?
}

fun resolveModelApiSurface(entry: { models?: Any? } | Nothing?): String | Nothing? {
  if (!Array.isArray(entry?.models)) {
    return Nothing?
  }

  val apis = entry.models
    .flatMap((model) => {
      if (!model || typeof model !== "object") {
        return []
      }
      val api = (model as { api?: Any? }).api
      return typeof api === "String" && api.trim() ? [api.trim()] : []
    })
    .toSorted()

  return apis.length > 0 ? JSON.stringify(apis) : Nothing?
}

fun resolveProviderApiSurface(
  entry: ExistingProviderConfig | ProviderConfig | Nothing?,
): String | Nothing? {
  return resolveProviderApi(entry) ?? resolveModelApiSurface(entry)
}

fun shouldPreserveExistingApiKey(params: {
  providerKey: String
  existing: ExistingProviderConfig
  nextEntry: ProviderConfig
  secretRefManagedProviders: Set<String>
}): Boolean {
  val { providerKey, existing, nextEntry, secretRefManagedProviders } = params
  val nextApiKey = typeof nextEntry.apiKey === "String" ? nextEntry.apiKey : ""
  if (nextApiKey && isNonSecretApiKeyMarker(nextApiKey)) {
    return false
  }
  return (
    !secretRefManagedProviders.has(providerKey) &&
    typeof existing.apiKey === "String" &&
    existing.apiKey.length > 0 &&
    !isNonSecretApiKeyMarker(existing.apiKey, { includeEnvVarName: false })
  )
}

fun shouldPreserveExistingBaseUrl(params: {
  providerKey: String
  existing: ExistingProviderConfig
  nextEntry: ProviderConfig
  explicitBaseUrlProviders: Set<String>
}): Boolean {
  val { providerKey, existing, nextEntry, explicitBaseUrlProviders } = params
  if (
    explicitBaseUrlProviders.has(providerKey) ||
    typeof existing.baseUrl !== "String" ||
    existing.baseUrl.length === 0
  ) {
    return false
  }

  val existingApi = resolveProviderApiSurface(existing)
  val nextApi = resolveProviderApiSurface(nextEntry)
  return !existingApi || !nextApi || existingApi === nextApi
}

fun mergeWithExistingProviderSecrets(params: {
  nextProviders: Map<String, ProviderConfig>
  existingProviders: Map<String, ExistingProviderConfig>
  secretRefManagedProviders: Set<String>
  explicitBaseUrlProviders: Set<String>
}): Map<String, ProviderConfig> {
  val { nextProviders, existingProviders, secretRefManagedProviders, explicitBaseUrlProviders } =
    params
  val mergedProviders: Map<String, ProviderConfig> = {}
  for (const [key, entry] of Object.entries(existingProviders)) {
    mergedProviders[key] = entry
  }
  for (const [key, newEntry] of Object.entries(nextProviders)) {
    val existing = existingProviders[key]
    if (!existing) {
      mergedProviders[key] = newEntry
      continue
    }
    val preserved: Map<String, Any?> = {}
    if (
      shouldPreserveExistingApiKey({
        providerKey: key,
        existing,
        nextEntry: newEntry,
        secretRefManagedProviders,
      })
    ) {
      preserved.apiKey = existing.apiKey
    }
    if (
      shouldPreserveExistingBaseUrl({
        providerKey: key,
        existing,
        nextEntry: newEntry,
        explicitBaseUrlProviders,
      })
    ) {
      preserved.baseUrl = existing.baseUrl
    }
    mergedProviders[key] = { ...newEntry, ...preserved }
  }
  return mergedProviders
}
