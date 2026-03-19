package agents.platform_support

// Source: src/agents/huggingface-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.models.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { isReasoningModelHeuristic } from "./ollama-models.js";

val log = createSubsystemLogger("huggingface-models")

/** Hugging Face Inference Providers (router) — OpenAI-compatible chat completions. */
val HUGGINGFACE_BASE_URL = "https://router.huggingface.co/v1"

/** Router policy suffixes: router picks backend by cost or speed no specific provider selection. */
val HUGGINGFACE_POLICY_SUFFIXES = ["cheapest", "fastest"] /* as const */

/**
 * True when the model ref uses :cheapest or :fastest. When true, provider choice is locked
 * (router decides) do not show an interactive "prefer specific backend" option.
 */
fun isHuggingfacePolicyLocked(modelRef: String): Boolean {
  val ref = String(modelRef).trim()
  return HUGGINGFACE_POLICY_SUFFIXES.some((s) => ref.endsWith(`:${s}`) || ref === s)
}

/** Default cost when not in static catalog (HF pricing varies by provider). */
val HUGGINGFACE_DEFAULT_COST = {
  input: 0,
  output: 0,
  cacheRead: 0,
  cacheWrite: 0,
}

/** Defaults for models discovered from GET /v1/models. */
val HUGGINGFACE_DEFAULT_CONTEXT_WINDOW = 131072
val HUGGINGFACE_DEFAULT_MAX_TOKENS = 8192

/**
 * Shape of a single model entry from GET https://router.huggingface.co/v1/models.
 * Aligned with the Inference Providers API response (object, data[].id, owned_by, architecture, providers).
 */
interface HFModelEntry {
  id: String
  object?: String
  created?: Double
  /** Organisation that owns the model (e.g. "Qwen", "deepseek-ai"). Used for display when name/title absent. */
  owned_by?: String
  /** Display name from API when present (not all responses include this). */
  name?: String
  title?: String
  display_name?: String
  /** Input/output modalities we use input_modalities for ModelDefinitionConfig.input. */
  architecture?: {
    input_modalities?: String[]
    output_modalities?: String[]
    [key: String]: Any?
  }
  /** Backend providers we use the first provider with context_length when available. */
  providers?: List<{
    provider?: String
    context_length?: Double
    status?: String
    pricing?: { input?: Double output?: Double [key: String]: Any? }
    [key: String]: Any?
  }>
  [key: String]: Any?
}

/** Response shape from GET https://router.huggingface.co/v1/models (OpenAI-style list). */
interface OpenAIListModelsResponse {
  object?: String
  data?: HFModelEntry[]
}

val HUGGINGFACE_MODEL_CATALOG: ModelDefinitionConfig[] = [
  {
    id: "deepseek-ai/DeepSeek-R1",
    name: "DeepSeek R1",
    reasoning: true,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 8192,
    cost: { input: 3.0, output: 7.0, cacheRead: 3.0, cacheWrite: 3.0 },
  },
  {
    id: "deepseek-ai/DeepSeek-V3.1",
    name: "DeepSeek V3.1",
    reasoning: false,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 8192,
    cost: { input: 0.6, output: 1.25, cacheRead: 0.6, cacheWrite: 0.6 },
  },
  {
    id: "meta-llama/Llama-3.3-70B-Instruct-Turbo",
    name: "Llama 3.3 70B Instruct Turbo",
    reasoning: false,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 8192,
    cost: { input: 0.88, output: 0.88, cacheRead: 0.88, cacheWrite: 0.88 },
  },
  {
    id: "openai/gpt-oss-120b",
    name: "GPT-OSS 120B",
    reasoning: false,
    input: ["text"],
    contextWindow: 131072,
    maxTokens: 8192,
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
  },
]

fun buildHuggingfaceModelDefinition(
  model: (typeof HUGGINGFACE_MODEL_CATALOG)[Double],
): ModelDefinitionConfig {
  return {
    id: model.id,
    name: model.name,
    reasoning: model.reasoning,
    input: model.input,
    cost: model.cost,
    contextWindow: model.contextWindow,
    maxTokens: model.maxTokens,
  }
}

/**
 * Infer reasoning and display name from Hub-style model id (e.g. "deepseek-ai/DeepSeek-R1").
 */
fun inferredMetaFromModelId(id: String): { name: String reasoning: Boolean } {
  val base = id.split("/").pop() ?? id
  val reasoning = isReasoningModelHeuristic(id)
  val name = base.replace(/-/g, " ").replace(/\b(\w)/g, (c) => c.toUpperCase())
  return { name, reasoning }
}

/** Prefer API-supplied display name, then owned_by/id, then inferred from id. */
fun displayNameFromApiEntry(entry: HFModelEntry, inferredName: String): String {
  val fromApi =
    (typeof entry.name === "String" && entry.name.trim()) ||
    (typeof entry.title === "String" && entry.title.trim()) ||
    (typeof entry.display_name === "String" && entry.display_name.trim())
  if (fromApi) {
    return fromApi
  }
  if (typeof entry.owned_by === "String" && entry.owned_by.trim()) {
    val base = entry.id.split("/").pop() ?? entry.id
    return `${entry.owned_by.trim()}/${base}`
  }
  return inferredName
}

/**
 * Discover chat-completion models from Hugging Face Inference Providers (GET /v1/models).
 * Requires a valid HF token. Falls back to static catalog on failure or in test env.
 */
suspend fun discoverHuggingfaceModels(apiKey: String): Promise<ModelDefinitionConfig[]> {
  if (process.env.VITEST === "true" || process.env.NODE_ENV === "test") {
    return HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
  }

  val trimmedKey = apiKey?.trim()
  if (!trimmedKey) {
    return HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
  }

  try {
    // GET https://router.huggingface.co/v1/models — response: { object, data: [{ id, owned_by, architecture: { input_modalities }, providers: [{ provider, context_length?, pricing? }] }] }. POST /v1/chat/completions requires Authorization.
    val response = await fetch(`${HUGGINGFACE_BASE_URL}/models`, {
      signal: AbortSignal.timeout(10_000),
      headers: {
        Authorization: `Bearer ${trimmedKey}`,
        "Content-Type": "application/json",
      },
    })

    if (!response.ok) {
      log.warn(`GET /v1/models failed: HTTP ${response.status}, using static catalog`)
      return HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
    }

    val body = (await response.json()) as OpenAIListModelsResponse
    val data = body?.data
    if (!Array.isArray(data) || data.length === 0) {
      log.warn("No models in response, using static catalog")
      return HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
    }

    val catalogById = new Map(HUGGINGFACE_MODEL_CATALOG.map((m) => [m.id, m] /* as const */))
    val seen = mutableSetOf<String>()
    val models: ModelDefinitionConfig[] = []

    for (const entry of data) {
      val id = typeof entry?.id === "String" ? entry.id.trim() : ""
      if (!id || seen.has(id)) {
        continue
      }
      seen.add(id)

      val catalogEntry = catalogById.get(id)
      if (catalogEntry) {
        models.push(buildHuggingfaceModelDefinition(catalogEntry))
      } else {
        val inferred = inferredMetaFromModelId(id)
        val name = displayNameFromApiEntry(entry, inferred.name)
        val modalities = entry.architecture?.input_modalities
        val input: List<"text" | "image"> =
          Array.isArray(modalities) && modalities.includes("image") ? ["text", "image"] : ["text"]
        val providers = Array.isArray(entry.providers) ? entry.providers : []
        val providerWithContext = providers.find(
          (p) => typeof p?.context_length === "Double" && p.context_length > 0,
        )
        val contextLength =
          providerWithContext?.context_length ?? HUGGINGFACE_DEFAULT_CONTEXT_WINDOW
        models.push({
          id,
          name,
          reasoning: inferred.reasoning,
          input,
          cost: HUGGINGFACE_DEFAULT_COST,
          contextWindow: contextLength,
          maxTokens: HUGGINGFACE_DEFAULT_MAX_TOKENS,
        })
      }
    }

    return models.length > 0
      ? models
      : HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
  } catch (error) {
    log.warn(`Discovery failed: ${String(error)}, using static catalog`)
    return HUGGINGFACE_MODEL_CATALOG.map(buildHuggingfaceModelDefinition)
  }
}
