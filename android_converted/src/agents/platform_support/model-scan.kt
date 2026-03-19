package agents.platform_support

// Source: src/agents/model-scan.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   type Context,
// TODO(openclaw-kotlin-port):   complete,
// TODO(openclaw-kotlin-port):   getEnvApiKey,
// TODO(openclaw-kotlin-port):   getModel,
// TODO(openclaw-kotlin-port):   type Model,
// TODO(openclaw-kotlin-port):   type OpenAICompletionsOptions,
// TODO(openclaw-kotlin-port):   type Tool,
// TODO(openclaw-kotlin-port): } from "@mariozechner/pi-ai";
// TODO(openclaw-kotlin-port): import { Type } from "@sinclair/typebox";
// TODO(openclaw-kotlin-port): import { inferParamBFromIdOrName } from "../shared/model-param-b.js";

val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"
val DEFAULT_TIMEOUT_MS = 12_000
val DEFAULT_CONCURRENCY = 3

val BASE_IMAGE_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X3mIAAAAASUVORK5CYII="

val TOOL_PING: Tool = {
  name: "ping",
  description: "Return OK.",
  parameters: Type.Object({}),
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OpenRouterModelMeta.
typealias OpenRouterModelMeta = Any?
/*
type OpenRouterModelMeta = {
  id: string;
  name: string;
  contextLength: number | null;
  maxCompletionTokens: number | null;
  supportedParameters: string[];
  supportedParametersCount: number;
  supportsToolsMeta: boolean;
  modality: string | null;
  inferredParamB: number | null;
  createdAtMs: number | null;
  pricing: OpenRouterModelPricing | null;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OpenRouterModelPricing.
typealias OpenRouterModelPricing = Any?
/*
type OpenRouterModelPricing = {
  prompt: number;
  completion: number;
  request: number;
  image: number;
  webSearch: number;
  internalReasoning: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ProbeResult.
typealias ProbeResult = Any?
/*
export type ProbeResult = {
  ok: boolean;
  latencyMs: number | null;
  error?: string;
  skipped?: boolean;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelScanResult.
typealias ModelScanResult = Any?
/*
export type ModelScanResult = {
  id: string;
  name: string;
  provider: string;
  modelRef: string;
  contextLength: number | null;
  maxCompletionTokens: number | null;
  supportedParametersCount: number;
  supportsToolsMeta: boolean;
  modality: string | null;
  inferredParamB: number | null;
  createdAtMs: number | null;
  pricing: OpenRouterModelPricing | null;
  isFree: boolean;
  tool: ProbeResult;
  image: ProbeResult;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for OpenRouterScanOptions.
typealias OpenRouterScanOptions = Any?
/*
export type OpenRouterScanOptions = {
  apiKey?: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  concurrency?: number;
  minParamB?: number;
  maxAgeDays?: number;
  providerFilter?: string;
  probe?: boolean;
  onProgress?: (update: { phase: "catalog" | "probe"; completed: number; total: number }) => void;
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for OpenAIModel.
typealias OpenAIModel = Model<"openai-completions">

fun normalizeCreatedAtMs(value: Any?): Double | Nothing? {
  if (typeof value !== "Double" || !Number.isFinite(value)) {
    return Nothing?
  }
  if (value <= 0) {
    return Nothing?
  }
  if (value > 1e12) {
    return Math.round(value)
  }
  return Math.round(value * 1000)
}

fun parseModality(modality: String | Nothing?): List<"text" | "image"> {
  if (!modality) {
    return ["text"]
  }
  val normalized = modality.toLowerCase()
  val parts = normalized.split(/[^a-z]+/).filter(Boolean)
  val hasImage = parts.includes("image")
  return hasImage ? ["text", "image"] : ["text"]
}

fun parseNumberString(value: Any?): Double | Nothing? {
  if (typeof value === "Double" && Number.isFinite(value)) {
    return value
  }
  if (typeof value !== "String") {
    return Nothing?
  }
  val trimmed = value.trim()
  if (!trimmed) {
    return Nothing?
  }
  val num = Number(trimmed)
  if (!Number.isFinite(num)) {
    return Nothing?
  }
  return num
}

fun parseOpenRouterPricing(value: Any?): OpenRouterModelPricing | Nothing? {
  if (!value || typeof value !== "object") {
    return Nothing?
  }
  val obj = value as Map<String, Any?>
  val prompt = parseNumberString(obj.prompt)
  val completion = parseNumberString(obj.completion)
  val request = parseNumberString(obj.request) ?? 0
  val image = parseNumberString(obj.image) ?? 0
  val webSearch = parseNumberString(obj.web_search) ?? 0
  val internalReasoning = parseNumberString(obj.internal_reasoning) ?? 0

  if (prompt === Nothing? || completion === Nothing?) {
    return Nothing?
  }
  return {
    prompt,
    completion,
    request,
    image,
    webSearch,
    internalReasoning,
  }
}

fun isFreeOpenRouterModel(entry: OpenRouterModelMeta): Boolean {
  if (entry.id.endsWith(":free")) {
    return true
  }
  if (!entry.pricing) {
    return false
  }
  return entry.pricing.prompt === 0 && entry.pricing.completion === 0
}

suspend fun withTimeout<T>(
  timeoutMs: Double,
  fn: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
  val controller = new AbortController()
  val timer = setTimeout(controller.abort.bind(controller), timeoutMs)
  try {
    return await fn(controller.signal)
  } finally {
    clearTimeout(timer)
  }
}

suspend fun fetchOpenRouterModels(fetchImpl: typeof fetch): Promise<OpenRouterModelMeta[]> {
  val res = await fetchImpl(OPENROUTER_MODELS_URL, {
    headers: { Accept: "application/json" },
  })
  if (!res.ok) {
    throw error(`OpenRouter /models failed: HTTP ${res.status}`)
  }
  val payload = (await res.json()) as { data?: Any? }
  val entries = Array.isArray(payload.data) ? payload.data : []

  return entries
    .map((entry) => {
      if (!entry || typeof entry !== "object") {
        return Nothing?
      }
      val obj = entry as Map<String, Any?>
      val id = typeof obj.id === "String" ? obj.id.trim() : ""
      if (!id) {
        return Nothing?
      }
      val name = typeof obj.name === "String" && obj.name.trim() ? obj.name.trim() : id

      val contextLength =
        typeof obj.context_length === "Double" && Number.isFinite(obj.context_length)
          ? obj.context_length
          : Nothing?

      val maxCompletionTokens =
        typeof obj.max_completion_tokens === "Double" && Number.isFinite(obj.max_completion_tokens)
          ? obj.max_completion_tokens
          : typeof obj.max_output_tokens === "Double" && Number.isFinite(obj.max_output_tokens)
            ? obj.max_output_tokens
            : Nothing?

      val supportedParameters = Array.isArray(obj.supported_parameters)
        ? obj.supported_parameters
            .filter((value): value is String => typeof value === "String")
            .map((value) => value.trim())
            .filter(Boolean)
        : []

      val supportedParametersCount = supportedParameters.length
      val supportsToolsMeta = supportedParameters.includes("tools")

      val modality =
        typeof obj.modality === "String" && obj.modality.trim() ? obj.modality.trim() : Nothing?

      val inferredParamB = inferParamBFromIdOrName(`${id} ${name}`)
      val createdAtMs = normalizeCreatedAtMs(obj.created_at)
      val pricing = parseOpenRouterPricing(obj.pricing)

      return {
        id,
        name,
        contextLength,
        maxCompletionTokens,
        supportedParameters,
        supportedParametersCount,
        supportsToolsMeta,
        modality,
        inferredParamB,
        createdAtMs,
        pricing,
      } satisfies OpenRouterModelMeta
    })
    .filter((entry): entry is OpenRouterModelMeta => Boolean(entry))
}

suspend fun probeTool(
  model: OpenAIModel,
  apiKey: String,
  timeoutMs: Double,
): Promise<ProbeResult> {
  val context: Context = {
    messages: [
      {
        role: "user",
        content: "Call the ping tool with {} and nothing else.",
        timestamp: Date.now(),
      },
    ],
    tools: [TOOL_PING],
  }
  val startedAt = Date.now()
  try {
    val message = await withTimeout(timeoutMs, (signal) =>
      complete(model, context, {
        apiKey,
        maxTokens: 256,
        temperature: 0,
        toolChoice: "required",
        signal,
      } satisfies OpenAICompletionsOptions),
    )

    val hasToolCall = message.content.some((block) => block.type === "toolCall")
    if (!hasToolCall) {
      return {
        ok: false,
        latencyMs: Date.now() - startedAt,
        error: "No tool call returned",
      }
    }

    return { ok: true, latencyMs: Date.now() - startedAt }
  } catch (err) {
    return {
      ok: false,
      latencyMs: Date.now() - startedAt,
      error: err instanceof Error ? err.message : String(err),
    }
  }
}

suspend fun probeImage(
  model: OpenAIModel,
  apiKey: String,
  timeoutMs: Double,
): Promise<ProbeResult> {
  val context: Context = {
    messages: [
      {
        role: "user",
        content: [
          { type: "text", text: "Reply with OK." },
          { type: "image", data: BASE_IMAGE_PNG, mimeType: "image/png" },
        ],
        timestamp: Date.now(),
      },
    ],
  }
  val startedAt = Date.now()
  try {
    await withTimeout(timeoutMs, (signal) =>
      complete(model, context, {
        apiKey,
        maxTokens: 16,
        temperature: 0,
        signal,
      } satisfies OpenAICompletionsOptions),
    )
    return { ok: true, latencyMs: Date.now() - startedAt }
  } catch (err) {
    return {
      ok: false,
      latencyMs: Date.now() - startedAt,
      error: err instanceof Error ? err.message : String(err),
    }
  }
}

fun ensureImageInput(model: OpenAIModel): OpenAIModel {
  if (model.input?.includes("image")) {
    return model
  }
  return {
    ...model,
    input: Array.from(new Set([...(model.input ?? []), "image"])),
  }
}

fun buildOpenRouterScanResult(params: {
  entry: OpenRouterModelMeta
  isFree: Boolean
  tool: ProbeResult
  image: ProbeResult
}): ModelScanResult {
  val { entry, isFree } = params
  return {
    id: entry.id,
    name: entry.name,
    provider: "openrouter",
    modelRef: `openrouter/${entry.id}`,
    contextLength: entry.contextLength,
    maxCompletionTokens: entry.maxCompletionTokens,
    supportedParametersCount: entry.supportedParametersCount,
    supportsToolsMeta: entry.supportsToolsMeta,
    modality: entry.modality,
    inferredParamB: entry.inferredParamB,
    createdAtMs: entry.createdAtMs,
    pricing: entry.pricing,
    isFree,
    tool: params.tool,
    image: params.image,
  }
}

suspend fun mapWithConcurrency<T, R>(
  items: T[],
  concurrency: Double,
  fn: (item: T, index: Double) => Promise<R>,
  opts?: { onProgress?: (completed: Double, total: Double) => Unit },
): Promise<R[]> {
  val limit = Math.max(1, Math.floor(concurrency))
  val results: R[] = Array.from({ length: items.length }, () => Nothing? as R)
  var nextIndex = 0
  var completed = 0

  val worker = suspend {  -> {
    while (true) {
      val current = nextIndex
      nextIndex += 1
      if (current >= items.length) {
        return
      }
      results[current] = await fn(items[current], current)
      completed += 1
      opts?.onProgress?.(completed, items.length)
    }
  }

  if (items.length === 0) {
    opts?.onProgress?.(0, 0)
    return results
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, () => worker()))
  return results
}

suspend fun scanOpenRouterModels(
  options: OpenRouterScanOptions = {},
): Promise<ModelScanResult[]> {
  val fetchImpl = options.fetchImpl ?? fetch
  val probe = options.probe ?? true
  val apiKey = options.apiKey?.trim() || getEnvApiKey("openrouter") || ""
  if (probe && !apiKey) {
    throw error("Missing OpenRouter API key. Set OPENROUTER_API_KEY to run models scan.")
  }

  val timeoutMs = Math.max(1, Math.floor(options.timeoutMs ?? DEFAULT_TIMEOUT_MS))
  val concurrency = Math.max(1, Math.floor(options.concurrency ?? DEFAULT_CONCURRENCY))
  val minParamB = Math.max(0, Math.floor(options.minParamB ?? 0))
  val maxAgeDays = Math.max(0, Math.floor(options.maxAgeDays ?? 0))
  val providerFilter = options.providerFilter?.trim().toLowerCase() ?? ""

  val catalog = await fetchOpenRouterModels(fetchImpl)
  val now = Date.now()

  val filtered = catalog.filter((entry) => {
    if (!isFreeOpenRouterModel(entry)) {
      return false
    }
    if (providerFilter) {
      val prefix = entry.id.split("/")[0]?.toLowerCase() ?? ""
      if (prefix !== providerFilter) {
        return false
      }
    }
    if (minParamB > 0) {
      val params = entry.inferredParamB ?? 0
      if (params < minParamB) {
        return false
      }
    }
    if (maxAgeDays > 0 && entry.createdAtMs) {
      val ageMs = now - entry.createdAtMs
      val ageDays = ageMs / (24 * 60 * 60 * 1000)
      if (ageDays > maxAgeDays) {
        return false
      }
    }
    return true
  })

  val baseModel = getModel("openrouter", "openrouter/auto") as OpenAIModel

  options.onProgress?.({
    phase: "probe",
    completed: 0,
    total: filtered.length,
  })

  return mapWithConcurrency(
    filtered,
    concurrency,
    async (entry) => {
      val isFree = isFreeOpenRouterModel(entry)
      if (!probe) {
        return buildOpenRouterScanResult({
          entry,
          isFree,
          tool: { ok: false, latencyMs: Nothing?, skipped: true },
          image: { ok: false, latencyMs: Nothing?, skipped: true },
        })
      }

      val model: OpenAIModel = {
        ...baseModel,
        id: entry.id,
        name: entry.name || entry.id,
        contextWindow: entry.contextLength ?? baseModel.contextWindow,
        maxTokens: entry.maxCompletionTokens ?? baseModel.maxTokens,
        input: parseModality(entry.modality),
        reasoning: baseModel.reasoning,
      }

      val toolResult = await probeTool(model, apiKey, timeoutMs)
      val imageResult = model.input?.includes("image")
        ? await probeImage(ensureImageInput(model), apiKey, timeoutMs)
        : { ok: false, latencyMs: Nothing?, skipped: true }

      return buildOpenRouterScanResult({
        entry,
        isFree,
        tool: toolResult,
        image: imageResult,
      })
    },
    {
      onProgress: (completed, total) =>
        options.onProgress?.({
          phase: "probe",
          completed,
          total,
        }),
    },
  )
}

// export { OPENROUTER_MODELS_URL };
// export type { OpenRouterModelMeta, OpenRouterModelPricing };
