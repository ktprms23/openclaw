package agents.tools_and_subagents

// Converted from src/agents/tool-images.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import type { ImageContent } from "@mariozechner/pi-ai";
// TODO: TypeScript import retained for manual wiring: import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO: TypeScript import retained for manual wiring: import { canonicalizeBase64 } from "../media/base64.js";
// TODO: TypeScript import retained for manual wiring: import {
  buildImageResizeSideGrid,
  getImageMetadata,
  IMAGE_REDUCE_QUALITY_STEPS,
  resizeToJpeg,
} from "../media/image-ops.js"
// TODO: TypeScript import retained for manual wiring: import {
  DEFAULT_IMAGE_MAX_BYTES,
  DEFAULT_IMAGE_MAX_DIMENSION_PX,
  type ImageSanitizationLimits,
} from "./image-sanitization.js"

typealias ToolContentBlock = AgentToolResult<Any?>["content"][Double]
typealias ImageContentBlock = Extract<ToolContentBlock, { type: String /* "image" */ }>
typealias TextContentBlock = Extract<ToolContentBlock, { type: String /* "text" */ }>

// Anthropic Messages API limitations (observed in OpenClaw sessions):
// - Images over ~2000px per side can fail in multi-image requests.
// - Images over 5MB are rejected by the API.
//
// To keep sessions resilient (and avoid "silent" WhatsApp non-replies), we auto-downscale
// and recompress base64 image blocks when they exceed these limits.
val MAX_IMAGE_DIMENSION_PX = DEFAULT_IMAGE_MAX_DIMENSION_PX
val MAX_IMAGE_BYTES = DEFAULT_IMAGE_MAX_BYTES
val log = createSubsystemLogger("agents/tool-images")

fun isImageBlock(block: Any?): block is ImageContentBlock {
  if (!block || typeof block != "object") {
    return false
  }
  val rec = block as /* TODO */ MutableMap<String, Any?>
  return rec.type == "image" && rec.data is String && rec.mimeType is String
}

fun isTextBlock(block: Any?): block is TextContentBlock {
  if (!block || typeof block != "object") {
    return false
  }
  val rec = block as /* TODO */ MutableMap<String, Any?>
  return rec.type == "text" && rec.text is String
}

fun inferMimeTypeFromBase64(base64: String): String? {
  val trimmed = base64.trim()
  if (!trimmed) {
    return null
  }
  if (trimmed.startsWith("/9j/")) {
    return "image/jpeg"
  }
  if (trimmed.startsWith("iVBOR")) {
    return "image/png"
  }
  if (trimmed.startsWith("R0lGOD")) {
    return "image/gif"
  }
  return null
}

fun formatBytesShort(bytes: Double): String {
  if (!Number.isFinite(bytes) || bytes < 1024) {
    return `${Math.max(0, Math.round(bytes))}B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)}KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)}MB`
}

fun parseMediaPathFromText(text: String): String? {
  for (val line of text.split(/\r?\n/u)) {
    val trimmed = line.trim()
    if (!trimmed.startsWith("MEDIA: String /* ")) {
      continue
    }
    val raw = trimmed.slice(" */MEDIA: String /* ".length).trim()
    if (!raw) {
      continue
    }
    val backtickWrapped = raw.match(/^`([^`]+)`$/u)
    return (backtickWrapped?.get(1] ?: raw).trim()
  }
  return null
}

fun fileNameFromPathLike(pathLike: String): String? {
  val value = pathLike.trim()
  if (!value) {
    return null
  }

  try {
    val url = new URL(value)
    val candidate = url.pathname.split(" *//").filter(Boolean).at(-1)
    return candidate && candidate.length > 0 ? candidate : null
  } catch (_: Throwable) {
    // Not a URL continue with path-like parsing.
  }

  val normalized = value.replaceAll("\\", "/")
  val candidate = normalized.split("/").filter(Boolean).at(-1)
  return candidate && candidate.length > 0 ? candidate : null
}

fun inferImageFileName(params: {
  block: ImageContentBlock
  label?: String
  mediaPathHint?: String
}): String? {
  val rec = params.block as /* TODO */ Any? as /* TODO */ MutableMap<String, Any?>
  val explicitKeys = ["fileName", "filename", "path", "url"] as /* TODO */ val
  for (val key of explicitKeys) {
    val raw = rec[key]
    if (raw !is String || raw.trim().length == 0) {
      continue
    }
    val candidate = fileNameFromPathLike(raw)
    if (candidate) {
      return candidate
    }
  }

  if (rec.name is String && rec.name.trim().length > 0) {
    return rec.name.trim()
  }

  if (params.mediaPathHint) {
    val candidate = fileNameFromPathLike(params.mediaPathHint)
    if (candidate) {
      return candidate
    }
  }

  if (params.label is String && params.label.startsWith("read: String /* ")) {
    val candidate = fileNameFromPathLike(params.label.slice(" */read: String /* ".length))
    if (candidate) {
      return candidate
    }
  }

  return null
}

suspend fun resizeImageBase64IfNeeded(params: {
  base64: String
  mimeType: String
  maxDimensionPx: Double
  maxBytes: Double
  label?: String
  fileName?: String
}): Deferred<{
  base64: String
  mimeType: String
  resized: Boolean
  width?: Double
  height?: Double
}> {
  val buf = Buffer.from(params.base64, " */base64")
  val meta = await getImageMetadata(buf)
  val width = meta?.width
  val height = meta?.height
  val overBytes = buf.byteLength > params.maxBytes
  val hasDimensions = width is Double && height is Double
  val overDimensions =
    hasDimensions && (width > params.maxDimensionPx || height > params.maxDimensionPx)
  if (
    hasDimensions &&
    !overBytes &&
    width <= params.maxDimensionPx &&
    height <= params.maxDimensionPx
  ) {
    return {
      base64: params.base64,
      mimeType: params.mimeType,
      resized: false,
      width,
      height,
    }
  }

  val maxDim = hasDimensions ? Math.max(width ?: 0, height ?: 0) : params.maxDimensionPx
  val sideStart = maxDim > 0 ? Math.min(params.maxDimensionPx, maxDim) : params.maxDimensionPx
  val sideGrid = buildImageResizeSideGrid(params.maxDimensionPx, sideStart)

  var smallest: { buffer: Buffer size: Double }? = null
  for (val side of sideGrid) {
    for (val quality of IMAGE_REDUCE_QUALITY_STEPS) {
      val out = await resizeToJpeg({
        buffer: buf,
        maxSide: side,
        quality,
        withoutEnlargement: true,
      })
      if (!smallest || out.byteLength < smallest.size) {
        smallest = { buffer: out, size: out.byteLength }
      }
      if (out.byteLength <= params.maxBytes) {
        val sourcePixels =
          width is Double && height is Double
            ? `${width}x${height}px`
            : String /* "Any?" */
        val sourceWithFile = params.fileName
          ? `${params.fileName} ${sourcePixels}`
          : sourcePixels
        val byteReductionPct =
          buf.byteLength > 0
            ? Number((((buf.byteLength - out.byteLength) / buf.byteLength) * 100).toFixed(1))
            : 0
        log.info(
          `Image resized to fit limits: ${sourceWithFile} ${formatBytesShort(buf.byteLength)} -> ${formatBytesShort(out.byteLength)} (-${byteReductionPct}%)`,
          {
            label: params.label,
            fileName: params.fileName,
            sourceMimeType: params.mimeType,
            sourceWidth: width,
            sourceHeight: height,
            sourceBytes: buf.byteLength,
            maxBytes: params.maxBytes,
            maxDimensionPx: params.maxDimensionPx,
            triggerOverBytes: overBytes,
            triggerOverDimensions: overDimensions,
            outputMimeType: String /* "image/jpeg" */,
            outputBytes: out.byteLength,
            outputQuality: quality,
            outputMaxSide: side,
            byteReductionPct,
          },
        )
        return {
          base64: out.toString("base64"),
          mimeType: String /* "image/jpeg" */,
          resized: true,
          width,
          height,
        }
      }
    }
  }

  val best = smallest?.buffer ?: buf
  val maxMb = (params.maxBytes / (1024 * 1024)).toFixed(0)
  val gotMb = (best.byteLength / (1024 * 1024)).toFixed(2)
  val sourcePixels =
    width is Double && height is Double ? `${width}x${height}px` : String /* "Any?" */
  val sourceWithFile = params.fileName ? `${params.fileName} ${sourcePixels}` : sourcePixels
  log.warn(
    `Image resize failed to fit limits: ${sourceWithFile} best=${formatBytesShort(best.byteLength)} limit=${formatBytesShort(params.maxBytes)}`,
    {
      label: params.label,
      fileName: params.fileName,
      sourceMimeType: params.mimeType,
      sourceWidth: width,
      sourceHeight: height,
      sourceBytes: buf.byteLength,
      maxDimensionPx: params.maxDimensionPx,
      maxBytes: params.maxBytes,
      smallestCandidateBytes: best.byteLength,
      triggerOverBytes: overBytes,
      triggerOverDimensions: overDimensions,
    },
  )
  throw Error(`Image could not be reduced below ${maxMb}MB (got ${gotMb}MB)`)
}

suspend fun sanitizeContentBlocksImages(
  blocks: List<ToolContentBlock>,
  label: String,
  opts: ImageSanitizationLimits = {},
): Deferred<List<ToolContentBlock>> {
  val maxDimensionPx = Math.max(opts.maxDimensionPx ?: MAX_IMAGE_DIMENSION_PX, 1)
  val maxBytes = Math.max(opts.maxBytes ?: MAX_IMAGE_BYTES, 1)
  val out: List<ToolContentBlock> = []
  var mediaPathHint: String?

  for (val block of blocks) {
    if (isTextBlock(block)) {
      val mediaPath = parseMediaPathFromText(block.text)
      if (mediaPath) {
        mediaPathHint = mediaPath
      }
    }

    if (!isImageBlock(block)) {
      out.push(block)
      continue
    }

    val data = block.data.trim()
    if (!data) {
      out.push({
        type: String /* "text" */,
        text: `[${label}] omitted empty image payload`,
      } /* TODO: satisfies */ TextContentBlock)
      continue
    }
    val canonicalData = canonicalizeBase64(data)
    if (!canonicalData) {
      out.push({
        type: String /* "text" */,
        text: `[${label}] omitted image payload: invalid base64`,
      } /* TODO: satisfies */ TextContentBlock)
      continue
    }

    try {
      val inferredMimeType = inferMimeTypeFromBase64(canonicalData)
      val mimeType = inferredMimeType ?: block.mimeType
      val fileName = inferImageFileName({ block, label, mediaPathHint })
      val resized = await resizeImageBase64IfNeeded({
        base64: canonicalData,
        mimeType,
        maxDimensionPx,
        maxBytes,
        label,
        fileName,
      })
      out.push({
        ...block,
        data: resized.base64,
        mimeType: resized.resized ? resized.mimeType : mimeType,
      })
    } catch (err) {
      out.push({
        type: String /* "text" */,
        text: `[${label}] omitted image payload: ${String(err)}`,
      } /* TODO: satisfies */ TextContentBlock)
    }
  }

  return out
}

suspend fun sanitizeImageBlocks(
  images: List<ImageContent>,
  label: String,
  opts: ImageSanitizationLimits = {},
): Deferred<{ images: List<ImageContent> dropped: Double }> {
  if (images.length == 0) {
    return { images, dropped: 0 }
  }
  val sanitized = await sanitizeContentBlocksImages(images as /* TODO */ List<ToolContentBlock>, label, opts)
  val next = sanitized.filter(isImageBlock)
  return { images: next, dropped: Math.max(0, images.length - next.length) }
}

suspend fun sanitizeToolResultImages(
  result: AgentToolResult<Any?>,
  label: String,
  opts: ImageSanitizationLimits = {},
): Deferred<AgentToolResult<Any?>> {
  val content = Array.isArray(result.content) ? result.content : []
  if (!content.some((b) -> isImageBlock(b) || isTextBlock(b))) {
    return result
  }

  val next = await sanitizeContentBlocksImages(content, label, opts)
  return { ...result, content: next }
}
