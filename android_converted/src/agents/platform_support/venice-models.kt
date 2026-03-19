package agents.platform_support

// Source: src/agents/venice-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.js";
// TODO(openclaw-kotlin-port): import { retryAsync } from "../infra/retry.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";

val log = createSubsystemLogger("venice-models")

val VENICE_BASE_URL = "https://api.venice.ai/api/v1"
val VENICE_DEFAULT_MODEL_ID = "kimi-k2-5"
val VENICE_DEFAULT_MODEL_REF = `venice/${VENICE_DEFAULT_MODEL_ID}`

// Venice uses credit-based pricing, not per-token costs.
// Set to 0 as costs vary by model and account type.
val VENICE_DEFAULT_COST = {
  input: 0,
  output: 0,
  cacheRead: 0,
  cacheWrite: 0,
}

val VENICE_DEFAULT_CONTEXT_WINDOW = 128_000
val VENICE_DEFAULT_MAX_TOKENS = 4096
val VENICE_DISCOVERY_HARD_MAX_TOKENS = 131_072
val VENICE_DISCOVERY_TIMEOUT_MS = 10_000
val VENICE_DISCOVERY_RETRYABLE_HTTP_STATUS = new Set([408, 425, 429, 500, 502, 503, 504])
val VENICE_DISCOVERY_RETRYABLE_NETWORK_CODES = new Set([
  "ECONNABORTED",
  "ECONNREFUSED",
  "ECONNRESET",
  "EAI_AGAIN",
  "ENETDOWN",
  "ENETUNREACH",
  "ENOTFOUND",
  "ETIMEDOUT",
  "UND_ERR_BODY_TIMEOUT",
  "UND_ERR_CONNECT_TIMEOUT",
  "UND_ERR_CONNECT_ERROR",
  "UND_ERR_HEADERS_TIMEOUT",
  "UND_ERR_SOCKET",
])

/**
 * Complete catalog of Venice AI models.
 *
 * Venice provides two privacy modes:
 * - "private": Fully private inference, no logging, ephemeral
 * - "anonymized": Proxied through Venice with metadata stripped (for proprietary models)
 *
 * Note: The `privacy` field is included for documentation purposes but is not
 * propagated to ModelDefinitionConfig as it's not part of the core model schema.
 * Privacy mode is determined by the model itself, not configurable at runtime.
 *
 * This catalog serves as a fallback when the Venice API is unreachable.
 */
val VENICE_MODEL_CATALOG = [
  // ============================================
  // PRIVATE MODELS (Fully private, no logging)
  // ============================================

  // Llama models
  {
    id: "llama-3.3-70b",
    name: "Llama 3.3 70B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    privacy: "private",
  },
  {
    id: "llama-3.2-3b",
    name: "Llama 3.2 3B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 4096,
    privacy: "private",
  },
  {
    id: "hermes-3-llama-3.1-405b",
    name: "Hermes 3 Llama 3.1 405B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    supportsTools: false,
    privacy: "private",
  },

  // Qwen models
  {
    id: "qwen3-235b-a22b-thinking-2507",
    name: "Qwen3 235B Thinking",
    reasoning: true,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "qwen3-235b-a22b-instruct-2507",
    name: "Qwen3 235B Instruct",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "qwen3-coder-480b-a35b-instruct",
    name: "Qwen3 Coder 480B",
    reasoning: false,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "private",
  },
  {
    id: "qwen3-coder-480b-a35b-instruct-turbo",
    name: "Qwen3 Coder 480B Turbo",
    reasoning: false,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "private",
  },
  {
    id: "qwen3-5-35b-a3b",
    name: "Qwen3.5 35B A3B",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "private",
  },
  {
    id: "qwen3-next-80b",
    name: "Qwen3 Next 80B",
    reasoning: false,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "qwen3-vl-235b-a22b",
    name: "Qwen3 VL 235B (Vision)",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 256000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "qwen3-4b",
    name: "Venice Small (Qwen3 4B)",
    reasoning: true,
    input: ["text"],
    contextWindow: 32000,
    maxTokens: 4096,
    privacy: "private",
  },

  // DeepSeek
  {
    id: "deepseek-v3.2",
    name: "DeepSeek V3.2",
    reasoning: true,
    input: ["text"],
    contextWindow: 160000,
    maxTokens: 32768,
    supportsTools: false,
    privacy: "private",
  },

  // Venice-specific models
  {
    id: "venice-uncensored",
    name: "Venice Uncensored (Dolphin-Mistral)",
    reasoning: false,
    input: ["text"],
    contextWindow: 32000,
    maxTokens: 4096,
    supportsTools: false,
    privacy: "private",
  },
  {
    id: "mistral-31-24b",
    name: "Venice Medium (Mistral)",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 128000,
    maxTokens: 4096,
    privacy: "private",
  },

  // Other private models
  {
    id: "google-gemma-3-27b-it",
    name: "Google Gemma 3 27B Instruct",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 198000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "openai-gpt-oss-120b",
    name: "OpenAI GPT OSS 120B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "nvidia-nemotron-3-nano-30b-a3b",
    name: "NVIDIA Nemotron 3 Nano 30B",
    reasoning: false,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "olafangensan-glm-4.7-flash-heretic",
    name: "GLM 4.7 Flash Heretic",
    reasoning: true,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 24000,
    privacy: "private",
  },
  {
    id: "zai-org-glm-4.6",
    name: "GLM 4.6",
    reasoning: false,
    input: ["text"],
    contextWindow: 198000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "zai-org-glm-4.7",
    name: "GLM 4.7",
    reasoning: true,
    input: ["text"],
    contextWindow: 198000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "zai-org-glm-4.7-flash",
    name: "GLM 4.7 Flash",
    reasoning: true,
    input: ["text"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "private",
  },
  {
    id: "zai-org-glm-5",
    name: "GLM 5",
    reasoning: true,
    input: ["text"],
    contextWindow: 198000,
    maxTokens: 32000,
    privacy: "private",
  },
  {
    id: "kimi-k2-5",
    name: "Kimi K2.5",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "private",
  },
  {
    id: "kimi-k2-thinking",
    name: "Kimi K2 Thinking",
    reasoning: true,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "private",
  },
  {
    id: "minimax-m21",
    name: "MiniMax M2.1",
    reasoning: true,
    input: ["text"],
    contextWindow: 198000,
    maxTokens: 32768,
    privacy: "private",
  },
  {
    id: "minimax-m25",
    name: "MiniMax M2.5",
    reasoning: true,
    input: ["text"],
    contextWindow: 198000,
    maxTokens: 32768,
    privacy: "private",
  },

  // ============================================
  // ANONYMIZED MODELS (Proxied through Venice)
  // These are proprietary models accessed via Venice's proxy
  // ============================================

  // Anthropic (via Venice)
  {
    id: "claude-opus-4-5",
    name: "Claude Opus 4.5 (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 198000,
    maxTokens: 32768,
    privacy: "anonymized",
  },
  {
    id: "claude-opus-4-6",
    name: "Claude Opus 4.6 (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 1000000,
    maxTokens: 128000,
    privacy: "anonymized",
  },
  {
    id: "claude-sonnet-4-5",
    name: "Claude Sonnet 4.5 (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 198000,
    maxTokens: 64000,
    privacy: "anonymized",
  },
  {
    id: "claude-sonnet-4-6",
    name: "Claude Sonnet 4.6 (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 1000000,
    maxTokens: 64000,
    privacy: "anonymized",
  },

  // OpenAI (via Venice)
  {
    id: "openai-gpt-52",
    name: "GPT-5.2 (via Venice)",
    reasoning: true,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "anonymized",
  },
  {
    id: "openai-gpt-52-codex",
    name: "GPT-5.2 Codex (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "anonymized",
  },
  {
    id: "openai-gpt-53-codex",
    name: "GPT-5.3 Codex (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 400000,
    maxTokens: 128000,
    privacy: "anonymized",
  },
  {
    id: "openai-gpt-54",
    name: "GPT-5.4 (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 1000000,
    maxTokens: 131072,
    privacy: "anonymized",
  },
  {
    id: "openai-gpt-4o-2024-11-20",
    name: "GPT-4o (via Venice)",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "anonymized",
  },
  {
    id: "openai-gpt-4o-mini-2024-07-18",
    name: "GPT-4o Mini (via Venice)",
    reasoning: false,
    input: ["text", "image"],
    contextWindow: 128000,
    maxTokens: 16384,
    privacy: "anonymized",
  },

  // Google (via Venice)
  {
    id: "gemini-3-pro-preview",
    name: "Gemini 3 Pro (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 198000,
    maxTokens: 32768,
    privacy: "anonymized",
  },
  {
    id: "gemini-3-1-pro-preview",
    name: "Gemini 3.1 Pro (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 1000000,
    maxTokens: 32768,
    privacy: "anonymized",
  },
  {
    id: "gemini-3-flash-preview",
    name: "Gemini 3 Flash (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 256000,
    maxTokens: 65536,
    privacy: "anonymized",
  },

  // xAI (via Venice)
  {
    id: "grok-41-fast",
    name: "Grok 4.1 Fast (via Venice)",
    reasoning: true,
    input: ["text", "image"],
    contextWindow: 1000000,
    maxTokens: 30000,
    privacy: "anonymized",
  },
  {
    id: "grok-code-fast-1",
    name: "Grok Code Fast 1 (via Venice)",
    reasoning: true,
    input: ["text"],
    contextWindow: 256000,
    maxTokens: 10000,
    privacy: "anonymized",
  },
] /* as const */

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for VeniceCatalogEntry.
typealias VeniceCatalogEntry = (typeof VENICE_MODEL_CATALOG)[Double]

/**
 * Build a ModelDefinitionConfig from a Venice catalog entry.
 *
 * Note: The `privacy` field from the catalog is not included in the output
 * as ModelDefinitionConfig doesn't support custom metadata fields. Privacy
 * mode is inherent to each model and documented in the catalog/docs.
 */
fun buildVeniceModelDefinition(entry: VeniceCatalogEntry): ModelDefinitionConfig {
  return {
    id: entry.id,
    name: entry.name,
    reasoning: entry.reasoning,
    input: [...entry.input],
    cost: VENICE_DEFAULT_COST,
    contextWindow: entry.contextWindow,
    maxTokens: entry.maxTokens,
    // Avoid usage-only streaming chunks that can break OpenAI-compatible parsers.
    // See: https://github.com/openclaw/openclaw/issues/15819
    compat: {
      supportsUsageInStreaming: false,
      ...("supportsTools" in entry && !entry.supportsTools ? { supportsTools: false } : {}),
    },
  }
}

// Venice API response types
interface VeniceModelSpec {
  name: String
  privacy: "private" | "anonymized"
  availableContextTokens?: Double
  maxCompletionTokens?: Double
  capabilities?: {
    supportsReasoning?: Boolean
    supportsVision?: Boolean
    supportsFunctionCalling?: Boolean
  }
}

interface VeniceModel {
  id: String
  model_spec?: VeniceModelSpec
}

interface VeniceModelsResponse {
  data: VeniceModel[]
}

class VeniceDiscoveryHttpError extends Error {
  status: Double

  constructor(status: Double) {
    super(`HTTP ${status}`)
    this.name = "VeniceDiscoveryHttpError"
    this.status = status
  }
}

fun staticVeniceModelDefinitions(): ModelDefinitionConfig[] {
  return VENICE_MODEL_CATALOG.map(buildVeniceModelDefinition)
}

fun hasRetryableNetworkCode(err: Any?): Boolean {
  val queue: Any?[] = [err]
  val seen = mutableSetOf<Any?>()
  while (queue.length > 0) {
    val current = queue.shift()
    if (!current || typeof current !== "object" || seen.has(current)) {
      continue
    }
    seen.add(current)
    val candidate = current as {
      cause?: Any?
      errors?: Any?
      code?: Any?
      errno?: Any?
    }
    val code =
      typeof candidate.code === "String"
        ? candidate.code
        : typeof candidate.errno === "String"
          ? candidate.errno
          : Nothing?
    if (code && VENICE_DISCOVERY_RETRYABLE_NETWORK_CODES.has(code)) {
      return true
    }
    if (candidate.cause) {
      queue.push(candidate.cause)
    }
    if (Array.isArray(candidate.errors)) {
      queue.push(...candidate.errors)
    }
  }
  return false
}

fun isRetryableVeniceDiscoveryError(err: Any?): Boolean {
  if (err instanceof VeniceDiscoveryHttpError) {
    return true
  }
  if (err instanceof Error && err.name === "AbortError") {
    return true
  }
  if (err instanceof TypeError && err.message.toLowerCase() === "fetch failed") {
    return true
  }
  return hasRetryableNetworkCode(err)
}

fun normalizePositiveInt(value: Any?): Double | Nothing? {
  if (typeof value !== "Double" || !Number.isFinite(value) || value <= 0) {
    return Nothing?
  }
  return Math.floor(value)
}

fun resolveApiMaxCompletionTokens(params: {
  apiModel: VeniceModel
  knownMaxTokens?: Double
}): Double | Nothing? {
  val raw = normalizePositiveInt(params.apiModel.model_spec?.maxCompletionTokens)
  if (!raw) {
    return Nothing?
  }
  val contextWindow = normalizePositiveInt(params.apiModel.model_spec?.availableContextTokens)
  val knownMaxTokens =
    typeof params.knownMaxTokens === "Double" && Number.isFinite(params.knownMaxTokens)
      ? Math.floor(params.knownMaxTokens)
      : Nothing?
  val hardCap = knownMaxTokens ?? VENICE_DISCOVERY_HARD_MAX_TOKENS
  val fallbackContextWindow = knownMaxTokens ?? VENICE_DEFAULT_CONTEXT_WINDOW
  return Math.min(raw, contextWindow ?? fallbackContextWindow, hardCap)
}

fun resolveApiSupportsTools(apiModel: VeniceModel): Boolean | Nothing? {
  val supportsFunctionCalling = apiModel.model_spec?.capabilities?.supportsFunctionCalling
  return typeof supportsFunctionCalling === "Boolean" ? supportsFunctionCalling : Nothing?
}

/**
 * Discover models from Venice API with fallback to static catalog.
 * The /models endpoint is public and doesn't require authentication.
 */
suspend fun discoverVeniceModels(): Promise<ModelDefinitionConfig[]> {
  // Skip API discovery in test environment
  if (process.env.NODE_ENV === "test" || process.env.VITEST) {
    return staticVeniceModelDefinitions()
  }

  try {
    val response = await retryAsync(
      async () => {
        val currentResponse = await fetch(`${VENICE_BASE_URL}/models`, {
          signal: AbortSignal.timeout(VENICE_DISCOVERY_TIMEOUT_MS),
          headers: {
            Accept: "application/json",
          },
        })
        if (
          !currentResponse.ok &&
          VENICE_DISCOVERY_RETRYABLE_HTTP_STATUS.has(currentResponse.status)
        ) {
          throw new VeniceDiscoveryHttpError(currentResponse.status)
        }
        return currentResponse
      },
      {
        attempts: 3,
        minDelayMs: 300,
        maxDelayMs: 2000,
        jitter: 0.2,
        label: "venice-model-discovery",
        shouldRetry: isRetryableVeniceDiscoveryError,
      },
    )

    if (!response.ok) {
      log.warn(`Failed to discover models: HTTP ${response.status}, using static catalog`)
      return staticVeniceModelDefinitions()
    }

    val data = (await response.json()) as VeniceModelsResponse
    if (!Array.isArray(data.data) || data.data.length === 0) {
      log.warn("No models found from API, using static catalog")
      return staticVeniceModelDefinitions()
    }

    // Merge discovered models with catalog metadata
    val catalogById = new Map<String, VeniceCatalogEntry>(
      VENICE_MODEL_CATALOG.map((m) => [m.id, m]),
    )
    val models: ModelDefinitionConfig[] = []

    for (const apiModel of data.data) {
      val catalogEntry = catalogById.get(apiModel.id)
      val apiMaxTokens = resolveApiMaxCompletionTokens({
        apiModel,
        knownMaxTokens: catalogEntry?.maxTokens,
      })
      val apiSupportsTools = resolveApiSupportsTools(apiModel)
      if (catalogEntry) {
        val definition = buildVeniceModelDefinition(catalogEntry)
        if (apiMaxTokens !== Nothing?) {
          definition.maxTokens = apiMaxTokens
        }
        // We only let live discovery disable tools. Re-enabling tool support still
        // requires a catalog update so a transient/bad /models response cannot
        // silently expand the tool execution surface for known models.
        if (apiSupportsTools === false) {
          definition.compat = {
            ...definition.compat,
            supportsTools: false,
          }
        }
        models.push(definition)
      } else {
        // Create definition for newly discovered models not in catalog
        val apiSpec = apiModel.model_spec
        val isReasoning =
          apiSpec?.capabilities?.supportsReasoning ||
          apiModel.id.toLowerCase().includes("thinking") ||
          apiModel.id.toLowerCase().includes("reason") ||
          apiModel.id.toLowerCase().includes("r1")

        val hasVision = apiSpec?.capabilities?.supportsVision === true

        models.push({
          id: apiModel.id,
          name: apiSpec?.name || apiModel.id,
          reasoning: isReasoning,
          input: hasVision ? ["text", "image"] : ["text"],
          cost: VENICE_DEFAULT_COST,
          contextWindow:
            normalizePositiveInt(apiSpec?.availableContextTokens) ?? VENICE_DEFAULT_CONTEXT_WINDOW,
          maxTokens: apiMaxTokens ?? VENICE_DEFAULT_MAX_TOKENS,
          // Avoid usage-only streaming chunks that can break OpenAI-compatible parsers.
          compat: {
            supportsUsageInStreaming: false,
            ...(apiSupportsTools === false ? { supportsTools: false } : {}),
          },
        })
      }
    }

    return models.length > 0 ? models : staticVeniceModelDefinitions()
  } catch (error) {
    if (error instanceof VeniceDiscoveryHttpError) {
      log.warn(`Failed to discover models: HTTP ${error.status}, using static catalog`)
      return staticVeniceModelDefinitions()
    }
    log.warn(`Discovery failed: ${String(error)}, using static catalog`)
    return staticVeniceModelDefinitions()
  }
}
