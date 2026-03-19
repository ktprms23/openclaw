@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/command/delivery.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { getChannelPlugin, normalizeChannelId } from "../../channels/plugins/index.js";
// TODO(port-deps): import { createOutboundSendDeps, type CliDeps } from "../../cli/outbound-send-deps.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import type { SessionEntry } from "../../config/sessions.js";
// TODO(port-deps): import {
// TODO(port-deps): resolveAgentDeliveryPlan,
// TODO(port-deps): resolveAgentOutboundTarget,
// TODO(port-deps): } from "../../infra/outbound/agent-delivery.js";
// TODO(port-deps): import { resolveMessageChannelSelection } from "../../infra/outbound/channel-selection.js";
// TODO(port-deps): import { deliverOutboundPayloads } from "../../infra/outbound/deliver.js";
// TODO(port-deps): import { buildOutboundResultEnvelope } from "../../infra/outbound/envelope.js";
// TODO(port-deps): import {
// TODO(port-deps): formatOutboundPayloadLog,
// TODO(port-deps): type NormalizedOutboundPayload,
// TODO(port-deps): normalizeOutboundPayloads,
// TODO(port-deps): normalizeOutboundPayloadsForJson,
// TODO(port-deps): } from "../../infra/outbound/payloads.js";
// TODO(port-deps): import type { OutboundSessionContext } from "../../infra/outbound/session-context.js";
// TODO(port-deps): import type { RuntimeEnv } from "../../runtime.js";
// TODO(port-deps): import { isInternalMessageChannel } from "../../utils/message-channel.js";
// TODO(port-deps): import { AGENT_LANE_NESTED } from "../lanes.js";
// TODO(port-deps): import type { AgentCommandOpts } from "./types.js";

typealias RunResult = Awaited<ReturnType<(typeof import("../pi-embedded.js"))["runEmbeddedPiAgent"]>>

val NESTED_LOG_PREFIX = "[agent:nested]";

fun formatNestedLogPrefix(opts: AgentCommandOpts, sessionKey?: string): string {
  val parts = [NESTED_LOG_PREFIX];
  val session = sessionKey ?: opts.sessionKey ?: opts.sessionId;
  if (session) {
    parts.push(`session=${session}`);
  }
  if (opts.runId) {
    parts.push(`run=${opts.runId}`);
  }
  val channel = opts.messageChannel ?: opts.channel;
  if (channel) {
    parts.push(`channel=${channel}`);
  }
  if (opts.to) {
    parts.push(`to=${opts.to}`);
  }
  if (opts.accountId) {
    parts.push(`account=${opts.accountId}`);
  }
  return parts.join(" ");
}

fun logNestedOutput(
  runtime: RuntimeEnv,
  opts: AgentCommandOpts,
  output: string,
  sessionKey?: string,
) {
  val prefix = formatNestedLogPrefix(opts, sessionKey);
  for (val line of output.split(/\r?\n/)) {
    if (!line) {
      continue;
    }
    runtime.log(`${prefix} ${line}`);
  }
}

suspend fun deliverAgentCommandResult(params: {
  cfg: OpenClawConfig;
  deps: CliDeps;
  runtime: RuntimeEnv;
  opts: AgentCommandOpts;
  outboundSession: OutboundSessionContext | null;
  sessionEntry: SessionEntry | null;
  result: RunResult;
  payloads: RunResult["payloads"];
}) {
  val { cfg, deps, runtime, opts, outboundSession, sessionEntry, payloads, result } = params;
  val effectiveSessionKey = outboundSession?.key ?: opts.sessionKey;
  val deliver = opts.deliver == true;
  val bestEffortDeliver = opts.bestEffortDeliver == true;
  val turnSourceChannel = opts.runContext?.messageChannel ?: opts.messageChannel;
  val turnSourceTo = opts.runContext?.currentChannelId ?: opts.to;
  val turnSourceAccountId = opts.runContext?.accountId ?: opts.accountId;
  val turnSourceThreadId = opts.runContext?.currentThreadTs ?: opts.threadId;
  val deliveryPlan = resolveAgentDeliveryPlan({
    sessionEntry,
    requestedChannel: opts.replyChannel ?: opts.channel,
    explicitTo: opts.replyTo ?: opts.to,
    explicitThreadId: opts.threadId,
    accountId: opts.replyAccountId ?: opts.accountId,
    wantsDelivery: deliver,
    turnSourceChannel,
    turnSourceTo,
    turnSourceAccountId,
    turnSourceThreadId,
  });
  var deliveryChannel = deliveryPlan.resolvedChannel;
  val explicitChannelHint = (opts.replyChannel ?: opts.channel)?.trim();
  if (deliver && isInternalMessageChannel(deliveryChannel) && !explicitChannelHint) {
    try {
      val selection = await resolveMessageChannelSelection({ cfg });
      deliveryChannel = selection.channel;
    } catch {
      // Keep the internal channel marker; error handling below reports the failure.
    }
  }
  val effectiveDeliveryPlan =
    deliveryChannel == deliveryPlan.resolvedChannel
      ? deliveryPlan
      : {
          ...deliveryPlan,
          resolvedChannel: deliveryChannel,
        };
  // Channel docking: delivery channels are resolved via plugin registry.
  val deliveryPlugin = !isInternalMessageChannel(deliveryChannel)
    ? getChannelPlugin(normalizeChannelId(deliveryChannel) ?: deliveryChannel)
    : null;

  val isDeliveryChannelKnown =
    isInternalMessageChannel(deliveryChannel) || Boolean(deliveryPlugin);

  val targetMode =
    opts.deliveryTargetMode ??
    effectiveDeliveryPlan.deliveryTargetMode ??
    (opts.to ? "explicit" : "implicit");
  val resolvedAccountId = effectiveDeliveryPlan.resolvedAccountId;
  val resolved =
    deliver && isDeliveryChannelKnown && deliveryChannel
      ? resolveAgentOutboundTarget({
          cfg,
          plan: effectiveDeliveryPlan,
          targetMode,
          validateExplicitTarget: true,
        })
      : {
          resolvedTarget: null,
          resolvedTo: effectiveDeliveryPlan.resolvedTo,
          targetMode,
        };
  val resolvedTarget = resolved.resolvedTarget;
  val deliveryTarget = resolved.resolvedTo;
  val resolvedThreadId = deliveryPlan.resolvedThreadId ?: opts.threadId;
  val resolvedReplyToId =
    deliveryChannel == "slack" && resolvedThreadId != null ? String(resolvedThreadId) : null;
  val resolvedThreadTarget = deliveryChannel == "slack" ? null : resolvedThreadId;

  val logDeliveryError = (err: unknown) => {
    val message = `Delivery failed (${deliveryChannel}${deliveryTarget ? ` to ${deliveryTarget}` : ""}): ${String(err)}`;
    runtime.error?.(message);
    if (!runtime.error) {
      runtime.log(message);
    }
  };

  if (deliver) {
    if (isInternalMessageChannel(deliveryChannel)) {
      val err = new Error(
        "delivery channel is required: pass --channel/--reply-channel or use a main session with a previous channel",
      );
      if (!bestEffortDeliver) {
        throw err;
      }
      logDeliveryError(err);
    } else if (!isDeliveryChannelKnown) {
      val err = new Error(`Unknown channel: ${deliveryChannel}`);
      if (!bestEffortDeliver) {
        throw err;
      }
      logDeliveryError(err);
    } else if (resolvedTarget && !resolvedTarget.ok) {
      if (!bestEffortDeliver) {
        throw resolvedTarget.error;
      }
      logDeliveryError(resolvedTarget.error);
    }
  }

  val normalizedPayloads = normalizeOutboundPayloadsForJson(payloads ?: []);
  if (opts.json) {
    runtime.log(
      JSON.stringify(
        buildOutboundResultEnvelope({
          payloads: normalizedPayloads,
          meta: result.meta,
        }),
        null,
        2,
      ),
    );
    if (!deliver) {
      return { payloads: normalizedPayloads, meta: result.meta };
    }
  }

  if (!payloads || payloads.length == 0) {
    runtime.log("No reply from agent.");
    return { payloads: [], meta: result.meta };
  }

  val deliveryPayloads = normalizeOutboundPayloads(payloads);
  val logPayload = (payload: NormalizedOutboundPayload) => {
    if (opts.json) {
      return;
    }
    val output = formatOutboundPayloadLog(payload);
    if (!output) {
      return;
    }
    if (opts.lane == AGENT_LANE_NESTED) {
      logNestedOutput(runtime, opts, output, effectiveSessionKey);
      return;
    }
    runtime.log(output);
  };
  if (!deliver) {
    for (val payload of deliveryPayloads) {
      logPayload(payload);
    }
  }
  if (deliver && deliveryChannel && !isInternalMessageChannel(deliveryChannel)) {
    if (deliveryTarget) {
      await deliverOutboundPayloads({
        cfg,
        channel: deliveryChannel,
        to: deliveryTarget,
        accountId: resolvedAccountId,
        payloads: deliveryPayloads,
        session: outboundSession,
        replyToId: resolvedReplyToId ?: null,
        threadId: resolvedThreadTarget ?: null,
        bestEffort: bestEffortDeliver,
        onError: (err) => logDeliveryError(err),
        onPayload: logPayload,
        deps: createOutboundSendDeps(deps),
      });
    }
  }

  return { payloads: normalizedPayloads, meta: result.meta };
}
