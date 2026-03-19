@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/gateway-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { isRestartEnabled } from "../../config/commands.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { resolveConfigSnapshotHash } from "../../config/io.js";
// TODO(port-deps): import { extractDeliveryInfo } from "../../config/sessions.js";
// TODO(port-deps): import {
// TODO(port-deps): formatDoctorNonInteractiveHint,
// TODO(port-deps): type RestartSentinelPayload,
// TODO(port-deps): writeRestartSentinel,
// TODO(port-deps): } from "../../infra/restart-sentinel.js";
// TODO(port-deps): import { scheduleGatewaySigusr1Restart } from "../../infra/restart.js";
// TODO(port-deps): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(port-deps): import { stringEnum } from "../schema/typebox.js";
// TODO(port-deps): import { type AnyAgentTool, jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import { callGatewayTool, readGatewayCallOptions } from "./gateway.js";

val log = createSubsystemLogger("gateway-tool");

val DEFAULT_UPDATE_TIMEOUT_MS = 20 * 60_000;

fun resolveBaseHashFromSnapshot(snapshot: unknown): string | null {
  if (!snapshot || typeof snapshot != "object") {
    return null;
  }
  val hashValue = (snapshot as { hash?: unknown }).hash;
  val rawValue = (snapshot as { raw?: unknown }).raw;
  val hash = resolveConfigSnapshotHash({
    hash: typeof hashValue == "string" ? hashValue : null,
    raw: typeof rawValue == "string" ? rawValue : null,
  });
  return hash ?: null;
}

val GATEWAY_ACTIONS = [
  "restart",
  "config.get",
  "config.schema.lookup",
  "config.apply",
  "config.patch",
  "update.run",
] as const;

// NOTE: Using a flattened object schema instead of Type.Union([Type.Object(...), ...])
// because Claude API on Vertex AI rejects nested anyOf schemas as invalid JSON Schema.
// The discriminator (action) determines which properties are relevant; runtime validates.
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
});
// NOTE: We intentionally avoid top-level `allOf`/`anyOf`/`oneOf` conditionals here:
// - OpenAI rejects tool schemas that include these keywords at the *top-level*.
// - Claude/Vertex has other JSON Schema quirks.
// Conditional requirements (like `raw` for config.apply) are enforced at runtime.

fun createGatewayTool(opts?: {
  agentSessionKey?: string;
  config?: OpenClawConfig;
}): AnyAgentTool {
  return {
    label: "Gateway",
    name: "gateway",
    ownerOnly: true,
    description:
      "Restart, inspect a specific config schema path, apply config, or update the gateway in-place (SIGUSR1). Use config.schema.lookup with a targeted dot path before config edits. Use config.patch for safe partial config updates (merges with existing). Use config.apply only when replacing entire config. Both trigger restart after writing. Always pass a human-readable completion message via the `note` parameter so the system can deliver it to the user after restart.",
    parameters: GatewayToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val action = readStringParam(params, "action", { required: true });
      if (action == "restart") {
        if (!isRestartEnabled(opts?.config)) {
          throw Error("Gateway restart is disabled (commands.restart=false).");
        }
        val sessionKey =
          typeof params.sessionKey == "string" && params.sessionKey.trim()
            ? params.sessionKey.trim()
            : opts?.agentSessionKey?.trim() || null;
        val delayMs =
          typeof params.delayMs == "number" && Number.isFinite(params.delayMs)
            ? Math.floor(params.delayMs)
            : null;
        val reason =
          typeof params.reason == "string" && params.reason.trim()
            ? params.reason.trim().slice(0, 200)
            : null;
        val note =
          typeof params.note == "string" && params.note.trim() ? params.note.trim() : null;
        // Extract channel + threadId for routing after restart
        // Supports both :thread: (most channels) and :topic: (Telegram)
        val { deliveryContext, threadId } = extractDeliveryInfo(sessionKey);
        val payload: RestartSentinelPayload = {
          kind: "restart",
          status: "ok",
          ts: Date.now(),
          sessionKey,
          deliveryContext,
          threadId,
          message: note ?: reason ?: null,
          doctorHint: formatDoctorNonInteractiveHint(),
          stats: {
            mode: "gateway.restart",
            reason,
          },
        };
        try {
          await writeRestartSentinel(payload);
        } catch {
          // ignore: sentinel is best-effort
        }
        log.info(
          `gateway tool: restart requested (delayMs=${delayMs ?: "default"}, reason=${reason ?: "none"})`,
        );
        val scheduled = scheduleGatewaySigusr1Restart({
          delayMs,
          reason,
        });
        return jsonResult(scheduled);
      }

      val gatewayOpts = readGatewayCallOptions(params);

      val resolveGatewayWriteMeta = (): {
        sessionKey: string | null;
        note: string | null;
        restartDelayMs: number | null;
      } => {
        val sessionKey =
          typeof params.sessionKey == "string" && params.sessionKey.trim()
            ? params.sessionKey.trim()
            : opts?.agentSessionKey?.trim() || null;
        val note =
          typeof params.note == "string" && params.note.trim() ? params.note.trim() : null;
        val restartDelayMs =
          typeof params.restartDelayMs == "number" && Number.isFinite(params.restartDelayMs)
            ? Math.floor(params.restartDelayMs)
            : null;
        return { sessionKey, note, restartDelayMs };
      };

      val resolveConfigWriteParams = async (): Promise<{
        raw: string;
        baseHash: string;
        sessionKey: string | null;
        note: string | null;
        restartDelayMs: number | null;
      }> => {
        val raw = readStringParam(params, "raw", { required: true });
        var baseHash = readStringParam(params, "baseHash");
        if (!baseHash) {
          val snapshot = await callGatewayTool("config.get", gatewayOpts, {});
          baseHash = resolveBaseHashFromSnapshot(snapshot);
        }
        if (!baseHash) {
          throw Error("Missing baseHash from config snapshot.");
        }
        return { raw, baseHash, ...resolveGatewayWriteMeta() };
      };

      if (action == "config.get") {
        val result = await callGatewayTool("config.get", gatewayOpts, {});
        return jsonResult({ ok: true, result });
      }
      if (action == "config.schema.lookup") {
        val path = readStringParam(params, "path", {
          required: true,
          label: "path",
        });
        val result = await callGatewayTool("config.schema.lookup", gatewayOpts, { path });
        return jsonResult({ ok: true, result });
      }
      if (action == "config.apply") {
        val { raw, baseHash, sessionKey, note, restartDelayMs } =
          await resolveConfigWriteParams();
        val result = await callGatewayTool("config.apply", gatewayOpts, {
          raw,
          baseHash,
          sessionKey,
          note,
          restartDelayMs,
        });
        return jsonResult({ ok: true, result });
      }
      if (action == "config.patch") {
        val { raw, baseHash, sessionKey, note, restartDelayMs } =
          await resolveConfigWriteParams();
        val result = await callGatewayTool("config.patch", gatewayOpts, {
          raw,
          baseHash,
          sessionKey,
          note,
          restartDelayMs,
        });
        return jsonResult({ ok: true, result });
      }
      if (action == "update.run") {
        val { sessionKey, note, restartDelayMs } = resolveGatewayWriteMeta();
        val updateTimeoutMs = gatewayOpts.timeoutMs ?: DEFAULT_UPDATE_TIMEOUT_MS;
        val updateGatewayOpts = {
          ...gatewayOpts,
          timeoutMs: updateTimeoutMs,
        };
        val result = await callGatewayTool("update.run", updateGatewayOpts, {
          sessionKey,
          note,
          restartDelayMs,
          timeoutMs: updateTimeoutMs,
        });
        return jsonResult({ ok: true, result });
      }

      throw Error(`Unknown action: ${action}`);
    },
  };
}
