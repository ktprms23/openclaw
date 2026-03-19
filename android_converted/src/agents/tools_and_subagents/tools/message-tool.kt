@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/message-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type, type TSchema } from "@sinclair/typebox";
// TODO(port-deps): import { listChannelPlugins } from "../../channels/plugins/index.js";
// TODO(port-deps): import {
// TODO(port-deps): channelSupportsMessageCapability,
// TODO(port-deps): channelSupportsMessageCapabilityForChannel,
// TODO(port-deps): listChannelMessageActions,
// TODO(port-deps): resolveChannelMessageToolSchemaProperties,
// TODO(port-deps): } from "../../channels/plugins/message-action-discovery.js";
// TODO(port-deps): import type { ChannelMessageCapability } from "../../channels/plugins/message-capabilities.js";
// TODO(port-deps): import {
// TODO(port-deps): CHANNEL_MESSAGE_ACTION_NAMES,
// TODO(port-deps): type ChannelMessageActionName,
// TODO(port-deps): } from "../../channels/plugins/types.js";
// TODO(port-deps): import { resolveCommandSecretRefsViaGateway } from "../../cli/command-secret-gateway.js";
// TODO(port-deps): import { getScopedChannelsCommandSecretTargets } from "../../cli/command-secret-targets.js";
// TODO(port-deps): import { resolveMessageSecretScope } from "../../cli/message-secret-scope.js";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import { GATEWAY_CLIENT_IDS, GATEWAY_CLIENT_MODES } from "../../gateway/protocol/client-info.js";
// TODO(port-deps): import { getToolResult, runMessageAction } from "../../infra/outbound/message-action-runner.js";
// TODO(port-deps): import { POLL_CREATION_PARAM_DEFS, SHARED_POLL_CREATION_PARAM_NAMES } from "../../poll-params.js";
// TODO(port-deps): import { normalizeAccountId } from "../../routing/session-key.js";
// TODO(port-deps): import { stripReasoningTagsFromText } from "../../shared/text/reasoning-tags.js";
// TODO(port-deps): import { normalizeMessageChannel } from "../../utils/message-channel.js";
// TODO(port-deps): import { resolveSessionAgentId } from "../agent-scope.js";
// TODO(port-deps): import { listChannelSupportedActions } from "../channel-tools.js";
// TODO(port-deps): import { channelTargetSchema, channelTargetsSchema, stringEnum } from "../schema/typebox.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readNumberParam, readStringParam } from "./common.js";
// TODO(port-deps): import { resolveGatewayOptions } from "./gateway.js";

val AllMessageActions = CHANNEL_MESSAGE_ACTION_NAMES;
val EXPLICIT_TARGET_ACTIONS = new Set<ChannelMessageActionName>([
  "send",
  "sendWithEffect",
  "sendAttachment",
  "reply",
  "thread-reply",
  "broadcast",
]);

fun actionNeedsExplicitTarget(action: ChannelMessageActionName): boolean {
  return EXPLICIT_TARGET_ACTIONS.has(action);
}
fun buildRoutingSchema() {
  return {
    channel: Type.Optional(Type.String()),
    target: Type.Optional(channelTargetSchema({ description: "Target channel/user id or name." })),
    targets: Type.Optional(channelTargetsSchema()),
    accountId: Type.Optional(Type.String()),
    dryRun: Type.Optional(Type.Boolean()),
  };
}

val interactiveOptionSchema = Type.Object({
  label: Type.String(),
  value: Type.String(),
});

val interactiveButtonSchema = Type.Object({
  label: Type.String(),
  value: Type.String(),
  style: Type.Optional(stringEnum(["primary", "secondary", "success", "danger"])),
});

val interactiveBlockSchema = Type.Object({
  type: stringEnum(["text", "buttons", "select"]),
  text: Type.Optional(Type.String()),
  buttons: Type.Optional(Type.Array(interactiveButtonSchema)),
  placeholder: Type.Optional(Type.String()),
  options: Type.Optional(Type.Array(interactiveOptionSchema)),
});

val interactiveMessageSchema = Type.Object(
  {
    blocks: Type.Array(interactiveBlockSchema),
  },
  {
    description:
      "Shared interactive message payload for buttons and selects. Channels render this into their native components when supported.",
  },
);

fun buildSendSchema(options: { includeInteractive: boolean }) {
  val props: Record<string, TSchema> = {
    message: Type.Optional(Type.String()),
    effectId: Type.Optional(
      Type.String({
        description: "Message effect name/id for sendWithEffect (e.g., invisible ink).",
      }),
    ),
    effect: Type.Optional(
      Type.String({ description: "Alias for effectId (e.g., invisible-ink, balloons)." }),
    ),
    media: Type.Optional(
      Type.String({
        description: "Media URL or local path. data: URLs are not supported here, use buffer.",
      }),
    ),
    filename: Type.Optional(Type.String()),
    buffer: Type.Optional(
      Type.String({
        description: "Base64 payload for attachments (optionally a data: URL).",
      }),
    ),
    contentType: Type.Optional(Type.String()),
    mimeType: Type.Optional(Type.String()),
    caption: Type.Optional(Type.String()),
    path: Type.Optional(Type.String()),
    filePath: Type.Optional(Type.String()),
    replyTo: Type.Optional(Type.String()),
    threadId: Type.Optional(Type.String()),
    asVoice: Type.Optional(Type.Boolean()),
    silent: Type.Optional(Type.Boolean()),
    quoteText: Type.Optional(
      Type.String({ description: "Quote text for Telegram reply_parameters" }),
    ),
    bestEffort: Type.Optional(Type.Boolean()),
    gifPlayback: Type.Optional(Type.Boolean()),
    forceDocument: Type.Optional(
      Type.Boolean({
        description: "Send image/GIF as document to avoid Telegram compression (Telegram only).",
      }),
    ),
    interactive: Type.Optional(interactiveMessageSchema),
  };
  if (!options.includeInteractive) {
    delete props.interactive;
  }
  return props;
}

fun buildReactionSchema() {
  return {
    messageId: Type.Optional(
      Type.String({
        description:
          "Target message id for reaction. If omitted, defaults to the current inbound message id when available.",
      }),
    ),
    message_id: Type.Optional(
      Type.String({
        // Intentional duplicate alias for tool-schema discoverability in LLMs.
        description:
          "snake_case alias of messageId. If omitted, defaults to the current inbound message id when available.",
      }),
    ),
    emoji: Type.Optional(Type.String()),
    remove: Type.Optional(Type.Boolean()),
    targetAuthor: Type.Optional(Type.String()),
    targetAuthorUuid: Type.Optional(Type.String()),
    groupId: Type.Optional(Type.String()),
  };
}

fun buildFetchSchema() {
  return {
    limit: Type.Optional(Type.Number()),
    pageSize: Type.Optional(Type.Number()),
    pageToken: Type.Optional(Type.String()),
    before: Type.Optional(Type.String()),
    after: Type.Optional(Type.String()),
    around: Type.Optional(Type.String()),
    fromMe: Type.Optional(Type.Boolean()),
    includeArchived: Type.Optional(Type.Boolean()),
  };
}

fun buildPollSchema() {
  val props: Record<string, TSchema> = {
    pollId: Type.Optional(Type.String()),
    pollOptionId: Type.Optional(
      Type.String({
        description: "Poll answer id to vote for. Use when the channel exposes stable answer ids.",
      }),
    ),
    pollOptionIds: Type.Optional(
      Type.Array(
        Type.String({
          description:
            "Poll answer ids to vote for in a multiselect poll. Use when the channel exposes stable answer ids.",
        }),
      ),
    ),
    pollOptionIndex: Type.Optional(
      Type.Number({
        description:
          "1-based poll option number to vote for, matching the rendered numbered poll choices.",
      }),
    ),
    pollOptionIndexes: Type.Optional(
      Type.Array(
        Type.Number({
          description:
            "1-based poll option numbers to vote for in a multiselect poll, matching the rendered numbered poll choices.",
        }),
      ),
    ),
  };
  for (val name of SHARED_POLL_CREATION_PARAM_NAMES) {
    val def = POLL_CREATION_PARAM_DEFS[name];
    switch (def.kind) {
      case "string":
        props[name] = Type.Optional(Type.String());
        break;
      case "stringArray":
        props[name] = Type.Optional(Type.Array(Type.String()));
        break;
      case "number":
        props[name] = Type.Optional(Type.Number());
        break;
      case "boolean":
        props[name] = Type.Optional(Type.Boolean());
        break;
    }
  }
  return props;
}

fun buildChannelTargetSchema() {
  return {
    channelId: Type.Optional(
      Type.String({ description: "Channel id filter (search/thread list/event create)." }),
    ),
    chatId: Type.Optional(
      Type.String({ description: "Chat id for chat-scoped metadata actions." }),
    ),
    channelIds: Type.Optional(
      Type.Array(Type.String({ description: "Channel id filter (repeatable)." })),
    ),
    memberId: Type.Optional(Type.String()),
    memberIdType: Type.Optional(Type.String()),
    guildId: Type.Optional(Type.String()),
    userId: Type.Optional(Type.String()),
    openId: Type.Optional(Type.String()),
    unionId: Type.Optional(Type.String()),
    authorId: Type.Optional(Type.String()),
    authorIds: Type.Optional(Type.Array(Type.String())),
    roleId: Type.Optional(Type.String()),
    roleIds: Type.Optional(Type.Array(Type.String())),
    participant: Type.Optional(Type.String()),
    includeMembers: Type.Optional(Type.Boolean()),
    members: Type.Optional(Type.Boolean()),
    scope: Type.Optional(Type.String()),
    kind: Type.Optional(Type.String()),
  };
}

fun buildStickerSchema() {
  return {
    emojiName: Type.Optional(Type.String()),
    stickerId: Type.Optional(Type.Array(Type.String())),
    stickerName: Type.Optional(Type.String()),
    stickerDesc: Type.Optional(Type.String()),
    stickerTags: Type.Optional(Type.String()),
  };
}

fun buildThreadSchema() {
  return {
    threadName: Type.Optional(Type.String()),
    autoArchiveMin: Type.Optional(Type.Number()),
    appliedTags: Type.Optional(Type.Array(Type.String())),
  };
}

fun buildEventSchema() {
  return {
    query: Type.Optional(Type.String()),
    eventName: Type.Optional(Type.String()),
    eventType: Type.Optional(Type.String()),
    startTime: Type.Optional(Type.String()),
    endTime: Type.Optional(Type.String()),
    desc: Type.Optional(Type.String()),
    location: Type.Optional(Type.String()),
    durationMin: Type.Optional(Type.Number()),
    until: Type.Optional(Type.String()),
  };
}

fun buildModerationSchema() {
  return {
    reason: Type.Optional(Type.String()),
    deleteDays: Type.Optional(Type.Number()),
  };
}

fun buildGatewaySchema() {
  return {
    gatewayUrl: Type.Optional(Type.String()),
    gatewayToken: Type.Optional(Type.String()),
    timeoutMs: Type.Optional(Type.Number()),
  };
}

fun buildPresenceSchema() {
  return {
    activityType: Type.Optional(
      Type.String({
        description: "Activity type: playing, streaming, listening, watching, competing, custom.",
      }),
    ),
    activityName: Type.Optional(
      Type.String({
        description: "Activity name shown in sidebar (e.g. 'with fire'). Ignored for custom type.",
      }),
    ),
    activityUrl: Type.Optional(
      Type.String({
        description:
          "Streaming URL (Twitch or YouTube). Only used with streaming type; may not render for bots.",
      }),
    ),
    activityState: Type.Optional(
      Type.String({
        description:
          "State text. For custom type this is the status text; for others it shows in the flyout.",
      }),
    ),
    status: Type.Optional(
      Type.String({ description: "Bot status: online, dnd, idle, invisible." }),
    ),
  };
}

fun buildChannelManagementSchema() {
  return {
    name: Type.Optional(Type.String()),
    type: Type.Optional(Type.Number()),
    parentId: Type.Optional(Type.String()),
    topic: Type.Optional(Type.String()),
    position: Type.Optional(Type.Number()),
    nsfw: Type.Optional(Type.Boolean()),
    rateLimitPerUser: Type.Optional(Type.Number()),
    categoryId: Type.Optional(Type.String()),
    clearParent: Type.Optional(
      Type.Boolean({
        description: "Clear the parent/category when supported by the provider.",
      }),
    ),
  };
}

fun buildMessageToolSchemaProps(options: {
  includeInteractive: boolean;
  extraProperties?: Record<string, TSchema>;
}) {
  return {
    ...buildRoutingSchema(),
    ...buildSendSchema(options),
    ...buildReactionSchema(),
    ...buildFetchSchema(),
    ...buildPollSchema(),
    ...buildChannelTargetSchema(),
    ...buildStickerSchema(),
    ...buildThreadSchema(),
    ...buildEventSchema(),
    ...buildModerationSchema(),
    ...buildGatewaySchema(),
    ...buildChannelManagementSchema(),
    ...buildPresenceSchema(),
    ...options.extraProperties,
  };
}

fun buildMessageToolSchemaFromActions(
  actions: string[],
  options: {
    includeInteractive: boolean;
    extraProperties?: Record<string, TSchema>;
  },
) {
  val props = buildMessageToolSchemaProps(options);
  return Type.Object({
    action: stringEnum(actions),
    ...props,
  });
}

val MessageToolSchema = buildMessageToolSchemaFromActions(AllMessageActions, {
  includeInteractive: true,
});

typealias MessageToolOptions = Any /* TODO: translate TypeScript alias */

fun resolveMessageToolSchemaActions(params: {
  cfg: OpenClawConfig;
  currentChannelProvider?: string;
  currentChannelId?: string;
  currentThreadTs?: string;
  currentMessageId?: string | number;
  currentAccountId?: string;
  sessionKey?: string;
  sessionId?: string;
  agentId?: string;
  requesterSenderId?: string;
}): string[] {
  val currentChannel = normalizeMessageChannel(params.currentChannelProvider);
  if (currentChannel) {
    val scopedActions = listChannelSupportedActions({
      cfg: params.cfg,
      channel: currentChannel,
      currentChannelId: params.currentChannelId,
      currentThreadTs: params.currentThreadTs,
      currentMessageId: params.currentMessageId,
      accountId: params.currentAccountId,
      sessionKey: params.sessionKey,
      sessionId: params.sessionId,
      agentId: params.agentId,
      requesterSenderId: params.requesterSenderId,
    });
    val allActions = new Set<string>(["send", ...scopedActions]);
    // Include actions from other configured channels so isolated/cron agents
    // can invoke cross-channel actions without validation errors.
    for (val plugin of listChannelPlugins()) {
      if (plugin.id == currentChannel) {
        continue;
      }
      for (val action of listChannelSupportedActions({
        cfg: params.cfg,
        channel: plugin.id,
        currentChannelId: params.currentChannelId,
        currentThreadTs: params.currentThreadTs,
        currentMessageId: params.currentMessageId,
        accountId: params.currentAccountId,
        sessionKey: params.sessionKey,
        sessionId: params.sessionId,
        agentId: params.agentId,
        requesterSenderId: params.requesterSenderId,
      })) {
        allActions.add(action);
      }
    }
    return Array.from(allActions);
  }
  val actions = listChannelMessageActions(params.cfg);
  return actions.length > 0 ? actions : ["send"];
}

fun resolveIncludeCapability(
  params: {
    cfg: OpenClawConfig;
    currentChannelProvider?: string;
    currentChannelId?: string;
    currentThreadTs?: string;
    currentMessageId?: string | number;
    currentAccountId?: string;
    sessionKey?: string;
    sessionId?: string;
    agentId?: string;
    requesterSenderId?: string;
  },
  capability: ChannelMessageCapability,
): boolean {
  val currentChannel = normalizeMessageChannel(params.currentChannelProvider);
  if (currentChannel) {
    return channelSupportsMessageCapabilityForChannel(
      {
        cfg: params.cfg,
        channel: currentChannel,
        currentChannelId: params.currentChannelId,
        currentThreadTs: params.currentThreadTs,
        currentMessageId: params.currentMessageId,
        accountId: params.currentAccountId,
        sessionKey: params.sessionKey,
        sessionId: params.sessionId,
        agentId: params.agentId,
        requesterSenderId: params.requesterSenderId,
      },
      capability,
    );
  }
  return channelSupportsMessageCapability(params.cfg, capability);
}

fun resolveIncludeInteractive(params: {
  cfg: OpenClawConfig;
  currentChannelProvider?: string;
  currentChannelId?: string;
  currentThreadTs?: string;
  currentMessageId?: string | number;
  currentAccountId?: string;
  sessionKey?: string;
  sessionId?: string;
  agentId?: string;
  requesterSenderId?: string;
}): boolean {
  return resolveIncludeCapability(params, "interactive");
}

fun buildMessageToolSchema(params: {
  cfg: OpenClawConfig;
  currentChannelProvider?: string;
  currentChannelId?: string;
  currentThreadTs?: string;
  currentMessageId?: string | number;
  currentAccountId?: string;
  sessionKey?: string;
  sessionId?: string;
  agentId?: string;
  requesterSenderId?: string;
}) {
  val actions = resolveMessageToolSchemaActions(params);
  val includeInteractive = resolveIncludeInteractive(params);
  val extraProperties = resolveChannelMessageToolSchemaProperties({
    cfg: params.cfg,
    channel: normalizeMessageChannel(params.currentChannelProvider),
    currentChannelId: params.currentChannelId,
    currentThreadTs: params.currentThreadTs,
    currentMessageId: params.currentMessageId,
    accountId: params.currentAccountId,
    sessionKey: params.sessionKey,
    sessionId: params.sessionId,
    agentId: params.agentId,
    requesterSenderId: params.requesterSenderId,
  });
  return buildMessageToolSchemaFromActions(actions.length > 0 ? actions : ["send"], {
    includeInteractive,
    extraProperties,
  });
}

fun resolveAgentAccountId(value?: string): string | null {
  val trimmed = value?.trim();
  if (!trimmed) {
    return null;
  }
  return normalizeAccountId(trimmed);
}

fun buildMessageToolDescription(options?: {
  config?: OpenClawConfig;
  currentChannel?: string;
  currentChannelId?: string;
  currentThreadTs?: string;
  currentMessageId?: string | number;
  currentAccountId?: string;
  sessionKey?: string;
  sessionId?: string;
  agentId?: string;
  requesterSenderId?: string;
}): string {
  val baseDescription = "Send, delete, and manage messages via channel plugins.";
  val resolvedOptions = options ?: {};
  val currentChannel = normalizeMessageChannel(resolvedOptions.currentChannel);

  // If we have a current channel, show its actions and list other configured channels
  if (currentChannel) {
    val channelActions = listChannelSupportedActions({
      cfg: resolvedOptions.config,
      channel: currentChannel,
      currentChannelId: resolvedOptions.currentChannelId,
      currentThreadTs: resolvedOptions.currentThreadTs,
      currentMessageId: resolvedOptions.currentMessageId,
      accountId: resolvedOptions.currentAccountId,
      sessionKey: resolvedOptions.sessionKey,
      sessionId: resolvedOptions.sessionId,
      agentId: resolvedOptions.agentId,
      requesterSenderId: resolvedOptions.requesterSenderId,
    });
    if (channelActions.length > 0) {
      // Always include "send" as a base action
      val allActions = new Set(["send", ...channelActions]);
      val actionList = Array.from(allActions).toSorted().join(", ");
      var desc = `${baseDescription} Current channel (${currentChannel}) supports: ${actionList}.`;

      // Include other configured channels so cron/isolated agents can discover them
      val otherChannels: string[] = [];
      for (val plugin of listChannelPlugins()) {
        if (plugin.id == currentChannel) {
          continue;
        }
        val actions = listChannelSupportedActions({
          cfg: resolvedOptions.config,
          channel: plugin.id,
          currentChannelId: resolvedOptions.currentChannelId,
          currentThreadTs: resolvedOptions.currentThreadTs,
          currentMessageId: resolvedOptions.currentMessageId,
          accountId: resolvedOptions.currentAccountId,
          sessionKey: resolvedOptions.sessionKey,
          sessionId: resolvedOptions.sessionId,
          agentId: resolvedOptions.agentId,
          requesterSenderId: resolvedOptions.requesterSenderId,
        });
        if (actions.length > 0) {
          val all = new Set(["send", ...actions]);
          otherChannels.push(`${plugin.id} (${Array.from(all).toSorted().join(", ")})`);
        }
      }
      if (otherChannels.length > 0) {
        desc += ` Other configured channels: ${otherChannels.join(", ")}.`;
      }

      return desc;
    }
  }

  // Fallback to generic description with all configured actions
  if (resolvedOptions.config) {
    val actions = listChannelMessageActions(resolvedOptions.config);
    if (actions.length > 0) {
      return `${baseDescription} Supports actions: ${actions.join(", ")}.`;
    }
  }

  return `${baseDescription} Supports actions: send, delete, react, poll, pin, threads, and more.`;
}

fun createMessageTool(options?: MessageToolOptions): AnyAgentTool {
  val agentAccountId = resolveAgentAccountId(options?.agentAccountId);
  val resolvedAgentId = options?.agentSessionKey
    ? resolveSessionAgentId({
        sessionKey: options.agentSessionKey,
        config: options?.config,
      })
    : null;
  val schema = options?.config
    ? buildMessageToolSchema({
        cfg: options.config,
        currentChannelProvider: options.currentChannelProvider,
        currentChannelId: options.currentChannelId,
        currentThreadTs: options.currentThreadTs,
        currentMessageId: options.currentMessageId,
        currentAccountId: agentAccountId,
        sessionKey: options.agentSessionKey,
        sessionId: options.sessionId,
        agentId: resolvedAgentId,
        requesterSenderId: options.requesterSenderId,
      })
    : MessageToolSchema;
  val description = buildMessageToolDescription({
    config: options?.config,
    currentChannel: options?.currentChannelProvider,
    currentChannelId: options?.currentChannelId,
    currentThreadTs: options?.currentThreadTs,
    currentMessageId: options?.currentMessageId,
    currentAccountId: agentAccountId,
    sessionKey: options?.agentSessionKey,
    sessionId: options?.sessionId,
    agentId: resolvedAgentId,
    requesterSenderId: options?.requesterSenderId,
  });

  return {
    label: "Message",
    name: "message",
    description,
    parameters: schema,
    execute: async (_toolCallId, args, signal) => {
      // Check if already aborted before doing any work
      if (signal?.aborted) {
        val err = new Error("Message send aborted");
        err.name = "AbortError";
        throw err;
      }
      // Shallow-copy so we don't mutate the original event args (used for logging/dedup).
      val params = { ...(args as Record<string, unknown>) };

      // Strip reasoning tags from text fields — models may include <think>…</think>
      // in tool arguments, and the messaging tool send path has no other tag filtering.
      for (val field of ["text", "content", "message", "caption"]) {
        if (typeof params[field] == "string") {
          params[field] = stripReasoningTagsFromText(params[field]);
        }
      }

      val action = readStringParam(params, "action", {
        required: true,
      }) as ChannelMessageActionName;
      var cfg = options?.config;
      if (!cfg) {
        val loadedRaw = loadConfig();
        val scope = resolveMessageSecretScope({
          channel: params.channel,
          target: params.target,
          targets: params.targets,
          fallbackChannel: options?.currentChannelProvider,
          accountId: params.accountId,
          fallbackAccountId: agentAccountId,
        });
        val scopedTargets = getScopedChannelsCommandSecretTargets({
          config: loadedRaw,
          channel: scope.channel,
          accountId: scope.accountId,
        });
        cfg = (
          await resolveCommandSecretRefsViaGateway({
            config: loadedRaw,
            commandName: "tools.message",
            targetIds: scopedTargets.targetIds,
            ...(scopedTargets.allowedPaths ? { allowedPaths: scopedTargets.allowedPaths } : {}),
            mode: "enforce_resolved",
          })
        ).resolvedConfig;
      }
      val requireExplicitTarget = options?.requireExplicitTarget == true;
      if (requireExplicitTarget && actionNeedsExplicitTarget(action)) {
        val explicitTarget =
          (typeof params.target == "string" && params.target.trim().length > 0) ||
          (typeof params.to == "string" && params.to.trim().length > 0) ||
          (typeof params.channelId == "string" && params.channelId.trim().length > 0) ||
          (Array.isArray(params.targets) &&
            params.targets.some((value) => typeof value == "string" && value.trim().length > 0));
        if (!explicitTarget) {
          throw Error(
            "Explicit message target required for this run. Provide target/targets (and channel when needed).",
          );
        }
      }

      val accountId = readStringParam(params, "accountId") ?: agentAccountId;
      if (accountId) {
        params.accountId = accountId;
      }

      val gatewayResolved = resolveGatewayOptions({
        gatewayUrl: readStringParam(params, "gatewayUrl", { trim: false }),
        gatewayToken: readStringParam(params, "gatewayToken", { trim: false }),
        timeoutMs: readNumberParam(params, "timeoutMs"),
      });
      val gateway = {
        url: gatewayResolved.url,
        token: gatewayResolved.token,
        timeoutMs: gatewayResolved.timeoutMs,
        clientName: GATEWAY_CLIENT_IDS.GATEWAY_CLIENT,
        clientDisplayName: "agent",
        mode: GATEWAY_CLIENT_MODES.BACKEND,
      };
      val hasCurrentMessageId =
        typeof options?.currentMessageId == "number" ||
        (typeof options?.currentMessageId == "string" &&
          options.currentMessageId.trim().length > 0);

      val toolContext =
        options?.currentChannelId ||
        options?.currentChannelProvider ||
        options?.currentThreadTs ||
        hasCurrentMessageId ||
        options?.replyToMode ||
        options?.hasRepliedRef
          ? {
              currentChannelId: options?.currentChannelId,
              currentChannelProvider: options?.currentChannelProvider,
              currentThreadTs: options?.currentThreadTs,
              currentMessageId: options?.currentMessageId,
              replyToMode: options?.replyToMode,
              hasRepliedRef: options?.hasRepliedRef,
              // Direct tool invocations should not add cross-context decoration.
              // The agent is composing a message, not forwarding from another chat.
              skipCrossContextDecoration: true,
            }
          : null;

      val result = await runMessageAction({
        cfg,
        action,
        params,
        defaultAccountId: accountId ?: null,
        requesterSenderId: options?.requesterSenderId,
        gateway,
        toolContext,
        sessionKey: options?.agentSessionKey,
        sessionId: options?.sessionId,
        agentId: resolvedAgentId,
        sandboxRoot: options?.sandboxRoot,
        abortSignal: signal,
      });

      val toolResult = getToolResult(result);
      if (toolResult) {
        return toolResult;
      }
      return jsonResult(result.payload);
    },
  };
}
