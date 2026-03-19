@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/media-tool-shared.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { type Api, type Model } from "@mariozechner/pi-ai";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { getDefaultLocalRoots } from "../../plugin-sdk/web-media.js";
// TODO(port-deps): import type { ImageModelConfig } from "./image-tool.helpers.js";
// TODO(port-deps): import type { ToolModelConfig } from "./model-config.helpers.js";
// TODO(port-deps): import { getApiKeyForModel, normalizeWorkspaceDir, requireApiKey } from "./tool-runtime.helpers.js";

typealias TextToolAttempt = Any /* TODO: translate TypeScript alias */

typealias TextToolResult = Any /* TODO: translate TypeScript alias */

fun applyImageModelConfigDefaults(
  cfg: OpenClawConfig | null,
  imageModelConfig: ImageModelConfig,
): OpenClawConfig | null {
  return applyAgentDefaultModelConfig(cfg, "imageModel", imageModelConfig);
}

fun applyImageGenerationModelConfigDefaults(
  cfg: OpenClawConfig | null,
  imageGenerationModelConfig: ToolModelConfig,
): OpenClawConfig | null {
  return applyAgentDefaultModelConfig(cfg, "imageGenerationModel", imageGenerationModelConfig);
}

fun applyAgentDefaultModelConfig(
  cfg: OpenClawConfig | null,
  key: "imageModel" | "imageGenerationModel",
  modelConfig: ToolModelConfig,
): OpenClawConfig | null {
  if (!cfg) {
    return null;
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
  };
}

fun resolveMediaToolLocalRoots(
  workspaceDirRaw: string | null,
  options?: { workspaceOnly?: boolean },
): string[] {
  val workspaceDir = normalizeWorkspaceDir(workspaceDirRaw);
  if (options?.workspaceOnly) {
    return workspaceDir ? [workspaceDir] : [];
  }
  val roots = getDefaultLocalRoots();
  if (!workspaceDir) {
    return [...roots];
  }
  return Array.from(new Set([...roots, workspaceDir]));
}

fun resolvePromptAndModelOverride(
  args: Record<string, unknown>,
  defaultPrompt: string,
): {
  prompt: string;
  modelOverride?: string;
} {
  val prompt =
    typeof args.prompt == "string" && args.prompt.trim() ? args.prompt.trim() : defaultPrompt;
  val modelOverride =
    typeof args.model == "string" && args.model.trim() ? args.model.trim() : null;
  return { prompt, modelOverride };
}

fun buildTextToolResult(
  result: TextToolResult,
  extraDetails: Record<string, unknown>,
): {
  content: Array<{ type: "text"; text: string }>;
  details: Record<string, unknown>;
} {
  return {
    content: [{ type: "text", text: result.text }],
    details: {
      model: `${result.provider}/${result.model}`,
      ...extraDetails,
      attempts: result.attempts,
    },
  };
}

fun resolveModelFromRegistry(params: {
  modelRegistry: { find: (provider: string, modelId: string) => unknown };
  provider: string;
  modelId: string;
}): Model<Api> {
  val model = params.modelRegistry.find(params.provider, params.modelId) as Model<Api> | null;
  if (!model) {
    throw Error(`Unknown model: ${params.provider}/${params.modelId}`);
  }
  return model;
}

suspend fun resolveModelRuntimeApiKey(params: {
  model: Model<Api>;
  cfg: OpenClawConfig | null;
  agentDir: string;
  authStorage: {
    setRuntimeApiKey: (provider: string, apiKey: string) => void;
  };
}): Promise<string> {
  val apiKeyInfo = await getApiKeyForModel({
    model: params.model,
    cfg: params.cfg,
    agentDir: params.agentDir,
  });
  val apiKey = requireApiKey(apiKeyInfo, params.model.provider);
  params.authStorage.setRuntimeApiKey(params.model.provider, apiKey);
  return apiKey;
}
