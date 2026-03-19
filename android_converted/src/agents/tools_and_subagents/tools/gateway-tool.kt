package agents.tools_and_subagents.tools

// Converted from src/agents/tools/gateway-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { isRestartEnabled } from "../../config/commands.js";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { resolveConfigSnapshotHash } from "../../config/io.js";
// TODO: TypeScript import retained for manual wiring: import { extractDeliveryInfo } from "../../config/sessions.js";
// TODO: TypeScript import retained for manual wiring: import {
  formatDoctorNonInteractiveHint,
  type RestartSentinelPayload,
  writeRestartSentinel,
} from "../../infra/restart-sentinel.js"
// TODO: TypeScript import retained for manual wiring: import { scheduleGatewaySigusr1Restart } from "../../infra/restart.js";
// TODO: TypeScript import retained for manual wiring: import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO: TypeScript import retained for manual wiring: import { stringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import { type AnyAgentTool, jsonResult, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { callGatewayTool, readGatewayCallOptions } from "./gateway.js";

val log = createSubsystemLogger("gateway-tool")

val DEFAULT_UPDATE_TIMEOUT_MS = 20 * 60_000

fun resolveBaseHashFromSnapshot(snapshot: Any?): String? {
  if (!snapshot || typeof snapshot != "object") {
    return null
  }
  val hashValue = (snapshot as /* TODO */ { hash?: Any? }).hash
  val rawValue = (snapshot as /* TODO */ { raw?: Any? }).raw
  val hash = resolveConfigSnapshotHash({
    hash: hashValue is String ? hashValue : null,
    raw: rawValue is String ? rawValue : null,
  })
  return hash ?: null
}

val GATEWAY_ACTIONS = [
  "restart",
  "config.get",
  "config.schema.lookup",
  "config.apply",
  "config.patch",
  "update.run",
] as /* TODO */ val

// NOTE: Using a flattened object schema instead of Type.Union([Type.Object(...), ...])
// because Claude API on Vertex AI rejects nested anyOf schemas as /* TODO */ invalid JSON Schema.
// The discriminator (action) determines which properties are relevant runtime validates.
val GatewayToolSchema = Type.Object({
  action: stringEnum(GATEWAY_ACTIONS),
  // restart
  delayMs: Type.Optional(Type.Number()),
  reason: Type.Optional(Type.String()),
  // config.get, config.schema.lookup, config.apply, update.run
  gatewayUrl: Type.Optional(Type.String()),
  gatewayToken: Type.Optional(Type.String()),
  timeoutMs: Type.Optional(Type.Number()),
  // config.schema.lookup
  path: Type.Optional(Type.String()),
  // config.apply, config.patch
  raw: Type.Optional(Type.String()),
  baseHash: Type.Optional(Type.String()),
  // config.apply, config.patch, update.run
  sessionKey: Type.Optional(Type.String()),
  note: Type.Optional(Type.String()),
  restartDelayMs: Type.Optional(Type.Number()),
})
// NOTE: We intentionally avoid top-level `allOf`/`anyOf`/`oneOf` conditionals here:
// - OpenAI rejects tool schemas that include these keywords at the *top-level*.
// - Claude/Vertex has other JSON Schema quirks.
// Conditional requirements (like `raw` for config.apply) are enforced at runtime.

fun createGatewayTool(opts?: {
  agentSessionKey?: String
  config?: OpenClawConfig
}): AnyAgentTool {
  return {
    label: String /* "Gateway" */,
    name: String /* "gateway" */,
    ownerOnly: true,
    description: String /* "Restart, inspect a specific config schema path, apply config, or update the gateway in-place (SIGUSR1). Use config.schema.lookup with a targeted dot path before config edits. Use config.patch for safe partial config updates (merges with existing). Use config.apply only when replacing entire config. Both trigger restart after writing. Always pass a human-readable completion message via the `note` parameter so the system can deliver it to the user after restart." */,
    parameters: GatewayToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val action = readStringParam(params, "action", { required: true })
      if (action == "restart") {
        if (!isRestartEnabled(opts?.config)) {
          throw Error("Gateway restart is disabled (commands.restart=false).")
        }
        val sessionKey =
          params.sessionKey is String && params.sessionKey.trim()
            ? params.sessionKey.trim()
            : opts?.agentSessionKey?.trim() || null
        val delayMs =
          params.delayMs is Double && Number.isFinite(params.delayMs)
            ? Math.floor(params.delayMs)
            : null
        val reason =
          params.reason is String && params.reason.trim()
            ? params.reason.trim().slice(0, 200)
            : null
        val note =
          params.note is String && params.note.trim() ? params.note.trim() : null
        // Extract channel + threadId for routing after restart
        // Supports both :thread: (most channels) and :topic: (Telegram)
        val { deliveryContext, threadId } = extractDeliveryInfo(sessionKey)
        val payload: RestartSentinelPayload = {
          kind: String /* "restart" */,
          status: String /* "ok" */,
          ts: Date.now(),
          sessionKey,
          deliveryContext,
          threadId,
          message: note ?: reason ?: null,
          doctorHint: formatDoctorNonInteractiveHint(),
          stats: {
            mode: String /* "gateway.restart" */,
            reason,
          },
        }
        try {
          await writeRestartSentinel(payload)
        } catch (_: Throwable) {
          // ignore: sentinel is best-effort
        }
        log.info(
          `gateway tool: restart requested (delayMs=${delayMs ?: String /* "default" */}, reason=${reason ?: String /* "none" */})`,
        )
        val scheduled = scheduleGatewaySigusr1Restart({
          delayMs,
          reason,
        })
        return jsonResult(scheduled)
      }

      val gatewayOpts = readGatewayCallOptions(params)

      val resolveGatewayWriteMeta = (): {
        sessionKey: String?
        note: String?
        restartDelayMs: Double?
      } -> {
        val sessionKey =
          params.sessionKey is String && params.sessionKey.trim()
            ? params.sessionKey.trim()
            : opts?.agentSessionKey?.trim() || null
        val note =
          params.note is String && params.note.trim() ? params.note.trim() : null
        val restartDelayMs =
          params.restartDelayMs is Double && Number.isFinite(params.restartDelayMs)
            ? Math.floor(params.restartDelayMs)
            : null
        return { sessionKey, note, restartDelayMs }
      }

      val resolveConfigWriteParams = async (): Deferred<{
        raw: String
        baseHash: String
        sessionKey: String?
        note: String?
        restartDelayMs: Double?
      }> -> {
        val raw = readStringParam(params, "raw", { required: true })
        var baseHash = readStringParam(params, "baseHash")
        if (!baseHash) {
          val snapshot = await callGatewayTool("config.get", gatewayOpts, {})
          baseHash = resolveBaseHashFromSnapshot(snapshot)
        }
        if (!baseHash) {
          throw Error("Missing baseHash from config snapshot.")
        }
        return { raw, baseHash, ...resolveGatewayWriteMeta() }
      }

      if (action == "config.get") {
        val result = await callGatewayTool("config.get", gatewayOpts, {})
        return jsonResult({ ok: true, result })
      }
      if (action == "config.schema.lookup") {
        val path = readStringParam(params, "path", {
          required: true,
          label: String /* "path" */,
        })
        val result = await callGatewayTool("config.schema.lookup", gatewayOpts, { path })
        return jsonResult({ ok: true, result })
      }
      if (action == "config.apply") {
        val { raw, baseHash, sessionKey, note, restartDelayMs } =
          await resolveConfigWriteParams()
        val result = await callGatewayTool("config.apply", gatewayOpts, {
          raw,
          baseHash,
          sessionKey,
          note,
          restartDelayMs,
        })
        return jsonResult({ ok: true, result })
      }
      if (action == "config.patch") {
        val { raw, baseHash, sessionKey, note, restartDelayMs } =
          await resolveConfigWriteParams()
        val result = await callGatewayTool("config.patch", gatewayOpts, {
          raw,
          baseHash,
          sessionKey,
          note,
          restartDelayMs,
        })
        return jsonResult({ ok: true, result })
      }
      if (action == "update.run") {
        val { sessionKey, note, restartDelayMs } = resolveGatewayWriteMeta()
        val updateTimeoutMs = gatewayOpts.timeoutMs ?: DEFAULT_UPDATE_TIMEOUT_MS
        val updateGatewayOpts = {
          ...gatewayOpts,
          timeoutMs: updateTimeoutMs,
        }
        val result = await callGatewayTool("update.run", updateGatewayOpts, {
          sessionKey,
          note,
          restartDelayMs,
          timeoutMs: updateTimeoutMs,
        })
        return jsonResult({ ok: true, result })
      }

      throw Error(`Unknown action: ${action}`)
    },
  }
}
