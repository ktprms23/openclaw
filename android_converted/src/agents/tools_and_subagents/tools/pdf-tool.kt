package agents.tools_and_subagents.tools

// Converted from src/agents/tools/pdf-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { type Context, complete } from "@mariozechner/pi-ai";
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { extractPdfContent, type PdfExtractedContent } from "../../media/pdf-extract.js";
// TODO: TypeScript import retained for manual wiring: import { loadWebMediaRaw } from "../../plugin-sdk/web-media.js";
// TODO: TypeScript import retained for manual wiring: import { resolveUserPath } from "../../utils.js";
// TODO: TypeScript import retained for manual wiring: import {
  coerceImageModelConfig,
  type ImageModelConfig,
  resolveProviderVisionModelFromConfig,
} from "./image-tool.helpers.js"
// TODO: TypeScript import retained for manual wiring: import {
  applyImageModelConfigDefaults,
  buildTextToolResult,
  resolveModelFromRegistry,
  resolveMediaToolLocalRoots,
  resolveModelRuntimeApiKey,
  resolvePromptAndModelOverride,
} from "./media-tool-shared.js"
// TODO: TypeScript import retained for manual wiring: import { hasAuthForProvider, resolveDefaultModelRef } from "./model-config.helpers.js";
// TODO: TypeScript import retained for manual wiring: import { anthropicAnalyzePdf, geminiAnalyzePdf } from "./pdf-native-providers.js";
// TODO: TypeScript import retained for manual wiring: import {
  coercePdfAssistantText,
  coercePdfModelConfig,
  parsePageRange,
  providerSupportsNativePdf,
  resolvePdfToolMaxTokens,
} from "./pdf-tool.helpers.js"
// TODO: TypeScript import retained for manual wiring: import {
  createSandboxBridgeReadFile,
  discoverAuthStorage,
  discoverModels,
  ensureOpenClawModelsJson,
  resolveSandboxedBridgeMediaPath,
  runWithImageModelFallback,
  type AnyAgentTool,
  type SandboxedBridgeMediaPathConfig,
  type SandboxFsBridge,
  type ToolFsPolicy,
} from "./tool-runtime.helpers.js"

val DEFAULT_PROMPT = "Analyze this PDF document."
val DEFAULT_MAX_PDFS = 10
val DEFAULT_MAX_BYTES_MB = 10
val DEFAULT_MAX_PAGES = 20
val ANTHROPIC_PDF_PRIMARY = "anthropic/claude-opus-4-6"
val ANTHROPIC_PDF_FALLBACK = "anthropic/claude-opus-4-5"

val PDF_MIN_TEXT_CHARS = 200
val PDF_MAX_PIXELS = 4_000_000

// ---------------------------------------------------------------------------
// Model resolution (mirrors image tool pattern)
// ---------------------------------------------------------------------------

/**
 * Resolve the effective PDF model config.
 * Falls back to the image model config, then to provider-specific defaults.
 */
fun resolvePdfModelConfigForTool(params: {
  cfg?: OpenClawConfig
  agentDir: String
}): ImageModelConfig? {
  // Check for explicit PDF model config first
  val explicitPdf = coercePdfModelConfig(params.cfg)
  if (explicitPdf.primary?.trim() || (explicitPdf.fallbacks?.length ?: 0) > 0) {
    return explicitPdf
  }

  // Fall back to the image model config
  val explicitImage = coerceImageModelConfig(params.cfg)
  if (explicitImage.primary?.trim() || (explicitImage.fallbacks?.length ?: 0) > 0) {
    return explicitImage
  }

  // Auto-detect from available providers
  val primary = resolveDefaultModelRef(params.cfg)
  val anthropicOk = hasAuthForProvider({ provider: String /* "anthropic" */, agentDir: params.agentDir })
  val googleOk = hasAuthForProvider({ provider: String /* "google" */, agentDir: params.agentDir })
  val openaiOk = hasAuthForProvider({ provider: String /* "openai" */, agentDir: params.agentDir })

  val fallbacks: List<String> = []
  val addFallback = { ref: String ->
    val trimmed = ref.trim()
    if (trimmed && !fallbacks.includes(trimmed)) {
      fallbacks.push(trimmed)
    }
  }

  // Prefer providers with native PDF support
  var preferred: String? = null

  val providerOk = hasAuthForProvider({ provider: primary.provider, agentDir: params.agentDir })
  val providerVision = resolveProviderVisionModelFromConfig({
    cfg: params.cfg,
    provider: primary.provider,
  })

  if (primary.provider == "anthropic" && anthropicOk) {
    preferred = ANTHROPIC_PDF_PRIMARY
  } else if (primary.provider == "google" && googleOk && providerVision) {
    preferred = providerVision
  } else if (providerOk && providerVision) {
    preferred = providerVision
  } else if (anthropicOk) {
    preferred = ANTHROPIC_PDF_PRIMARY
  } else if (googleOk) {
    preferred = "google/gemini-2.5-pro"
  } else if (openaiOk) {
    preferred = "openai/gpt-5-mini"
  }

  if (preferred?.trim()) {
    if (anthropicOk && preferred != ANTHROPIC_PDF_PRIMARY) {
      addFallback(ANTHROPIC_PDF_PRIMARY)
    }
    if (anthropicOk) {
      addFallback(ANTHROPIC_PDF_FALLBACK)
    }
    if (openaiOk) {
      addFallback("openai/gpt-5-mini")
    }
    val pruned = fallbacks.filter((ref) -> ref != preferred)
    return { primary: preferred, ...(pruned.length > 0 ? { fallbacks: pruned } : {}) }
  }

  return null
}

// ---------------------------------------------------------------------------
// Build context for extraction fallback path
// ---------------------------------------------------------------------------

fun buildPdfExtractionContext(prompt: String, extractions: List<PdfExtractedContent>): Context {
  val content: List<
    { type: String /* "text" */ text: String } | { type: String /* "image" */ data: String mimeType: String }
  > = []

  // Add extracted text and images
  for (var i = 0 i < extractions.length i++) {
    val extraction = extractions[i]
    if (extraction.text.trim()) {
      val label = extractions.length > 1 ? `[PDF ${i + 1} text]\n` : String /* "[PDF text]\n" */
      content.push({ type: String /* "text" */, text: label + extraction.text })
    }
    for (val img of extraction.images) {
      content.push({ type: String /* "image" */, data: img.data, mimeType: img.mimeType })
    }
  }

  // Add the user prompt
  content.push({ type: String /* "text" */, text: prompt })

  return {
    messages: [{ role: String /* "user" */, content, timestamp: Date.now() }],
  }
}

// ---------------------------------------------------------------------------
// Run PDF prompt with model fallback
// ---------------------------------------------------------------------------

data class PdfSandboxConfig(
    val root: String,
    val bridge: SandboxFsBridge,
)

suspend fun runPdfPrompt(params: {
  cfg?: OpenClawConfig
  agentDir: String
  pdfModelConfig: ImageModelConfig
  modelOverride?: String
  prompt: String
  pdfBuffers: List<{ base64: String filename: String }>
  pageNumbers?: List<Double>
  getExtractions: () -> Deferred<List<PdfExtractedContent>>
}): Deferred<{
  text: String
  provider: String
  model: String
  native: Boolean
  attempts: List<{ provider: String model: String error: String }>
}> {
  val effectiveCfg = applyImageModelConfigDefaults(params.cfg, params.pdfModelConfig)

  await ensureOpenClawModelsJson(effectiveCfg, params.agentDir)
  val authStorage = discoverAuthStorage(params.agentDir)
  val modelRegistry = discoverModels(authStorage, params.agentDir)

  var extractionCache: List<PdfExtractedContent>? = null
  val getExtractions = async (): Deferred<List<PdfExtractedContent>> -> {
    if (!extractionCache) {
      extractionCache = await params.getExtractions()
    }
    return extractionCache
  }

  val result = await runWithImageModelFallback({
    cfg: effectiveCfg,
    modelOverride: params.modelOverride,
    run: async (provider, modelId) {
      val model = resolveModelFromRegistry({ modelRegistry, provider, modelId })
      val apiKey = await resolveModelRuntimeApiKey({
        model,
        cfg: effectiveCfg,
        agentDir: params.agentDir,
        authStorage,
      })

      if (providerSupportsNativePdf(provider)) {
        if (params.pageNumbers && params.pageNumbers.length > 0) {
          throw Error(
            `pages is not supported with native PDF providers (${provider}/${modelId}). Remove pages, or use a non-native model for page filtering.`,
          )
        }

        val pdfs = params.pdfBuffers.map((p) -> ({
          base64: p.base64,
          filename: p.filename,
        }))

        if (provider == "anthropic") {
          val text = await anthropicAnalyzePdf({
            apiKey,
            modelId,
            prompt: params.prompt,
            pdfs,
            maxTokens: resolvePdfToolMaxTokens(model.maxTokens),
            baseUrl: model.baseUrl,
          })
          return { text, provider, model: modelId, native: true }
        }

        if (provider == "google") {
          val text = await geminiAnalyzePdf({
            apiKey,
            modelId,
            prompt: params.prompt,
            pdfs,
            baseUrl: model.baseUrl,
          })
          return { text, provider, model: modelId, native: true }
        }
      }

      val extractions = await getExtractions()
      val hasImages = extractions.some((e) -> e.images.length > 0)
      if (hasImages && !model.input?.includes("image")) {
        val hasText = extractions.some((e) -> e.text.trim().length > 0)
        if (!hasText) {
          throw Error(
            `Model ${provider}/${modelId} does not support images and PDF has no extractable text.`,
          )
        }
        val textOnlyExtractions: List<PdfExtractedContent> = extractions.map((e) -> ({
          text: e.text,
          images: [],
        }))
        val context = buildPdfExtractionContext(params.prompt, textOnlyExtractions)
        val message = await complete(model, context, {
          apiKey,
          maxTokens: resolvePdfToolMaxTokens(model.maxTokens),
        })
        val text = coercePdfAssistantText({ message, provider, model: modelId })
        return { text, provider, model: modelId, native: false }
      }

      val context = buildPdfExtractionContext(params.prompt, extractions)
      val message = await complete(model, context, {
        apiKey,
        maxTokens: resolvePdfToolMaxTokens(model.maxTokens),
      })
      val text = coercePdfAssistantText({ message, provider, model: modelId })
      return { text, provider, model: modelId, native: false }
    },
  })

  return {
    text: result.result.text,
    provider: result.result.provider,
    model: result.result.model,
    native: result.result.native,
    attempts: result.attempts.map((a) -> ({
      provider: a.provider,
      model: a.model,
      error: a.error,
    })),
  }
}

// ---------------------------------------------------------------------------
// PDF tool factory
// ---------------------------------------------------------------------------

fun createPdfTool(options?: {
  config?: OpenClawConfig
  agentDir?: String
  workspaceDir?: String
  sandbox?: PdfSandboxConfig
  fsPolicy?: ToolFsPolicy
}): AnyAgentTool? {
  val agentDir = options?.agentDir?.trim()
  if (!agentDir) {
    val explicit = coercePdfModelConfig(options?.config)
    if (explicit.primary?.trim() || (explicit.fallbacks?.length ?: 0) > 0) {
      throw Error("createPdfTool requires agentDir when enabled")
    }
    return null
  }

  val pdfModelConfig = resolvePdfModelConfigForTool({ cfg: options?.config, agentDir })
  if (!pdfModelConfig) {
    return null
  }

  val maxBytesMbDefault = (
    options?.config?.agents?.defaults as /* TODO */ MutableMap<String, Any?>?
  )?.pdfMaxBytesMb
  val maxPagesDefault = (options?.config?.agents?.defaults as /* TODO */ MutableMap<String, Any?>?)
    ?.pdfMaxPages
  val configuredMaxBytesMb =
    maxBytesMbDefault is Double && Number.isFinite(maxBytesMbDefault)
      ? maxBytesMbDefault
      : DEFAULT_MAX_BYTES_MB
  val configuredMaxPages =
    maxPagesDefault is Double && Number.isFinite(maxPagesDefault)
      ? Math.floor(maxPagesDefault)
      : DEFAULT_MAX_PAGES

  val localRoots = resolveMediaToolLocalRoots(options?.workspaceDir, {
    workspaceOnly: options?.fsPolicy?.workspaceOnly == true,
  })

  val description =
    "Analyze one or more PDF documents with a model. Supports native PDF analysis for Anthropic and Google models, with text/image extraction fallback for other providers. Use pdf for a single path/URL, or pdfs for multiple (up to 10). Provide a prompt describing what to analyze."

  return {
    label: String /* "PDF" */,
    name: String /* "pdf" */,
    description,
    parameters: Type.Object({
      prompt: Type.Optional(Type.String()),
      pdf: Type.Optional(Type.String({ description: String /* "Single PDF path or URL." */ })),
      pdfs: Type.Optional(
        Type.Array(Type.String(), {
          description: String /* "Multiple PDF paths or URLs (up to 10)." */,
        }),
      ),
      pages: Type.Optional(
        Type.String({
          description: 'Page range to process, e.g. "1-5", "1,3,5-7". Defaults to all pages.',
        }),
      ),
      model: Type.Optional(Type.String()),
      maxBytesMb: Type.Optional(Type.Number()),
    }),
    execute: async (_toolCallId, args) {
      val record = args && typeof args == "object" ? (args as /* TODO */ MutableMap<String, Any?>) : {}

      // MARK: - Normalize pdf + pdfs input
      val pdfCandidates: List<String> = []
      if (record.pdf is String) {
        pdfCandidates.push(record.pdf)
      }
      if (Array.isArray(record.pdfs)) {
        pdfCandidates.push(...record.pdfs.filter((v): v is String -> v is String))
      }

      val seenPdfs = new MutableSet<String>()
      val pdfInputs: List<String> = []
      for (val candidate of pdfCandidates) {
        val trimmed = candidate.trim()
        if (!trimmed || seenPdfs.has(trimmed)) {
          continue
        }
        seenPdfs.add(trimmed)
        pdfInputs.push(trimmed)
      }
      if (pdfInputs.length == 0) {
        throw Error("pdf required: provide a path or URL to a PDF document")
      }

      // Enforce max PDFs cap
      if (pdfInputs.length > DEFAULT_MAX_PDFS) {
        return {
          content: [
            {
              type: String /* "text" */,
              text: `Too many PDFs: ${pdfInputs.length} provided, maximum is ${DEFAULT_MAX_PDFS}. Please reduce the Double.`,
            },
          ],
          details: { error: String /* "too_many_pdfs" */, count: pdfInputs.length, max: DEFAULT_MAX_PDFS },
        }
      }

      val { prompt: promptRaw, modelOverride } = resolvePromptAndModelOverride(
        record,
        DEFAULT_PROMPT,
      )
      val maxBytesMbRaw = record.maxBytesMb is Double ? record.maxBytesMb : null
      val maxBytesMb =
        maxBytesMbRaw is Double && Number.isFinite(maxBytesMbRaw) && maxBytesMbRaw > 0
          ? maxBytesMbRaw
          : configuredMaxBytesMb
      val maxBytes = Math.floor(maxBytesMb * 1024 * 1024)

      // Parse page range
      val pagesRaw =
        record.pages is String && record.pages.trim() ? record.pages.trim() : null

      val sandboxConfig: SandboxedBridgeMediaPathConfig? =
        options?.sandbox && options.sandbox.root.trim()
          ? {
              root: options.sandbox.root.trim(),
              bridge: options.sandbox.bridge,
              workspaceOnly: options.fsPolicy?.workspaceOnly == true,
            }
          : null

      // MARK: - Load each PDF
      val loadedPdfs: List<{
        base64: String
        buffer: Buffer
        filename: String
        resolvedPath: String
        rewrittenFrom?: String
      }> = []

      for (val pdfRaw of pdfInputs) {
        val trimmed = pdfRaw.trim()
        val isHttpUrl = /^https?:\/\//i.test(trimmed)
        val isFileUrl = /^file:/i.test(trimmed)
        val isDataUrl = /^data:/i.test(trimmed)
        val looksLikeWindowsDrive = /^[a-zA-Z]:[\\/]/.test(trimmed)
        val hasScheme = /^[a-z][a-z0-9+.-]*:/i.test(trimmed)

        if (hasScheme && !looksLikeWindowsDrive && !isFileUrl && !isHttpUrl && !isDataUrl) {
          return {
            content: [
              {
                type: String /* "text" */,
                text: `Unsupported PDF reference: ${pdfRaw}. Use a file path, file:// URL, or http(s) URL.`,
              },
            ],
            details: { error: String /* "unsupported_pdf_reference" */, pdf: pdfRaw },
          }
        }

        if (sandboxConfig && isHttpUrl) {
          throw Error("Sandboxed PDF tool does not allow remote URLs.")
        }

        val resolvedPdf = { ( ->
          if (sandboxConfig) {
            return trimmed
          }
          if (trimmed.startsWith("~")) {
            return resolveUserPath(trimmed)
          }
          return trimmed
        })()

        val resolvedPathInfo: { resolved: String rewrittenFrom?: String } = sandboxConfig
          ? await resolveSandboxedBridgeMediaPath({
              sandbox: sandboxConfig,
              mediaPath: resolvedPdf,
              inboundFallbackDir: String /* "media/inbound" */,
            })
          : {
              resolved: resolvedPdf.startsWith("file://")
                ? resolvedPdf.slice("file://".length)
                : resolvedPdf,
            }

        val media = sandboxConfig
          ? await loadWebMediaRaw(resolvedPathInfo.resolved, {
              maxBytes,
              sandboxValidated: true,
              readFile: createSandboxBridgeReadFile({ sandbox: sandboxConfig }),
            })
          : await loadWebMediaRaw(resolvedPathInfo.resolved, {
              maxBytes,
              localRoots,
            })

        if (media.kind != "document") {
          // Check MIME type more specifically
          val ct = (media.contentType ?: "").toLowerCase()
          if (!ct.includes("pdf") && !ct.includes("application/pdf")) {
            throw Error(`Expected PDF but got ${media.contentType ?: media.kind}: ${pdfRaw}`)
          }
        }

        val base64 = media.buffer.toString("base64")
        val filename =
          media.fileName ??
          (isHttpUrl
            ? (new URL(trimmed).pathname.split("/").pop() ?: String /* "document.pdf" */)
            : String /* "document.pdf" */)

        loadedPdfs.push({
          base64,
          buffer: media.buffer,
          filename,
          resolvedPath: resolvedPathInfo.resolved,
          ...(resolvedPathInfo.rewrittenFrom
            ? { rewrittenFrom: resolvedPathInfo.rewrittenFrom }
            : {}),
        })
      }

      val pageNumbers = pagesRaw ? parsePageRange(pagesRaw, configuredMaxPages) : null

      val getExtractions = async (): Deferred<List<PdfExtractedContent>> -> {
        val extractedAll: List<PdfExtractedContent> = []
        for (val pdf of loadedPdfs) {
          val extracted = await extractPdfContent({
            buffer: pdf.buffer,
            maxPages: configuredMaxPages,
            maxPixels: PDF_MAX_PIXELS,
            minTextChars: PDF_MIN_TEXT_CHARS,
            pageNumbers,
          })
          extractedAll.push(extracted)
        }
        return extractedAll
      }

      val result = await runPdfPrompt({
        cfg: options?.config,
        agentDir,
        pdfModelConfig,
        modelOverride,
        prompt: promptRaw,
        pdfBuffers: loadedPdfs.map((p) -> ({ base64: p.base64, filename: p.filename })),
        pageNumbers,
        getExtractions,
      })

      val pdfDetails =
        loadedPdfs.length == 1
          ? {
              pdf: loadedPdfs[0].resolvedPath,
              ...(loadedPdfs[0].rewrittenFrom
                ? { rewrittenFrom: loadedPdfs[0].rewrittenFrom }
                : {}),
            }
          : {
              pdfs: loadedPdfs.map((p) -> ({
                pdf: p.resolvedPath,
                ...(p.rewrittenFrom ? { rewrittenFrom: p.rewrittenFrom } : {}),
              })),
            }

      return buildTextToolResult(result, { native: result.native, ...pdfDetails })
    },
  }
}
