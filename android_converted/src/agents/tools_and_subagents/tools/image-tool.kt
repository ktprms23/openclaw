@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/image-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { getMediaUnderstandingProvider } from "../../media-understanding/providers/index.js";
// TODO(port-deps): import { buildProviderRegistry } from "../../media-understanding/runner.js";
// TODO(port-deps): import { loadWebMedia } from "../../plugin-sdk/web-media.js";
// TODO(port-deps): import { resolveUserPath } from "../../utils.js";
// TODO(port-deps): import { isMinimaxVlmProvider } from "../minimax-vlm.js";
// TODO(port-deps): import {
// TODO(port-deps): coerceImageAssistantText,
// TODO(port-deps): coerceImageModelConfig,
// TODO(port-deps): decodeDataUrl,
// TODO(port-deps): type ImageModelConfig,
// TODO(port-deps): resolveProviderVisionModelFromConfig,
// TODO(port-deps): } from "./image-tool.helpers.js";
// TODO(port-deps): import {
// TODO(port-deps): applyImageModelConfigDefaults,
// TODO(port-deps): buildTextToolResult,
// TODO(port-deps): resolveMediaToolLocalRoots,
// TODO(port-deps): resolvePromptAndModelOverride,
// TODO(port-deps): } from "./media-tool-shared.js";
// TODO(port-deps): import {
// TODO(port-deps): buildToolModelConfigFromCandidates,
// TODO(port-deps): hasToolModelConfig,
// TODO(port-deps): resolveDefaultModelRef,
// TODO(port-deps): } from "./model-config.helpers.js";
// TODO(port-deps): import {
// TODO(port-deps): createSandboxBridgeReadFile,
// TODO(port-deps): resolveSandboxedBridgeMediaPath,
// TODO(port-deps): runWithImageModelFallback,
// TODO(port-deps): type AnyAgentTool,
// TODO(port-deps): type SandboxedBridgeMediaPathConfig,
// TODO(port-deps): type SandboxFsBridge,
// TODO(port-deps): type ToolFsPolicy,
// TODO(port-deps): } from "./tool-runtime.helpers.js";

val DEFAULT_PROMPT = "Describe the image.";
val ANTHROPIC_IMAGE_PRIMARY = "anthropic/claude-opus-4-6";
val ANTHROPIC_IMAGE_FALLBACK = "anthropic/claude-opus-4-5";
val DEFAULT_MAX_IMAGES = 20;

val __testing = {
  decodeDataUrl,
  coerceImageAssistantText,
  resolveImageToolMaxTokens,
} as const;

fun resolveImageToolMaxTokens(modelMaxTokens: number | null, requestedMaxTokens = 4096) {
  if (
    typeof modelMaxTokens != "number" ||
    !Number.isFinite(modelMaxTokens) ||
    modelMaxTokens <= 0
  ) {
    return requestedMaxTokens;
  }
  return Math.min(requestedMaxTokens, modelMaxTokens);
}

/**
 * Resolve the effective image model config for the `image` tool.
 *
 * - Prefer explicit config (`agents.defaults.imageModel`).
 * - Otherwise, try to "pair" the primary model with an image-capable model:
 *   - same provider (best effort)
 *   - fall back to OpenAI/Anthropic when available
 */
fun resolveImageModelConfigForTool(params: {
  cfg?: OpenClawConfig;
  agentDir: string;
}): ImageModelConfig | null {
  // Note: We intentionally do NOT gate based on primarySupportsImages here.
  // Even when the primary model supports images, we keep the tool available
  // because images are auto-injected into prompts (see attempt.ts detectAndLoadPromptImages).
  // The tool description is adjusted via modelHasVision to discourage redundant usage.
  val explicit = coerceImageModelConfig(params.cfg);
  if (hasToolModelConfig(explicit)) {
    return explicit;
  }

  val primary = resolveDefaultModelRef(params.cfg);

  val providerVisionFromConfig = resolveProviderVisionModelFromConfig({
    cfg: params.cfg,
    provider: primary.provider,
  });
  val primaryCandidates = (() => {
    if (isMinimaxVlmProvider(primary.provider)) {
      return [`${primary.provider}/MiniMax-VL-01`];
    }
    if (providerVisionFromConfig) {
      return [providerVisionFromConfig];
    }
    if (primary.provider == "zai") {
      return ["zai/glm-4.6v"];
    }
    if (primary.provider == "openai") {
      return ["openai/gpt-5-mini"];
    }
    if (primary.provider == "anthropic") {
      return [ANTHROPIC_IMAGE_PRIMARY];
    }
    return [];
  })();

  return buildToolModelConfigFromCandidates({
    explicit,
    agentDir: params.agentDir,
    candidates: [...primaryCandidates, "openai/gpt-5-mini", ANTHROPIC_IMAGE_FALLBACK],
  });
}

fun pickMaxBytes(cfg?: OpenClawConfig, maxBytesMb?: number): number | null {
  if (typeof maxBytesMb == "number" && Number.isFinite(maxBytesMb) && maxBytesMb > 0) {
    return Math.floor(maxBytesMb * 1024 * 1024);
  }
  val configured = cfg?.agents?.defaults?.mediaMaxMb;
  if (typeof configured == "number" && Number.isFinite(configured) && configured > 0) {
    return Math.floor(configured * 1024 * 1024);
  }
  return null;
}

typealias ImageSandboxConfig = Any /* TODO: translate TypeScript alias */

suspend fun runImagePrompt(params: {
  cfg?: OpenClawConfig;
  agentDir: string;
  imageModelConfig: ImageModelConfig;
  modelOverride?: string;
  prompt: string;
  images: Array<{ buffer: Buffer; mimeType: string }>;
}): Promise<{
  text: string;
  provider: string;
  model: string;
  attempts: Array<{ provider: string; model: string; error: string }>;
}> {
  val effectiveCfg = applyImageModelConfigDefaults(params.cfg, params.imageModelConfig);
  val providerCfg: OpenClawConfig = effectiveCfg ?: {};
  val providerRegistry = buildProviderRegistry(null, providerCfg);

  val result = await runWithImageModelFallback({
    cfg: effectiveCfg,
    modelOverride: params.modelOverride,
    run: async (provider, modelId) => {
      val imageProvider = getMediaUnderstandingProvider(provider, providerRegistry);
      if (!imageProvider) {
        throw Error(`No media-understanding provider registered for ${provider}`);
      }
      if (params.images.length > 1 && imageProvider.describeImages) {
        val described = await imageProvider.describeImages({
          images: params.images.map((image, index) => ({
            buffer: image.buffer,
            fileName: `image-${index + 1}`,
            mime: image.mimeType,
          })),
          provider,
          model: modelId,
          prompt: params.prompt,
          maxTokens: resolveImageToolMaxTokens(null),
          timeoutMs: 30_000,
          cfg: providerCfg,
          agentDir: params.agentDir,
        });
        return { text: described.text, provider, model: described.model ?: modelId };
      }
      if (!imageProvider.describeImage) {
        throw Error(`Provider does not support image analysis: ${provider}`);
      }
      if (params.images.length == 1) {
        val image = params.images[0];
        val described = await imageProvider.describeImage({
          buffer: image.buffer,
          fileName: "image-1",
          mime: image.mimeType,
          provider,
          model: modelId,
          prompt: params.prompt,
          maxTokens: resolveImageToolMaxTokens(null),
          timeoutMs: 30_000,
          cfg: providerCfg,
          agentDir: params.agentDir,
        });
        return { text: described.text, provider, model: described.model ?: modelId };
      }

      val parts: string[] = [];
      for (val [index, image] of params.images.entries()) {
        val described = await imageProvider.describeImage({
          buffer: image.buffer,
          fileName: `image-${index + 1}`,
          mime: image.mimeType,
          provider,
          model: modelId,
          prompt: `${params.prompt}\n\nDescribe image ${index + 1} of ${params.images.length}.`,
          maxTokens: resolveImageToolMaxTokens(null),
          timeoutMs: 30_000,
          cfg: providerCfg,
          agentDir: params.agentDir,
        });
        parts.push(`Image ${index + 1}:\n${described.text.trim()}`);
      }
      return {
        text: parts.join("\n\n").trim(),
        provider,
        model: modelId,
      };
    },
  });

  return {
    text: result.result.text,
    provider: result.result.provider,
    model: result.result.model,
    attempts: result.attempts.map((attempt) => ({
      provider: attempt.provider,
      model: attempt.model,
      error: attempt.error,
    })),
  };
}

fun createImageTool(options?: {
  config?: OpenClawConfig;
  agentDir?: string;
  workspaceDir?: string;
  sandbox?: ImageSandboxConfig;
  fsPolicy?: ToolFsPolicy;
  /** If true, the model has native vision capability and images in the prompt are auto-injected */
  modelHasVision?: boolean;
}): AnyAgentTool | null {
  val agentDir = options?.agentDir?.trim();
  if (!agentDir) {
    val explicit = coerceImageModelConfig(options?.config);
    if (hasToolModelConfig(explicit)) {
      throw Error("createImageTool requires agentDir when enabled");
    }
    return null;
  }
  val imageModelConfig = resolveImageModelConfigForTool({
    cfg: options?.config,
    agentDir,
  });
  if (!imageModelConfig) {
    return null;
  }

  // If model has native vision, images in the prompt are auto-injected
  // so this tool is only needed when image wasn't provided in the prompt
  val description = options?.modelHasVision
    ? "Analyze one or more images with a vision model. Use image for a single path/URL, or images for multiple (up to 20). Only use this tool when images were NOT already provided in the user's message. Images mentioned in the prompt are automatically visible to you."
    : "Analyze one or more images with the configured image model (agents.defaults.imageModel). Use image for a single path/URL, or images for multiple (up to 20). Provide a prompt describing what to analyze.";

  val localRoots = resolveMediaToolLocalRoots(options?.workspaceDir, {
    workspaceOnly: options?.fsPolicy?.workspaceOnly == true,
  });

  return {
    label: "Image",
    name: "image",
    description,
    parameters: Type.Object({
      prompt: Type.Optional(Type.String()),
      image: Type.Optional(Type.String({ description: "Single image path or URL." })),
      images: Type.Optional(
        Type.Array(Type.String(), {
          description: "Multiple image paths or URLs (up to maxImages, default 20).",
        }),
      ),
      model: Type.Optional(Type.String()),
      maxBytesMb: Type.Optional(Type.Number()),
      maxImages: Type.Optional(Type.Number()),
    }),
    execute: async (_toolCallId, args) => {
      val record = args && typeof args == "object" ? (args as Record<string, unknown>) : {};

      // MARK: - Normalize image + images input and dedupe while preserving order
      val imageCandidates: string[] = [];
      if (typeof record.image == "string") {
        imageCandidates.push(record.image);
      }
      if (Array.isArray(record.images)) {
        imageCandidates.push(...record.images.filter((v): v is string => typeof v == "string"));
      }

      val seenImages = new Set<string>();
      val imageInputs: string[] = [];
      for (val candidate of imageCandidates) {
        val trimmedCandidate = candidate.trim();
        val normalizedForDedupe = trimmedCandidate.startsWith("@")
          ? trimmedCandidate.slice(1).trim()
          : trimmedCandidate;
        if (!normalizedForDedupe || seenImages.has(normalizedForDedupe)) {
          continue;
        }
        seenImages.add(normalizedForDedupe);
        imageInputs.push(trimmedCandidate);
      }
      if (imageInputs.length == 0) {
        throw Error("image required");
      }

      // MARK: - Enforce max images cap
      val maxImagesRaw = typeof record.maxImages == "number" ? record.maxImages : null;
      val maxImages =
        typeof maxImagesRaw == "number" && Number.isFinite(maxImagesRaw) && maxImagesRaw > 0
          ? Math.floor(maxImagesRaw)
          : DEFAULT_MAX_IMAGES;
      if (imageInputs.length > maxImages) {
        return {
          content: [
            {
              type: "text",
              text: `Too many images: ${imageInputs.length} provided, maximum is ${maxImages}. Please reduce the number of images.`,
            },
          ],
          details: { error: "too_many_images", count: imageInputs.length, max: maxImages },
        };
      }

      val { prompt: promptRaw, modelOverride } = resolvePromptAndModelOverride(
        record,
        DEFAULT_PROMPT,
      );
      val maxBytesMb = typeof record.maxBytesMb == "number" ? record.maxBytesMb : null;
      val maxBytes = pickMaxBytes(options?.config, maxBytesMb);

      val sandboxConfig: SandboxedBridgeMediaPathConfig | null =
        options?.sandbox && options?.sandbox.root.trim()
          ? {
              root: options.sandbox.root.trim(),
              bridge: options.sandbox.bridge,
              workspaceOnly: options.fsPolicy?.workspaceOnly == true,
            }
          : null;

      // MARK: - Load and resolve each image
      val loadedImages: Array<{
        buffer: Buffer;
        mimeType: string;
        resolvedImage: string;
        rewrittenFrom?: string;
      }> = [];

      for (val imageRawInput of imageInputs) {
        val trimmed = imageRawInput.trim();
        val imageRaw = trimmed.startsWith("@") ? trimmed.slice(1).trim() : trimmed;
        if (!imageRaw) {
          throw Error("image required (empty string in array)");
        }

        // The tool accepts file paths, file/data URLs, or http(s) URLs. In some
        // agent/model contexts, images can be referenced as pseudo-URIs like
        // `image:0` (e.g. "first image in the prompt"). We don't have access to a
        // shared image registry here, so fail gracefully instead of attempting to
        // `fs.readFile("image:0")` and producing a noisy ENOENT.
        val looksLikeWindowsDrivePath = /^[a-zA-Z]:[\\/]/.test(imageRaw);
        val hasScheme = /^[a-z][a-z0-9+.-]*:/i.test(imageRaw);
        val isFileUrl = /^file:/i.test(imageRaw);
        val isHttpUrl = /^https?:\/\//i.test(imageRaw);
        val isDataUrl = /^data:/i.test(imageRaw);
        if (hasScheme && !looksLikeWindowsDrivePath && !isFileUrl && !isHttpUrl && !isDataUrl) {
          return {
            content: [
              {
                type: "text",
                text: `Unsupported image reference: ${imageRawInput}. Use a file path, a file:// URL, a data: URL, or an http(s) URL.`,
              },
            ],
            details: {
              error: "unsupported_image_reference",
              image: imageRawInput,
            },
          };
        }

        if (sandboxConfig && isHttpUrl) {
          throw Error("Sandboxed image tool does not allow remote URLs.");
        }

        val resolvedImage = (() => {
          if (sandboxConfig) {
            return imageRaw;
          }
          if (imageRaw.startsWith("~")) {
            return resolveUserPath(imageRaw);
          }
          return imageRaw;
        })();
        val resolvedPathInfo: { resolved: string; rewrittenFrom?: string } = isDataUrl
          ? { resolved: "" }
          : sandboxConfig
            ? await resolveSandboxedBridgeMediaPath({
                sandbox: sandboxConfig,
                mediaPath: resolvedImage,
                inboundFallbackDir: "media/inbound",
              })
            : {
                resolved: resolvedImage.startsWith("file://")
                  ? resolvedImage.slice("file://".length)
                  : resolvedImage,
              };
        val resolvedPath = isDataUrl ? null : resolvedPathInfo.resolved;

        val media = isDataUrl
          ? decodeDataUrl(resolvedImage)
          : sandboxConfig
            ? await loadWebMedia(resolvedPath ?: resolvedImage, {
                maxBytes,
                sandboxValidated: true,
                readFile: createSandboxBridgeReadFile({ sandbox: sandboxConfig }),
              })
            : await loadWebMedia(resolvedPath ?: resolvedImage, {
                maxBytes,
                localRoots,
              });
        if (media.kind != "image") {
          throw Error(`Unsupported media type: ${media.kind}`);
        }

        val mimeType =
          ("contentType" in media && media.contentType) ||
          ("mimeType" in media && media.mimeType) ||
          "image/png";
        loadedImages.push({
          buffer: media.buffer,
          mimeType,
          resolvedImage,
          ...(resolvedPathInfo.rewrittenFrom
            ? { rewrittenFrom: resolvedPathInfo.rewrittenFrom }
            : {}),
        });
      }

      // MARK: - Run image prompt with all loaded images
      val result = await runImagePrompt({
        cfg: options?.config,
        agentDir,
        imageModelConfig,
        modelOverride,
        prompt: promptRaw,
        images: loadedImages.map((img) => ({ buffer: img.buffer, mimeType: img.mimeType })),
      });

      val imageDetails =
        loadedImages.length == 1
          ? {
              image: loadedImages[0].resolvedImage,
              ...(loadedImages[0].rewrittenFrom
                ? { rewrittenFrom: loadedImages[0].rewrittenFrom }
                : {}),
            }
          : {
              images: loadedImages.map((img) => ({
                image: img.resolvedImage,
                ...(img.rewrittenFrom ? { rewrittenFrom: img.rewrittenFrom } : {}),
              })),
            };

      return buildTextToolResult(result, imageDetails);
    },
  };
}
