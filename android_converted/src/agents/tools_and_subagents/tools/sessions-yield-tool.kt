package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-yield-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readStringParam } from "./common.js";

val SessionsYieldToolSchema = Type.Object({
  message: Type.Optional(Type.String()),
})

fun createSessionsYieldTool(opts?: {
  sessionId?: String
  onYield?: (message: String) -> Deferred<Unit> | Unit
}): AnyAgentTool {
  return {
    label: String /* "Yield" */,
    name: String /* "sessions_yield" */,
    description: String /* "End your current turn. Use after spawning subagents to receive their results as /* TODO */ the next message." */,
    parameters: SessionsYieldToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val message = readStringParam(params, "message") || "Turn yielded."
      if (!opts?.sessionId) {
        return jsonResult({ status: String /* "error" */, error: String /* "No session context" */ })
      }
      if (!opts?.onYield) {
        return jsonResult({ status: String /* "error" */, error: String /* "Yield not supported in this context" */ })
      }
      await opts.onYield(message)
      return jsonResult({ status: String /* "yielded" */, message })
    },
  }
}
