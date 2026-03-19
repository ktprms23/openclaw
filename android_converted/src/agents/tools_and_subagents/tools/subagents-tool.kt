@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/subagents-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import { optionalStringEnum } from "../schema/typebox.js";
// TODO(port-deps): import {
// TODO(port-deps): buildSubagentList,
// TODO(port-deps): DEFAULT_RECENT_MINUTES,
// TODO(port-deps): isActiveSubagentRun,
// TODO(port-deps): killAllControlledSubagentRuns,
// TODO(port-deps): killControlledSubagentRun,
// TODO(port-deps): listControlledSubagentRuns,
// TODO(port-deps): MAX_RECENT_MINUTES,
// TODO(port-deps): MAX_STEER_MESSAGE_CHARS,
// TODO(port-deps): resolveControlledSubagentTarget,
// TODO(port-deps): resolveSubagentController,
// TODO(port-deps): steerControlledSubagentRun,
// TODO(port-deps): createPendingDescendantCounter,
// TODO(port-deps): } from "../subagent-control.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readNumberParam, readStringParam } from "./common.js";

val SUBAGENT_ACTIONS = ["list", "kill", "steer"] as const;
typealias SubagentAction = (typeof SUBAGENT_ACTIONS)[number]

val SubagentsToolSchema = Type.Object({
  action: optionalStringEnum(SUBAGENT_ACTIONS),
  target: Type.Optional(Type.String()),
  message: Type.Optional(Type.String()),
  recentMinutes: Type.Optional(Type.Number({ minimum: 1 })),
});

fun createSubagentsTool(opts?: { agentSessionKey?: string }): AnyAgentTool {
  return {
    label: "Subagents",
    name: "subagents",
    description:
      "List, kill, or steer spawned sub-agents for this requester session. Use this for sub-agent orchestration.",
    parameters: SubagentsToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val action = (readStringParam(params, "action") ?: "list") as SubagentAction;
      val cfg = loadConfig();
      val controller = resolveSubagentController({
        cfg,
        agentSessionKey: opts?.agentSessionKey,
      });
      val runs = listControlledSubagentRuns(controller.controllerSessionKey);
      val recentMinutesRaw = readNumberParam(params, "recentMinutes");
      val recentMinutes = recentMinutesRaw
        ? Math.max(1, Math.min(MAX_RECENT_MINUTES, Math.floor(recentMinutesRaw)))
        : DEFAULT_RECENT_MINUTES;
      val pendingDescendantCount = createPendingDescendantCounter();
      val isActive = (entry: (typeof runs)[number]) =>
        isActiveSubagentRun(entry, pendingDescendantCount);

      if (action == "list") {
        val list = buildSubagentList({
          cfg,
          runs,
          recentMinutes,
        });
        return jsonResult({
          status: "ok",
          action: "list",
          requesterSessionKey: controller.controllerSessionKey,
          callerSessionKey: controller.callerSessionKey,
          callerIsSubagent: controller.callerIsSubagent,
          total: list.total,
          active: list.active.map(({ line: _line, ...view }) => view),
          recent: list.recent.map(({ line: _line, ...view }) => view),
          text: list.text,
        });
      }

      if (action == "kill") {
        val target = readStringParam(params, "target", { required: true });
        if (target == "all" || target == "*") {
          val result = await killAllControlledSubagentRuns({
            cfg,
            controller,
            runs,
          });
          if (result.status == "forbidden") {
            return jsonResult({
              status: "forbidden",
              action: "kill",
              target: "all",
              error: result.error,
            });
          }
          return jsonResult({
            status: "ok",
            action: "kill",
            target: "all",
            killed: result.killed,
            labels: result.labels,
            text:
              result.killed > 0
                ? `killed ${result.killed} subagent${result.killed == 1 ? "" : "s"}.`
                : "no running subagents to kill.",
          });
        }
        val resolved = resolveControlledSubagentTarget(runs, target, {
          recentMinutes,
          isActive,
        });
        if (!resolved.entry) {
          return jsonResult({
            status: "error",
            action: "kill",
            target,
            error: resolved.error ?: "Unknown subagent target.",
          });
        }
        val result = await killControlledSubagentRun({
          cfg,
          controller,
          entry: resolved.entry,
        });
        return jsonResult({
          status: result.status,
          action: "kill",
          target,
          runId: result.runId,
          sessionKey: result.sessionKey,
          label: result.label,
          cascadeKilled: "cascadeKilled" in result ? result.cascadeKilled : null,
          cascadeLabels: "cascadeLabels" in result ? result.cascadeLabels : null,
          error: "error" in result ? result.error : null,
          text: result.text,
        });
      }

      if (action == "steer") {
        val target = readStringParam(params, "target", { required: true });
        val message = readStringParam(params, "message", { required: true });
        if (message.length > MAX_STEER_MESSAGE_CHARS) {
          return jsonResult({
            status: "error",
            action: "steer",
            target,
            error: `Message too long (${message.length} chars, max ${MAX_STEER_MESSAGE_CHARS}).`,
          });
        }
        val resolved = resolveControlledSubagentTarget(runs, target, {
          recentMinutes,
          isActive,
        });
        if (!resolved.entry) {
          return jsonResult({
            status: "error",
            action: "steer",
            target,
            error: resolved.error ?: "Unknown subagent target.",
          });
        }
        val result = await steerControlledSubagentRun({
          cfg,
          controller,
          entry: resolved.entry,
          message,
        });
        return jsonResult({
          status: result.status,
          action: "steer",
          target,
          runId: result.runId,
          sessionKey: result.sessionKey,
          sessionId: result.sessionId,
          mode: "mode" in result ? result.mode : null,
          label: "label" in result ? result.label : null,
          error: "error" in result ? result.error : null,
          text: result.text,
        });
      }

      return jsonResult({
        status: "error",
        error: "Unsupported action.",
      });
    },
  };
}
