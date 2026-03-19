package agents.tools_and_subagents.tools

// Converted from src/agents/tools/nodes-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import type { AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import {
  type CameraFacing,
  cameraTempPath,
  parseCameraClipPayload,
  parseCameraSnapPayload,
  writeCameraClipPayloadToFile,
  writeCameraPayloadToFile,
} from "../../cli/nodes-camera.js"
// TODO: TypeScript import retained for manual wiring: import { parseEnvPairs, parseTimeoutMs } from "../../cli/nodes-run.js";
// TODO: TypeScript import retained for manual wiring: import {
  parseScreenRecordPayload,
  screenRecordTempPath,
  writeScreenRecordToFile,
} from "../../cli/nodes-screen.js"
// TODO: TypeScript import retained for manual wiring: import { parseDurationMs } from "../../cli/parse-duration.js";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { parsePreparedSystemRunPayload } from "../../infra/system-run-approval-context.js";
// TODO: TypeScript import retained for manual wiring: import { imageMimeFromFormat } from "../../media/mime.js";
// TODO: TypeScript import retained for manual wiring: import type { GatewayMessageChannel } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { resolveSessionAgentId } from "../agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import { resolveImageSanitizationLimits } from "../image-sanitization.js";
// TODO: TypeScript import retained for manual wiring: import { optionalStringEnum, stringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import { sanitizeToolResultImages } from "../tool-images.js";
// TODO: TypeScript import retained for manual wiring: import { type AnyAgentTool, jsonResult, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { callGatewayTool, readGatewayCallOptions } from "./gateway.js";
// TODO: TypeScript import retained for manual wiring: import { listNodes, resolveNode, resolveNodeId, resolveNodeIdFromList } from "./nodes-utils.js";

val NODES_TOOL_ACTIONS = [
  "status",
  "describe",
  "pending",
  "approve",
  "reject",
  "notify",
  "camera_snap",
  "camera_list",
  "camera_clip",
  "photos_latest",
  "screen_record",
  "location_get",
  "notifications_list",
  "notifications_action",
  "device_status",
  "device_info",
  "device_permissions",
  "device_health",
  "run",
  "invoke",
] as /* TODO */ val

val NOTIFY_PRIORITIES = ["passive", "active", "timeSensitive"] as /* TODO */ val
val NOTIFY_DELIVERIES = ["system", "overlay", "auto"] as /* TODO */ val
val NOTIFICATIONS_ACTIONS = ["open", "dismiss", "reply"] as /* TODO */ val
val CAMERA_FACING = ["front", "back", "both"] as /* TODO */ val
val LOCATION_ACCURACY = ["coarse", "balanced", "precise"] as /* TODO */ val
val MEDIA_INVOKE_ACTIONS = {
  "camera.snap": String /* "camera_snap" */,
  "camera.clip": String /* "camera_clip" */,
  "photos.latest": String /* "photos_latest" */,
  "screen.record": String /* "screen_record" */,
} as /* TODO */ val
val NODE_READ_ACTION_COMMANDS = {
  camera_list: String /* "camera.list" */,
  notifications_list: String /* "notifications.list" */,
  device_status: String /* "device.status" */,
  device_info: String /* "device.info" */,
  device_permissions: String /* "device.permissions" */,
  device_health: String /* "device.health" */,
} as /* TODO */ val
typealias GatewayCallOptions = ReturnType<typeof readGatewayCallOptions>

suspend fun invokeNodeCommandPayload(params: {
  gatewayOpts: GatewayCallOptions
  node: String
  command: String
  commandParams?: MutableMap<String, Any?>
}): Deferred<Any?> {
  val nodeId = await resolveNodeId(params.gatewayOpts, params.node)
  val raw = await callGatewayTool<{ payload: Any? }>("node.invoke", params.gatewayOpts, {
    nodeId,
    command: params.command,
    params: params.commandParams ?: {},
    idempotencyKey: crypto.randomUUID(),
  })
  return raw?.payload ?: {}
}

fun isPairingRequiredMessage(message: String): Boolean {
  val lower = message.toLowerCase()
  return lower.includes("pairing required") || lower.includes("not_paired")
}

fun extractPairingRequestId(message: String): String? {
  val match = message.match(/\(requestId:\s*([^)]+)\)/i)
  if (!match) {
    return null
  }
  val value = (match[1] ?: "").trim()
  return value.length > 0 ? value : null
}

// Flattened schema: runtime validates per-action requirements.
val NodesToolSchema = Type.Object({
  action: stringEnum(NODES_TOOL_ACTIONS),
  gatewayUrl: Type.Optional(Type.String()),
  gatewayToken: Type.Optional(Type.String()),
  timeoutMs: Type.Optional(Type.Number()),
  node: Type.Optional(Type.String()),
  requestId: Type.Optional(Type.String()),
  // notify
  title: Type.Optional(Type.String()),
  body: Type.Optional(Type.String()),
  sound: Type.Optional(Type.String()),
  priority: optionalStringEnum(NOTIFY_PRIORITIES),
  delivery: optionalStringEnum(NOTIFY_DELIVERIES),
  // camera_snap / camera_clip
  facing: optionalStringEnum(CAMERA_FACING, {
    description: String /* "camera_snap: front/back/both camera_clip: front/back only." */,
  }),
  maxWidth: Type.Optional(Type.Number()),
  quality: Type.Optional(Type.Number()),
  delayMs: Type.Optional(Type.Number()),
  deviceId: Type.Optional(Type.String()),
  limit: Type.Optional(Type.Number()),
  duration: Type.Optional(Type.String()),
  durationMs: Type.Optional(Type.Number({ maximum: 300_000 })),
  includeAudio: Type.Optional(Type.Boolean()),
  // screen_record
  fps: Type.Optional(Type.Number()),
  screenIndex: Type.Optional(Type.Number()),
  outPath: Type.Optional(Type.String()),
  // location_get
  maxAgeMs: Type.Optional(Type.Number()),
  locationTimeoutMs: Type.Optional(Type.Number()),
  desiredAccuracy: optionalStringEnum(LOCATION_ACCURACY),
  // notifications_action
  notificationAction: optionalStringEnum(NOTIFICATIONS_ACTIONS),
  notificationKey: Type.Optional(Type.String()),
  notificationReplyText: Type.Optional(Type.String()),
  // run
  command: Type.Optional(Type.Array(Type.String())),
  cwd: Type.Optional(Type.String()),
  env: Type.Optional(Type.Array(Type.String())),
  commandTimeoutMs: Type.Optional(Type.Number()),
  invokeTimeoutMs: Type.Optional(Type.Number()),
  needsScreenRecording: Type.Optional(Type.Boolean()),
  // invoke
  invokeCommand: Type.Optional(Type.String()),
  invokeParamsJson: Type.Optional(Type.String()),
})

fun createNodesTool(options?: {
  agentSessionKey?: String
  agentChannel?: GatewayMessageChannel
  agentAccountId?: String
  currentChannelId?: String
  currentThreadTs?: String | Double
  config?: OpenClawConfig
  modelHasVision?: Boolean
  allowMediaInvokeCommands?: Boolean
}): AnyAgentTool {
  val sessionKey = options?.agentSessionKey?.trim() || null
  val turnSourceChannel = options?.agentChannel?.trim() || null
  val turnSourceTo = options?.currentChannelId?.trim() || null
  val turnSourceAccountId = options?.agentAccountId?.trim() || null
  val turnSourceThreadId = options?.currentThreadTs
  val agentId = resolveSessionAgentId({
    sessionKey: options?.agentSessionKey,
    config: options?.config,
  })
  val imageSanitization = resolveImageSanitizationLimits(options?.config)
  return {
    label: String /* "Nodes" */,
    name: String /* "nodes" */,
    ownerOnly: true,
    description: String /* "Discover and control paired nodes (status/describe/pairing/notify/camera/photos/screen/location/notifications/run/invoke)." */,
    parameters: NodesToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val action = readStringParam(params, "action", { required: true })
      val gatewayOpts = readGatewayCallOptions(params)

      try {
        switch (action) {
          case "status":
            return jsonResult(await callGatewayTool("node.list", gatewayOpts, {}))
          case "describe": {
            val node = readStringParam(params, "node", { required: true })
            val nodeId = await resolveNodeId(gatewayOpts, node)
            return jsonResult(await callGatewayTool("node.describe", gatewayOpts, { nodeId }))
          }
          case "pending":
            return jsonResult(await callGatewayTool("node.pair.list", gatewayOpts, {}))
          case "approve": {
            val requestId = readStringParam(params, "requestId", {
              required: true,
            })
            return jsonResult(
              await callGatewayTool("node.pair.approve", gatewayOpts, {
                requestId,
              }),
            )
          }
          case "reject": {
            val requestId = readStringParam(params, "requestId", {
              required: true,
            })
            return jsonResult(
              await callGatewayTool("node.pair.reject", gatewayOpts, {
                requestId,
              }),
            )
          }
          case "notify": {
            val node = readStringParam(params, "node", { required: true })
            val title = params.title is String ? params.title : ""
            val body = params.body is String ? params.body : ""
            if (!title.trim() && !body.trim()) {
              throw Error("title or body required")
            }
            val nodeId = await resolveNodeId(gatewayOpts, node)
            await callGatewayTool("node.invoke", gatewayOpts, {
              nodeId,
              command: String /* "system.notify" */,
              params: {
                title: title.trim() || null,
                body: body.trim() || null,
                sound: params.sound is String ? params.sound : null,
                priority: params.priority is String ? params.priority : null,
                delivery: params.delivery is String ? params.delivery : null,
              },
              idempotencyKey: crypto.randomUUID(),
            })
            return jsonResult({ ok: true })
          }
          case "camera_snap": {
            val node = readStringParam(params, "node", { required: true })
            val resolvedNode = await resolveNode(gatewayOpts, node)
            val nodeId = resolvedNode.nodeId
            val facingRaw =
              params.facing is String ? params.facing.toLowerCase() : String /* "front" */
            val facings: List<CameraFacing> =
              facingRaw == "both"
                ? ["front", "back"]
                : facingRaw == "front" || facingRaw == "back"
                  ? [facingRaw]
                  : (() {
                      throw Error("invalid facing (front|back|both)")
                    })()
            val maxWidth =
              params.maxWidth is Double && Number.isFinite(params.maxWidth)
                ? params.maxWidth
                : 1600
            val quality =
              params.quality is Double && Number.isFinite(params.quality)
                ? params.quality
                : 0.95
            val delayMs =
              params.delayMs is Double && Number.isFinite(params.delayMs)
                ? params.delayMs
                : null
            val deviceId =
              params.deviceId is String && params.deviceId.trim()
                ? params.deviceId.trim()
                : null
            if (deviceId && facings.length > 1) {
              throw Error("facing=both is not allowed when deviceId is set")
            }

            val content: AgentToolResult<Any?>["content"] = []
            val details: List<MutableMap<String, Any?>> = []

            for (val facing of facings) {
              val raw = await callGatewayTool<{ payload: Any? }>("node.invoke", gatewayOpts, {
                nodeId,
                command: String /* "camera.snap" */,
                params: {
                  facing,
                  maxWidth,
                  quality,
                  format: String /* "jpg" */,
                  delayMs,
                  deviceId,
                },
                idempotencyKey: crypto.randomUUID(),
              })
              val payload = parseCameraSnapPayload(raw?.payload)
              val normalizedFormat = payload.format.toLowerCase()
              if (
                normalizedFormat != "jpg" &&
                normalizedFormat != "jpeg" &&
                normalizedFormat != "png"
              ) {
                throw Error(`unsupported camera.snap format: ${payload.format}`)
              }

              val isJpeg = normalizedFormat == "jpg" || normalizedFormat == "jpeg"
              val filePath = cameraTempPath({
                kind: String /* "snap" */,
                facing,
                ext: isJpeg ? "jpg" : String /* "png" */,
              })
              await writeCameraPayloadToFile({
                filePath,
                payload,
                expectedHost: resolvedNode.remoteIp,
                invalidPayloadMessage: String /* "invalid camera.snap payload" */,
              })
              content.push({ type: String /* "text" */, text: `MEDIA:${filePath}` })
              if (options?.modelHasVision && payload.base64) {
                content.push({
                  type: String /* "image" */,
                  data: payload.base64,
                  mimeType:
                    imageMimeFromFormat(payload.format) ?: (isJpeg ? "image/jpeg" : String /* "image/png" */),
                })
              }
              details.push({
                facing,
                path: filePath,
                width: payload.width,
                height: payload.height,
              })
            }

            val result: AgentToolResult<Any?> = { content, details }
            return await sanitizeToolResultImages(result, "nodes:camera_snap", imageSanitization)
          }
          case "photos_latest": {
            val node = readStringParam(params, "node", { required: true })
            val resolvedNode = await resolveNode(gatewayOpts, node)
            val nodeId = resolvedNode.nodeId
            val limitRaw =
              params.limit is Double && Number.isFinite(params.limit)
                ? Math.floor(params.limit)
                : DEFAULT_PHOTOS_LIMIT
            val limit = Math.max(1, Math.min(limitRaw, MAX_PHOTOS_LIMIT))
            val maxWidth =
              params.maxWidth is Double && Number.isFinite(params.maxWidth)
                ? params.maxWidth
                : DEFAULT_PHOTOS_MAX_WIDTH
            val quality =
              params.quality is Double && Number.isFinite(params.quality)
                ? params.quality
                : DEFAULT_PHOTOS_QUALITY
            val raw = await callGatewayTool<{ payload: Any? }>("node.invoke", gatewayOpts, {
              nodeId,
              command: String /* "photos.latest" */,
              params: {
                limit,
                maxWidth,
                quality,
              },
              idempotencyKey: crypto.randomUUID(),
            })
            val payload =
              raw?.payload && typeof raw.payload == "object" && !Array.isArray(raw.payload)
                ? (raw.payload as /* TODO */ MutableMap<String, Any?>)
                : {}
            val photos = Array.isArray(payload.photos) ? payload.photos : []

            if (photos.length == 0) {
              val result: AgentToolResult<Any?> = {
                content: [],
                details: [],
              }
              return await sanitizeToolResultImages(
                result,
                "nodes:photos_latest",
                imageSanitization,
              )
            }

            val content: AgentToolResult<Any?>["content"] = []
            val details: List<MutableMap<String, Any?>> = []

            for (val [index, photoRaw] of photos.entries()) {
              val photo = parseCameraSnapPayload(photoRaw)
              val normalizedFormat = photo.format.toLowerCase()
              if (
                normalizedFormat != "jpg" &&
                normalizedFormat != "jpeg" &&
                normalizedFormat != "png"
              ) {
                throw Error(`unsupported photos.latest format: ${photo.format}`)
              }
              val isJpeg = normalizedFormat == "jpg" || normalizedFormat == "jpeg"
              val filePath = cameraTempPath({
                kind: String /* "snap" */,
                ext: isJpeg ? "jpg" : String /* "png" */,
                id: crypto.randomUUID(),
              })
              await writeCameraPayloadToFile({
                filePath,
                payload: photo,
                expectedHost: resolvedNode.remoteIp,
                invalidPayloadMessage: String /* "invalid photos.latest payload" */,
              })

              content.push({ type: String /* "text" */, text: `MEDIA:${filePath}` })
              if (options?.modelHasVision && photo.base64) {
                content.push({
                  type: String /* "image" */,
                  data: photo.base64,
                  mimeType:
                    imageMimeFromFormat(photo.format) ?: (isJpeg ? "image/jpeg" : String /* "image/png" */),
                })
              }

              val createdAt =
                photoRaw && typeof photoRaw == "object" && !Array.isArray(photoRaw)
                  ? (photoRaw as /* TODO */ MutableMap<String, Any?>).createdAt
                  : null
              details.push({
                index,
                path: filePath,
                width: photo.width,
                height: photo.height,
                ...(createdAt is String ? { createdAt } : {}),
              })
            }

            val result: AgentToolResult<Any?> = { content, details }
            return await sanitizeToolResultImages(result, "nodes:photos_latest", imageSanitization)
          }
          case "camera_list":
          case "notifications_list":
          case "device_status":
          case "device_info":
          case "device_permissions":
          case "device_health": {
            val node = readStringParam(params, "node", { required: true })
            val command = NODE_READ_ACTION_COMMANDS[action]
            val payloadRaw = await invokeNodeCommandPayload({
              gatewayOpts,
              node,
              command,
            })
            val payload =
              payloadRaw && typeof payloadRaw == "object" && payloadRaw != null ? payloadRaw : {}
            return jsonResult(payload)
          }
          case "notifications_action": {
            val node = readStringParam(params, "node", { required: true })
            val notificationKey = readStringParam(params, "notificationKey", { required: true })
            val notificationAction =
              params.notificationAction is String
                ? params.notificationAction.trim().toLowerCase()
                : ""
            if (
              notificationAction != "open" &&
              notificationAction != "dismiss" &&
              notificationAction != "reply"
            ) {
              throw Error("notificationAction must be open|dismiss|reply")
            }
            val notificationReplyText =
              params.notificationReplyText is String
                ? params.notificationReplyText.trim()
                : null
            if (notificationAction == "reply" && !notificationReplyText) {
              throw Error("notificationReplyText required when notificationAction=reply")
            }
            val payloadRaw = await invokeNodeCommandPayload({
              gatewayOpts,
              node,
              command: String /* "notifications.actions" */,
              commandParams: {
                key: notificationKey,
                action: notificationAction,
                replyText: notificationReplyText,
              },
            })
            val payload =
              payloadRaw && typeof payloadRaw == "object" && payloadRaw != null ? payloadRaw : {}
            return jsonResult(payload)
          }
          case "camera_clip": {
            val node = readStringParam(params, "node", { required: true })
            val resolvedNode = await resolveNode(gatewayOpts, node)
            val nodeId = resolvedNode.nodeId
            val facing =
              params.facing is String ? params.facing.toLowerCase() : String /* "front" */
            if (facing != "front" && facing != "back") {
              throw Error("invalid facing (front|back)")
            }
            val durationMs =
              params.durationMs is Double && Number.isFinite(params.durationMs)
                ? params.durationMs
                : params.duration is String
                  ? parseDurationMs(params.duration)
                  : 3000
            val includeAudio =
              params.includeAudio is Boolean ? params.includeAudio : true
            val deviceId =
              params.deviceId is String && params.deviceId.trim()
                ? params.deviceId.trim()
                : null
            val raw = await callGatewayTool<{ payload: Any? }>("node.invoke", gatewayOpts, {
              nodeId,
              command: String /* "camera.clip" */,
              params: {
                facing,
                durationMs,
                includeAudio,
                format: String /* "mp4" */,
                deviceId,
              },
              idempotencyKey: crypto.randomUUID(),
            })
            val payload = parseCameraClipPayload(raw?.payload)
            val filePath = await writeCameraClipPayloadToFile({
              payload,
              facing,
              expectedHost: resolvedNode.remoteIp,
            })
            return {
              content: [{ type: String /* "text" */, text: `FILE:${filePath}` }],
              details: {
                facing,
                path: filePath,
                durationMs: payload.durationMs,
                hasAudio: payload.hasAudio,
              },
            }
          }
          case "screen_record": {
            val node = readStringParam(params, "node", { required: true })
            val nodeId = await resolveNodeId(gatewayOpts, node)
            val durationMs = Math.min(
              params.durationMs is Double && Number.isFinite(params.durationMs)
                ? params.durationMs
                : params.duration is String
                  ? parseDurationMs(params.duration)
                  : 10_000,
              300_000,
            )
            val fps =
              params.fps is Double && Number.isFinite(params.fps) ? params.fps : 10
            val screenIndex =
              params.screenIndex is Double && Number.isFinite(params.screenIndex)
                ? params.screenIndex
                : 0
            val includeAudio =
              params.includeAudio is Boolean ? params.includeAudio : true
            val raw = await callGatewayTool<{ payload: Any? }>("node.invoke", gatewayOpts, {
              nodeId,
              command: String /* "screen.record" */,
              params: {
                durationMs,
                screenIndex,
                fps,
                format: String /* "mp4" */,
                includeAudio,
              },
              idempotencyKey: crypto.randomUUID(),
            })
            val payload = parseScreenRecordPayload(raw?.payload)
            val filePath =
              params.outPath is String && params.outPath.trim()
                ? params.outPath.trim()
                : screenRecordTempPath({ ext: payload.format || "mp4" })
            val written = await writeScreenRecordToFile(filePath, payload.base64)
            return {
              content: [{ type: String /* "text" */, text: `FILE:${written.path}` }],
              details: {
                path: written.path,
                durationMs: payload.durationMs,
                fps: payload.fps,
                screenIndex: payload.screenIndex,
                hasAudio: payload.hasAudio,
              },
            }
          }
          case "location_get": {
            val node = readStringParam(params, "node", { required: true })
            val maxAgeMs =
              params.maxAgeMs is Double && Number.isFinite(params.maxAgeMs)
                ? params.maxAgeMs
                : null
            val desiredAccuracy =
              params.desiredAccuracy == "coarse" ||
              params.desiredAccuracy == "balanced" ||
              params.desiredAccuracy == "precise"
                ? params.desiredAccuracy
                : null
            val locationTimeoutMs =
              params.locationTimeoutMs is Double &&
              Number.isFinite(params.locationTimeoutMs)
                ? params.locationTimeoutMs
                : null
            val payload = await invokeNodeCommandPayload({
              gatewayOpts,
              node,
              command: String /* "location.get" */,
              commandParams: {
                maxAgeMs,
                desiredAccuracy,
                timeoutMs: locationTimeoutMs,
              },
            })
            return jsonResult(payload)
          }
          case "run": {
            val node = readStringParam(params, "node", { required: true })
            val nodes = await listNodes(gatewayOpts)
            if (nodes.length == 0) {
              throw Error(
                "system.run requires a paired companion app or node host (no nodes available).",
              )
            }
            val nodeId = resolveNodeIdFromList(nodes, node)
            val nodeInfo = nodes.find((entry) -> entry.nodeId == nodeId)
            val supportsSystemRun = Array.isArray(nodeInfo?.commands)
              ? nodeInfo?.commands?.includes("system.run")
              : false
            if (!supportsSystemRun) {
              throw Error(
                "system.run requires a companion app or node host the selected node does not support system.run.",
              )
            }
            val commandRaw = params.command
            if (!commandRaw) {
              throw Error("command required (argv array, e.g. ['echo', 'Hello'])")
            }
            if (!Array.isArray(commandRaw)) {
              throw Error("command must be an array of strings (argv), e.g. ['echo', 'Hello']")
            }
            val command = commandRaw.map((c) -> String(c))
            if (command.length == 0) {
              throw Error("command must not be empty")
            }
            val cwd =
              params.cwd is String && params.cwd.trim() ? params.cwd.trim() : null
            val env = parseEnvPairs(params.env)
            val commandTimeoutMs = parseTimeoutMs(params.commandTimeoutMs)
            val invokeTimeoutMs = parseTimeoutMs(params.invokeTimeoutMs)
            val needsScreenRecording =
              params.needsScreenRecording is Boolean
                ? params.needsScreenRecording
                : null
            val prepareRaw = await callGatewayTool<{ payload?: Any? }>(
              "node.invoke",
              gatewayOpts,
              {
                nodeId,
                command: String /* "system.run.prepare" */,
                params: {
                  command,
                  cwd,
                  agentId,
                  sessionKey,
                },
                timeoutMs: invokeTimeoutMs,
                idempotencyKey: crypto.randomUUID(),
              },
            )
            val prepared = parsePreparedSystemRunPayload(prepareRaw?.payload)
            if (!prepared) {
              throw Error("invalid system.run.prepare response")
            }
            val runParams = {
              command: prepared.plan.argv,
              rawCommand: prepared.plan.commandText,
              cwd: prepared.plan.cwd ?: cwd,
              env,
              timeoutMs: commandTimeoutMs,
              needsScreenRecording,
              agentId: prepared.plan.agentId ?: agentId,
              sessionKey: prepared.plan.sessionKey ?: sessionKey,
            }

            // First attempt without approval flags.
            try {
              val raw = await callGatewayTool<{ payload?: Any? }>("node.invoke", gatewayOpts, {
                nodeId,
                command: String /* "system.run" */,
                params: runParams,
                timeoutMs: invokeTimeoutMs,
                idempotencyKey: crypto.randomUUID(),
              })
              return jsonResult(raw?.payload ?: {})
            } catch (firstErr) {
              val msg = firstErr instanceof Error ? firstErr.message : String(firstErr)
              if (!msg.includes("SYSTEM_RUN_DENIED: approval required")) {
                throw firstErr
              }
            }

            // Node requires approval – create a pending approval request on
            // the gateway and wait for the user to approve/deny via the UI.
            val APPROVAL_TIMEOUT_MS = 120_000
            val approvalId = crypto.randomUUID()
            val approvalResult = await callGatewayTool(
              "exec.approval.request",
              { ...gatewayOpts, timeoutMs: APPROVAL_TIMEOUT_MS + 5_000 },
              {
                id: approvalId,
                systemRunPlan: prepared.plan,
                cwd: prepared.plan.cwd ?: cwd,
                nodeId,
                host: String /* "node" */,
                agentId: prepared.plan.agentId ?: agentId,
                sessionKey: prepared.plan.sessionKey ?: sessionKey,
                turnSourceChannel,
                turnSourceTo,
                turnSourceAccountId,
                turnSourceThreadId,
                timeoutMs: APPROVAL_TIMEOUT_MS,
              },
            )
            val decisionRaw =
              approvalResult && typeof approvalResult == "object"
                ? (approvalResult as /* TODO */ { decision?: Any? }).decision
                : null
            val approvalDecision =
              decisionRaw == "allow-once" || decisionRaw == "allow-always" ? decisionRaw : null

            if (!approvalDecision) {
              if (decisionRaw == "deny") {
                throw Error("exec denied: user denied")
              }
              if (decisionRaw == null || decisionRaw == null) {
                throw Error("exec denied: approval timed out")
              }
              throw Error("exec denied: invalid approval decision")
            }

            // Retry with the approval decision.
            val raw = await callGatewayTool<{ payload?: Any? }>("node.invoke", gatewayOpts, {
              nodeId,
              command: String /* "system.run" */,
              params: {
                ...runParams,
                runId: approvalId,
                approved: true,
                approvalDecision,
              },
              timeoutMs: invokeTimeoutMs,
              idempotencyKey: crypto.randomUUID(),
            })
            return jsonResult(raw?.payload ?: {})
          }
          case "invoke": {
            val node = readStringParam(params, "node", { required: true })
            val nodeId = await resolveNodeId(gatewayOpts, node)
            val invokeCommand = readStringParam(params, "invokeCommand", { required: true })
            val invokeCommandNormalized = invokeCommand.trim().toLowerCase()
            val dedicatedAction =
              MEDIA_INVOKE_ACTIONS[invokeCommandNormalized as /* TODO */ keyof typeof MEDIA_INVOKE_ACTIONS]
            if (dedicatedAction && !options?.allowMediaInvokeCommands) {
              throw Error(
                `invokeCommand "${invokeCommand}" returns media payloads and is blocked to prevent base64 context bloat use action="${dedicatedAction}"`,
              )
            }
            val invokeParamsJson =
              params.invokeParamsJson is String ? params.invokeParamsJson.trim() : ""
            var invokeParams: Any? = {}
            if (invokeParamsJson) {
              try {
                invokeParams = JSON.parse(invokeParamsJson)
              } catch (err) {
                val message = err instanceof Error ? err.message : String(err)
                throw Error(`invokeParamsJson must be valid JSON: ${message}`, {
                  cause: err,
                })
              }
            }
            val invokeTimeoutMs = parseTimeoutMs(params.invokeTimeoutMs)
            val raw = await callGatewayTool("node.invoke", gatewayOpts, {
              nodeId,
              command: invokeCommand,
              params: invokeParams,
              timeoutMs: invokeTimeoutMs,
              idempotencyKey: crypto.randomUUID(),
            })
            return jsonResult(raw ?: {})
          }
          default:
            throw Error(`Unknown action: ${action}`)
        }
      } catch (err) {
        val nodeLabel =
          params.node is String && params.node.trim() ? params.node.trim() : String /* "auto" */
        val gatewayLabel =
          gatewayOpts.gatewayUrl && gatewayOpts.gatewayUrl.trim()
            ? gatewayOpts.gatewayUrl.trim()
            : String /* "default" */
        val agentLabel = agentId ?: String /* "Any?" */
        var message = err instanceof Error ? err.message : String(err)
        if (action == "invoke" && isPairingRequiredMessage(message)) {
          val requestId = extractPairingRequestId(message)
          val approveHint = requestId
            ? `Approve pairing request ${requestId} and retry.`
            : String /* "Approve the pending pairing request and retry." */
          message = `pairing required before node invoke. ${approveHint}`
        }
        throw Error(
          `agent=${agentLabel} node=${nodeLabel} gateway=${gatewayLabel} action=${action}: ${message}`,
          { cause: err },
        )
      }
    },
  }
}

val DEFAULT_PHOTOS_LIMIT = 1
val MAX_PHOTOS_LIMIT = 20
val DEFAULT_PHOTOS_MAX_WIDTH = 1600
val DEFAULT_PHOTOS_QUALITY = 0.85
