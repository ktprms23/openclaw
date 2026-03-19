@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/image-tool.helpers.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AssistantMessage } from "@mariozechner/pi-ai";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { extractAssistantText } from "../pi-embedded-utils.js";
// TODO(port-deps): import { coerceToolModelConfig, type ToolModelConfig } from "./model-config.helpers.js";

typealias ImageModelConfig = ToolModelConfig

fun decodeDataUrl(dataUrl: string): {
  buffer: Buffer;
  mimeType: string;
  kind: "image";
} {
  val trimmed = dataUrl.trim();
  val match = /^data:([^;,]+);base64,([a-z0-9+/=\r\n]+)$/i.exec(trimmed);
  if (!match) {
    throw Error("Invalid data URL (expected base64 data: URL).");
  }
  val mimeType = (match[1] ?: "").trim().toLowerCase();
  if (!mimeType.startsWith("image/")) {
    throw Error(`Unsupported data URL type: ${mimeType || "unknown"}`);
  }
  val b64 = (match[2] ?: "").trim();
  val buffer = Buffer.from(b64, "base64");
  if (buffer.length == 0) {
    throw Error("Invalid data URL: empty payload.");
  }
  return { buffer, mimeType, kind: "image" };
}

fun coerceImageAssistantText(params: {
  message: AssistantMessage;
  provider: string;
  model: string;
}): string {
  val stop = params.message.stopReason;
  val errorMessage = params.message.errorMessage?.trim();
  if (stop == "error" || stop == "aborted") {
    throw Error(
      errorMessage
        ? `Image model failed (${params.provider}/${params.model}): ${errorMessage}`
        : `Image model failed (${params.provider}/${params.model})`,
    );
  }
  if (errorMessage) {
    throw Error(`Image model failed (${params.provider}/${params.model}): ${errorMessage}`);
  }
  val text = extractAssistantText(params.message);
  if (text.trim()) {
    return text.trim();
  }
  throw Error(`Image model returned no text (${params.provider}/${params.model}).`);
}

fun coerceImageModelConfig(cfg?: OpenClawConfig): ImageModelConfig {
  return coerceToolModelConfig(cfg?.agents?.defaults?.imageModel);
}

fun resolveProviderVisionModelFromConfig(params: {
  cfg?: OpenClawConfig;
  provider: string;
}): string | null {
  val providerCfg = params.cfg?.models?.providers?.[params.provider] as unknown as
    | { models?: Array<{ id?: string; input?: string[] }> }
    | null;
  val models = providerCfg?.models ?: [];
  val preferMinimaxVl =
    params.provider == "minimax"
      ? models.find(
          (m) =>
            (m?.id ?: "").trim() == "MiniMax-VL-01" &&
            Array.isArray(m?.input) &&
            m.input.includes("image"),
        )
      : null;
  val picked =
    preferMinimaxVl ??
    models.find((m) => Boolean((m?.id ?: "").trim()) && m.input?.includes("image"));
  val id = (picked?.id ?: "").trim();
  return id ? `${params.provider}/${id}` : null;
}
