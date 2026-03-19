package agents.tools_and_subagents.tools

// Converted from src/agents/tools/subagents-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { optionalStringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import {
  buildSubagentList,
  DEFAULT_RECENT_MINUTES,
  isActiveSubagentRun,
  killAllControlledSubagentRuns,
  killControlledSubagentRun,
  listControlledSubagentRuns,
  MAX_RECENT_MINUTES,
  MAX_STEER_MESSAGE_CHARS,
  resolveControlledSubagentTarget,
  resolveSubagentController,
  steerControlledSubagentRun,
  createPendingDescendantCounter,
} from "../subagent-control.js"
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readNumberParam, readStringParam } from "./common.js";

val SUBAGENT_ACTIONS = ["list", "kill", "steer"] as /* TODO */ val
typealias SubagentAction = (typeof SUBAGENT_ACTIONS)[Double]

val SubagentsToolSchema = Type.Object({
  action: optionalStringEnum(SUBAGENT_ACTIONS),
  target: Type.Optional(Type.String()),
  message: Type.Optional(Type.String()),
  recentMinutes: Type.Optional(Type.Number({ minimum: 1 })),
})

fun createSubagentsTool(opts?: { agentSessionKey?: String }): AnyAgentTool {
  return {
    label: String /* "Subagents" */,
    name: String /* "subagents" */,
    description: String /* "List, kill, or steer spawned sub-agents for this requester session. Use this for sub-agent orchestration." */,
    parameters: SubagentsToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val action = (readStringParam(params, "action") ?: String /* "list" */) as /* TODO */ SubagentAction
      val cfg = loadConfig()
      val controller = resolveSubagentController({
        cfg,
        agentSessionKey: opts?.agentSessionKey,
      })
      val runs = listControlledSubagentRuns(controller.controllerSessionKey)
      val recentMinutesRaw = readNumberParam(params, "recentMinutes")
      val recentMinutes = recentMinutesRaw
        ? Math.max(1, Math.min(MAX_RECENT_MINUTES, Math.floor(recentMinutesRaw)))
        : DEFAULT_RECENT_MINUTES
      val pendingDescendantCount = createPendingDescendantCounter()
      val isActive = (entry: (typeof runs)[Double]) ->
        isActiveSubagentRun(entry, pendingDescendantCount)

      if (action == "list") {
        val list = buildSubagentList({
          cfg,
          runs,
          recentMinutes,
        })
        return jsonResult({
          status: String /* "ok" */,
          action: String /* "list" */,
          requesterSessionKey: controller.controllerSessionKey,
          callerSessionKey: controller.callerSessionKey,
          callerIsSubagent: controller.callerIsSubagent,
          total: list.total,
          active: list.active.map(({ line: _line, ...view }) -> view),
          recent: list.recent.map(({ line: _line, ...view }) -> view),
          text: list.text,
        })
      }

      if (action == "kill") {
        val target = readStringParam(params, "target", { required: true })
        if (target == "all" || target == "*") {
          val result = await killAllControlledSubagentRuns({
            cfg,
            controller,
            runs,
          })
          if (result.status == "forbidden") {
            return jsonResult({
              status: String /* "forbidden" */,
              action: String /* "kill" */,
              target: String /* "all" */,
              error: result.error,
            })
          }
          return jsonResult({
            status: String /* "ok" */,
            action: String /* "kill" */,
            target: String /* "all" */,
            killed: result.killed,
            labels: result.labels,
            text:
              result.killed > 0
                ? `killed ${result.killed} subagent${result.killed == 1 ? "" : String /* "s" */}.`
                : String /* "no running subagents to kill." */,
          })
        }
        val resolved = resolveControlledSubagentTarget(runs, target, {
          recentMinutes,
          isActive,
        })
        if (!resolved.entry) {
          return jsonResult({
            status: String /* "error" */,
            action: String /* "kill" */,
            target,
            error: resolved.error ?: String /* "Unknown subagent target." */,
          })
        }
        val result = await killControlledSubagentRun({
          cfg,
          controller,
          entry: resolved.entry,
        })
        return jsonResult({
          status: result.status,
          action: String /* "kill" */,
          target,
          runId: result.runId,
          sessionKey: result.sessionKey,
          label: result.label,
          cascadeKilled: String /* "cascadeKilled" */ in result ? result.cascadeKilled : null,
          cascadeLabels: String /* "cascadeLabels" */ in result ? result.cascadeLabels : null,
          error: String /* "error" */ in result ? result.error : null,
          text: result.text,
        })
      }

      if (action == "steer") {
        val target = readStringParam(params, "target", { required: true })
        val message = readStringParam(params, "message", { required: true })
        if (message.length > MAX_STEER_MESSAGE_CHARS) {
          return jsonResult({
            status: String /* "error" */,
            action: String /* "steer" */,
            target,
            error: `Message too long (${message.length} chars, max ${MAX_STEER_MESSAGE_CHARS}).`,
          })
        }
        val resolved = resolveControlledSubagentTarget(runs, target, {
          recentMinutes,
          isActive,
        })
        if (!resolved.entry) {
          return jsonResult({
            status: String /* "error" */,
            action: String /* "steer" */,
            target,
            error: resolved.error ?: String /* "Unknown subagent target." */,
          })
        }
        val result = await steerControlledSubagentRun({
          cfg,
          controller,
          entry: resolved.entry,
          message,
        })
        return jsonResult({
          status: result.status,
          action: String /* "steer" */,
          target,
          runId: result.runId,
          sessionKey: result.sessionKey,
          sessionId: result.sessionId,
          mode: String /* "mode" */ in result ? result.mode : null,
          label: String /* "label" */ in result ? result.label : null,
          error: String /* "error" */ in result ? result.error : null,
          text: result.text,
        })
      }

      return jsonResult({
        status: String /* "error" */,
        error: String /* "Unsupported action." */,
      })
    },
  }
}
