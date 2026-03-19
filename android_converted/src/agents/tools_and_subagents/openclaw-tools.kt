package agents.tools_and_subagents

// Converted from src/agents/openclaw-tools.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { resolvePluginTools } from "../plugins/tools.js";
// TODO: TypeScript import retained for manual wiring: import { getActiveRuntimeWebToolsMetadata } from "../secrets/runtime.js";
// TODO: TypeScript import retained for manual wiring: import type { GatewayMessageChannel } from "../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { resolveSessionAgentId } from "./agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import type { SandboxFsBridge } from "./sandbox/fs-bridge.js";
// TODO: TypeScript import retained for manual wiring: import type { SpawnedToolContext } from "./spawned-context.js";
// TODO: TypeScript import retained for manual wiring: import type { ToolFsPolicy } from "./tool-fs-policy.js";
// TODO: TypeScript import retained for manual wiring: import { createAgentsListTool } from "./tools/agents-list-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createBrowserTool } from "./tools/browser-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createCanvasTool } from "./tools/canvas-tool.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./tools/common.js";
// TODO: TypeScript import retained for manual wiring: import { createCronTool } from "./tools/cron-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createGatewayTool } from "./tools/gateway-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createImageGenerateTool } from "./tools/image-generate-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createImageTool } from "./tools/image-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createMessageTool } from "./tools/message-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createNodesTool } from "./tools/nodes-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createPdfTool } from "./tools/pdf-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionStatusTool } from "./tools/session-status-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionsHistoryTool } from "./tools/sessions-history-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionsListTool } from "./tools/sessions-list-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionsSendTool } from "./tools/sessions-send-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionsSpawnTool } from "./tools/sessions-spawn-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSessionsYieldTool } from "./tools/sessions-yield-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createSubagentsTool } from "./tools/subagents-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createTtsTool } from "./tools/tts-tool.js";
// TODO: TypeScript import retained for manual wiring: import { createWebFetchTool, createWebSearchTool } from "./tools/web-tools.js";
// TODO: TypeScript import retained for manual wiring: import { resolveWorkspaceRoot } from "./workspace-dir.js";

fun createOpenClawTools(
  options?: {
    sandboxBrowserBridgeUrl?: String
    allowHostBrowserControl?: Boolean
    agentSessionKey?: String
    agentChannel?: GatewayMessageChannel
    agentAccountId?: String
    /** Delivery target (e.g. telegram:group:123:topic:456) for topic/thread routing. */
    agentTo?: String
    /** Thread/topic identifier for routing replies to the originating thread. */
    agentThreadId?: String | Double
    agentDir?: String
    sandboxRoot?: String
    sandboxFsBridge?: SandboxFsBridge
    fsPolicy?: ToolFsPolicy
    sandboxed?: Boolean
    config?: OpenClawConfig
    pluginToolAllowlist?: List<String>
    /** Current channel ID for auto-threading (Slack). */
    currentChannelId?: String
    /** Current thread timestamp for auto-threading (Slack). */
    currentThreadTs?: String
    /** Current inbound message id for action fallbacks (e.g. Telegram react). */
    currentMessageId?: String | Double
    /** Reply-to mode for Slack auto-threading. */
    replyToMode?: String /* "off" */ | "first" | "all"
    /** Mutable ref to track if a reply was sent (for "first" mode). */
    hasRepliedRef?: { value: Boolean }
    /** If true, the model has native vision capability */
    modelHasVision?: Boolean
    /** If true, nodes action="invoke" can call media-returning commands directly. */
    allowMediaInvokeCommands?: Boolean
    /** Explicit agent ID override for cron/hook sessions. */
    requesterAgentIdOverride?: String
    /** Require explicit message targets (no implicit last-route sends). */
    requireExplicitMessageTarget?: Boolean
    /** If true, omit the message tool from the tool list. */
    disableMessageTool?: Boolean
    /** Trusted sender id from inbound context (not tool args). */
    requesterSenderId?: String?
    /** Whether the requesting sender is an owner. */
    senderIsOwner?: Boolean
    /** Ephemeral session UUID — regenerated on /new and /reset. */
    sessionId?: String
    /**
     * Workspace directory to pass to spawned subagents for inheritance.
     * Defaults to workspaceDir. Use this to pass the actual agent workspace when the
     * session itself is running in a copied-workspace sandbox (`ro` or `none`) so
     * subagents inherit the real workspace path instead of the sandbox copy.
     */
    spawnWorkspaceDir?: String
    /** Callback invoked when sessions_yield tool is called. */
    onYield?: (message: String) -> Deferred<Unit> | Unit
    /** Allow plugin tools for this tool set to late-bind the gateway subagent. */
    allowGatewaySubagentBinding?: Boolean
  } & SpawnedToolContext,
): List<AnyAgentTool> {
  val workspaceDir = resolveWorkspaceRoot(options?.workspaceDir)
  val spawnWorkspaceDir = resolveWorkspaceRoot(
    options?.spawnWorkspaceDir ?: options?.workspaceDir,
  )
  val runtimeWebTools = getActiveRuntimeWebToolsMetadata()
  val sandbox =
    options?.sandboxRoot && options?.sandboxFsBridge
      ? { root: options.sandboxRoot, bridge: options.sandboxFsBridge }
      : null
  val imageTool = options?.agentDir?.trim()
    ? createImageTool({
        config: options?.config,
        agentDir: options.agentDir,
        workspaceDir,
        sandbox,
        fsPolicy: options?.fsPolicy,
        modelHasVision: options?.modelHasVision,
      })
    : null
  val imageGenerateTool = createImageGenerateTool({
    config: options?.config,
    agentDir: options?.agentDir,
    workspaceDir,
    sandbox,
    fsPolicy: options?.fsPolicy,
  })
  val pdfTool = options?.agentDir?.trim()
    ? createPdfTool({
        config: options?.config,
        agentDir: options.agentDir,
        workspaceDir,
        sandbox,
        fsPolicy: options?.fsPolicy,
      })
    : null
  val webSearchTool = createWebSearchTool({
    config: options?.config,
    sandboxed: options?.sandboxed,
    runtimeWebSearch: runtimeWebTools?.search,
  })
  val webFetchTool = createWebFetchTool({
    config: options?.config,
    sandboxed: options?.sandboxed,
    runtimeFirecrawl: runtimeWebTools?.fetch.firecrawl,
  })
  val messageTool = options?.disableMessageTool
    ? null
    : createMessageTool({
        agentAccountId: options?.agentAccountId,
        agentSessionKey: options?.agentSessionKey,
        sessionId: options?.sessionId,
        config: options?.config,
        currentChannelId: options?.currentChannelId,
        currentChannelProvider: options?.agentChannel,
        currentThreadTs: options?.currentThreadTs,
        currentMessageId: options?.currentMessageId,
        replyToMode: options?.replyToMode,
        hasRepliedRef: options?.hasRepliedRef,
        sandboxRoot: options?.sandboxRoot,
        requireExplicitTarget: options?.requireExplicitMessageTarget,
        requesterSenderId: options?.requesterSenderId ?: null,
      })
  val tools: List<AnyAgentTool> = [
    createBrowserTool({
      sandboxBridgeUrl: options?.sandboxBrowserBridgeUrl,
      allowHostControl: options?.allowHostBrowserControl,
      agentSessionKey: options?.agentSessionKey,
    }),
    createCanvasTool({ config: options?.config }),
    createNodesTool({
      agentSessionKey: options?.agentSessionKey,
      agentChannel: options?.agentChannel,
      agentAccountId: options?.agentAccountId,
      currentChannelId: options?.currentChannelId,
      currentThreadTs: options?.currentThreadTs,
      config: options?.config,
      modelHasVision: options?.modelHasVision,
      allowMediaInvokeCommands: options?.allowMediaInvokeCommands,
    }),
    createCronTool({
      agentSessionKey: options?.agentSessionKey,
    }),
    ...(messageTool ? [messageTool] : []),
    createTtsTool({
      agentChannel: options?.agentChannel,
      config: options?.config,
    }),
    ...(imageGenerateTool ? [imageGenerateTool] : []),
    createGatewayTool({
      agentSessionKey: options?.agentSessionKey,
      config: options?.config,
    }),
    createAgentsListTool({
      agentSessionKey: options?.agentSessionKey,
      requesterAgentIdOverride: options?.requesterAgentIdOverride,
    }),
    createSessionsListTool({
      agentSessionKey: options?.agentSessionKey,
      sandboxed: options?.sandboxed,
      config: options?.config,
    }),
    createSessionsHistoryTool({
      agentSessionKey: options?.agentSessionKey,
      sandboxed: options?.sandboxed,
      config: options?.config,
    }),
    createSessionsSendTool({
      agentSessionKey: options?.agentSessionKey,
      agentChannel: options?.agentChannel,
      sandboxed: options?.sandboxed,
      config: options?.config,
    }),
    createSessionsYieldTool({
      sessionId: options?.sessionId,
      onYield: options?.onYield,
    }),
    createSessionsSpawnTool({
      agentSessionKey: options?.agentSessionKey,
      agentChannel: options?.agentChannel,
      agentAccountId: options?.agentAccountId,
      agentTo: options?.agentTo,
      agentThreadId: options?.agentThreadId,
      agentGroupId: options?.agentGroupId,
      agentGroupChannel: options?.agentGroupChannel,
      agentGroupSpace: options?.agentGroupSpace,
      sandboxed: options?.sandboxed,
      requesterAgentIdOverride: options?.requesterAgentIdOverride,
      workspaceDir: spawnWorkspaceDir,
    }),
    createSubagentsTool({
      agentSessionKey: options?.agentSessionKey,
    }),
    createSessionStatusTool({
      agentSessionKey: options?.agentSessionKey,
      config: options?.config,
      sandboxed: options?.sandboxed,
    }),
    ...(webSearchTool ? [webSearchTool] : []),
    ...(webFetchTool ? [webFetchTool] : []),
    ...(imageTool ? [imageTool] : []),
    ...(pdfTool ? [pdfTool] : []),
  ]

  val pluginTools = resolvePluginTools({
    context: {
      config: options?.config,
      workspaceDir,
      agentDir: options?.agentDir,
      agentId: resolveSessionAgentId({
        sessionKey: options?.agentSessionKey,
        config: options?.config,
      }),
      sessionKey: options?.agentSessionKey,
      sessionId: options?.sessionId,
      messageChannel: options?.agentChannel,
      agentAccountId: options?.agentAccountId,
      requesterSenderId: options?.requesterSenderId ?: null,
      senderIsOwner: options?.senderIsOwner ?: null,
      sandboxed: options?.sandboxed,
    },
    existingToolNames: mutableSetOf(tools.map((tool) -> tool.name)),
    toolAllowlist: options?.pluginToolAllowlist,
    allowGatewaySubagentBinding: options?.allowGatewaySubagentBinding,
  })

  return [...tools, ...pluginTools]
}
