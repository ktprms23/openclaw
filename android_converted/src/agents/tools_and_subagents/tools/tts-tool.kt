package agents.tools_and_subagents.tools

// Converted from src/agents/tools/tts-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { SILENT_REPLY_TOKEN } from "../../auto-reply/tokens.js";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { textToSpeech } from "../../tts/tts.js";
// TODO: TypeScript import retained for manual wiring: import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { readStringParam } from "./common.js";

val TtsToolSchema = Type.Object({
  text: Type.String({ description: String /* "Text to convert to speech." */ }),
  channel: Type.Optional(
    Type.String({ description: String /* "Optional channel id to pick output format (e.g. telegram)." */ }),
  ),
})

fun createTtsTool(opts?: {
  config?: OpenClawConfig
  agentChannel?: GatewayMessageChannel
}): AnyAgentTool {
  return {
    label: String /* "TTS" */,
    name: String /* "tts" */,
    description: `Convert text to speech. Audio is delivered automatically from the tool result — reply with ${SILENT_REPLY_TOKEN} after a successful call to avoid duplicate messages.`,
    parameters: TtsToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val text = readStringParam(params, "text", { required: true })
      val channel = readStringParam(params, "channel")
      val cfg = opts?.config ?: loadConfig()
      val result = await textToSpeech({
        text,
        cfg,
        channel: channel ?: opts?.agentChannel,
      })

      if (result.success && result.audioPath) {
        val lines: List<String> = []
        // Tag Telegram Opus output as /* TODO */ a voice bubble instead of a file attachment.
        if (result.voiceCompatible) {
          lines.push("[[audio_as_voice]]")
        }
        lines.push(`MEDIA:${result.audioPath}`)
        return {
          content: [{ type: String /* "text" */, text: lines.join("\n") }],
          details: { audioPath: result.audioPath, provider: result.provider },
        }
      }

      return {
        content: [
          {
            type: String /* "text" */,
            text: result.error ?: String /* "TTS conversion failed" */,
          },
        ],
        details: { error: result.error },
      }
    },
  }
}
