@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/tts-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { SILENT_REPLY_TOKEN } from "../../auto-reply/tokens.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import { textToSpeech } from "../../tts/tts.js";
// TODO(port-deps): import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { readStringParam } from "./common.js";

val TtsToolSchema = Type.Object({
  text: Type.String({ description: "Text to convert to speech." }),
  channel: Type.Optional(
    Type.String({ description: "Optional channel id to pick output format (e.g. telegram)." }),
  ),
});

fun createTtsTool(opts?: {
  config?: OpenClawConfig;
  agentChannel?: GatewayMessageChannel;
}): AnyAgentTool {
  return {
    label: "TTS",
    name: "tts",
    description: `Convert text to speech. Audio is delivered automatically from the tool result — reply with ${SILENT_REPLY_TOKEN} after a successful call to avoid duplicate messages.`,
    parameters: TtsToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val text = readStringParam(params, "text", { required: true });
      val channel = readStringParam(params, "channel");
      val cfg = opts?.config ?: loadConfig();
      val result = await textToSpeech({
        text,
        cfg,
        channel: channel ?: opts?.agentChannel,
      });

      if (result.success && result.audioPath) {
        val lines: string[] = [];
        // Tag Telegram Opus output as a voice bubble instead of a file attachment.
        if (result.voiceCompatible) {
          lines.push("[[audio_as_voice]]");
        }
        lines.push(`MEDIA:${result.audioPath}`);
        return {
          content: [{ type: "text", text: lines.join("\n") }],
          details: { audioPath: result.audioPath, provider: result.provider },
        };
      }

      return {
        content: [
          {
            type: "text",
            text: result.error ?: "TTS conversion failed",
          },
        ],
        details: { error: result.error },
      };
    },
  };
}
