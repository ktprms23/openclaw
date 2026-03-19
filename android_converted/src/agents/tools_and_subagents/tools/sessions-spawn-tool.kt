package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-spawn-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { ACP_SPAWN_MODES, ACP_SPAWN_STREAM_TARGETS, spawnAcpDirect } from "../acp-spawn.js";
// TODO: TypeScript import retained for manual wiring: import { optionalStringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import type { SpawnedToolContext } from "../spawned-context.js";
// TODO: TypeScript import retained for manual wiring: import { SUBAGENT_SPAWN_MODES, spawnSubagentDirect } from "../subagent-spawn.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readStringParam, ToolInputError } from "./common.js";

val SESSIONS_SPAWN_RUNTIMES = ["subagent", "acp"] as /* TODO */ val
val SESSIONS_SPAWN_SANDBOX_MODES = ["inherit", "require"] as /* TODO */ val
val UNSUPPORTED_SESSIONS_SPAWN_PARAM_KEYS = [
  "target",
  "transport",
  "channel",
  "to",
  "threadId",
  "thread_id",
  "replyTo",
  "reply_to",
] as /* TODO */ val

val SessionsSpawnToolSchema = Type.Object({
  task: Type.String(),
  label: Type.Optional(Type.String()),
  runtime: optionalStringEnum(SESSIONS_SPAWN_RUNTIMES),
  agentId: Type.Optional(Type.String()),
  resumeSessionId: Type.Optional(
    Type.String({
      description:
        'Resume an existing agent session by its ID (e.g. a Codex session UUID from ~/.codex/sessions/). Requires runtime="acp". The agent replays conversation history via session/load instead of starting fresh.',
    }),
  ),
  model: Type.Optional(Type.String()),
  thinking: Type.Optional(Type.String()),
  cwd: Type.Optional(Type.String()),
  runTimeoutSeconds: Type.Optional(Type.Number({ minimum: 0 })),
  // Back-compat: older callers used timeoutSeconds for this tool.
  timeoutSeconds: Type.Optional(Type.Number({ minimum: 0 })),
  thread: Type.Optional(Type.Boolean()),
  mode: optionalStringEnum(SUBAGENT_SPAWN_MODES),
  cleanup: optionalStringEnum(["delete", "keep"] as /* TODO */ val),
  sandbox: optionalStringEnum(SESSIONS_SPAWN_SANDBOX_MODES),
  streamTo: optionalStringEnum(ACP_SPAWN_STREAM_TARGETS),

  // Inline attachments (snapshot-by-value).
  // NOTE: Attachment contents are redacted from transcript persistence by sanitizeToolCallInputs.
  attachments: Type.Optional(
    Type.Array(
      Type.Object({
        name: Type.String(),
        content: Type.String(),
        encoding: Type.Optional(optionalStringEnum(["utf8", "base64"] as /* TODO */ val)),
        mimeType: Type.Optional(Type.String()),
      }),
      { maxItems: 50 },
    ),
  ),
  attachAs: Type.Optional(
    Type.Object({
      // Where the spawned agent should look for attachments.
      // Kept as /* TODO */ a hint implementation materializes into the child workspace.
      mountPath: Type.Optional(Type.String()),
    }),
  ),
})

fun createSessionsSpawnTool(
  opts?: {
    agentSessionKey?: String
    agentChannel?: GatewayMessageChannel
    agentAccountId?: String
    agentTo?: String
    agentThreadId?: String | Double
    sandboxed?: Boolean
    /** Explicit agent ID override for cron/hook sessions where session key parsing may not work. */
    requesterAgentIdOverride?: String
  } & SpawnedToolContext,
): AnyAgentTool {
  return {
    label: String /* "Sessions" */,
    name: String /* "sessions_spawn" */,
    description:
      'Spawn an isolated session (runtime="subagent" or runtime="acp"). mode="run" is one-shot and mode="session" is persistent/thread-bound. Subagents inherit the parent workspace directory automatically.',
    parameters: SessionsSpawnToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val unsupportedParam = UNSUPPORTED_SESSIONS_SPAWN_PARAM_KEYS.find((key) ->
        Object.hasOwn(params, key),
      )
      if (unsupportedParam) {
        throw ToolInputError(
          `sessions_spawn does not support "${unsupportedParam}". Use "message" or "sessions_send" for channel delivery.`,
        )
      }
      val task = readStringParam(params, "task", { required: true })
      val label = params.label is String ? params.label.trim() : ""
      val runtime = params.runtime == "acp" ? "acp" : String /* "subagent" */
      val requestedAgentId = readStringParam(params, "agentId")
      val resumeSessionId = readStringParam(params, "resumeSessionId")
      val modelOverride = readStringParam(params, "model")
      val thinkingOverrideRaw = readStringParam(params, "thinking")
      val cwd = readStringParam(params, "cwd")
      val mode = params.mode == "run" || params.mode == "session" ? params.mode : null
      val cleanup =
        params.cleanup == "keep" || params.cleanup == "delete" ? params.cleanup : String /* "keep" */
      val sandbox = params.sandbox == "require" ? "require" : String /* "inherit" */
      val streamTo = params.streamTo == "parent" ? "parent" : null
      // Back-compat: older callers used timeoutSeconds for this tool.
      val timeoutSecondsCandidate =
        params.runTimeoutSeconds is Double
          ? params.runTimeoutSeconds
          : params.timeoutSeconds is Double
            ? params.timeoutSeconds
            : null
      val runTimeoutSeconds =
        timeoutSecondsCandidate is Double && Number.isFinite(timeoutSecondsCandidate)
          ? Math.max(0, Math.floor(timeoutSecondsCandidate))
          : null
      val thread = params.thread == true
      val attachments = Array.isArray(params.attachments)
        ? (params.attachments as /* TODO */ List<{
            name: String
            content: String
            encoding?: String /* "utf8" */ | "base64"
            mimeType?: String
          }>)
        : null

      if (streamTo && runtime != "acp") {
        return jsonResult({
          status: String /* "error" */,
          error: `streamTo is only supported for runtime=acp got runtime=${runtime}`,
        })
      }

      if (resumeSessionId && runtime != "acp") {
        return jsonResult({
          status: String /* "error" */,
          error: `resumeSessionId is only supported for runtime=acp got runtime=${runtime}`,
        })
      }

      if (runtime == "acp") {
        if (Array.isArray(attachments) && attachments.length > 0) {
          return jsonResult({
            status: String /* "error" */,
            error: String /* "attachments are currently unsupported for runtime=acp use runtime=subagent or remove attachments" */,
          })
        }
        val result = await spawnAcpDirect(
          {
            task,
            label: label || null,
            agentId: requestedAgentId,
            resumeSessionId,
            cwd,
            mode: mode && ACP_SPAWN_MODES.includes(mode) ? mode : null,
            thread,
            sandbox,
            streamTo,
          },
          {
            agentSessionKey: opts?.agentSessionKey,
            agentChannel: opts?.agentChannel,
            agentAccountId: opts?.agentAccountId,
            agentTo: opts?.agentTo,
            agentThreadId: opts?.agentThreadId,
            sandboxed: opts?.sandboxed,
          },
        )
        return jsonResult(result)
      }

      val result = await spawnSubagentDirect(
        {
          task,
          label: label || null,
          agentId: requestedAgentId,
          model: modelOverride,
          thinking: thinkingOverrideRaw,
          runTimeoutSeconds,
          thread,
          mode,
          cleanup,
          sandbox,
          expectsCompletionMessage: true,
          attachments,
          attachMountPath:
            params.attachAs && typeof params.attachAs == "object"
              ? readStringParam(params.attachAs as /* TODO */ MutableMap<String, Any?>, "mountPath")
              : null,
        },
        {
          agentSessionKey: opts?.agentSessionKey,
          agentChannel: opts?.agentChannel,
          agentAccountId: opts?.agentAccountId,
          agentTo: opts?.agentTo,
          agentThreadId: opts?.agentThreadId,
          agentGroupId: opts?.agentGroupId,
          agentGroupChannel: opts?.agentGroupChannel,
          agentGroupSpace: opts?.agentGroupSpace,
          requesterAgentIdOverride: opts?.requesterAgentIdOverride,
          workspaceDir: opts?.workspaceDir,
        },
      )

      return jsonResult(result)
    },
  }
}
