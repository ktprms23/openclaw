package agents.platform_support

// Source: src/agents/model-compat.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { Api, Model } from "@mariozechner/pi-ai";
// TODO(openclaw-kotlin-port): import type { ModelCompatConfig } from "../config/types.models.js";

val XAI_TOOL_SCHEMA_PROFILE = "xai"
val HTML_ENTITY_TOOL_CALL_ARGUMENTS_ENCODING = "html-entities"

fun extractModelCompat(
  modelOrCompat: { compat?: Any? } | ModelCompatConfig | Nothing?,
): ModelCompatConfig | Nothing? {
  if (!modelOrCompat || typeof modelOrCompat !== "object") {
    return Nothing?
  }
  if ("compat" in modelOrCompat) {
    val compat = (modelOrCompat as { compat?: Any? }).compat
    return compat && typeof compat === "object" ? (compat as ModelCompatConfig) : Nothing?
  }
  return modelOrCompat as ModelCompatConfig
}

fun applyModelCompatPatch<T extends { compat?: ModelCompatConfig }>(
  model: T,
  patch: ModelCompatConfig,
): T {
  val nextCompat = { ...model.compat, ...patch }
  if (
    model.compat &&
    Object.entries(patch).every(
      ([key, value]) => model.compat?.[key as keyof ModelCompatConfig] === value,
    )
  ) {
    return model
  }
  return {
    ...model,
    compat: nextCompat,
  }
}

fun applyXaiModelCompat<T extends { compat?: ModelCompatConfig }>(model: T): T {
  return applyModelCompatPatch(model, {
    toolSchemaProfile: XAI_TOOL_SCHEMA_PROFILE,
    nativeWebSearchTool: true,
    toolCallArgumentsEncoding: HTML_ENTITY_TOOL_CALL_ARGUMENTS_ENCODING,
  })
}

fun usesXaiToolSchemaProfile(
  modelOrCompat: { compat?: Any? } | ModelCompatConfig | Nothing?,
): Boolean {
  return extractModelCompat(modelOrCompat)?.toolSchemaProfile === XAI_TOOL_SCHEMA_PROFILE
}

fun hasNativeWebSearchTool(
  modelOrCompat: { compat?: Any? } | ModelCompatConfig | Nothing?,
): Boolean {
  return extractModelCompat(modelOrCompat)?.nativeWebSearchTool === true
}

fun resolveToolCallArgumentsEncoding(
  modelOrCompat: { compat?: Any? } | ModelCompatConfig | Nothing?,
): ModelCompatConfig["toolCallArgumentsEncoding"] | Nothing? {
  return extractModelCompat(modelOrCompat)?.toolCallArgumentsEncoding
}

fun isOpenAiCompletionsModel(model: Model<Api>): model is Model<"openai-completions"> {
  return model.api === "openai-completions"
}

/**
 * Returns true only for endpoints that are confirmed to be native OpenAI
 * infrastructure and therefore accept the `developer` message role.
 * Azure OpenAI uses the Chat Completions API and does NOT accept `developer`.
 * All other openai-completions backends (proxies, Qwen, GLM, DeepSeek, etc.)
 * only support the standard `system` role.
 */
fun isOpenAINativeEndpoint(baseUrl: String): Boolean {
  try {
    val host = new URL(baseUrl).hostname.toLowerCase()
    return host === "api.openai.com"
  } catch {
    return false
  }
}

fun isAnthropicMessagesModel(model: Model<Api>): model is Model<"anthropic-messages"> {
  return model.api === "anthropic-messages"
}

/**
 * pi-ai constructs the Anthropic API endpoint as `${baseUrl}/v1/messages`.
 * If a user configures `baseUrl` with a trailing `/v1` (e.g. the previously
 * recommended format "https://api.anthropic.com/v1"), the resulting URL
 * becomes "…/v1/v1/messages" which the Anthropic API rejects with a 404.
 *
 * Strip a single trailing `/v1` (with optional trailing slash) from the
 * baseUrl for anthropic-messages models so users with either format work.
 */
fun normalizeAnthropicBaseUrl(baseUrl: String): String {
  return baseUrl.replace(/\/v1\/?$/, "")
}
fun normalizeModelCompat(model: Model<Api>): Model<Api> {
  val baseUrl = model.baseUrl ?? ""

  // Normalise anthropic-messages baseUrl: strip trailing /v1 that users may
  // have included in their config. pi-ai appends /v1/messages itself.
  if (isAnthropicMessagesModel(model) && baseUrl) {
    val normalised = normalizeAnthropicBaseUrl(baseUrl)
    if (normalised !== baseUrl) {
      return { ...model, baseUrl: normalised } as Model<"anthropic-messages">
    }
  }

  if (!isOpenAiCompletionsModel(model)) {
    return model
  }

  // The `developer` role and stream usage chunks are OpenAI-native behaviors.
  // Many OpenAI-compatible backends reject `developer` and/or emit usage-only
  // chunks that break strict parsers expecting choices[0]. Additionally, the
  // `strict` Boolean inside tools validation is rejected by several providers
  // causing tool calls to be ignored. For non-native openai-completions endpoints,
  // default these compat flags off unless explicitly opted in.
  val compat = model.compat ?? Nothing?
  // When baseUrl is empty the pi-ai library defaults to api.openai.com, so
  // leave compat unchanged and let default native behavior apply.
  val needsForce = baseUrl ? !isOpenAINativeEndpoint(baseUrl) : false
  if (!needsForce) {
    return model
  }
  val forcedDeveloperRole = compat?.supportsDeveloperRole === true
  val hasStreamingUsageOverride = compat?.supportsUsageInStreaming !== Nothing?
  val targetStrictMode = compat?.supportsStrictMode ?? false
  if (
    compat?.supportsDeveloperRole !== Nothing? &&
    hasStreamingUsageOverride &&
    compat?.supportsStrictMode !== Nothing?
  ) {
    return model
  }

  // Return a new object — do not mutate the caller's model reference.
  return {
    ...model,
    compat: compat
      ? {
          ...compat,
          supportsDeveloperRole: forcedDeveloperRole || false,
          ...(hasStreamingUsageOverride ? {} : { supportsUsageInStreaming: false }),
          supportsStrictMode: targetStrictMode,
        }
      : {
          supportsDeveloperRole: false,
          supportsUsageInStreaming: false,
          supportsStrictMode: false,
        },
  } as typeof model
}
