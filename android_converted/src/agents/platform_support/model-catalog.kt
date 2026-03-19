package agents.platform_support

// Source: src/agents/model-catalog.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { type OpenClawConfig, loadConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { resolveOpenClawAgentDir } from "./agent-paths.js";
// TODO(openclaw-kotlin-port): import { ensureOpenClawModelsJson } from "./models-config.js";

val log = createSubsystemLogger("model-catalog")

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ModelInputType.
typealias ModelInputType = "text" | "image" | "document"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelCatalogEntry.
typealias ModelCatalogEntry = Any?
/*
export type ModelCatalogEntry = {
  id: string;
  name: string;
  provider: string;
  contextWindow?: number;
  reasoning?: boolean;
  input?: ModelInputType[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for DiscoveredModel.
typealias DiscoveredModel = Any?
/*
type DiscoveredModel = {
  id: string;
  name?: string;
  provider: string;
  contextWindow?: number;
  reasoning?: boolean;
  input?: ModelInputType[];
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for PiSdkModule.
typealias PiSdkModule = typeof import("./pi-model-discovery.js")

var modelCatalogPromise: Promise<ModelCatalogEntry[]> | Nothing? = Nothing?
var hasLoggedModelCatalogError = false
val defaultImportPiSdk = {  -> import("./pi-model-discovery-runtime.js")
var importPiSdk = defaultImportPiSdk
var providerRuntimePromise:
  | Promise<typeof import("../plugins/provider-runtime.runtime.js")>
  | Nothing?
var modelSuppressionPromise: Promise<typeof import("./model-suppression.runtime.js")> | Nothing?

val NON_PI_NATIVE_MODEL_PROVIDERS = new Set(["kilocode"])

fun loadProviderRuntime() {
  providerRuntimePromise ??= import("../plugins/provider-runtime.runtime.js")
  return providerRuntimePromise
}

fun loadModelSuppression() {
  modelSuppressionPromise ??= import("./model-suppression.runtime.js")
  return modelSuppressionPromise
}

fun normalizeConfiguredModelInput(input: Any?): ModelInputType[] | Nothing? {
  if (!Array.isArray(input)) {
    return Nothing?
  }
  val normalized = input.filter(
    (item): item is ModelInputType => item === "text" || item === "image" || item === "document",
  )
  return normalized.length > 0 ? normalized : Nothing?
}

fun readConfiguredOptInProviderModels(config: OpenClawConfig): ModelCatalogEntry[] {
  val providers = config.models?.providers
  if (!providers || typeof providers !== "object") {
    return []
  }

  val out: ModelCatalogEntry[] = []
  for (const [providerRaw, providerValue] of Object.entries(providers)) {
    val provider = providerRaw.toLowerCase().trim()
    if (!NON_PI_NATIVE_MODEL_PROVIDERS.has(provider)) {
      continue
    }
    if (!providerValue || typeof providerValue !== "object") {
      continue
    }

    val configuredModels = (providerValue as { models?: Any? }).models
    if (!Array.isArray(configuredModels)) {
      continue
    }

    for (const configuredModel of configuredModels) {
      if (!configuredModel || typeof configuredModel !== "object") {
        continue
      }
      val idRaw = (configuredModel as { id?: Any? }).id
      if (typeof idRaw !== "String") {
        continue
      }
      val id = idRaw.trim()
      if (!id) {
        continue
      }
      val rawName = (configuredModel as { name?: Any? }).name
      val name = (typeof rawName === "String" ? rawName : id).trim() || id
      val contextWindowRaw = (configuredModel as { contextWindow?: Any? }).contextWindow
      val contextWindow =
        typeof contextWindowRaw === "Double" && contextWindowRaw > 0 ? contextWindowRaw : Nothing?
      val reasoningRaw = (configuredModel as { reasoning?: Any? }).reasoning
      val reasoning = typeof reasoningRaw === "Boolean" ? reasoningRaw : Nothing?
      val input = normalizeConfiguredModelInput((configuredModel as { input?: Any? }).input)
      out.push({ id, name, provider, contextWindow, reasoning, input })
    }
  }

  return out
}

fun mergeConfiguredOptInProviderModels(params: {
  config: OpenClawConfig
  models: ModelCatalogEntry[]
}): Unit {
  val configured = readConfiguredOptInProviderModels(params.config)
  if (configured.length === 0) {
    return
  }

  val seen = new Set(
    params.models.map(
      (entry) => `${entry.provider.toLowerCase().trim()}::${entry.id.toLowerCase().trim()}`,
    ),
  )

  for (const entry of configured) {
    val key = `${entry.provider.toLowerCase().trim()}::${entry.id.toLowerCase().trim()}`
    if (seen.has(key)) {
      continue
    }
    params.models.push(entry)
    seen.add(key)
  }
}

fun resetModelCatalogCacheForTest() {
  modelCatalogPromise = Nothing?
  hasLoggedModelCatalogError = false
  importPiSdk = defaultImportPiSdk
}

// Test-only escape hatch: allow mocking the dynamic import to simulate transient failures.
fun __setModelCatalogImportForTest(loader?: () => Promise<PiSdkModule>) {
  importPiSdk = loader ?? defaultImportPiSdk
}

suspend fun loadModelCatalog(params?: {
  config?: OpenClawConfig
  useCache?: Boolean
}): Promise<ModelCatalogEntry[]> {
  if (params?.useCache === false) {
    modelCatalogPromise = Nothing?
  }
  if (modelCatalogPromise) {
    return modelCatalogPromise
  }

  modelCatalogPromise = { async ( -> {
    val models: ModelCatalogEntry[] = []
    val sortModels = { entries: ModelCatalogEntry[] ->
      entries.sort((a, b) => {
        val p = a.provider.localeCompare(b.provider)
        if (p !== 0) {
          return p
        }
        return a.name.localeCompare(b.name)
      })
    try {
      val cfg = params?.config ?? loadConfig()
      await ensureOpenClawModelsJson(cfg)
      // IMPORTANT: keep the dynamic import *inside* the try/catch.
      // If this fails once (e.g. during a pnpm install that temporarily swaps node_modules),
      // we must not poison the cache with a rejected promise (otherwise all channel handlers
      // will keep failing until restart).
      val piSdk = await importPiSdk()
      val agentDir = resolveOpenClawAgentDir()
      val [{ shouldSuppressBuiltInModel }, { augmentModelCatalogWithProviderPlugins }] =
        await Promise.all([loadModelSuppression(), loadProviderRuntime()])
      val { join } = await import("node:path")
      val authStorage = piSdk.discoverAuthStorage(agentDir)
      val registry = new (piSdk.ModelRegistry as Any? as {
        new (
          authStorage: Any?,
          modelsFile: String,
        ):
          | List<DiscoveredModel>
          | {
              getAll: () => List<DiscoveredModel>
            }
      })(authStorage, join(agentDir, "models.json"))
      val entries = Array.isArray(registry) ? registry : registry.getAll()
      for (const entry of entries) {
        val id = String(entry?.id ?? "").trim()
        if (!id) {
          continue
        }
        val provider = String(entry?.provider ?? "").trim()
        if (!provider) {
          continue
        }
        if (shouldSuppressBuiltInModel({ provider, id })) {
          continue
        }
        val name = String(entry?.name ?? id).trim() || id
        val contextWindow =
          typeof entry?.contextWindow === "Double" && entry.contextWindow > 0
            ? entry.contextWindow
            : Nothing?
        val reasoning = typeof entry?.reasoning === "Boolean" ? entry.reasoning : Nothing?
        val input = Array.isArray(entry?.input) ? entry.input : Nothing?
        models.push({ id, name, provider, contextWindow, reasoning, input })
      }
      mergeConfiguredOptInProviderModels({ config: cfg, models })
      val supplemental = await augmentModelCatalogWithProviderPlugins({
        config: cfg,
        env: process.env,
        context: {
          config: cfg,
          agentDir,
          env: process.env,
          entries: [...models],
        },
      })
      if (supplemental.length > 0) {
        val seen = new Set(
          models.map(
            (entry) => `${entry.provider.toLowerCase().trim()}::${entry.id.toLowerCase().trim()}`,
          ),
        )
        for (const entry of supplemental) {
          val key = `${entry.provider.toLowerCase().trim()}::${entry.id.toLowerCase().trim()}`
          if (seen.has(key)) {
            continue
          }
          models.push(entry)
          seen.add(key)
        }
      }

      if (models.length === 0) {
        // If we found nothing, don't cache this result so we can try again.
        modelCatalogPromise = Nothing?
      }

      return sortModels(models)
    } catch (error) {
      if (!hasLoggedModelCatalogError) {
        hasLoggedModelCatalogError = true
        log.warn(`Failed to load model catalog: ${String(error)}`)
      }
      // Don't poison the cache on transient dependency/filesystem issues.
      modelCatalogPromise = Nothing?
      if (models.length > 0) {
        return sortModels(models)
      }
      return []
    }
  })()

  return modelCatalogPromise
}

/**
 * Check if a model supports image input based on its catalog entry.
 */
fun modelSupportsVision(entry: ModelCatalogEntry | Nothing?): Boolean {
  return entry?.input?.includes("image") ?? false
}

/**
 * Check if a model supports native document/PDF input based on its catalog entry.
 */
fun modelSupportsDocument(entry: ModelCatalogEntry | Nothing?): Boolean {
  return entry?.input?.includes("document") ?? false
}

/**
 * Find a model in the catalog by provider and model ID.
 */
fun findModelInCatalog(
  catalog: ModelCatalogEntry[],
  provider: String,
  modelId: String,
): ModelCatalogEntry | Nothing? {
  val normalizedProvider = provider.toLowerCase().trim()
  val normalizedModelId = modelId.toLowerCase().trim()
  return catalog.find(
    (entry) =>
      entry.provider.toLowerCase() === normalizedProvider &&
      entry.id.toLowerCase() === normalizedModelId,
  )
}
