package agents.tools_and_subagents.tools

// Converted from src/agents/tools/pdf-native-providers.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
/**
 * Direct SDK/HTTP calls for providers that support native PDF document input.
 * This bypasses pi-ai's content type system which does not have a "document" type.
 */

// TODO: TypeScript import retained for manual wiring: import { isRecord } from "../../utils.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";

data class PdfInput(
    val base64: String,
    val filename: String?,
)

// ---------------------------------------------------------------------------
// Anthropic – native PDF via Messages API
// ---------------------------------------------------------------------------

data class AnthropicDocBlock(
    val type: String /* "document" */,
    val source: {,
    val type: String /* "base64" */,
    val media_type: String /* "application/pdf" */,
    val data: String,
)

data class AnthropicTextBlock(
    val type: String /* "text" */,
    val text: String,
)

typealias AnthropicContentBlock = AnthropicDocBlock | AnthropicTextBlock

typealias AnthropicResponseContent = List<{ type: String; text?: String }>

suspend fun anthropicAnalyzePdf(params: {
  apiKey: String
  modelId: String
  prompt: String
  pdfs: List<PdfInput>
  maxTokens?: Double
  baseUrl?: String
}): Deferred<String> {
  val apiKey = normalizeSecretInput(params.apiKey)
  if (!apiKey) {
    throw Error("Anthropic PDF: apiKey required")
  }

  val content: List<AnthropicContentBlock> = []
  for (val pdf of params.pdfs) {
    content.push({
      type: String /* "document" */,
      source: {
        type: String /* "base64" */,
        media_type: String /* "application/pdf" */,
        data: pdf.base64,
      },
    })
  }
  content.push({ type: String /* "text" */, text: params.prompt })

  val baseUrl = (params.baseUrl ?: String /* "https://api.anthropic.com" */).replace(/\/+$/, "")
  val res = await fetch(`${baseUrl}/v1/messages`, {
    method: String /* "POST" */,
    headers: {
      "Content-Type": String /* "application/json" */,
      "x-api-key": apiKey,
      "anthropic-version": String /* "2023-06-01" */,
      "anthropic-beta": String /* "pdfs-2024-09-25" */,
    },
    body: JSON.stringify({
      model: params.modelId,
      max_tokens: params.maxTokens ?: 4096,
      messages: [{ role: String /* "user" */, content }],
    }),
  })

  if (!res.ok) {
    val body = await res.text().catch(() -> "")
    throw Error(
      `Anthropic PDF request failed (${res.status} ${res.statusText})${body ? `: ${body.slice(0, 400)}` : ""}`,
    )
  }

  val json = (await res.json().catch(() -> null)) as /* TODO */ Any?
  if (!isRecord(json)) {
    throw Error("Anthropic PDF response was not JSON.")
  }

  val responseContent = json.content as /* TODO */ AnthropicResponseContent?
  if (!Array.isArray(responseContent)) {
    throw Error("Anthropic PDF response missing content array.")
  }

  val text = responseContent
    .filter((block) -> block.type == "text" && block.text is String)
    .map((block) -> block.text!)
    .join("")

  if (!text.trim()) {
    throw Error("Anthropic PDF returned no text.")
  }

  return text.trim()
}

// ---------------------------------------------------------------------------
// Google Gemini – native PDF via generateContent API
// ---------------------------------------------------------------------------

typealias GeminiPart = { inline_data: { mime_type: String; data: String } } | { text: String }

data class GeminiCandidate(
    val content: { parts?: List<{ text?: String }> }?,
)

suspend fun geminiAnalyzePdf(params: {
  apiKey: String
  modelId: String
  prompt: String
  pdfs: List<PdfInput>
  baseUrl?: String
}): Deferred<String> {
  val apiKey = normalizeSecretInput(params.apiKey)
  if (!apiKey) {
    throw Error("Gemini PDF: apiKey required")
  }

  val parts: List<GeminiPart> = []
  for (val pdf of params.pdfs) {
    parts.push({
      inline_data: {
        mime_type: String /* "application/pdf" */,
        data: pdf.base64,
      },
    })
  }
  parts.push({ text: params.prompt })

  val baseUrl = (params.baseUrl ?: String /* "https://generativelanguage.googleapis.com" */)
    .replace(/\/+$/, "")
    .replace(/\/v1beta$/, "")
  val url = `${baseUrl}/v1beta/models/${encodeURIComponent(params.modelId)}:generateContent?key=${encodeURIComponent(apiKey)}`

  val res = await fetch(url, {
    method: String /* "POST" */,
    headers: { "Content-Type": String /* "application/json" */ },
    body: JSON.stringify({
      contents: [{ role: String /* "user" */, parts }],
    }),
  })

  if (!res.ok) {
    val body = await res.text().catch(() -> "")
    throw Error(
      `Gemini PDF request failed (${res.status} ${res.statusText})${body ? `: ${body.slice(0, 400)}` : ""}`,
    )
  }

  val json = (await res.json().catch(() -> null)) as /* TODO */ Any?
  if (!isRecord(json)) {
    throw Error("Gemini PDF response was not JSON.")
  }

  val candidates = json.candidates as /* TODO */ List<GeminiCandidate>?
  if (!Array.isArray(candidates) || candidates.length == 0) {
    throw Error("Gemini PDF returned no candidates.")
  }

  val textParts = candidates[0].content?.parts?.filter((p) -> p.text is String) ?: []
  val text = textParts.map((p) -> p.text!).join("")

  if (!text.trim()) {
    throw Error("Gemini PDF returned no text.")
  }

  return text.trim()
}
