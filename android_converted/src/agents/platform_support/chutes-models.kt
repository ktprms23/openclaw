package agents.platform_support

// Source: src/agents/chutes-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.models.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";

val log = createSubsystemLogger("chutes-models")

/** Chutes.ai OpenAI-compatible API base URL. */
val CHUTES_BASE_URL = "https://llm.chutes.ai/v1"

val CHUTES_DEFAULT_MODEL_ID = "zai-org/GLM-4.7-TEE"
val CHUTES_DEFAULT_MODEL_REF = `chutes/${CHUTES_DEFAULT_MODEL_ID}`

/** Default cost for Chutes models (actual cost varies by model and compute). */
val CHUTES_DEFAULT_COST = {
  input: 0,
  output: 0,
  cacheRead: 0,
  cacheWrite: 0,
}

/** Default context window and max tokens for discovered models. */
val CHUTES_DEFAULT_CONTEXT_WINDOW = 128000
val CHUTES_DEFAULT_MAX_TOKENS = 4096

/**
 * Static catalog of popular Chutes models.
 * Used as a fallback and for initial onboarding allowlisting.
 */
val CHUTES_MODEL_CATALOG: ModelDefinitionConfig[] = [
  {
    id: "Qwen/Qwen3-32B",
    name: "Qwen/Qwen3-32B",
    reasoning: true,
    input: ["text"],
    contextWindow: 40960,
    maxTokens: 40960,
    cost: { input: 0.08, output: 0.24, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/Mistral-Nemo-Instruct-2407",
    name: "unsloth/Mistral-Nemo-Instruct-2407",
    reasoning: false,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.02, output: 0.04, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3-0324-TEE",
    name: "deepseek-ai/DeepSeek-V3-0324-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 65536,
    cost: { input: 0.25, output: 1, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-235B-A22B-Instruct-2507-TEE",
    name: "Qwen/Qwen3-235B-A22B-Instruct-2507-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 262144,
    maxTokens: 65536,
    cost: { input: 0.08, output: 0.55, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "openai/gpt-oss-120b-TEE",
    name: "openai/gpt-oss-120b-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 65536,
    cost: { input: 0.05, output: 0.45, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "chutesai/Mistral-Small-3.1-24B-Instruct-2503",
    name: "chutesai/Mistral-Small-3.1-24B-Instruct-2503",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.03, output: 0.11, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3.2-TEE",
    name: "deepseek-ai/DeepSeek-V3.2-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 65536,
    cost: { input: 0.28, output: 0.42, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-4.7-TEE",
    name: "zai-org/GLM-4.7-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 202752,
    maxTokens: 65535,
    cost: { input: 0.4, output: 2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "moonshotai/Kimi-K2.5-TEE",
    name: "moonshotai/Kimi-K2.5-TEE",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 262144,
    maxTokens: 65535,
    cost: { input: 0.45, output: 2.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/gemma-3-27b-it",
    name: "unsloth/gemma-3-27b-it",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 128000,
    maxTokens: 65536,
    cost: { input: 0.04, output: 0.15, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "XiaomiMiMo/MiMo-V2-Flash-TEE",
    name: "XiaomiMiMo/MiMo-V2-Flash-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 262144,
    maxTokens: 65536,
    cost: { input: 0.09, output: 0.29, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "chutesai/Mistral-Small-3.2-24B-Instruct-2506",
    name: "chutesai/Mistral-Small-3.2-24B-Instruct-2506",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.06, output: 0.18, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-R1-0528-TEE",
    name: "deepseek-ai/DeepSeek-R1-0528-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 65536,
    cost: { input: 0.45, output: 2.15, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-5-TEE",
    name: "zai-org/GLM-5-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 202752,
    maxTokens: 65535,
    cost: { input: 0.95, output: 3.15, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3.1-TEE",
    name: "deepseek-ai/DeepSeek-V3.1-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 65536,
    cost: { input: 0.2, output: 0.8, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3.1-Terminus-TEE",
    name: "deepseek-ai/DeepSeek-V3.1-Terminus-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 65536,
    cost: { input: 0.23, output: 0.9, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/gemma-3-4b-it",
    name: "unsloth/gemma-3-4b-it",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 96000,
    maxTokens: 96000,
    cost: { input: 0.01, output: 0.03, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "MiniMaxAI/MiniMax-M2.5-TEE",
    name: "MiniMaxAI/MiniMax-M2.5-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 196608,
    maxTokens: 65536,
    cost: { input: 0.3, output: 1.1, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "tngtech/DeepSeek-TNG-R1T2-Chimera",
    name: "tngtech/DeepSeek-TNG-R1T2-Chimera",
    reasoning: true,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 163840,
    cost: { input: 0.25, output: 0.85, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-Coder-Next-TEE",
    name: "Qwen/Qwen3-Coder-Next-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 262144,
    maxTokens: 65536,
    cost: { input: 0.12, output: 0.75, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "NousResearch/Hermes-4-405B-FP8-TEE",
    name: "NousResearch/Hermes-4-405B-FP8-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 65536,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3",
    name: "deepseek-ai/DeepSeek-V3",
    reasoning: false,
    input: ["text"],
    contextWindow: 163840,
    maxTokens: 163840,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "openai/gpt-oss-20b",
    name: "openai/gpt-oss-20b",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.04, output: 0.15, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/Llama-3.2-3B-Instruct",
    name: "unsloth/Llama-3.2-3B-Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    cost: { input: 0.01, output: 0.01, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/Mistral-Small-24B-Instruct-2501",
    name: "unsloth/Mistral-Small-24B-Instruct-2501",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 32768,
    maxTokens: 32768,
    cost: { input: 0.07, output: 0.3, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-4.7-FP8",
    name: "zai-org/GLM-4.7-FP8",
    reasoning: true,
    input: ["text"],
    contextWindow: 202752,
    maxTokens: 65535,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-4.6-TEE",
    name: "zai-org/GLM-4.6-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 202752,
    maxTokens: 65536,
    cost: { input: 0.4, output: 1.7, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3.5-397B-A17B-TEE",
    name: "Qwen/Qwen3.5-397B-A17B-TEE",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 262144,
    maxTokens: 65536,
    cost: { input: 0.55, output: 3.5, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen2.5-72B-Instruct",
    name: "Qwen/Qwen2.5-72B-Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 32768,
    maxTokens: 32768,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "NousResearch/DeepHermes-3-Mistral-24B-Preview",
    name: "NousResearch/DeepHermes-3-Mistral-24B-Preview",
    reasoning: false,
    input: ["text"],
    contextWindow: 32768,
    maxTokens: 32768,
    cost: { input: 0.02, output: 0.1, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-Next-80B-A3B-Instruct",
    name: "Qwen/Qwen3-Next-80B-A3B-Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 262144,
    maxTokens: 262144,
    cost: { input: 0.1, output: 0.8, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-4.6-FP8",
    name: "zai-org/GLM-4.6-FP8",
    reasoning: true,
    input: ["text"],
    contextWindow: 202752,
    maxTokens: 65535,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-235B-A22B-Thinking-2507",
    name: "Qwen/Qwen3-235B-A22B-Thinking-2507",
    reasoning: true,
    input: ["text"],
    contextWindow: 262144,
    maxTokens: 262144,
    cost: { input: 0.11, output: 0.6, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "deepseek-ai/DeepSeek-R1-Distill-Llama-70B",
    name: "deepseek-ai/DeepSeek-R1-Distill-Llama-70B",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.03, output: 0.11, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "tngtech/R1T2-Chimera-Speed",
    name: "tngtech/R1T2-Chimera-Speed",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 65536,
    cost: { input: 0.22, output: 0.6, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "zai-org/GLM-4.6V",
    name: "zai-org/GLM-4.6V",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 131072,
    maxTokens: 65536,
    cost: { input: 0.3, output: 0.9, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen2.5-VL-32B-Instruct",
    name: "Qwen/Qwen2.5-VL-32B-Instruct",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 16384,
    maxTokens: 16384,
    cost: { input: 0.05, output: 0.22, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-VL-235B-A22B-Instruct",
    name: "Qwen/Qwen3-VL-235B-A22B-Instruct",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 262144,
    maxTokens: 262144,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-14B",
    name: "Qwen/Qwen3-14B",
    reasoning: true,
    input: ["text"],
    contextWindow: 40960,
    maxTokens: 40960,
    cost: { input: 0.05, output: 0.22, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen2.5-Coder-32B-Instruct",
    name: "Qwen/Qwen2.5-Coder-32B-Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 32768,
    maxTokens: 32768,
    cost: { input: 0.03, output: 0.11, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3-30B-A3B",
    name: "Qwen/Qwen3-30B-A3B",
    reasoning: true,
    input: ["text"],
    contextWindow: 40960,
    maxTokens: 40960,
    cost: { input: 0.06, output: 0.22, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/gemma-3-12b-it",
    name: "unsloth/gemma-3-12b-it",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.03, output: 0.1, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "unsloth/Llama-3.2-1B-Instruct",
    name: "unsloth/Llama-3.2-1B-Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    cost: { input: 0.01, output: 0.01, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B-BF16-TEE",
    name: "nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B-BF16-TEE",
    reasoning: true,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    cost: { input: 0.3, output: 1.2, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "NousResearch/Hermes-4-14B",
    name: "NousResearch/Hermes-4-14B",
    reasoning: true,
    input: ["text"],
    contextWindow: 40960,
    maxTokens: 40960,
    cost: { input: 0.01, output: 0.05, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "Qwen/Qwen3Guard-Gen-0.6B",
    name: "Qwen/Qwen3Guard-Gen-0.6B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    cost: { input: 0.01, output: 0.01, cacheRead: 0, cacheWrite: 0 },
  },
  {
    id: "rednote-hilab/dots.ocr",
    name: "rednote-hilab/dots.ocr",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 131072,
    maxTokens: 131072,
    cost: { input: 0.01, output: 0.01, cacheRead: 0, cacheWrite: 0 },
  },
]

fun buildChutesModelDefinition(
  model: (typeof CHUTES_MODEL_CATALOG)[Double],
): ModelDefinitionConfig {
  return {
    ...model,
    // Avoid usage-only streaming chunks that can break OpenAI-compatible parsers.
    compat: {
      supportsUsageInStreaming: false,
    },
  }
}

interface ChutesModelEntry {
  id: String
  name?: String
  supported_features?: String[]
  input_modalities?: String[]
  context_length?: Double
  max_output_length?: Double
  pricing?: {
    prompt?: Double
    completion?: Double
  }
  [key: String]: Any?
}

interface OpenAIListModelsResponse {
  data?: ChutesModelEntry[]
}

val CACHE_TTL = 5 * 60 * 1000 // 5 minutes
val CACHE_MAX_ENTRIES = 100

interface CacheEntry {
  models: ModelDefinitionConfig[]
  time: Double
}

// Keyed by trimmed access token (empty String = unauthenticated).
// Prevents a public unauthenticated result from suppressing authenticated
// discovery for users with token-scoped private models.
val modelCache = mutableMapOf<String, CacheEntry>()

/** @internal - For testing only */
fun clearChutesModelCache() {
  modelCache.clear()
}

fun pruneExpiredCacheEntries(now: Double = Date.now()): Unit {
  for (const [key, entry] of modelCache.entries()) {
    if (now - entry.time >= CACHE_TTL) {
      modelCache.delete(key)
    }
  }
}

/** Cache the result for the given token key and return it. */
fun cacheAndReturn(
  tokenKey: String,
  models: ModelDefinitionConfig[],
): ModelDefinitionConfig[] {
  val now = Date.now()
  pruneExpiredCacheEntries(now)

  if (!modelCache.has(tokenKey) && modelCache.size >= CACHE_MAX_ENTRIES) {
    val oldest = modelCache.keys().next()
    if (!oldest.done) {
      modelCache.delete(oldest.value)
    }
  }

  modelCache.set(tokenKey, { models, time: now })
  return models
}

/**
 * Discover models from Chutes.ai API with fallback to static catalog.
 * Mimics the logic in Chutes init script.
 */
suspend fun discoverChutesModels(accessToken?: String): Promise<ModelDefinitionConfig[]> {
  val trimmedKey = accessToken?.trim() ?? ""

  // Return cached result for this token if still within TTL
  val now = Date.now()
  pruneExpiredCacheEntries(now)
  val cached = modelCache.get(trimmedKey)
  if (cached) {
    return cached.models
  }

  // Skip API discovery in test environment
  if (process.env.NODE_ENV === "test" || process.env.VITEST === "true") {
    return CHUTES_MODEL_CATALOG.map(buildChutesModelDefinition)
  }

  // If auth fails the result comes from the public endpoint — cache it under ""
  // so the original token key stays uncached and retries cleanly next TTL window.
  var effectiveKey = trimmedKey
  val staticCatalog = {  ->
    cacheAndReturn(effectiveKey, CHUTES_MODEL_CATALOG.map(buildChutesModelDefinition))

  val headers: Map<String, String> = {}
  if (trimmedKey) {
    headers.Authorization = `Bearer ${trimmedKey}`
  }

  try {
    var response = await fetch(`${CHUTES_BASE_URL}/models`, {
      signal: AbortSignal.timeout(10_000),
      headers,
    })

    if (response.status === 401 && trimmedKey) {
      // Auth failed — fall back to the public (unauthenticated) endpoint.
      // Cache the result under "" so the bad token stays uncached and can
      // be retried with a refreshed credential after the TTL expires.
      effectiveKey = ""
      response = await fetch(`${CHUTES_BASE_URL}/models`, {
        signal: AbortSignal.timeout(10_000),
      })
    }

    if (!response.ok) {
      // Only log if it's not a common auth/overload error that we have a fallback for
      if (response.status !== 401 && response.status !== 503) {
        log.warn(`GET /v1/models failed: HTTP ${response.status}, using static catalog`)
      }
      return staticCatalog()
    }

    val body = (await response.json()) as OpenAIListModelsResponse
    val data = body?.data
    if (!Array.isArray(data) || data.length === 0) {
      log.warn("No models in response, using static catalog")
      return staticCatalog()
    }

    val seen = mutableSetOf<String>()
    val models: ModelDefinitionConfig[] = []

    for (const entry of data) {
      val id = typeof entry?.id === "String" ? entry.id.trim() : ""
      if (!id || seen.has(id)) {
        continue
      }
      seen.add(id)

      val isReasoning =
        entry.supported_features?.includes("reasoning") ||
        id.toLowerCase().includes("r1") ||
        id.toLowerCase().includes("thinking") ||
        id.toLowerCase().includes("reason") ||
        id.toLowerCase().includes("tee")

      val input: List<"text" | "image"> = (entry.input_modalities || ["text"]).filter(
        (i): i is "text" | "image" => i === "text" || i === "image",
      )

      models.push({
        id,
        name: id, // Mirror init.sh: uses id for name
        reasoning: isReasoning,
        input,
        cost: {
          input: entry.pricing?.prompt || 0,
          output: entry.pricing?.completion || 0,
          cacheRead: 0,
          cacheWrite: 0,
        },
        contextWindow: entry.context_length || CHUTES_DEFAULT_CONTEXT_WINDOW,
        maxTokens: entry.max_output_length || CHUTES_DEFAULT_MAX_TOKENS,
        compat: {
          supportsUsageInStreaming: false,
        },
      })
    }

    return cacheAndReturn(
      effectiveKey,
      models.length > 0 ? models : CHUTES_MODEL_CATALOG.map(buildChutesModelDefinition),
    )
  } catch (error) {
    log.warn(`Discovery failed: ${String(error)}, using static catalog`)
    return staticCatalog()
  }
}
