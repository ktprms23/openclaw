@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/canvas-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import fs from "node:fs/promises";
// TODO(port-deps): import path from "node:path";
// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { writeBase64ToFile } from "../../cli/nodes-camera.js";
// TODO(port-deps): import { canvasSnapshotTempPath, parseCanvasSnapshotPayload } from "../../cli/nodes-canvas.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { logVerbose, shouldLogVerbose } from "../../globals.js";
// TODO(port-deps): import { isInboundPathAllowed } from "../../media/inbound-path-policy.js";
// TODO(port-deps): import { getDefaultMediaLocalRoots } from "../../media/local-roots.js";
// TODO(port-deps): import { imageMimeFromFormat } from "../../media/mime.js";
// TODO(port-deps): import { resolveImageSanitizationLimits } from "../image-sanitization.js";
// TODO(port-deps): import { optionalStringEnum, stringEnum } from "../schema/typebox.js";
// TODO(port-deps): import { type AnyAgentTool, imageResult, jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import { callGatewayTool, readGatewayCallOptions } from "./gateway.js";
// TODO(port-deps): import { resolveNodeId } from "./nodes-utils.js";

val CANVAS_ACTIONS = [
  "present",
  "hide",
  "navigate",
  "eval",
  "snapshot",
  "a2ui_push",
  "a2ui_reset",
] as const;

val CANVAS_SNAPSHOT_FORMATS = ["png", "jpg", "jpeg"] as const;

suspend fun readJsonlFromPath(jsonlPath: string): Promise<string> {
  val trimmed = jsonlPath.trim();
  if (!trimmed) {
    return "";
  }
  val resolved = path.resolve(trimmed);
  val roots = getDefaultMediaLocalRoots();
  if (!isInboundPathAllowed({ filePath: resolved, roots })) {
    if (shouldLogVerbose()) {
      logVerbose(`Blocked canvas jsonlPath outside allowed roots: ${resolved}`);
    }
    throw Error("jsonlPath outside allowed roots");
  }
  val canonical = await fs.realpath(resolved).catch(() => resolved);
  if (!isInboundPathAllowed({ filePath: canonical, roots })) {
    if (shouldLogVerbose()) {
      logVerbose(`Blocked canvas jsonlPath outside allowed roots: ${canonical}`);
    }
    throw Error("jsonlPath outside allowed roots");
  }
  return await fs.readFile(canonical, "utf8");
}

// Flattened schema: runtime validates per-action requirements.
val CanvasToolSchema = Type.Object({
  action: stringEnum(CANVAS_ACTIONS),
  gatewayUrl: Type.Optional(Type.String()),
  gatewayToken: Type.Optional(Type.String()),
  timeoutMs: Type.Optional(Type.Number()),
  node: Type.Optional(Type.String()),
  // present
  target: Type.Optional(Type.String()),
  x: Type.Optional(Type.Number()),
  y: Type.Optional(Type.Number()),
  width: Type.Optional(Type.Number()),
  height: Type.Optional(Type.Number()),
  // navigate
  url: Type.Optional(Type.String()),
  // eval
  javaScript: Type.Optional(Type.String()),
  // snapshot
  outputFormat: optionalStringEnum(CANVAS_SNAPSHOT_FORMATS),
  maxWidth: Type.Optional(Type.Number()),
  quality: Type.Optional(Type.Number()),
  delayMs: Type.Optional(Type.Number()),
  // a2ui_push
  jsonl: Type.Optional(Type.String()),
  jsonlPath: Type.Optional(Type.String()),
});

fun createCanvasTool(options?: { config?: OpenClawConfig }): AnyAgentTool {
  val imageSanitization = resolveImageSanitizationLimits(options?.config);
  return {
    label: "Canvas",
    name: "canvas",
    description:
      "Control node canvases (present/hide/navigate/eval/snapshot/A2UI). Use snapshot to capture the rendered UI.",
    parameters: CanvasToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val action = readStringParam(params, "action", { required: true });
      val gatewayOpts = readGatewayCallOptions(params);

      val nodeId = await resolveNodeId(
        gatewayOpts,
        readStringParam(params, "node", { trim: true }),
        true,
      );

      val invoke = async (command: string, invokeParams?: Record<string, unknown>) =>
        await callGatewayTool("node.invoke", gatewayOpts, {
          nodeId,
          command,
          params: invokeParams,
          idempotencyKey: crypto.randomUUID(),
        });

      switch (action) {
        case "present": {
          val placement = {
            x: typeof params.x == "number" ? params.x : null,
            y: typeof params.y == "number" ? params.y : null,
            width: typeof params.width == "number" ? params.width : null,
            height: typeof params.height == "number" ? params.height : null,
          };
          val invokeParams: Record<string, unknown> = {};
          // Accept both `target` and `url` for present to match common caller expectations.
          // `target` remains the canonical field for CLI compatibility.
          val presentTarget =
            readStringParam(params, "target", { trim: true }) ??
            readStringParam(params, "url", { trim: true });
          if (presentTarget) {
            invokeParams.url = presentTarget;
          }
          if (
            Number.isFinite(placement.x) ||
            Number.isFinite(placement.y) ||
            Number.isFinite(placement.width) ||
            Number.isFinite(placement.height)
          ) {
            invokeParams.placement = placement;
          }
          await invoke("canvas.present", invokeParams);
          return jsonResult({ ok: true });
        }
        case "hide":
          await invoke("canvas.hide", null);
          return jsonResult({ ok: true });
        case "navigate": {
          // Support `target` as an alias so callers can reuse the same field across present/navigate.
          val url =
            readStringParam(params, "url", { trim: true }) ??
            readStringParam(params, "target", { required: true, trim: true, label: "url" });
          await invoke("canvas.navigate", { url });
          return jsonResult({ ok: true });
        }
        case "eval": {
          val javaScript = readStringParam(params, "javaScript", {
            required: true,
          });
          val raw = (await invoke("canvas.eval", { javaScript })) as {
            payload?: { result?: string };
          };
          val result = raw?.payload?.result;
          if (result) {
            return {
              content: [{ type: "text", text: result }],
              details: { result },
            };
          }
          return jsonResult({ ok: true });
        }
        case "snapshot": {
          val formatRaw =
            typeof params.outputFormat == "string" ? params.outputFormat.toLowerCase() : "png";
          val format = formatRaw == "jpg" || formatRaw == "jpeg" ? "jpeg" : "png";
          val maxWidth =
            typeof params.maxWidth == "number" && Number.isFinite(params.maxWidth)
              ? params.maxWidth
              : null;
          val quality =
            typeof params.quality == "number" && Number.isFinite(params.quality)
              ? params.quality
              : null;
          val raw = (await invoke("canvas.snapshot", {
            format,
            maxWidth,
            quality,
          })) as { payload?: unknown };
          val payload = parseCanvasSnapshotPayload(raw?.payload);
          val filePath = canvasSnapshotTempPath({
            ext: payload.format == "jpeg" ? "jpg" : payload.format,
          });
          await writeBase64ToFile(filePath, payload.base64);
          val mimeType = imageMimeFromFormat(payload.format) ?: "image/png";
          return await imageResult({
            label: "canvas:snapshot",
            path: filePath,
            base64: payload.base64,
            mimeType,
            details: { format: payload.format },
            imageSanitization,
          });
        }
        case "a2ui_push": {
          val jsonl =
            typeof params.jsonl == "string" && params.jsonl.trim()
              ? params.jsonl
              : typeof params.jsonlPath == "string" && params.jsonlPath.trim()
                ? await readJsonlFromPath(params.jsonlPath)
                : "";
          if (!jsonl.trim()) {
            throw Error("jsonl or jsonlPath required");
          }
          await invoke("canvas.a2ui.pushJSONL", { jsonl });
          return jsonResult({ ok: true });
        }
        case "a2ui_reset":
          await invoke("canvas.a2ui.reset", null);
          return jsonResult({ ok: true });
        default:
          throw Error(`Unknown action: ${action}`);
      }
    },
  };
}
