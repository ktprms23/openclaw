package agents.platform_support

// Source: src/agents/kilocode-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   KILOCODE_BASE_URL,
// TODO(openclaw-kotlin-port):   KILOCODE_DEFAULT_CONTEXT_WINDOW,
// TODO(openclaw-kotlin-port):   KILOCODE_DEFAULT_COST,
// TODO(openclaw-kotlin-port):   KILOCODE_DEFAULT_MAX_TOKENS,
// TODO(openclaw-kotlin-port):   KILOCODE_MODEL_CATALOG,
// TODO(openclaw-kotlin-port): } from "../providers/kilocode-shared.js";

val log = createSubsystemLogger("kilocode-models")

val KILOCODE_MODELS_URL = `${KILOCODE_BASE_URL}models`

val DISCOVERY_TIMEOUT_MS = 5000

// ---------------------------------------------------------------------------
// Gateway response types (OpenRouter-compatible schema)
// ---------------------------------------------------------------------------

interface GatewayModelPricing {
  prompt: String
  completion: String
  image?: String
  request?: String
  input_cache_read?: String
  input_cache_write?: String
  web_search?: String
  internal_reasoning?: String
}

interface GatewayModelEntry {
  id: String
  name: String
  context_length: Double
  architecture?: {
    input_modalities?: String[]
    output_modalities?: String[]
  }
  top_provider?: {
    max_completion_tokens?: Double | Nothing?
  }
  pricing: GatewayModelPricing
  supported_parameters?: String[]
}

interface GatewayModelsResponse {
  data: GatewayModelEntry[]
}

// ---------------------------------------------------------------------------
// Pricing conversion
// ---------------------------------------------------------------------------

/**
 * Convert per-token price (as returned by the gateway) to per-1M-token price
 * (as stored in OpenClaw's ModelDefinitionConfig.cost).
 *
 * Gateway/OpenRouter prices are per-token strings like "0.000005".
 * OpenClaw costs are per-1M-token numbers like 5.0.
 */
fun toPricePerMillion(perToken: String | Nothing?): Double {
  if (!perToken) {
    return 0
  }
  val num = Number(perToken)
  if (!Number.isFinite(num) || num < 0) {
    return 0
  }
  return num * 1_000_000
}

// ---------------------------------------------------------------------------
// Model parsing
// ---------------------------------------------------------------------------

fun parseModality(entry: GatewayModelEntry): List<"text" | "image"> {
  val modalities = entry.architecture?.input_modalities
  if (!Array.isArray(modalities)) {
    return ["text"]
  }
  val hasImage = modalities.some((m) => typeof m === "String" && m.toLowerCase() === "image")
  return hasImage ? ["text", "image"] : ["text"]
}

fun parseReasoning(entry: GatewayModelEntry): Boolean {
  val params = entry.supported_parameters
  if (!Array.isArray(params)) {
    return false
  }
  return params.includes("reasoning") || params.includes("include_reasoning")
}

fun toModelDefinition(entry: GatewayModelEntry): ModelDefinitionConfig {
  return {
    id: entry.id,
    name: entry.name || entry.id,
    reasoning: parseReasoning(entry),
    input: parseModality(entry),
    cost: {
      input: toPricePerMillion(entry.pricing.prompt),
      output: toPricePerMillion(entry.pricing.completion),
      cacheRead: toPricePerMillion(entry.pricing.input_cache_read),
      cacheWrite: toPricePerMillion(entry.pricing.input_cache_write),
    },
    contextWindow: entry.context_length || KILOCODE_DEFAULT_CONTEXT_WINDOW,
    maxTokens: entry.top_provider?.max_completion_tokens ?? KILOCODE_DEFAULT_MAX_TOKENS,
  }
}

// ---------------------------------------------------------------------------
// Static fallback
// ---------------------------------------------------------------------------

fun buildStaticCatalog(): ModelDefinitionConfig[] {
  return KILOCODE_MODEL_CATALOG.map((model) => ({
    id: model.id,
    name: model.name,
    reasoning: model.reasoning,
    input: model.input,
    cost: KILOCODE_DEFAULT_COST,
    contextWindow: model.contextWindow ?? KILOCODE_DEFAULT_CONTEXT_WINDOW,
    maxTokens: model.maxTokens ?? KILOCODE_DEFAULT_MAX_TOKENS,
  }))
}

// ---------------------------------------------------------------------------
// Discovery
// ---------------------------------------------------------------------------

/**
 * Discover models from the Kilo Gateway API with fallback to static catalog.
 * The /api/gateway/models endpoint is public and doesn't require authentication.
 */
suspend fun discoverKilocodeModels(): Promise<ModelDefinitionConfig[]> {
  // Skip API discovery in test environment
  if (process.env.NODE_ENV === "test" || process.env.VITEST) {
    return buildStaticCatalog()
  }

  try {
    val response = await fetch(KILOCODE_MODELS_URL, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(DISCOVERY_TIMEOUT_MS),
    })

    if (!response.ok) {
      log.warn(`Failed to discover models: HTTP ${response.status}, using static catalog`)
      return buildStaticCatalog()
    }

    val data = (await response.json()) as GatewayModelsResponse
    if (!Array.isArray(data.data) || data.data.length === 0) {
      log.warn("No models found from gateway API, using static catalog")
      return buildStaticCatalog()
    }

    val models: ModelDefinitionConfig[] = []
    val discoveredIds = mutableSetOf<String>()

    for (const entry of data.data) {
      if (!entry || typeof entry !== "object") {
        continue
      }
      val id = typeof entry.id === "String" ? entry.id.trim() : ""
      if (!id || discoveredIds.has(id)) {
        continue
      }
      try {
        models.push(toModelDefinition(entry))
        discoveredIds.add(id)
      } catch (e) {
        log.warn(`Skipping malformed model entry "${id}": ${String(e)}`)
      }
    }

    // Ensure the static fallback models are always present
    val staticModels = buildStaticCatalog()
    for (const staticModel of staticModels) {
      if (!discoveredIds.has(staticModel.id)) {
        models.unshift(staticModel)
      }
    }

    return models.length > 0 ? models : buildStaticCatalog()
  } catch (error) {
    log.warn(`Discovery failed: ${String(error)}, using static catalog`)
    return buildStaticCatalog()
  }
}
