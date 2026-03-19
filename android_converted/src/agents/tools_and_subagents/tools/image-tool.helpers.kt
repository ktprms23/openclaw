package agents.tools_and_subagents.tools

// Converted from src/agents/tools/image-tool.helpers.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AssistantMessage } from "@mariozechner/pi-ai";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { extractAssistantText } from "../pi-embedded-utils.js";
// TODO: TypeScript import retained for manual wiring: import { coerceToolModelConfig, type ToolModelConfig } from "./model-config.helpers.js";

typealias ImageModelConfig = ToolModelConfig

fun decodeDataUrl(dataUrl: String): {
  buffer: Buffer
  mimeType: String
  kind: String /* "image" */
} {
  val trimmed = dataUrl.trim()
  val match = /^data:([^,]+)base64,([a-z0-9+/=\r\n]+)$/i.exec(trimmed)
  if (!match) {
    throw Error("Invalid data URL (expected base64 data: URL).")
  }
  val mimeType = (match[1] ?: "").trim().toLowerCase()
  if (!mimeType.startsWith("image/")) {
    throw Error(`Unsupported data URL type: ${mimeType || "Any?"}`)
  }
  val b64 = (match[2] ?: "").trim()
  val buffer = Buffer.from(b64, "base64")
  if (buffer.length == 0) {
    throw Error("Invalid data URL: empty payload.")
  }
  return { buffer, mimeType, kind: String /* "image" */ }
}

fun coerceImageAssistantText(params: {
  message: AssistantMessage
  provider: String
  model: String
}): String {
  val stop = params.message.stopReason
  val errorMessage = params.message.errorMessage?.trim()
  if (stop == "error" || stop == "aborted") {
    throw Error(
      errorMessage
        ? `Image model failed (${params.provider}/${params.model}): ${errorMessage}`
        : `Image model failed (${params.provider}/${params.model})`,
    )
  }
  if (errorMessage) {
    throw Error(`Image model failed (${params.provider}/${params.model}): ${errorMessage}`)
  }
  val text = extractAssistantText(params.message)
  if (text.trim()) {
    return text.trim()
  }
  throw Error(`Image model returned no text (${params.provider}/${params.model}).`)
}

fun coerceImageModelConfig(cfg?: OpenClawConfig): ImageModelConfig {
  return coerceToolModelConfig(cfg?.agents?.defaults?.imageModel)
}

fun resolveProviderVisionModelFromConfig(params: {
  cfg?: OpenClawConfig
  provider: String
}): String? {
  val providerCfg = params.cfg?.models?.providers?.get(params.provider] as /* TODO */ Any? as
    | { models?: List<{ id?: String input?: List<String> }> }
   ?
  val models = providerCfg?.models ?: []
  val preferMinimaxVl =
    params.provider == "minimax"
      ? models.find(
          (m) ->
            (m?.id ?: "").trim() == "MiniMax-VL-01" &&
            Array.isArray(m?.input) &&
            m.input.includes("image"),
        )
      : null
  val picked =
    preferMinimaxVl ??
    models.find((m) -> Boolean((m?.id ?: "").trim()) && m.input?.includes("image"))
  val id = (picked?.id ?: "").trim()
  return id ? `${params.provider}/${id}` : null
}
