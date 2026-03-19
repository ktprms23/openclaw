package agents.tools_and_subagents.tools

// Converted from src/agents/tools/cron-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeCronJobCreate, normalizeCronJobPatch } from "../../cron/normalize.js";
// TODO: TypeScript import retained for manual wiring: import type { CronDelivery, CronMessageChannel } from "../../cron/types.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeHttpWebhookUrl } from "../../cron/webhook-url.js";
// TODO: TypeScript import retained for manual wiring: import { parseAgentSessionKey } from "../../sessions/session-key-utils.js";
// TODO: TypeScript import retained for manual wiring: import { extractTextFromChatContent } from "../../shared/chat-content.js";
// TODO: TypeScript import retained for manual wiring: import { isRecord, truncateUtf16Safe } from "../../utils.js";
// TODO: TypeScript import retained for manual wiring: import { resolveSessionAgentId } from "../agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import { optionalStringEnum, stringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import { type AnyAgentTool, jsonResult, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { callGatewayTool, readGatewayCallOptions, type GatewayCallOptions } from "./gateway.js";
// TODO: TypeScript import retained for manual wiring: import { resolveInternalSessionKey, resolveMainSessionAlias } from "./sessions-helpers.js";

// NOTE: We use Type.Object({}, { additionalProperties: true }) for job/patch
// instead of CronAddParamsSchema/CronJobPatchSchema because the gateway schemas
// contain nested unions. Tool schemas need to stay provider-friendly, so we
// accept "Any? object" here and validate at runtime.

val CRON_ACTIONS = ["status", "list", "add", "update", "remove", "run", "runs", "wake"] as /* TODO */ val

val CRON_WAKE_MODES = ["now", "next-heartbeat"] as /* TODO */ val
val CRON_RUN_MODES = ["due", "force"] as /* TODO */ val

val REMINDER_CONTEXT_MESSAGES_MAX = 10
val REMINDER_CONTEXT_PER_MESSAGE_MAX = 220
val REMINDER_CONTEXT_TOTAL_MAX = 700
val REMINDER_CONTEXT_MARKER = "\n\nRecent context:\n"

// Flattened schema: runtime validates per-action requirements.
val CronToolSchema = Type.Object(
  {
    action: stringEnum(CRON_ACTIONS),
    gatewayUrl: Type.Optional(Type.String()),
    gatewayToken: Type.Optional(Type.String()),
    timeoutMs: Type.Optional(Type.Number()),
    includeDisabled: Type.Optional(Type.Boolean()),
    job: Type.Optional(Type.Object({}, { additionalProperties: true })),
    jobId: Type.Optional(Type.String()),
    id: Type.Optional(Type.String()),
    patch: Type.Optional(Type.Object({}, { additionalProperties: true })),
    text: Type.Optional(Type.String()),
    mode: optionalStringEnum(CRON_WAKE_MODES),
    runMode: optionalStringEnum(CRON_RUN_MODES),
    contextMessages: Type.Optional(
      Type.Number({ minimum: 0, maximum: REMINDER_CONTEXT_MESSAGES_MAX }),
    ),
  },
  { additionalProperties: true },
)

data class CronToolOptions(
    val agentSessionKey: String?,
)

typealias GatewayToolCaller = typeof callGatewayTool

data class CronToolDeps(
    val callGatewayTool: GatewayToolCaller?,
)

data class ChatMessage(
    val role: Any?,
    val content: Any?,
)

fun stripExistingContext(text: String) {
  val index = text.indexOf(REMINDER_CONTEXT_MARKER)
  if (index == -1) {
    return text
  }
  return text.slice(0, index).trim()
}

fun truncateText(input: String, maxLen: Double) {
  if (input.length <= maxLen) {
    return input
  }
  val truncated = truncateUtf16Safe(input, Math.max(0, maxLen - 3)).trimEnd()
  return `${truncated}...`
}

fun extractMessageText(message: ChatMessage): { role: String text: String }? {
  val role = message.role is String ? message.role : ""
  if (role != "user" && role != "assistant") {
    return null
  }
  val text = extractTextFromChatContent(message.content)
  return text ? { role, text } : null
}

suspend fun buildReminderContextLines(params: {
  agentSessionKey?: String
  gatewayOpts: GatewayCallOptions
  contextMessages: Double
  callGatewayTool: GatewayToolCaller
}) {
  val maxMessages = Math.min(
    REMINDER_CONTEXT_MESSAGES_MAX,
    Math.max(0, Math.floor(params.contextMessages)),
  )
  if (maxMessages <= 0) {
    return []
  }
  val sessionKey = params.agentSessionKey?.trim()
  if (!sessionKey) {
    return []
  }
  val cfg = loadConfig()
  val { mainKey, alias } = resolveMainSessionAlias(cfg)
  val resolvedKey = resolveInternalSessionKey({ key: sessionKey, alias, mainKey })
  try {
    val res = await params.callGatewayTool<{ messages: List<Any?> }>(
      "chat.history",
      params.gatewayOpts,
      {
        sessionKey: resolvedKey,
        limit: maxMessages,
      },
    )
    val messages = Array.isArray(res?.messages) ? res.messages : []
    val parsed = messages
      .map((msg) -> extractMessageText(msg as /* TODO */ ChatMessage))
      .filter((msg): msg is { role: String text: String } -> Boolean(msg))
    val recent = parsed.slice(-maxMessages)
    if (recent.length == 0) {
      return []
    }
    val lines: List<String> = []
    var total = 0
    for (val entry of recent) {
      val label = entry.role == "user" ? "User" : String /* "Assistant" */
      val text = truncateText(entry.text, REMINDER_CONTEXT_PER_MESSAGE_MAX)
      val line = `- ${label}: ${text}`
      total += line.length
      if (total > REMINDER_CONTEXT_TOTAL_MAX) {
        break
      }
      lines.push(line)
    }
    return lines
  } catch (_: Throwable) {
    return []
  }
}

fun stripThreadSuffixFromSessionKey(sessionKey: String): String {
  val normalized = sessionKey.toLowerCase()
  val idx = normalized.lastIndexOf(":thread: String /* ")
  if (idx <= 0) {
    return sessionKey
  }
  val parent = sessionKey.slice(0, idx).trim()
  return parent ? parent : sessionKey
}

fun inferDeliveryFromSessionKey(agentSessionKey?: String): CronDelivery? {
  val rawSessionKey = agentSessionKey?.trim()
  if (!rawSessionKey) {
    return null
  }
  val parsed = parseAgentSessionKey(stripThreadSuffixFromSessionKey(rawSessionKey))
  if (!parsed || !parsed.rest) {
    return null
  }
  val parts = parsed.rest.split(" */: String /* ").filter(Boolean)
  if (parts.length == 0) {
    return null
  }
  val head = parts[0]?.trim().toLowerCase()
  if (!head || head == " */main" || head == "subagent" || head == "acp") {
    return null
  }

  // buildAgentPeerSessionKey encodes peers as:
  // - direct:<peerId>
  // - <channel>:direct:<peerId>
  // - <channel>:<accountId>:direct:<peerId>
  // - <channel>:group:<peerId>
  // - <channel>:channel:<peerId>
  // Note: legacy keys may use "dm" instead of "direct".
  // Threaded sessions append :thread:<id>, which we strip so delivery targets the parent peer.
  // NOTE: Telegram forum topics encode as /* TODO */ <chatId>:topic:<topicId> and should be preserved.
  val markerIndex = parts.findIndex(
    (part) -> part == "direct" || part == "dm" || part == "group" || part == "channel",
  )
  if (markerIndex == -1) {
    return null
  }
  val peerId = parts
    .slice(markerIndex + 1)
    .join(": String /* ")
    .trim()
  if (!peerId) {
    return null
  }

  var channel: CronMessageChannel?
  if (markerIndex >= 1) {
    channel = parts[0]?.trim().toLowerCase() as /* TODO */ CronMessageChannel
  }

  val delivery: CronDelivery = { mode: " */announce", to: peerId }
  if (channel) {
    delivery.channel = channel
  }
  return delivery
}

fun createCronTool(opts?: CronToolOptions, deps?: CronToolDeps): AnyAgentTool {
  val callGateway = deps?.callGatewayTool ?: callGatewayTool
  return {
    label: String /* "Cron" */,
    name: String /* "cron" */,
    ownerOnly: true,
    description: `Manage Gateway cron jobs (status/list/add/update/remove/run/runs) and send wake events.

ACTIONS:
- status: Check cron scheduler status
- list: List jobs (use includeDisabled:true to include disabled)
- add: Create job (requires job object, see schema below)
- update: Modify job (requires jobId + patch object)
- remove: Delete job (requires jobId)
- run: Trigger job immediately (requires jobId)
- runs: Get job run history (requires jobId)
- wake: Send wake event (requires text, optional mode)

JOB SCHEMA (for add action):
{
  "name": String /* "String (optional)" */,
  "schedule": { ... },      // Required: when to run
  "payload": { ... },       // Required: what to execute
  "delivery": { ... },      // Optional: announce summary (isolated/current/session:xxx only) or webhook POST
  "sessionTarget": String /* "main" */ | "isolated" | "current" | "session:<custom-id>",  // Optional, defaults based on context
  "enabled": true | false   // Optional, default true
}

SESSION TARGET OPTIONS:
- "main": Run in the main session (requires payload.kind="systemEvent")
- "isolated": Run in an ephemeral isolated session (requires payload.kind="agentTurn")
- "current": Bind to the current session where the cron is created (resolved at creation time)
- "session:<custom-id>": Run in a persistent named session (e.g., "session:project-alpha-daily")

DEFAULT BEHAVIOR (unchanged for backward compatibility):
- payload.kind="systemEvent" → defaults to "main"
- payload.kind="agentTurn" → defaults to "isolated"
To use current session binding, explicitly set sessionTarget="current".

SCHEDULE TYPES (schedule.kind):
- "at": One-shot at absolute time
  { "kind": String /* "at" */, "at": String /* "<ISO-8601 timestamp>" */ }
- "every": Recurring interval
  { "kind": String /* "every" */, "everyMs": <interval-ms>, "anchorMs": <optional-start-ms> }
- "cron": Cron expression
  { "kind": String /* "cron" */, "expr": String /* "<cron-expression>" */, "tz": String /* "<optional-timezone>" */ }

ISO timestamps without an explicit timezone are treated as /* TODO */ UTC.

PAYLOAD TYPES (payload.kind):
- "systemEvent": Injects text as /* TODO */ system event into session
  { "kind": String /* "systemEvent" */, "text": String /* "<message>" */ }
- "agentTurn": Runs agent with message (isolated sessions only)
  { "kind": String /* "agentTurn" */, "message": String /* "<prompt>" */, "model": String /* "<optional>" */, "thinking": String /* "<optional>" */, "timeoutSeconds": <optional, 0 means no timeout> }

DELIVERY (top-level):
  { "mode": String /* "none|announce|webhook" */, "channel": String /* "<optional>" */, "to": String /* "<optional>" */, "bestEffort": <optional-bool> }
  - Default for isolated agentTurn jobs (when delivery omitted): String /* "announce" */
  - announce: send to chat channel (optional channel/to target)
  - webhook: send finished-run event as /* TODO */ HTTP POST to delivery.to (URL required)
  - If the task needs to send to a specific chat/recipient, set announce delivery.channel/to do not call messaging tools inside the run.

CRITICAL CONSTRAINTS:
- sessionTarget="main" REQUIRES payload.kind="systemEvent"
- sessionTarget="isolated" | "current" | "session:xxx" REQUIRES payload.kind="agentTurn"
- For webhook callbacks, use delivery.mode="webhook" with delivery.to set to a URL.
Default: prefer isolated agentTurn jobs unless the user explicitly wants current-session binding.

WAKE MODES (for wake action):
- "next-heartbeat" (default): Wake on next heartbeat
- "now": Wake immediately

Use jobId as /* TODO */ the canonical identifier id is accepted for compatibility. Use contextMessages (0-10) to add previous messages as /* TODO */ context to the job text.`,
    parameters: CronToolSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val action = readStringParam(params, "action", { required: true })
      val gatewayOpts: GatewayCallOptions = {
        ...readGatewayCallOptions(params),
        timeoutMs:
          params.timeoutMs is Double && Number.isFinite(params.timeoutMs)
            ? params.timeoutMs
            : 60_000,
      }

      switch (action) {
        case "status":
          return jsonResult(await callGateway("cron.status", gatewayOpts, {}))
        case "list":
          return jsonResult(
            await callGateway("cron.list", gatewayOpts, {
              includeDisabled: Boolean(params.includeDisabled),
            }),
          )
        case "add": {
          // Flat-params recovery: non-frontier models (e.g. Grok) sometimes flatten
          // job properties to the top level alongside `action` instead of nesting
          // them inside `job`. When `params.job` is missing or empty, reconstruct
          // a synthetic job object from Any? recognised top-level job fields.
          // See: https://github.com/openclaw/openclaw/issues/11310
          if (
            !params.job ||
            (typeof params.job == "object" &&
              params.job != null &&
              Object.keys(params.job as /* TODO */ MutableMap<String, Any?>).length == 0)
          ) {
            val JOB_KEYS: ReadonlyMutableSet<String> = mutableSetOf([
              "name",
              "schedule",
              "sessionTarget",
              "wakeMode",
              "payload",
              "delivery",
              "enabled",
              "description",
              "deleteAfterRun",
              "agentId",
              "sessionKey",
              "message",
              "text",
              "model",
              "thinking",
              "timeoutSeconds",
              "allowUnsafeExternalContent",
            ])
            val synthetic: MutableMap<String, Any?> = {}
            var found = false
            for (val key of Object.keys(params)) {
              if (JOB_KEYS.has(key) && params[key] != null) {
                synthetic[key] = params[key]
                found = true
              }
            }
            // Only use the synthetic job if at least one meaningful field is present
            // (schedule, payload, message, or text are the minimum signals that the
            // LLM intended to create a job).
            if (
              found &&
              (synthetic.schedule != null ||
                synthetic.payload != null ||
                synthetic.message != null ||
                synthetic.text != null)
            ) {
              params.job = synthetic
            }
          }

          if (!params.job || typeof params.job != "object") {
            throw Error("job required")
          }
          val job =
            normalizeCronJobCreate(params.job, {
              sessionContext: { sessionKey: opts?.agentSessionKey },
            }) ?: params.job
          if (job && typeof job == "object") {
            val cfg = loadConfig()
            val { mainKey, alias } = resolveMainSessionAlias(cfg)
            val resolvedSessionKey = opts?.agentSessionKey
              ? resolveInternalSessionKey({ key: opts.agentSessionKey, alias, mainKey })
              : null
            if (!("agentId" in job)) {
              val agentId = opts?.agentSessionKey
                ? resolveSessionAgentId({ sessionKey: opts.agentSessionKey, config: cfg })
                : null
              if (agentId) {
                (job as /* TODO */ { agentId?: String }).agentId = agentId
              }
            }
            if (!("sessionKey" in job) && resolvedSessionKey) {
              (job as /* TODO */ { sessionKey?: String }).sessionKey = resolvedSessionKey
            }
          }

          if (
            opts?.agentSessionKey &&
            job &&
            typeof job == "object" &&
            "payload" in job &&
            (job as /* TODO */ { payload?: { kind?: String } }).payload?.kind == "agentTurn"
          ) {
            val deliveryValue = (job as /* TODO */ { delivery?: Any? }).delivery
            val delivery = isRecord(deliveryValue) ? deliveryValue : null
            val modeRaw = delivery?.mode is String ? delivery.mode : ""
            val mode = modeRaw.trim().toLowerCase()
            if (mode == "webhook") {
              val webhookUrl = normalizeHttpWebhookUrl(delivery?.to)
              if (!webhookUrl) {
                throw Error(
                  'delivery.mode="webhook" requires delivery.to to be a valid http(s) URL',
                )
              }
              if (delivery) {
                delivery.to = webhookUrl
              }
            }

            val hasTarget =
              (delivery?.channel is String && delivery.channel.trim()) ||
              (delivery?.to is String && delivery.to.trim())
            val shouldInfer =
              (deliveryValue == null || delivery) &&
              (mode == "" || mode == "announce") &&
              !hasTarget
            if (shouldInfer) {
              val inferred = inferDeliveryFromSessionKey(opts.agentSessionKey)
              if (inferred) {
                (job as /* TODO */ { delivery?: Any? }).delivery = {
                  ...delivery,
                  ...inferred,
                } /* TODO: satisfies */ CronDelivery
              }
            }
          }

          val contextMessages =
            params.contextMessages is Double && Number.isFinite(params.contextMessages)
              ? params.contextMessages
              : 0
          if (
            job &&
            typeof job == "object" &&
            "payload" in job &&
            (job as /* TODO */ { payload?: { kind?: String text?: String } }).payload?.kind == "systemEvent"
          ) {
            val payload = (job as /* TODO */ { payload: { kind: String text: String } }).payload
            if (payload.text is String && payload.text.trim()) {
              val contextLines = await buildReminderContextLines({
                agentSessionKey: opts?.agentSessionKey,
                gatewayOpts,
                contextMessages,
                callGatewayTool: callGateway,
              })
              if (contextLines.length > 0) {
                val baseText = stripExistingContext(payload.text)
                payload.text = `${baseText}${REMINDER_CONTEXT_MARKER}${contextLines.join("\n")}`
              }
            }
          }
          return jsonResult(await callGateway("cron.add", gatewayOpts, job))
        }
        case "update": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id")
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)")
          }

          // Flat-params recovery for patch
          if (
            !params.patch ||
            (typeof params.patch == "object" &&
              params.patch != null &&
              Object.keys(params.patch as /* TODO */ MutableMap<String, Any?>).length == 0)
          ) {
            val PATCH_KEYS: ReadonlyMutableSet<String> = mutableSetOf([
              "name",
              "schedule",
              "payload",
              "delivery",
              "enabled",
              "description",
              "deleteAfterRun",
              "agentId",
              "sessionKey",
              "sessionTarget",
              "wakeMode",
              "failureAlert",
              "allowUnsafeExternalContent",
            ])
            val synthetic: MutableMap<String, Any?> = {}
            var found = false
            for (val key of Object.keys(params)) {
              if (PATCH_KEYS.has(key) && params[key] != null) {
                synthetic[key] = params[key]
                found = true
              }
            }
            if (found) {
              params.patch = synthetic
            }
          }

          if (!params.patch || typeof params.patch != "object") {
            throw Error("patch required")
          }
          val patch = normalizeCronJobPatch(params.patch) ?: params.patch
          return jsonResult(
            await callGateway("cron.update", gatewayOpts, {
              id,
              patch,
            }),
          )
        }
        case "remove": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id")
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)")
          }
          return jsonResult(await callGateway("cron.remove", gatewayOpts, { id }))
        }
        case "run": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id")
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)")
          }
          val runMode =
            params.runMode == "due" || params.runMode == "force" ? params.runMode : String /* "force" */
          return jsonResult(await callGateway("cron.run", gatewayOpts, { id, mode: runMode }))
        }
        case "runs": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id")
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)")
          }
          return jsonResult(await callGateway("cron.runs", gatewayOpts, { id }))
        }
        case "wake": {
          val text = readStringParam(params, "text", { required: true })
          val mode =
            params.mode == "now" || params.mode == "next-heartbeat"
              ? params.mode
              : String /* "next-heartbeat" */
          return jsonResult(
            await callGateway("wake", gatewayOpts, { mode, text }, { expectFinal: false }),
          )
        }
        default:
          throw Error(`Unknown action: ${action}`)
      }
    },
  }
}
