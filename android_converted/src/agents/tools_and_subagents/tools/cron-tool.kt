@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/cron-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import { normalizeCronJobCreate, normalizeCronJobPatch } from "../../cron/normalize.js";
// TODO(port-deps): import type { CronDelivery, CronMessageChannel } from "../../cron/types.js";
// TODO(port-deps): import { normalizeHttpWebhookUrl } from "../../cron/webhook-url.js";
// TODO(port-deps): import { parseAgentSessionKey } from "../../sessions/session-key-utils.js";
// TODO(port-deps): import { extractTextFromChatContent } from "../../shared/chat-content.js";
// TODO(port-deps): import { isRecord, truncateUtf16Safe } from "../../utils.js";
// TODO(port-deps): import { resolveSessionAgentId } from "../agent-scope.js";
// TODO(port-deps): import { optionalStringEnum, stringEnum } from "../schema/typebox.js";
// TODO(port-deps): import { type AnyAgentTool, jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import { callGatewayTool, readGatewayCallOptions, type GatewayCallOptions } from "./gateway.js";
// TODO(port-deps): import { resolveInternalSessionKey, resolveMainSessionAlias } from "./sessions-helpers.js";

// NOTE: We use Type.Object({}, { additionalProperties: true }) for job/patch
// instead of CronAddParamsSchema/CronJobPatchSchema because the gateway schemas
// contain nested unions. Tool schemas need to stay provider-friendly, so we
// accept "any object" here and validate at runtime.

val CRON_ACTIONS = ["status", "list", "add", "update", "remove", "run", "runs", "wake"] as const;

val CRON_WAKE_MODES = ["now", "next-heartbeat"] as const;
val CRON_RUN_MODES = ["due", "force"] as const;

val REMINDER_CONTEXT_MESSAGES_MAX = 10;
val REMINDER_CONTEXT_PER_MESSAGE_MAX = 220;
val REMINDER_CONTEXT_TOTAL_MAX = 700;
val REMINDER_CONTEXT_MARKER = "\n\nRecent context:\n";

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
);

typealias CronToolOptions = Any /* TODO: translate TypeScript alias */

typealias GatewayToolCaller = typeof callGatewayTool

typealias CronToolDeps = Any /* TODO: translate TypeScript alias */

typealias ChatMessage = Any /* TODO: translate TypeScript alias */

fun stripExistingContext(text: string) {
  val index = text.indexOf(REMINDER_CONTEXT_MARKER);
  if (index == -1) {
    return text;
  }
  return text.slice(0, index).trim();
}

fun truncateText(input: string, maxLen: number) {
  if (input.length <= maxLen) {
    return input;
  }
  val truncated = truncateUtf16Safe(input, Math.max(0, maxLen - 3)).trimEnd();
  return `${truncated}...`;
}

fun extractMessageText(message: ChatMessage): { role: string; text: string } | null {
  val role = typeof message.role == "string" ? message.role : "";
  if (role != "user" && role != "assistant") {
    return null;
  }
  val text = extractTextFromChatContent(message.content);
  return text ? { role, text } : null;
}

suspend fun buildReminderContextLines(params: {
  agentSessionKey?: string;
  gatewayOpts: GatewayCallOptions;
  contextMessages: number;
  callGatewayTool: GatewayToolCaller;
}) {
  val maxMessages = Math.min(
    REMINDER_CONTEXT_MESSAGES_MAX,
    Math.max(0, Math.floor(params.contextMessages)),
  );
  if (maxMessages <= 0) {
    return [];
  }
  val sessionKey = params.agentSessionKey?.trim();
  if (!sessionKey) {
    return [];
  }
  val cfg = loadConfig();
  val { mainKey, alias } = resolveMainSessionAlias(cfg);
  val resolvedKey = resolveInternalSessionKey({ key: sessionKey, alias, mainKey });
  try {
    val res = await params.callGatewayTool<{ messages: Array<unknown> }>(
      "chat.history",
      params.gatewayOpts,
      {
        sessionKey: resolvedKey,
        limit: maxMessages,
      },
    );
    val messages = Array.isArray(res?.messages) ? res.messages : [];
    val parsed = messages
      .map((msg) => extractMessageText(msg as ChatMessage))
      .filter((msg): msg is { role: string; text: string } => Boolean(msg));
    val recent = parsed.slice(-maxMessages);
    if (recent.length == 0) {
      return [];
    }
    val lines: string[] = [];
    var total = 0;
    for (val entry of recent) {
      val label = entry.role == "user" ? "User" : "Assistant";
      val text = truncateText(entry.text, REMINDER_CONTEXT_PER_MESSAGE_MAX);
      val line = `- ${label}: ${text}`;
      total += line.length;
      if (total > REMINDER_CONTEXT_TOTAL_MAX) {
        break;
      }
      lines.push(line);
    }
    return lines;
  } catch {
    return [];
  }
}

fun stripThreadSuffixFromSessionKey(sessionKey: string): string {
  val normalized = sessionKey.toLowerCase();
  val idx = normalized.lastIndexOf(":thread:");
  if (idx <= 0) {
    return sessionKey;
  }
  val parent = sessionKey.slice(0, idx).trim();
  return parent ? parent : sessionKey;
}

fun inferDeliveryFromSessionKey(agentSessionKey?: string): CronDelivery | null {
  val rawSessionKey = agentSessionKey?.trim();
  if (!rawSessionKey) {
    return null;
  }
  val parsed = parseAgentSessionKey(stripThreadSuffixFromSessionKey(rawSessionKey));
  if (!parsed || !parsed.rest) {
    return null;
  }
  val parts = parsed.rest.split(":").filter(Boolean);
  if (parts.length == 0) {
    return null;
  }
  val head = parts[0]?.trim().toLowerCase();
  if (!head || head == "main" || head == "subagent" || head == "acp") {
    return null;
  }

  // buildAgentPeerSessionKey encodes peers as:
  // - direct:<peerId>
  // - <channel>:direct:<peerId>
  // - <channel>:<accountId>:direct:<peerId>
  // - <channel>:group:<peerId>
  // - <channel>:channel:<peerId>
  // Note: legacy keys may use "dm" instead of "direct".
  // Threaded sessions append :thread:<id>, which we strip so delivery targets the parent peer.
  // NOTE: Telegram forum topics encode as <chatId>:topic:<topicId> and should be preserved.
  val markerIndex = parts.findIndex(
    (part) => part == "direct" || part == "dm" || part == "group" || part == "channel",
  );
  if (markerIndex == -1) {
    return null;
  }
  val peerId = parts
    .slice(markerIndex + 1)
    .join(":")
    .trim();
  if (!peerId) {
    return null;
  }

  var channel: CronMessageChannel | null;
  if (markerIndex >= 1) {
    channel = parts[0]?.trim().toLowerCase() as CronMessageChannel;
  }

  val delivery: CronDelivery = { mode: "announce", to: peerId };
  if (channel) {
    delivery.channel = channel;
  }
  return delivery;
}

fun createCronTool(opts?: CronToolOptions, deps?: CronToolDeps): AnyAgentTool {
  val callGateway = deps?.callGatewayTool ?: callGatewayTool;
  return {
    label: "Cron",
    name: "cron",
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
  "name": "string (optional)",
  "schedule": { ... },      // Required: when to run
  "payload": { ... },       // Required: what to execute
  "delivery": { ... },      // Optional: announce summary (isolated/current/session:xxx only) or webhook POST
  "sessionTarget": "main" | "isolated" | "current" | "session:<custom-id>",  // Optional, defaults based on context
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
  { "kind": "at", "at": "<ISO-8601 timestamp>" }
- "every": Recurring interval
  { "kind": "every", "everyMs": <interval-ms>, "anchorMs": <optional-start-ms> }
- "cron": Cron expression
  { "kind": "cron", "expr": "<cron-expression>", "tz": "<optional-timezone>" }

ISO timestamps without an explicit timezone are treated as UTC.

PAYLOAD TYPES (payload.kind):
- "systemEvent": Injects text as system event into session
  { "kind": "systemEvent", "text": "<message>" }
- "agentTurn": Runs agent with message (isolated sessions only)
  { "kind": "agentTurn", "message": "<prompt>", "model": "<optional>", "thinking": "<optional>", "timeoutSeconds": <optional, 0 means no timeout> }

DELIVERY (top-level):
  { "mode": "none|announce|webhook", "channel": "<optional>", "to": "<optional>", "bestEffort": <optional-bool> }
  - Default for isolated agentTurn jobs (when delivery omitted): "announce"
  - announce: send to chat channel (optional channel/to target)
  - webhook: send finished-run event as HTTP POST to delivery.to (URL required)
  - If the task needs to send to a specific chat/recipient, set announce delivery.channel/to; do not call messaging tools inside the run.

CRITICAL CONSTRAINTS:
- sessionTarget="main" REQUIRES payload.kind="systemEvent"
- sessionTarget="isolated" | "current" | "session:xxx" REQUIRES payload.kind="agentTurn"
- For webhook callbacks, use delivery.mode="webhook" with delivery.to set to a URL.
Default: prefer isolated agentTurn jobs unless the user explicitly wants current-session binding.

WAKE MODES (for wake action):
- "next-heartbeat" (default): Wake on next heartbeat
- "now": Wake immediately

Use jobId as the canonical identifier; id is accepted for compatibility. Use contextMessages (0-10) to add previous messages as context to the job text.`,
    parameters: CronToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val action = readStringParam(params, "action", { required: true });
      val gatewayOpts: GatewayCallOptions = {
        ...readGatewayCallOptions(params),
        timeoutMs:
          typeof params.timeoutMs == "number" && Number.isFinite(params.timeoutMs)
            ? params.timeoutMs
            : 60_000,
      };

      switch (action) {
        case "status":
          return jsonResult(await callGateway("cron.status", gatewayOpts, {}));
        case "list":
          return jsonResult(
            await callGateway("cron.list", gatewayOpts, {
              includeDisabled: Boolean(params.includeDisabled),
            }),
          );
        case "add": {
          // Flat-params recovery: non-frontier models (e.g. Grok) sometimes flatten
          // job properties to the top level alongside `action` instead of nesting
          // them inside `job`. When `params.job` is missing or empty, reconstruct
          // a synthetic job object from any recognised top-level job fields.
          // See: https://github.com/openclaw/openclaw/issues/11310
          if (
            !params.job ||
            (typeof params.job == "object" &&
              params.job != null &&
              Object.keys(params.job as Record<string, unknown>).length == 0)
          ) {
            val JOB_KEYS: ReadonlySet<string> = new Set([
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
            ]);
            val synthetic: Record<string, unknown> = {};
            var found = false;
            for (val key of Object.keys(params)) {
              if (JOB_KEYS.has(key) && params[key] != null) {
                synthetic[key] = params[key];
                found = true;
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
              params.job = synthetic;
            }
          }

          if (!params.job || typeof params.job != "object") {
            throw Error("job required");
          }
          val job =
            normalizeCronJobCreate(params.job, {
              sessionContext: { sessionKey: opts?.agentSessionKey },
            }) ?: params.job;
          if (job && typeof job == "object") {
            val cfg = loadConfig();
            val { mainKey, alias } = resolveMainSessionAlias(cfg);
            val resolvedSessionKey = opts?.agentSessionKey
              ? resolveInternalSessionKey({ key: opts.agentSessionKey, alias, mainKey })
              : null;
            if (!("agentId" in job)) {
              val agentId = opts?.agentSessionKey
                ? resolveSessionAgentId({ sessionKey: opts.agentSessionKey, config: cfg })
                : null;
              if (agentId) {
                (job as { agentId?: string }).agentId = agentId;
              }
            }
            if (!("sessionKey" in job) && resolvedSessionKey) {
              (job as { sessionKey?: string }).sessionKey = resolvedSessionKey;
            }
          }

          if (
            opts?.agentSessionKey &&
            job &&
            typeof job == "object" &&
            "payload" in job &&
            (job as { payload?: { kind?: string } }).payload?.kind == "agentTurn"
          ) {
            val deliveryValue = (job as { delivery?: unknown }).delivery;
            val delivery = isRecord(deliveryValue) ? deliveryValue : null;
            val modeRaw = typeof delivery?.mode == "string" ? delivery.mode : "";
            val mode = modeRaw.trim().toLowerCase();
            if (mode == "webhook") {
              val webhookUrl = normalizeHttpWebhookUrl(delivery?.to);
              if (!webhookUrl) {
                throw Error(
                  'delivery.mode="webhook" requires delivery.to to be a valid http(s) URL',
                );
              }
              if (delivery) {
                delivery.to = webhookUrl;
              }
            }

            val hasTarget =
              (typeof delivery?.channel == "string" && delivery.channel.trim()) ||
              (typeof delivery?.to == "string" && delivery.to.trim());
            val shouldInfer =
              (deliveryValue == null || delivery) &&
              (mode == "" || mode == "announce") &&
              !hasTarget;
            if (shouldInfer) {
              val inferred = inferDeliveryFromSessionKey(opts.agentSessionKey);
              if (inferred) {
                (job as { delivery?: unknown }).delivery = {
                  ...delivery,
                  ...inferred,
                } satisfies CronDelivery;
              }
            }
          }

          val contextMessages =
            typeof params.contextMessages == "number" && Number.isFinite(params.contextMessages)
              ? params.contextMessages
              : 0;
          if (
            job &&
            typeof job == "object" &&
            "payload" in job &&
            (job as { payload?: { kind?: string; text?: string } }).payload?.kind == "systemEvent"
          ) {
            val payload = (job as { payload: { kind: string; text: string } }).payload;
            if (typeof payload.text == "string" && payload.text.trim()) {
              val contextLines = await buildReminderContextLines({
                agentSessionKey: opts?.agentSessionKey,
                gatewayOpts,
                contextMessages,
                callGatewayTool: callGateway,
              });
              if (contextLines.length > 0) {
                val baseText = stripExistingContext(payload.text);
                payload.text = `${baseText}${REMINDER_CONTEXT_MARKER}${contextLines.join("\n")}`;
              }
            }
          }
          return jsonResult(await callGateway("cron.add", gatewayOpts, job));
        }
        case "update": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id");
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)");
          }

          // Flat-params recovery for patch
          if (
            !params.patch ||
            (typeof params.patch == "object" &&
              params.patch != null &&
              Object.keys(params.patch as Record<string, unknown>).length == 0)
          ) {
            val PATCH_KEYS: ReadonlySet<string> = new Set([
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
            ]);
            val synthetic: Record<string, unknown> = {};
            var found = false;
            for (val key of Object.keys(params)) {
              if (PATCH_KEYS.has(key) && params[key] != null) {
                synthetic[key] = params[key];
                found = true;
              }
            }
            if (found) {
              params.patch = synthetic;
            }
          }

          if (!params.patch || typeof params.patch != "object") {
            throw Error("patch required");
          }
          val patch = normalizeCronJobPatch(params.patch) ?: params.patch;
          return jsonResult(
            await callGateway("cron.update", gatewayOpts, {
              id,
              patch,
            }),
          );
        }
        case "remove": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id");
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)");
          }
          return jsonResult(await callGateway("cron.remove", gatewayOpts, { id }));
        }
        case "run": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id");
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)");
          }
          val runMode =
            params.runMode == "due" || params.runMode == "force" ? params.runMode : "force";
          return jsonResult(await callGateway("cron.run", gatewayOpts, { id, mode: runMode }));
        }
        case "runs": {
          val id = readStringParam(params, "jobId") ?: readStringParam(params, "id");
          if (!id) {
            throw Error("jobId required (id accepted for backward compatibility)");
          }
          return jsonResult(await callGateway("cron.runs", gatewayOpts, { id }));
        }
        case "wake": {
          val text = readStringParam(params, "text", { required: true });
          val mode =
            params.mode == "now" || params.mode == "next-heartbeat"
              ? params.mode
              : "next-heartbeat";
          return jsonResult(
            await callGateway("wake", gatewayOpts, { mode, text }, { expectFinal: false }),
          );
        }
        default:
          throw Error(`Unknown action: ${action}`);
      }
    },
  };
}
