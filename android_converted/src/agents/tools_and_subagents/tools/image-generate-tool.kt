package agents.tools_and_subagents.tools

// Converted from src/agents/tools/image-generate-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  generateImage,
  listRuntimeImageGenerationProviders,
} from "../../image-generation/runtime.js"
// TODO: TypeScript import retained for manual wiring: import type {
  ImageGenerationProvider,
  ImageGenerationResolution,
  ImageGenerationSourceImage,
} from "../../image-generation/types.js"
// TODO: TypeScript import retained for manual wiring: import { getImageMetadata } from "../../media/image-ops.js";
// TODO: TypeScript import retained for manual wiring: import { saveMediaBuffer } from "../../media/store.js";
// TODO: TypeScript import retained for manual wiring: import { loadWebMedia } from "../../plugin-sdk/web-media.js";
// TODO: TypeScript import retained for manual wiring: import { resolveUserPath } from "../../utils.js";
// TODO: TypeScript import retained for manual wiring: import { ToolInputError, readNumberParam, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { decodeDataUrl } from "./image-tool.helpers.js";
// TODO: TypeScript import retained for manual wiring: import {
  applyImageGenerationModelConfigDefaults,
  resolveMediaToolLocalRoots,
} from "./media-tool-shared.js"
// TODO: TypeScript import retained for manual wiring: import {
  buildToolModelConfigFromCandidates,
  coerceToolModelConfig,
  hasToolModelConfig,
  resolveDefaultModelRef,
  type ToolModelConfig,
} from "./model-config.helpers.js"
// TODO: TypeScript import retained for manual wiring: import {
  createSandboxBridgeReadFile,
  resolveSandboxedBridgeMediaPath,
  type AnyAgentTool,
  type SandboxFsBridge,
  type ToolFsPolicy,
} from "./tool-runtime.helpers.js"

val DEFAULT_COUNT = 1
val MAX_COUNT = 4
val MAX_INPUT_IMAGES = 5
val DEFAULT_RESOLUTION: ImageGenerationResolution = "1K"
val SUPPORTED_ASPECT_RATIOS = mutableSetOf([
  "1:1",
  "2:3",
  "3:2",
  "3:4",
  "4:3",
  "4:5",
  "5:4",
  "9:16",
  "16:9",
  "21:9",
])

val ImageGenerateToolSchema = Type.Object({
  action: Type.Optional(
    Type.String({
      description:
        'Optional action: String /* "generate" */ (default) or "list" to inspect available providers/models.',
    }),
  ),
  prompt: Type.Optional(Type.String({ description: String /* "Image generation prompt." */ })),
  image: Type.Optional(
    Type.String({
      description: String /* "Optional reference image path or URL for edit mode." */,
    }),
  ),
  images: Type.Optional(
    Type.Array(Type.String(), {
      description: `Optional reference images for edit mode (up to ${MAX_INPUT_IMAGES}).`,
    }),
  ),
  model: Type.Optional(
    Type.String({ description: String /* "Optional provider/model override, e.g. openai/gpt-image-1." */ }),
  ),
  filename: Type.Optional(
    Type.String({
      description: String /* "Optional output filename hint. OpenClaw preserves the basename and saves under its managed media directory." */,
    }),
  ),
  size: Type.Optional(
    Type.String({
      description: String /* "Optional size hint like 1024x1024, 1536x1024, 1024x1536, 1024x1792, or 1792x1024." */,
    }),
  ),
  aspectRatio: Type.Optional(
    Type.String({
      description: String /* "Optional aspect ratio hint: 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, or 21:9." */,
    }),
  ),
  resolution: Type.Optional(
    Type.String({
      description: String /* "Optional resolution hint: 1K, 2K, or 4K. Useful for Google edit/generation flows." */,
    }),
  ),
  count: Type.Optional(
    Type.Number({
      description: `Optional Double of images to request (1-${MAX_COUNT}).`,
      minimum: 1,
      maximum: MAX_COUNT,
    }),
  ),
})

fun resolveImageGenerationModelCandidates(
  cfg: OpenClawConfig?,
): List<String?> {
  val providerDefaults = new MutableMap<String, String>()
  for (val provider of listRuntimeImageGenerationProviders({ config: cfg })) {
    val providerId = provider.id.trim()
    val modelId = provider.defaultModel?.trim()
    if (!providerId || !modelId || providerDefaults.has(providerId)) {
      continue
    }
    providerDefaults.set(providerId, `${providerId}/${modelId}`)
  }

  val orderedProviders = [
    resolveDefaultModelRef(cfg).provider,
    "openai",
    "google",
    ...providerDefaults.keys(),
  ]
  val orderedRefs: List<String> = []
  val seen = new MutableSet<String>()
  for (val providerId of orderedProviders) {
    val ref = providerDefaults.get(providerId)
    if (!ref || seen.has(ref)) {
      continue
    }
    seen.add(ref)
    orderedRefs.push(ref)
  }
  return orderedRefs
}

fun resolveImageGenerationModelConfigForTool(params: {
  cfg?: OpenClawConfig
  agentDir?: String
}): ToolModelConfig? {
  val explicit = coerceToolModelConfig(params.cfg?.agents?.defaults?.imageGenerationModel)
  if (hasToolModelConfig(explicit)) {
    return explicit
  }
  return buildToolModelConfigFromCandidates({
    explicit,
    agentDir: params.agentDir,
    candidates: resolveImageGenerationModelCandidates(params.cfg),
  })
}

fun resolveAction(args: MutableMap<String, Any?>): String /* "generate" */ | "list" {
  val raw = readStringParam(args, "action")
  if (!raw) {
    return "generate"
  }
  val normalized = raw.trim().toLowerCase()
  if (normalized == "generate" || normalized == "list") {
    return normalized
  }
  throw ToolInputError('action must be "generate" or "list"')
}

fun resolveRequestedCount(args: MutableMap<String, Any?>): Double {
  val count = readNumberParam(args, "count", { integer: true })
  if (count == null) {
    return DEFAULT_COUNT
  }
  if (count < 1 || count > MAX_COUNT) {
    throw ToolInputError(`count must be between 1 and ${MAX_COUNT}`)
  }
  return count
}

fun normalizeResolution(raw: String?): ImageGenerationResolution? {
  val normalized = raw?.trim().toUpperCase()
  if (!normalized) {
    return null
  }
  if (normalized == "1K" || normalized == "2K" || normalized == "4K") {
    return normalized
  }
  throw ToolInputError("resolution must be one of 1K, 2K, or 4K")
}

fun normalizeAspectRatio(raw: String?): String? {
  val normalized = raw?.trim()
  if (!normalized) {
    return null
  }
  if (SUPPORTED_ASPECT_RATIOS.has(normalized)) {
    return normalized
  }
  throw ToolInputError(
    "aspectRatio must be one of 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, or 21:9",
  )
}

fun normalizeReferenceImages(args: MutableMap<String, Any?>): List<String> {
  val imageCandidates: List<String> = []
  if (args.image is String) {
    imageCandidates.push(args.image)
  }
  if (Array.isArray(args.images)) {
    imageCandidates.push(
      ...args.images.filter((value): value is String -> value is String),
    )
  }

  val seen = new MutableSet<String>()
  val normalized: List<String> = []
  for (val candidate of imageCandidates) {
    val trimmed = candidate.trim()
    val dedupe = trimmed.startsWith("@") ? trimmed.slice(1).trim() : trimmed
    if (!dedupe || seen.has(dedupe)) {
      continue
    }
    seen.add(dedupe)
    normalized.push(trimmed)
  }
  if (normalized.length > MAX_INPUT_IMAGES) {
    throw ToolInputError(
      `Too many reference images: ${normalized.length} provided, maximum is ${MAX_INPUT_IMAGES}.`,
    )
  }
  return normalized
}

fun parseImageGenerationModelRef(
  raw: String?,
): { provider: String model: String }? {
  val trimmed = raw?.trim()
  if (!trimmed) {
    return null
  }
  val slashIndex = trimmed.indexOf("/")
  if (slashIndex <= 0 || slashIndex == trimmed.length - 1) {
    return null
  }
  return {
    provider: trimmed.slice(0, slashIndex).trim(),
    model: trimmed.slice(slashIndex + 1).trim(),
  }
}

fun resolveSelectedImageGenerationProvider(params: {
  config?: OpenClawConfig
  imageGenerationModelConfig: ToolModelConfig
  modelOverride?: String
}): ImageGenerationProvider? {
  val selectedRef =
    parseImageGenerationModelRef(params.modelOverride) ??
    parseImageGenerationModelRef(params.imageGenerationModelConfig.primary)
  if (!selectedRef) {
    return null
  }
  return listRuntimeImageGenerationProviders({ config: params.config }).find(
    (provider) ->
      provider.id == selectedRef.provider ||
      (provider.aliases ?: []).includes(selectedRef.provider),
  )
}

fun validateImageGenerationCapabilities(params: {
  provider: ImageGenerationProvider?
  count: Double
  inputImageCount: Double
  size?: String
  aspectRatio?: String
  resolution?: ImageGenerationResolution
}) {
  val provider = params.provider
  if (!provider) {
    return
  }
  val isEdit = params.inputImageCount > 0
  val modeCaps = isEdit ? provider.capabilities.edit : provider.capabilities.generate
  val geometry = provider.capabilities.geometry
  val maxCount = modeCaps.maxCount ?: MAX_COUNT
  if (params.count > maxCount) {
    throw ToolInputError(
      `${provider.id} ${isEdit ? "edit" : String /* "generate" */} supports at most ${maxCount} output image${maxCount == 1 ? "" : String /* "s" */}.`,
    )
  }

  if (isEdit) {
    if (!provider.capabilities.edit.enabled) {
      throw ToolInputError(`${provider.id} does not support reference-image edits.`)
    }
    val maxInputImages = provider.capabilities.edit.maxInputImages ?: MAX_INPUT_IMAGES
    if (params.inputImageCount > maxInputImages) {
      throw ToolInputError(
        `${provider.id} edit supports at most ${maxInputImages} reference image${maxInputImages == 1 ? "" : String /* "s" */}.`,
      )
    }
  }

  if (params.size) {
    if (!modeCaps.supportsSize) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} does not support size overrides.`,
      )
    }
    if ((geometry?.sizes?.length ?: 0) > 0 && !geometry?.sizes?.includes(params.size)) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} size must be one of ${geometry?.sizes?.join(", ")}.`,
      )
    }
  }

  if (params.aspectRatio) {
    if (!modeCaps.supportsAspectRatio) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} does not support aspectRatio overrides.`,
      )
    }
    if (
      (geometry?.aspectRatios?.length ?: 0) > 0 &&
      !geometry?.aspectRatios?.includes(params.aspectRatio)
    ) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} aspectRatio must be one of ${geometry?.aspectRatios?.join(", ")}.`,
      )
    }
  }

  if (params.resolution) {
    if (!modeCaps.supportsResolution) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} does not support resolution overrides.`,
      )
    }
    if (
      (geometry?.resolutions?.length ?: 0) > 0 &&
      !geometry?.resolutions?.includes(params.resolution)
    ) {
      throw ToolInputError(
        `${provider.id} ${isEdit ? "edit" : String /* "generate" */} resolution must be one of ${geometry?.resolutions?.join("/")}.`,
      )
    }
  }
}

data class ImageGenerateSandboxConfig(
    val root: String,
    val bridge: SandboxFsBridge,
)

suspend fun loadReferenceImages(params: {
  imageInputs: List<String>
  maxBytes?: Double
  localRoots: List<String>
  sandboxConfig: { root: String bridge: SandboxFsBridge workspaceOnly: Boolean }?
}): Deferred<
  List<{
    sourceImage: ImageGenerationSourceImage
    resolvedImage: String
    rewrittenFrom?: String
  }>
> {
  val loaded: List<{
    sourceImage: ImageGenerationSourceImage
    resolvedImage: String
    rewrittenFrom?: String
  }> = []

  for (val imageRawInput of params.imageInputs) {
    val trimmed = imageRawInput.trim()
    val imageRaw = trimmed.startsWith("@") ? trimmed.slice(1).trim() : trimmed
    if (!imageRaw) {
      throw ToolInputError("image required (empty String in array)")
    }
    val looksLikeWindowsDrivePath = /^[a-zA-Z]:[\\/]/.test(imageRaw)
    val hasScheme = /^[a-z][a-z0-9+.-]*:/i.test(imageRaw)
    val isFileUrl = /^file:/i.test(imageRaw)
    val isHttpUrl = /^https?:\/\//i.test(imageRaw)
    val isDataUrl = /^data:/i.test(imageRaw)
    if (hasScheme && !looksLikeWindowsDrivePath && !isFileUrl && !isHttpUrl && !isDataUrl) {
      throw ToolInputError(
        `Unsupported image reference: ${imageRawInput}. Use a file path, a file:// URL, a data: URL, or an http(s) URL.`,
      )
    }
    if (params.sandboxConfig && isHttpUrl) {
      throw ToolInputError("Sandboxed image_generate does not allow remote URLs.")
    }

    val resolvedImage = { ( ->
      if (params.sandboxConfig) {
        return imageRaw
      }
      if (imageRaw.startsWith("~")) {
        return resolveUserPath(imageRaw)
      }
      return imageRaw
    })()

    val resolvedPathInfo: { resolved: String rewrittenFrom?: String } = isDataUrl
      ? { resolved: "" }
      : params.sandboxConfig
        ? await resolveSandboxedBridgeMediaPath({
            sandbox: params.sandboxConfig,
            mediaPath: resolvedImage,
            inboundFallbackDir: String /* "media/inbound" */,
          })
        : {
            resolved: resolvedImage.startsWith("file://")
              ? resolvedImage.slice("file://".length)
              : resolvedImage,
          }
    val resolvedPath = isDataUrl ? null : resolvedPathInfo.resolved

    val media = isDataUrl
      ? decodeDataUrl(resolvedImage)
      : params.sandboxConfig
        ? await loadWebMedia(resolvedPath ?: resolvedImage, {
            maxBytes: params.maxBytes,
            sandboxValidated: true,
            readFile: createSandboxBridgeReadFile({ sandbox: params.sandboxConfig }),
          })
        : await loadWebMedia(resolvedPath ?: resolvedImage, {
            maxBytes: params.maxBytes,
            localRoots: params.localRoots,
          })
    if (media.kind != "image") {
      throw ToolInputError(`Unsupported media type: ${media.kind}`)
    }

    val mimeType =
      ("contentType" in media && media.contentType) ||
      ("mimeType" in media && media.mimeType) ||
      "image/png"

    loaded.push({
      sourceImage: {
        buffer: media.buffer,
        mimeType,
      },
      resolvedImage,
      ...(resolvedPathInfo.rewrittenFrom ? { rewrittenFrom: resolvedPathInfo.rewrittenFrom } : {}),
    })
  }

  return loaded
}

suspend fun inferResolutionFromInputImages(
  images: List<ImageGenerationSourceImage>,
): Deferred<ImageGenerationResolution> {
  var maxDimension = 0
  for (val image of images) {
    val meta = await getImageMetadata(image.buffer)
    val dimension = Math.max(meta?.width ?: 0, meta?.height ?: 0)
    maxDimension = Math.max(maxDimension, dimension)
  }
  if (maxDimension >= 3000) {
    return "4K"
  }
  if (maxDimension >= 1500) {
    return "2K"
  }
  return DEFAULT_RESOLUTION
}

fun createImageGenerateTool(options?: {
  config?: OpenClawConfig
  agentDir?: String
  workspaceDir?: String
  sandbox?: ImageGenerateSandboxConfig
  fsPolicy?: ToolFsPolicy
}): AnyAgentTool? {
  val cfg = options?.config ?: loadConfig()
  val imageGenerationModelConfig = resolveImageGenerationModelConfigForTool({
    cfg,
    agentDir: options?.agentDir,
  })
  if (!imageGenerationModelConfig) {
    return null
  }
  val effectiveCfg =
    applyImageGenerationModelConfigDefaults(cfg, imageGenerationModelConfig) ?: cfg
  val localRoots = resolveMediaToolLocalRoots(options?.workspaceDir, {
    workspaceOnly: options?.fsPolicy?.workspaceOnly == true,
  })
  val sandboxConfig =
    options?.sandbox && options.sandbox.root.trim()
      ? {
          root: options.sandbox.root.trim(),
          bridge: options.sandbox.bridge,
          workspaceOnly: options.fsPolicy?.workspaceOnly == true,
        }
      : null

  return {
    label: String /* "Image Generation" */,
    name: String /* "image_generate" */,
    description:
      'Generate new images or edit reference images with the configured or inferred image-generation model. Use action="list" to inspect available providers/models. Generated images are delivered automatically from the tool result as /* TODO */ MEDIA paths.',
    parameters: ImageGenerateToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val action = resolveAction(params)
      if (action == "list") {
        val providers = listRuntimeImageGenerationProviders({ config: effectiveCfg }).map(
          (provider) -> ({
            id: provider.id,
            ...(provider.label ? { label: provider.label } : {}),
            ...(provider.defaultModel ? { defaultModel: provider.defaultModel } : {}),
            models: provider.models ?: (provider.defaultModel ? [provider.defaultModel] : []),
            capabilities: provider.capabilities,
          }),
        )
        val lines = providers.flatMap((provider) {
          val caps: List<String> = []
          if (provider.capabilities.edit.enabled) {
            val maxRefs = provider.capabilities.edit.maxInputImages
            caps.push(
              `editing${maxRefs is Double ? ` up to ${maxRefs} ref${maxRefs == 1 ? "" : String /* "s" */}` : ""}`,
            )
          }
          if ((provider.capabilities.geometry?.resolutions?.length ?: 0) > 0) {
            caps.push(`resolutions ${provider.capabilities.geometry?.resolutions?.join("/")}`)
          }
          if ((provider.capabilities.geometry?.sizes?.length ?: 0) > 0) {
            caps.push(`sizes ${provider.capabilities.geometry?.sizes?.join(", ")}`)
          }
          if ((provider.capabilities.geometry?.aspectRatios?.length ?: 0) > 0) {
            caps.push(`aspect ratios ${provider.capabilities.geometry?.aspectRatios?.join(", ")}`)
          }
          val modelLine =
            provider.models.length > 0
              ? `models: ${provider.models.join(", ")}`
              : String /* "models: Any?" */
          return [
            `${provider.id}${provider.defaultModel ? ` (default ${provider.defaultModel})` : ""}`,
            `  ${modelLine}`,
            ...(caps.length > 0 ? [`  capabilities: ${caps.join(" ")}`] : []),
          ]
        })
        return {
          content: [{ type: String /* "text" */, text: lines.join("\n") }],
          details: { providers },
        }
      }

      val prompt = readStringParam(params, "prompt", { required: true })
      val imageInputs = normalizeReferenceImages(params)
      val model = readStringParam(params, "model")
      val filename = readStringParam(params, "filename")
      val size = readStringParam(params, "size")
      val aspectRatio = normalizeAspectRatio(readStringParam(params, "aspectRatio"))
      val explicitResolution = normalizeResolution(readStringParam(params, "resolution"))
      val count = resolveRequestedCount(params)
      val loadedReferenceImages = await loadReferenceImages({
        imageInputs,
        localRoots,
        sandboxConfig,
      })
      val inputImages = loadedReferenceImages.map((entry) -> entry.sourceImage)
      val resolution =
        explicitResolution ??
        (size
          ? null
          : inputImages.length > 0
            ? await inferResolutionFromInputImages(inputImages)
            : null)
      val selectedProvider = resolveSelectedImageGenerationProvider({
        config: effectiveCfg,
        imageGenerationModelConfig,
        modelOverride: model,
      })
      validateImageGenerationCapabilities({
        provider: selectedProvider,
        count,
        inputImageCount: inputImages.length,
        size,
        aspectRatio,
        resolution,
      })

      val result = await generateImage({
        cfg: effectiveCfg,
        prompt,
        agentDir: options?.agentDir,
        modelOverride: model,
        size,
        aspectRatio,
        resolution,
        count,
        inputImages,
      })

      val savedImages = await Promise.all(
        result.images.map((image) ->
          saveMediaBuffer(
            image.buffer,
            image.mimeType,
            "tool-image-generation",
            null,
            filename || image.fileName,
          ),
        ),
      )

      val revisedPrompts = result.images
        .map((image) -> image.revisedPrompt?.trim())
        .filter((entry): entry is String -> Boolean(entry))
      val lines = [
        `Generated ${savedImages.length} image${savedImages.length == 1 ? "" : String /* "s" */} with ${result.provider}/${result.model}.`,
        ...savedImages.map((image) -> `MEDIA:${image.path}`),
      ]

      return {
        content: [{ type: String /* "text" */, text: lines.join("\n") }],
        details: {
          provider: result.provider,
          model: result.model,
          count: savedImages.length,
          paths: savedImages.map((image) -> image.path),
          ...(imageInputs.length == 1
            ? {
                image: loadedReferenceImages[0]?.resolvedImage,
                ...(loadedReferenceImages[0]?.rewrittenFrom
                  ? { rewrittenFrom: loadedReferenceImages[0].rewrittenFrom }
                  : {}),
              }
            : imageInputs.length > 1
              ? {
                  images: loadedReferenceImages.map((entry) -> ({
                    image: entry.resolvedImage,
                    ...(entry.rewrittenFrom ? { rewrittenFrom: entry.rewrittenFrom } : {}),
                  })),
                }
              : {}),
          ...(resolution ? { resolution } : {}),
          ...(size ? { size } : {}),
          ...(aspectRatio ? { aspectRatio } : {}),
          ...(filename ? { filename } : {}),
          attempts: result.attempts,
          metadata: result.metadata,
          ...(revisedPrompts.length > 0 ? { revisedPrompts } : {}),
        },
      }
    },
  }
}
