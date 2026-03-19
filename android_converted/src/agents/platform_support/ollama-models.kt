package agents.platform_support

// Source: src/agents/ollama-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.models.js";
// TODO(openclaw-kotlin-port): import { OLLAMA_DEFAULT_BASE_URL } from "./ollama-defaults.js";

val OLLAMA_DEFAULT_CONTEXT_WINDOW = 128000
val OLLAMA_DEFAULT_MAX_TOKENS = 8192
val OLLAMA_DEFAULT_COST = {
  input: 0,
  output: 0,
  cacheRead: 0,
  cacheWrite: 0,
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OllamaTagModel.
typealias OllamaTagModel = Any?
/*
export type OllamaTagModel = {
  name: string;
  modified_at?: string;
  size?: number;
  digest?: string;
  remote_host?: string;
  details?: {
    family?: string;
    parameter_size?: string;
  };
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OllamaTagsResponse.
typealias OllamaTagsResponse = Any?
/*
export type OllamaTagsResponse = {
  models?: OllamaTagModel[];
};
*/

typealias OllamaModelWithContext = OllamaTagModel & {
  contextWindow?: Double
}

val OLLAMA_SHOW_CONCURRENCY = 8

/**
 * Derive the Ollama native API base URL from a configured base URL.
 *
 * Users typically configure `baseUrl` with a `/v1` suffix (e.g.
 * `http://192.168.20.14:11434/v1`) for the OpenAI-compatible endpoint.
 * The native Ollama API lives at the root (e.g. `/api/tags`), so we
 * strip the `/v1` suffix when present.
 */
fun resolveOllamaApiBase(configuredBaseUrl?: String): String {
  if (!configuredBaseUrl) {
    return OLLAMA_DEFAULT_BASE_URL
  }
  val trimmed = configuredBaseUrl.replace(/\/+$/, "")
  return trimmed.replace(/\/v1$/i, "")
}

suspend fun queryOllamaContextWindow(
  apiBase: String,
  modelName: String,
): Promise<Double | Nothing?> {
  try {
    val response = await fetch(`${apiBase}/api/show`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: modelName }),
      signal: AbortSignal.timeout(3000),
    })
    if (!response.ok) {
      return Nothing?
    }
    val data = (await response.json()) as { model_info?: Map<String, Any?> }
    if (!data.model_info) {
      return Nothing?
    }
    for (const [key, value] of Object.entries(data.model_info)) {
      if (key.endsWith(".context_length") && typeof value === "Double" && Number.isFinite(value)) {
        val contextWindow = Math.floor(value)
        if (contextWindow > 0) {
          return contextWindow
        }
      }
    }
    return Nothing?
  } catch {
    return Nothing?
  }
}

suspend fun enrichOllamaModelsWithContext(
  apiBase: String,
  models: OllamaTagModel[],
  opts?: { concurrency?: Double },
): Promise<OllamaModelWithContext[]> {
  val concurrency = Math.max(1, Math.floor(opts?.concurrency ?? OLLAMA_SHOW_CONCURRENCY))
  val enriched: OllamaModelWithContext[] = []
  for (let index = 0 index < models.length index += concurrency) {
    val batch = models.slice(index, index + concurrency)
    val batchResults = await Promise.all(
      batch.map(async (model) => ({
        ...model,
        contextWindow: await queryOllamaContextWindow(apiBase, model.name),
      })),
    )
    enriched.push(...batchResults)
  }
  return enriched
}

/** Heuristic: treat models with "r1", "reasoning", or "think" in the name as reasoning models. */
fun isReasoningModelHeuristic(modelId: String): Boolean {
  return /r1|reasoning|think|reason/i.test(modelId)
}

/** Build a ModelDefinitionConfig for an Ollama model with default values. */
fun buildOllamaModelDefinition(
  modelId: String,
  contextWindow?: Double,
): ModelDefinitionConfig {
  return {
    id: modelId,
    name: modelId,
    reasoning: isReasoningModelHeuristic(modelId),
    input: ["text"],
    cost: OLLAMA_DEFAULT_COST,
    contextWindow: contextWindow ?? OLLAMA_DEFAULT_CONTEXT_WINDOW,
    maxTokens: OLLAMA_DEFAULT_MAX_TOKENS,
  }
}

/** Fetch the model list from a running Ollama instance. */
suspend fun fetchOllamaModels(
  baseUrl: String,
): Promise<{ reachable: Boolean models: OllamaTagModel[] }> {
  try {
    val apiBase = resolveOllamaApiBase(baseUrl)
    val response = await fetch(`${apiBase}/api/tags`, {
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) {
      return { reachable: true, models: [] }
    }
    val data = (await response.json()) as OllamaTagsResponse
    val models = { data.models ?? []).filter((m -> m.name)
    return { reachable: true, models }
  } catch {
    return { reachable: false, models: [] }
  }
}
