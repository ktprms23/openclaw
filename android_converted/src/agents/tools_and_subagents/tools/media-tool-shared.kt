package agents.tools_and_subagents.tools

// Converted from src/agents/tools/media-tool-shared.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { type Api, type Model } from "@mariozechner/pi-ai";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { getDefaultLocalRoots } from "../../plugin-sdk/web-media.js";
// TODO: TypeScript import retained for manual wiring: import type { ImageModelConfig } from "./image-tool.helpers.js";
// TODO: TypeScript import retained for manual wiring: import type { ToolModelConfig } from "./model-config.helpers.js";
// TODO: TypeScript import retained for manual wiring: import { getApiKeyForModel, normalizeWorkspaceDir, requireApiKey } from "./tool-runtime.helpers.js";

data class TextToolAttempt(
    val provider: String,
    val model: String,
    val error: String,
)

data class TextToolResult(
    val text: String,
    val provider: String,
    val model: String,
    val attempts: List<TextToolAttempt>,
)

fun applyImageModelConfigDefaults(
  cfg: OpenClawConfig?,
  imageModelConfig: ImageModelConfig,
): OpenClawConfig? {
  return applyAgentDefaultModelConfig(cfg, "imageModel", imageModelConfig)
}

fun applyImageGenerationModelConfigDefaults(
  cfg: OpenClawConfig?,
  imageGenerationModelConfig: ToolModelConfig,
): OpenClawConfig? {
  return applyAgentDefaultModelConfig(cfg, "imageGenerationModel", imageGenerationModelConfig)
}

fun applyAgentDefaultModelConfig(
  cfg: OpenClawConfig?,
  key: String /* "imageModel" */ | "imageGenerationModel",
  modelConfig: ToolModelConfig,
): OpenClawConfig? {
  if (!cfg) {
    return null
  }
  return {
    ...cfg,
    agents: {
      ...cfg.agents,
      defaults: {
        ...cfg.agents?.defaults,
        [key]: modelConfig,
      },
    },
  }
}

fun resolveMediaToolLocalRoots(
  workspaceDirRaw: String?,
  options?: { workspaceOnly?: Boolean },
): List<String> {
  val workspaceDir = normalizeWorkspaceDir(workspaceDirRaw)
  if (options?.workspaceOnly) {
    return workspaceDir ? [workspaceDir] : []
  }
  val roots = getDefaultLocalRoots()
  if (!workspaceDir) {
    return [...roots]
  }
  return Array.from(mutableSetOf([...roots, workspaceDir]))
}

fun resolvePromptAndModelOverride(
  args: MutableMap<String, Any?>,
  defaultPrompt: String,
): {
  prompt: String
  modelOverride?: String
} {
  val prompt =
    args.prompt is String && args.prompt.trim() ? args.prompt.trim() : defaultPrompt
  val modelOverride =
    args.model is String && args.model.trim() ? args.model.trim() : null
  return { prompt, modelOverride }
}

fun buildTextToolResult(
  result: TextToolResult,
  extraDetails: MutableMap<String, Any?>,
): {
  content: List<{ type: String /* "text" */ text: String }>
  details: MutableMap<String, Any?>
} {
  return {
    content: [{ type: String /* "text" */, text: result.text }],
    details: {
      model: `${result.provider}/${result.model}`,
      ...extraDetails,
      attempts: result.attempts,
    },
  }
}

fun resolveModelFromRegistry(params: {
  modelRegistry: { find: (provider: String, modelId: String) -> Any? }
  provider: String
  modelId: String
}): Model<Api> {
  val model = params.modelRegistry.find(params.provider, params.modelId) as /* TODO */ Model<Api>?
  if (!model) {
    throw Error(`Unknown model: ${params.provider}/${params.modelId}`)
  }
  return model
}

suspend fun resolveModelRuntimeApiKey(params: {
  model: Model<Api>
  cfg: OpenClawConfig?
  agentDir: String
  authStorage: {
    setRuntimeApiKey: (provider: String, apiKey: String) -> Unit
  }
}): Deferred<String> {
  val apiKeyInfo = await getApiKeyForModel({
    model: params.model,
    cfg: params.cfg,
    agentDir: params.agentDir,
  })
  val apiKey = requireApiKey(apiKeyInfo, params.model.provider)
  params.authStorage.setRuntimeApiKey(params.model.provider, apiKey)
  return apiKey
}
