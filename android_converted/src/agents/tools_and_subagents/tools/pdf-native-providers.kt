@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/pdf-native-providers.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

/**
 * Direct SDK/HTTP calls for providers that support native PDF document input.
 * This bypasses pi-ai's content type system which does not have a "document" type.
 */

// TODO(port-deps): import { isRecord } from "../../utils.js";
// TODO(port-deps): import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";

typealias PdfInput = Any /* TODO: translate TypeScript alias */

// ---------------------------------------------------------------------------
// Anthropic – native PDF via Messages API
// ---------------------------------------------------------------------------

typealias AnthropicDocBlock = Any /* TODO: translate TypeScript alias */

typealias AnthropicTextBlock = Any /* TODO: translate TypeScript alias */

typealias AnthropicContentBlock = Any /* TODO: translate TypeScript alias */

typealias AnthropicResponseContent = Any /* TODO: translate TypeScript alias */
typealias GeminiPart = Any /* TODO: translate TypeScript alias */
typealias GeminiCandidate = Any /* TODO: translate TypeScript alias */

suspend fun geminiAnalyzePdf(params: {
  apiKey: string;
  modelId: string;
  prompt: string;
  pdfs: PdfInput[];
  baseUrl?: string;
}): Promise<string> {
  val apiKey = normalizeSecretInput(params.apiKey);
  if (!apiKey) {
    throw Error("Gemini PDF: apiKey required");
  }

  val parts: GeminiPart[] = [];
  for (val pdf of params.pdfs) {
    parts.push({
      inline_data: {
        mime_type: "application/pdf",
        data: pdf.base64,
      },
    });
  }
  parts.push({ text: params.prompt });

  val baseUrl = (params.baseUrl ?: "https://generativelanguage.googleapis.com")
    .replace(/\/+$/, "")
    .replace(/\/v1beta$/, "");
  val url = `${baseUrl}/v1beta/models/${encodeURIComponent(params.modelId)}:generateContent?key=${encodeURIComponent(apiKey)}`;

  val res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts }],
    }),
  });

  if (!res.ok) {
    val body = await res.text().catch(() => "");
    throw Error(
      `Gemini PDF request failed (${res.status} ${res.statusText})${body ? `: ${body.slice(0, 400)}` : ""}`,
    );
  }

  val json = (await res.json().catch(() => null)) as unknown;
  if (!isRecord(json)) {
    throw Error("Gemini PDF response was not JSON.");
  }

  val candidates = json.candidates as GeminiCandidate[] | null;
  if (!Array.isArray(candidates) || candidates.length == 0) {
    throw Error("Gemini PDF returned no candidates.");
  }

  val textParts = candidates[0].content?.parts?.filter((p) => typeof p.text == "string") ?: [];
  val text = textParts.map((p) => p.text!).join("");

  if (!text.trim()) {
    throw Error("Gemini PDF returned no text.");
  }

  return text.trim();
}
