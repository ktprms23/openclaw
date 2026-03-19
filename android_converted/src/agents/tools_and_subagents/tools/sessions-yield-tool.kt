@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-yield-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readStringParam } from "./common.js";

val SessionsYieldToolSchema = Type.Object({
  message: Type.Optional(Type.String()),
});

fun createSessionsYieldTool(opts?: {
  sessionId?: string;
  onYield?: (message: string) => Promise<void> | void;
}): AnyAgentTool {
  return {
    label: "Yield",
    name: "sessions_yield",
    description:
      "End your current turn. Use after spawning subagents to receive their results as the next message.",
    parameters: SessionsYieldToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val message = readStringParam(params, "message") || "Turn yielded.";
      if (!opts?.sessionId) {
        return jsonResult({ status: "error", error: "No session context" });
      }
      if (!opts?.onYield) {
        return jsonResult({ status: "error", error: "Yield not supported in this context" });
      }
      await opts.onYield(message);
      return jsonResult({ status: "yielded", message });
    },
  };
}
