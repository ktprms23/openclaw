package agents.platform_support

// Source: src/agents/models-config.providers.discovery.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.models.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   enrichOllamaModelsWithContext,
// TODO(openclaw-kotlin-port):   OLLAMA_DEFAULT_CONTEXT_WINDOW,
// TODO(openclaw-kotlin-port):   OLLAMA_DEFAULT_COST,
// TODO(openclaw-kotlin-port):   OLLAMA_DEFAULT_MAX_TOKENS,
// TODO(openclaw-kotlin-port):   isReasoningModelHeuristic,
// TODO(openclaw-kotlin-port):   resolveOllamaApiBase,
// TODO(openclaw-kotlin-port):   type OllamaTagsResponse,
// TODO(openclaw-kotlin-port): } from "./ollama-models.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   SELF_HOSTED_DEFAULT_CONTEXT_WINDOW,
// TODO(openclaw-kotlin-port):   SELF_HOSTED_DEFAULT_COST,
// TODO(openclaw-kotlin-port):   SELF_HOSTED_DEFAULT_MAX_TOKENS,
// TODO(openclaw-kotlin-port): } from "./self-hosted-provider-defaults.js";
// TODO(openclaw-kotlin-port): import { SGLANG_DEFAULT_BASE_URL, SGLANG_PROVIDER_LABEL } from "./sglang-defaults.js";
// TODO(openclaw-kotlin-port): import { VLLM_DEFAULT_BASE_URL, VLLM_PROVIDER_LABEL } from "./vllm-defaults.js";
// export { buildHuggingfaceProvider } from "../../extensions/huggingface/provider-catalog.js";
// export { buildKilocodeProviderWithDiscovery } from "../../extensions/kilocode/provider-catalog.js";
// export { buildVeniceProvider } from "../../extensions/venice/provider-catalog.js";
// export { buildVercelAiGatewayProvider } from "../../extensions/vercel-ai-gateway/provider-catalog.js";

// export { resolveOllamaApiBase } from "./ollama-models.js";

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ModelsConfig.
typealias ModelsConfig = NonNullable<OpenClawConfig["models"]>
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ProviderConfig.
typealias ProviderConfig = NonNullable<ModelsConfig["providers"]>[String]

val log = createSubsystemLogger("agents/model-providers")

val OLLAMA_SHOW_CONCURRENCY = 8
val OLLAMA_SHOW_MAX_MODELS = 200

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OpenAICompatModelsResponse.
typealias OpenAICompatModelsResponse = Any?
/*
type OpenAICompatModelsResponse = {
  data?: Array<{
    id?: string;
  }>;
};
*/

suspend fun discoverOllamaModels(
  baseUrl?: String,
  opts?: { quiet?: Boolean },
): Promise<ModelDefinitionConfig[]> {
  if (process.env.VITEST || process.env.NODE_ENV === "test") {
    return []
  }
  try {
    val apiBase = resolveOllamaApiBase(baseUrl)
    val response = await fetch(`${apiBase}/api/tags`, {
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) {
      if (!opts?.quiet) {
        log.warn(`Failed to discover Ollama models: ${response.status}`)
      }
      return []
    }
    val data = (await response.json()) as OllamaTagsResponse
    if (!data.models || data.models.length === 0) {
      log.debug("No Ollama models found on local instance")
      return []
    }
    val modelsToInspect = data.models.slice(0, OLLAMA_SHOW_MAX_MODELS)
    if (modelsToInspect.length < data.models.length && !opts?.quiet) {
      log.warn(
        `Capping Ollama /api/show inspection to ${OLLAMA_SHOW_MAX_MODELS} models (received ${data.models.length})`,
      )
    }
    val discovered = await enrichOllamaModelsWithContext(apiBase, modelsToInspect, {
      concurrency: OLLAMA_SHOW_CONCURRENCY,
    })
    return discovered.map((model) => ({
      id: model.name,
      name: model.name,
      reasoning: isReasoningModelHeuristic(model.name),
      input: ["text"],
      cost: OLLAMA_DEFAULT_COST,
      contextWindow: model.contextWindow ?? OLLAMA_DEFAULT_CONTEXT_WINDOW,
      maxTokens: OLLAMA_DEFAULT_MAX_TOKENS,
    }))
  } catch (error) {
    if (!opts?.quiet) {
      log.warn(`Failed to discover Ollama models: ${String(error)}`)
    }
    return []
  }
}

suspend fun discoverOpenAICompatibleLocalModels(params: {
  baseUrl: String
  apiKey?: String
  label: String
  contextWindow?: Double
  maxTokens?: Double
}): Promise<ModelDefinitionConfig[]> {
  if (process.env.VITEST || process.env.NODE_ENV === "test") {
    return []
  }

  val trimmedBaseUrl = params.baseUrl.trim().replace(/\/+$/, "")
  val url = `${trimmedBaseUrl}/models`

  try {
    val trimmedApiKey = params.apiKey?.trim()
    val response = await fetch(url, {
      headers: trimmedApiKey ? { Authorization: `Bearer ${trimmedApiKey}` } : Nothing?,
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) {
      log.warn(`Failed to discover ${params.label} models: ${response.status}`)
      return []
    }
    val data = (await response.json()) as OpenAICompatModelsResponse
    val models = data.data ?? []
    if (models.length === 0) {
      log.warn(`No ${params.label} models found on local instance`)
      return []
    }

    return models
      .map((model) => ({ id: typeof model.id === "String" ? model.id.trim() : "" }))
      .filter((model) => Boolean(model.id))
      .map((model) => {
        val modelId = model.id
        return {
          id: modelId,
          name: modelId,
          reasoning: isReasoningModelHeuristic(modelId),
          input: ["text"],
          cost: SELF_HOSTED_DEFAULT_COST,
          contextWindow: params.contextWindow ?? SELF_HOSTED_DEFAULT_CONTEXT_WINDOW,
          maxTokens: params.maxTokens ?? SELF_HOSTED_DEFAULT_MAX_TOKENS,
        } satisfies ModelDefinitionConfig
      })
  } catch (error) {
    log.warn(`Failed to discover ${params.label} models: ${String(error)}`)
    return []
  }
}

suspend fun buildOllamaProvider(
  configuredBaseUrl?: String,
  opts?: { quiet?: Boolean },
): Promise<ProviderConfig> {
  val models = await discoverOllamaModels(configuredBaseUrl, opts)
  return {
    baseUrl: resolveOllamaApiBase(configuredBaseUrl),
    api: "ollama",
    models,
  }
}

suspend fun buildVllmProvider(params?: {
  baseUrl?: String
  apiKey?: String
}): Promise<ProviderConfig> {
  val baseUrl = (params?.baseUrl?.trim() || VLLM_DEFAULT_BASE_URL).replace(/\/+$/, "")
  val models = await discoverOpenAICompatibleLocalModels({
    baseUrl,
    apiKey: params?.apiKey,
    label: VLLM_PROVIDER_LABEL,
  })
  return {
    baseUrl,
    api: "openai-completions",
    models,
  }
}

suspend fun buildSglangProvider(params?: {
  baseUrl?: String
  apiKey?: String
}): Promise<ProviderConfig> {
  val baseUrl = (params?.baseUrl?.trim() || SGLANG_DEFAULT_BASE_URL).replace(/\/+$/, "")
  val models = await discoverOpenAICompatibleLocalModels({
    baseUrl,
    apiKey: params?.apiKey,
    label: SGLANG_PROVIDER_LABEL,
  })
  return {
    baseUrl,
    api: "openai-completions",
    models,
  }
}
