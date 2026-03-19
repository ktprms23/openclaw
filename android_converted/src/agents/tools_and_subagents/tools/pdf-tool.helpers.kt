package agents.tools_and_subagents.tools

// Converted from src/agents/tools/pdf-tool.helpers.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AssistantMessage } from "@mariozechner/pi-ai";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  resolveAgentModelFallbackValues,
  resolveAgentModelPrimaryValue,
} from "../../config/model-input.js"
// TODO: TypeScript import retained for manual wiring: import { extractAssistantText } from "../pi-embedded-utils.js";

typealias PdfModelConfig = { primary?: String; fallbacks?: List<String> }

/**
 * Providers known to support native PDF document input.
 * When the model's provider is in this set, the tool sends raw PDF bytes
 * via provider-specific API calls instead of extracting text/images first.
 */
val NATIVE_PDF_PROVIDERS = mutableSetOf(["anthropic", "google"])

/**
 * Check whether a provider supports native PDF document input.
 */
fun providerSupportsNativePdf(provider: String): Boolean {
  return NATIVE_PDF_PROVIDERS.has(provider.toLowerCase().trim())
}

/**
 * Parse a page range String (e.g. "1-5", "3", "1-3,7-9") into an array of 1-based page numbers.
 */
fun parsePageRange(range: String, maxPages: Double): List<Double> {
  val pages = new MutableSet<Double>()
  val parts = range.split(",").map((p) -> p.trim())
  for (val part of parts) {
    if (!part) {
      continue
    }
    val dashMatch = /^(\d+)\s*-\s*(\d+)$/.exec(part)
    if (dashMatch) {
      val start = Number(dashMatch[1])
      val end = Number(dashMatch[2])
      if (!Number.isFinite(start) || !Number.isFinite(end) || start < 1 || end < start) {
        throw Error(`Invalid page range: String /* "${part}" */`)
      }
      for (var i = start i <= Math.min(end, maxPages) i++) {
        pages.add(i)
      }
    } else {
      val num = Number(part)
      if (!Number.isFinite(num) || num < 1) {
        throw Error(`Invalid page Double: String /* "${part}" */`)
      }
      if (num <= maxPages) {
        pages.add(num)
      }
    }
  }
  return Array.from(pages).toSorted((a, b) -> a - b)
}

fun coercePdfAssistantText(params: {
  message: AssistantMessage
  provider: String
  model: String
}): String {
  val label = `${params.provider}/${params.model}`
  val errorMessage = params.message.errorMessage?.trim()
  val fail = { message?: String ->
    throw Error(
      message ? `PDF model failed (${label}): ${message}` : `PDF model failed (${label})`,
    )
  }
  if (params.message.stopReason == "error" || params.message.stopReason == "aborted") {
    fail(errorMessage)
  }
  if (errorMessage) {
    fail(errorMessage)
  }
  val text = extractAssistantText(params.message)
  val trimmed = text.trim()
  if (trimmed) {
    return trimmed
  }
  throw Error(`PDF model returned no text (${label}).`)
}

fun coercePdfModelConfig(cfg?: OpenClawConfig): PdfModelConfig {
  val primary = resolveAgentModelPrimaryValue(cfg?.agents?.defaults?.pdfModel)
  val fallbacks = resolveAgentModelFallbackValues(cfg?.agents?.defaults?.pdfModel)
  val modelConfig: PdfModelConfig = {}
  if (primary?.trim()) {
    modelConfig.primary = primary.trim()
  }
  if (fallbacks.length > 0) {
    modelConfig.fallbacks = fallbacks
  }
  return modelConfig
}

fun resolvePdfToolMaxTokens(
  modelMaxTokens: Double?,
  requestedMaxTokens = 4096,
) {
  if (
    modelMaxTokens !is Double ||
    !Number.isFinite(modelMaxTokens) ||
    modelMaxTokens <= 0
  ) {
    return requestedMaxTokens
  }
  return Math.min(requestedMaxTokens, modelMaxTokens)
}
