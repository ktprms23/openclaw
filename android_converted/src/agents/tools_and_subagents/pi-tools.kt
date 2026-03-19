package agents.tools_and_subagents

// Converted from src/agents/pi-tools.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { codingTools, createReadTool, readTool } from "@mariozechner/pi-coding-agent";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import type { ModelCompatConfig } from "../config/types.models.js";
// TODO: TypeScript import retained for manual wiring: import type { ToolLoopDetectionConfig } from "../config/types.tools.js";
// TODO: TypeScript import retained for manual wiring: import { resolveMergedSafeBinProfileFixtures } from "../infra/exec-safe-bin-runtime-policy.js";
// TODO: TypeScript import retained for manual wiring: import { logWarn } from "../logger.js";
// TODO: TypeScript import retained for manual wiring: import { getPluginToolMeta } from "../plugins/tools.js";
// TODO: TypeScript import retained for manual wiring: import { isSubagentSessionKey } from "../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { resolveGatewayMessageChannel } from "../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { resolveAgentConfig } from "./agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import { createApplyPatchTool } from "./apply-patch.js";
// TODO: TypeScript import retained for manual wiring: import {
  createExecTool,
  createProcessTool,
  type ExecToolDefaults,
  type ProcessToolDefaults,
} from "./bash-tools.js"
// TODO: TypeScript import retained for manual wiring: import { listChannelAgentTools } from "./channel-tools.js";
// TODO: TypeScript import retained for manual wiring: import { resolveImageSanitizationLimits } from "./image-sanitization.js";
// TODO: TypeScript import retained for manual wiring: import type { ModelAuthMode } from "./model-auth.js";
// TODO: TypeScript import retained for manual wiring: import { hasNativeWebSearchTool } from "./model-compat.js";
// TODO: TypeScript import retained for manual wiring: import { createOpenClawTools } from "./openclaw-tools.js";
// TODO: TypeScript import retained for manual wiring: import { wrapToolWithAbortSignal } from "./pi-tools.abort.js";
// TODO: TypeScript import retained for manual wiring: import { wrapToolWithBeforeToolCallHook } from "./pi-tools.before-tool-call.js";
// TODO: TypeScript import retained for manual wiring: import {
  isToolAllowedByPolicies,
  resolveEffectiveToolPolicy,
  resolveGroupToolPolicy,
  resolveSubagentToolPolicyForSession,
} from "./pi-tools.policy.js"
// TODO: TypeScript import retained for manual wiring: import {
  assertRequiredParams,
  createHostWorkspaceEditTool,
  createHostWorkspaceWriteTool,
  createOpenClawReadTool,
  createSandboxedEditTool,
  createSandboxedReadTool,
  createSandboxedWriteTool,
  normalizeToolParams,
  patchToolSchemaForClaudeCompatibility,
  wrapToolMemoryFlushAppendOnlyWrite,
  wrapToolWorkspaceRootGuard,
  wrapToolWorkspaceRootGuardWithOptions,
  wrapToolParamNormalization,
} from "./pi-tools.read.js"
// TODO: TypeScript import retained for manual wiring: import { cleanToolSchemaForGemini, normalizeToolParameters } from "./pi-tools.schema.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./pi-tools.types.js";
// TODO: TypeScript import retained for manual wiring: import type { SandboxContext } from "./sandbox.js";
// TODO: TypeScript import retained for manual wiring: import { createToolFsPolicy, resolveToolFsConfig } from "./tool-fs-policy.js";
// TODO: TypeScript import retained for manual wiring: import {
  applyToolPolicyPipeline,
  buildDefaultToolPolicyPipelineSteps,
} from "./tool-policy-pipeline.js"
// TODO: TypeScript import retained for manual wiring: import {
  applyOwnerOnlyToolPolicy,
  collectExplicitAllowlist,
  mergeAlsoAllowPolicy,
  resolveToolProfilePolicy,
} from "./tool-policy.js"
// TODO: TypeScript import retained for manual wiring: import { resolveWorkspaceRoot } from "./workspace-dir.js";

fun isOpenAIProvider(provider?: String) {
  val normalized = provider?.trim().toLowerCase()
  return normalized == "openai" || normalized == "openai-codex"
}

val TOOL_DENY_BY_MESSAGE_PROVIDER: Readonly<MutableMap<String, val List<String>>> = {
  voice: ["tts"],
}
val TOOL_DENY_FOR_XAI_PROVIDERS = mutableSetOf(["web_search"])
val MEMORY_FLUSH_ALLOWED_TOOL_NAMES = mutableSetOf(["read", "write"])

fun normalizeMessageProvider(messageProvider?: String): String? {
  val normalized = messageProvider?.trim().toLowerCase()
  return normalized && normalized.length > 0 ? normalized : null
}

fun applyMessageProviderToolPolicy(
  tools: List<AnyAgentTool>,
  messageProvider?: String,
): List<AnyAgentTool> {
  val normalizedProvider = normalizeMessageProvider(messageProvider)
  if (!normalizedProvider) {
    return tools
  }
  val deniedTools = TOOL_DENY_BY_MESSAGE_PROVIDER[normalizedProvider]
  if (!deniedTools || deniedTools.length == 0) {
    return tools
  }
  val deniedSet = mutableSetOf(deniedTools)
  return tools.filter((tool) -> !deniedSet.has(tool.name))
}

fun applyModelProviderToolPolicy(
  tools: List<AnyAgentTool>,
  params?: { modelCompat?: ModelCompatConfig },
): List<AnyAgentTool> {
  if (!hasNativeWebSearchTool(params?.modelCompat)) {
    return tools
  }
  // Models with a native web_search tool cannot receive OpenClaw's
  // web_search at the same time or the request will collide.
  return tools.filter((tool) -> !TOOL_DENY_FOR_XAI_PROVIDERS.has(tool.name))
}

fun isApplyPatchAllowedForModel(params: {
  modelProvider?: String
  modelId?: String
  allowModels?: List<String>
}) {
  val allowModels = Array.isArray(params.allowModels) ? params.allowModels : []
  if (allowModels.length == 0) {
    return true
  }
  val modelId = params.modelId?.trim()
  if (!modelId) {
    return false
  }
  val normalizedModelId = modelId.toLowerCase()
  val provider = params.modelProvider?.trim().toLowerCase()
  val normalizedFull =
    provider && !normalizedModelId.includes("/")
      ? `${provider}/${normalizedModelId}`
      : normalizedModelId
  return allowModels.some((entry) {
    val normalized = entry.trim().toLowerCase()
    if (!normalized) {
      return false
    }
    return normalized == normalizedModelId || normalized == normalizedFull
  })
}

fun resolveExecConfig(params: { cfg?: OpenClawConfig agentId?: String }) {
  val cfg = params.cfg
  val globalExec = cfg?.tools?.exec
  val agentExec =
    cfg && params.agentId ? resolveAgentConfig(cfg, params.agentId)?.tools?.exec : null
  return {
    host: agentExec?.host ?: globalExec?.host,
    security: agentExec?.security ?: globalExec?.security,
    ask: agentExec?.ask ?: globalExec?.ask,
    node: agentExec?.node ?: globalExec?.node,
    pathPrepend: agentExec?.pathPrepend ?: globalExec?.pathPrepend,
    safeBins: agentExec?.safeBins ?: globalExec?.safeBins,
    safeBinTrustedDirs: agentExec?.safeBinTrustedDirs ?: globalExec?.safeBinTrustedDirs,
    safeBinProfiles: resolveMergedSafeBinProfileFixtures({
      global: globalExec,
      local: agentExec,
    }),
    backgroundMs: agentExec?.backgroundMs ?: globalExec?.backgroundMs,
    timeoutSec: agentExec?.timeoutSec ?: globalExec?.timeoutSec,
    approvalRunningNoticeMs:
      agentExec?.approvalRunningNoticeMs ?: globalExec?.approvalRunningNoticeMs,
    cleanupMs: agentExec?.cleanupMs ?: globalExec?.cleanupMs,
    notifyOnExit: agentExec?.notifyOnExit ?: globalExec?.notifyOnExit,
    notifyOnExitEmptySuccess:
      agentExec?.notifyOnExitEmptySuccess ?: globalExec?.notifyOnExitEmptySuccess,
    applyPatch: agentExec?.applyPatch ?: globalExec?.applyPatch,
  }
}

fun resolveToolLoopDetectionConfig(params: {
  cfg?: OpenClawConfig
  agentId?: String
}): ToolLoopDetectionConfig? {
  val global = params.cfg?.tools?.loopDetection
  val agent =
    params.agentId && params.cfg
      ? resolveAgentConfig(params.cfg, params.agentId)?.tools?.loopDetection
      : null

  if (!agent) {
    return global
  }
  if (!global) {
    return agent
  }

  return {
    ...global,
    ...agent,
    detectors: {
      ...global.detectors,
      ...agent.detectors,
    },
  }
}

val __testing = {
  cleanToolSchemaForGemini,
  normalizeToolParams,
  patchToolSchemaForClaudeCompatibility,
  wrapToolParamNormalization,
  assertRequiredParams,
  applyModelProviderToolPolicy,
} as /* TODO */ val

fun createOpenClawCodingTools(options?: {
  agentId?: String
  exec?: ExecToolDefaults & ProcessToolDefaults
  messageProvider?: String
  agentAccountId?: String
  messageTo?: String
  messageThreadId?: String | Double
  sandbox?: SandboxContext?
  sessionKey?: String
  /** Ephemeral session UUID — regenerated on /new and /reset. */
  sessionId?: String
  /** Stable run identifier for this agent invocation. */
  runId?: String
  /** What initiated this run (for trigger-specific tool restrictions). */
  trigger?: String
  /** Relative workspace path that memory-triggered writes may append to. */
  memoryFlushWritePath?: String
  agentDir?: String
  workspaceDir?: String
  /**
   * Workspace directory that spawned subagents should inherit.
   * When sandboxing uses a copied workspace (`ro` or `none`), workspaceDir is the
   * sandbox copy but subagents should inherit the real agent workspace instead.
   * Defaults to workspaceDir when not set.
   */
  spawnWorkspaceDir?: String
  config?: OpenClawConfig
  abortSignal?: AbortSignal /* TODO */
  /**
   * Provider of the currently selected model (used for provider-specific tool quirks).
   * Example: String /* "anthropic" */, "openai", "google", "openai-codex".
   */
  modelProvider?: String
  /** Model id for the current provider (used for model-specific tool gating). */
  modelId?: String
  /** Model context window in tokens (used to scale read-tool output budget). */
  modelContextWindowTokens?: Double
  /** Resolved runtime model compatibility hints. */
  modelCompat?: ModelCompatConfig
  /**
   * Auth mode for the current provider. We only need this for Anthropic OAuth
   * tool-name blocking quirks.
   */
  modelAuthMode?: ModelAuthMode
  /** Current channel ID for auto-threading (Slack). */
  currentChannelId?: String
  /** Current thread timestamp for auto-threading (Slack). */
  currentThreadTs?: String
  /** Current inbound message id for action fallbacks (e.g. Telegram react). */
  currentMessageId?: String | Double
  /** Group id for channel-level tool policy resolution. */
  groupId?: String?
  /** Group channel label (e.g. #general) for channel-level tool policy resolution. */
  groupChannel?: String?
  /** Group space label (e.g. guild/team id) for channel-level tool policy resolution. */
  groupSpace?: String?
  /** Parent session key for subagent group policy inheritance. */
  spawnedBy?: String?
  senderId?: String?
  senderName?: String?
  senderUsername?: String?
  senderE164?: String?
  /** Reply-to mode for Slack auto-threading. */
  replyToMode?: String /* "off" */ | "first" | "all"
  /** Mutable ref to track if a reply was sent (for "first" mode). */
  hasRepliedRef?: { value: Boolean }
  /** Allow plugin tools for this run to late-bind the gateway subagent. */
  allowGatewaySubagentBinding?: Boolean
  /** If true, the model has native vision capability */
  modelHasVision?: Boolean
  /** Require explicit message targets (no implicit last-route sends). */
  requireExplicitMessageTarget?: Boolean
  /** If true, omit the message tool from the tool list. */
  disableMessageTool?: Boolean
  /** Whether the sender is an owner (required for owner-only tools). */
  senderIsOwner?: Boolean
  /** Callback invoked when sessions_yield tool is called. */
  onYield?: (message: String) -> Deferred<Unit> | Unit
}): List<AnyAgentTool> {
  val execToolName = "exec"
  val sandbox = options?.sandbox?.enabled ? options.sandbox : null
  val isMemoryFlushRun = options?.trigger == "memory"
  if (isMemoryFlushRun && !options?.memoryFlushWritePath) {
    throw Error("memoryFlushWritePath required for memory-triggered tool runs")
  }
  val memoryFlushWritePath = isMemoryFlushRun ? options.memoryFlushWritePath : null
  val {
    agentId,
    globalPolicy,
    globalProviderPolicy,
    agentPolicy,
    agentProviderPolicy,
    profile,
    providerProfile,
    profileAlsoAllow,
    providerProfileAlsoAllow,
  } = resolveEffectiveToolPolicy({
    config: options?.config,
    sessionKey: options?.sessionKey,
    agentId: options?.agentId,
    modelProvider: options?.modelProvider,
    modelId: options?.modelId,
  })
  val groupPolicy = resolveGroupToolPolicy({
    config: options?.config,
    sessionKey: options?.sessionKey,
    spawnedBy: options?.spawnedBy,
    messageProvider: options?.messageProvider,
    groupId: options?.groupId,
    groupChannel: options?.groupChannel,
    groupSpace: options?.groupSpace,
    accountId: options?.agentAccountId,
    senderId: options?.senderId,
    senderName: options?.senderName,
    senderUsername: options?.senderUsername,
    senderE164: options?.senderE164,
  })
  val profilePolicy = resolveToolProfilePolicy(profile)
  val providerProfilePolicy = resolveToolProfilePolicy(providerProfile)

  val profilePolicyWithAlsoAllow = mergeAlsoAllowPolicy(profilePolicy, profileAlsoAllow)
  val providerProfilePolicyWithAlsoAllow = mergeAlsoAllowPolicy(
    providerProfilePolicy,
    providerProfileAlsoAllow,
  )
  // Prefer sessionKey for process isolation scope to prevent cross-session process visibility/killing.
  // Fallback to agentId if no sessionKey is available (e.g. legacy or global contexts).
  val scopeKey =
    options?.exec?.scopeKey ?: options?.sessionKey ?: (agentId ? `agent:${agentId}` : null)
  val subagentPolicy =
    isSubagentSessionKey(options?.sessionKey) && options?.sessionKey
      ? resolveSubagentToolPolicyForSession(options.config, options.sessionKey)
      : null
  val allowBackground = isToolAllowedByPolicies("process", [
    profilePolicyWithAlsoAllow,
    providerProfilePolicyWithAlsoAllow,
    globalPolicy,
    globalProviderPolicy,
    agentPolicy,
    agentProviderPolicy,
    groupPolicy,
    sandbox?.tools,
    subagentPolicy,
  ])
  val execConfig = resolveExecConfig({ cfg: options?.config, agentId })
  val fsConfig = resolveToolFsConfig({ cfg: options?.config, agentId })
  val fsPolicy = createToolFsPolicy({
    workspaceOnly: isMemoryFlushRun || fsConfig.workspaceOnly,
  })
  val sandboxRoot = sandbox?.workspaceDir
  val sandboxFsBridge = sandbox?.fsBridge
  val allowWorkspaceWrites = sandbox?.workspaceAccess != "ro"
  val workspaceRoot = resolveWorkspaceRoot(options?.workspaceDir)
  val workspaceOnly = fsPolicy.workspaceOnly
  val applyPatchConfig = execConfig.applyPatch
  // Secure by default: apply_patch is workspace-contained unless explicitly disabled.
  // (tools.fs.workspaceOnly is a separate umbrella flag for read/write/edit/apply_patch.)
  val applyPatchWorkspaceOnly = workspaceOnly || applyPatchConfig?.workspaceOnly != false
  val applyPatchEnabled =
    !!applyPatchConfig?.enabled &&
    isOpenAIProvider(options?.modelProvider) &&
    isApplyPatchAllowedForModel({
      modelProvider: options?.modelProvider,
      modelId: options?.modelId,
      allowModels: applyPatchConfig?.allowModels,
    })

  if (sandboxRoot && !sandboxFsBridge) {
    throw Error("Sandbox filesystem bridge is unavailable.")
  }
  val imageSanitization = resolveImageSanitizationLimits(options?.config)

  val base = { codingTools as /* TODO */ Any? as /* TODO */ List<AnyAgentTool>).flatMap((tool ->
    if (tool.name == readTool.name) {
      if (sandboxRoot) {
        val sandboxed = createSandboxedReadTool({
          root: sandboxRoot,
          bridge: sandboxFsBridge!,
          modelContextWindowTokens: options?.modelContextWindowTokens,
          imageSanitization,
        })
        return [
          workspaceOnly
            ? wrapToolWorkspaceRootGuardWithOptions(sandboxed, sandboxRoot, {
                containerWorkdir: sandbox.containerWorkdir,
              })
            : sandboxed,
        ]
      }
      val freshReadTool = createReadTool(workspaceRoot)
      val wrapped = createOpenClawReadTool(freshReadTool, {
        modelContextWindowTokens: options?.modelContextWindowTokens,
        imageSanitization,
      })
      return [workspaceOnly ? wrapToolWorkspaceRootGuard(wrapped, workspaceRoot) : wrapped]
    }
    if (tool.name == "bash" || tool.name == execToolName) {
      return []
    }
    if (tool.name == "write") {
      if (sandboxRoot) {
        return []
      }
      val wrapped = createHostWorkspaceWriteTool(workspaceRoot, { workspaceOnly })
      return [workspaceOnly ? wrapToolWorkspaceRootGuard(wrapped, workspaceRoot) : wrapped]
    }
    if (tool.name == "edit") {
      if (sandboxRoot) {
        return []
      }
      val wrapped = createHostWorkspaceEditTool(workspaceRoot, { workspaceOnly })
      return [workspaceOnly ? wrapToolWorkspaceRootGuard(wrapped, workspaceRoot) : wrapped]
    }
    return [tool]
  })
  val { cleanupMs: cleanupMsOverride, ...execDefaults } = options?.exec ?: {}
  val execTool = createExecTool({
    ...execDefaults,
    host: options?.exec?.host ?: execConfig.host,
    security: options?.exec?.security ?: execConfig.security,
    ask: options?.exec?.ask ?: execConfig.ask,
    node: options?.exec?.node ?: execConfig.node,
    pathPrepend: options?.exec?.pathPrepend ?: execConfig.pathPrepend,
    safeBins: options?.exec?.safeBins ?: execConfig.safeBins,
    safeBinTrustedDirs: options?.exec?.safeBinTrustedDirs ?: execConfig.safeBinTrustedDirs,
    safeBinProfiles: options?.exec?.safeBinProfiles ?: execConfig.safeBinProfiles,
    agentId,
    cwd: workspaceRoot,
    allowBackground,
    scopeKey,
    sessionKey: options?.sessionKey,
    messageProvider: options?.messageProvider,
    currentChannelId: options?.currentChannelId,
    currentThreadTs: options?.currentThreadTs,
    accountId: options?.agentAccountId,
    backgroundMs: options?.exec?.backgroundMs ?: execConfig.backgroundMs,
    timeoutSec: options?.exec?.timeoutSec ?: execConfig.timeoutSec,
    approvalRunningNoticeMs:
      options?.exec?.approvalRunningNoticeMs ?: execConfig.approvalRunningNoticeMs,
    notifyOnExit: options?.exec?.notifyOnExit ?: execConfig.notifyOnExit,
    notifyOnExitEmptySuccess:
      options?.exec?.notifyOnExitEmptySuccess ?: execConfig.notifyOnExitEmptySuccess,
    sandbox: sandbox
      ? {
          containerName: sandbox.containerName,
          workspaceDir: sandbox.workspaceDir,
          containerWorkdir: sandbox.containerWorkdir,
          env: sandbox.backend?.env ?: sandbox.docker.env,
          buildExecSpec: sandbox.backend?.buildExecSpec.bind(sandbox.backend),
          finalizeExec: sandbox.backend?.finalizeExec?.bind(sandbox.backend),
        }
      : null,
  })
  val processTool = createProcessTool({
    cleanupMs: cleanupMsOverride ?: execConfig.cleanupMs,
    scopeKey,
  })
  val applyPatchTool =
    !applyPatchEnabled || (sandboxRoot && !allowWorkspaceWrites)
      ? null
      : createApplyPatchTool({
          cwd: sandboxRoot ?: workspaceRoot,
          sandbox:
            sandboxRoot && allowWorkspaceWrites
              ? { root: sandboxRoot, bridge: sandboxFsBridge! }
              : null,
          workspaceOnly: applyPatchWorkspaceOnly,
        })
  val tools: List<AnyAgentTool> = [
    ...base,
    ...(sandboxRoot
      ? allowWorkspaceWrites
        ? [
            workspaceOnly
              ? wrapToolWorkspaceRootGuardWithOptions(
                  createSandboxedEditTool({ root: sandboxRoot, bridge: sandboxFsBridge! }),
                  sandboxRoot,
                  {
                    containerWorkdir: sandbox.containerWorkdir,
                  },
                )
              : createSandboxedEditTool({ root: sandboxRoot, bridge: sandboxFsBridge! }),
            workspaceOnly
              ? wrapToolWorkspaceRootGuardWithOptions(
                  createSandboxedWriteTool({ root: sandboxRoot, bridge: sandboxFsBridge! }),
                  sandboxRoot,
                  {
                    containerWorkdir: sandbox.containerWorkdir,
                  },
                )
              : createSandboxedWriteTool({ root: sandboxRoot, bridge: sandboxFsBridge! }),
          ]
        : []
      : []),
    ...(applyPatchTool ? [applyPatchTool as /* TODO */ Any? as /* TODO */ AnyAgentTool] : []),
    execTool as /* TODO */ Any? as /* TODO */ AnyAgentTool,
    processTool as /* TODO */ Any? as /* TODO */ AnyAgentTool,
    // Channel docking: include channel-defined agent tools (login, etc.).
    ...listChannelAgentTools({ cfg: options?.config }),
    ...createOpenClawTools({
      sandboxBrowserBridgeUrl: sandbox?.browser?.bridgeUrl,
      allowHostBrowserControl: sandbox ? sandbox.browserAllowHostControl : true,
      agentSessionKey: options?.sessionKey,
      agentChannel: resolveGatewayMessageChannel(options?.messageProvider),
      agentAccountId: options?.agentAccountId,
      agentTo: options?.messageTo,
      agentThreadId: options?.messageThreadId,
      agentGroupId: options?.groupId ?: null,
      agentGroupChannel: options?.groupChannel ?: null,
      agentGroupSpace: options?.groupSpace ?: null,
      agentDir: options?.agentDir,
      sandboxRoot,
      sandboxFsBridge,
      fsPolicy,
      workspaceDir: workspaceRoot,
      spawnWorkspaceDir: options?.spawnWorkspaceDir
        ? resolveWorkspaceRoot(options.spawnWorkspaceDir)
        : null,
      sandboxed: !!sandbox,
      config: options?.config,
      pluginToolAllowlist: collectExplicitAllowlist([
        profilePolicy,
        providerProfilePolicy,
        globalPolicy,
        globalProviderPolicy,
        agentPolicy,
        agentProviderPolicy,
        groupPolicy,
        sandbox?.tools,
        subagentPolicy,
      ]),
      currentChannelId: options?.currentChannelId,
      currentThreadTs: options?.currentThreadTs,
      currentMessageId: options?.currentMessageId,
      replyToMode: options?.replyToMode,
      hasRepliedRef: options?.hasRepliedRef,
      modelHasVision: options?.modelHasVision,
      requireExplicitMessageTarget: options?.requireExplicitMessageTarget,
      disableMessageTool: options?.disableMessageTool,
      requesterAgentIdOverride: agentId,
      requesterSenderId: options?.senderId,
      senderIsOwner: options?.senderIsOwner,
      sessionId: options?.sessionId,
      onYield: options?.onYield,
      allowGatewaySubagentBinding: options?.allowGatewaySubagentBinding,
    }),
  ]
  val toolsForMemoryFlush =
    isMemoryFlushRun && memoryFlushWritePath
      ? tools.flatMap((tool) {
          if (!MEMORY_FLUSH_ALLOWED_TOOL_NAMES.has(tool.name)) {
            return []
          }
          if (tool.name == "write") {
            return [
              wrapToolMemoryFlushAppendOnlyWrite(tool, {
                root: sandboxRoot ?: workspaceRoot,
                relativePath: memoryFlushWritePath,
                containerWorkdir: sandbox?.containerWorkdir,
                sandbox:
                  sandboxRoot && sandboxFsBridge
                    ? { root: sandboxRoot, bridge: sandboxFsBridge }
                    : null,
              }),
            ]
          }
          return [tool]
        })
      : tools
  val toolsForMessageProvider = applyMessageProviderToolPolicy(
    toolsForMemoryFlush,
    options?.messageProvider,
  )
  val toolsForModelProvider = applyModelProviderToolPolicy(toolsForMessageProvider, {
    modelCompat: options?.modelCompat,
  })
  // Security: treat Any?/null as /* TODO */ unauthorized (opt-in, not opt-out)
  val senderIsOwner = options?.senderIsOwner == true
  val toolsByAuthorization = applyOwnerOnlyToolPolicy(toolsForModelProvider, senderIsOwner)
  val subagentFiltered = applyToolPolicyPipeline({
    tools: toolsByAuthorization,
    toolMeta: (tool) -> getPluginToolMeta(tool),
    warn: logWarn,
    steps: [
      ...buildDefaultToolPolicyPipelineSteps({
        profilePolicy: profilePolicyWithAlsoAllow,
        profile,
        providerProfilePolicy: providerProfilePolicyWithAlsoAllow,
        providerProfile,
        globalPolicy,
        globalProviderPolicy,
        agentPolicy,
        agentProviderPolicy,
        groupPolicy,
        agentId,
      }),
      { policy: sandbox?.tools, label: String /* "sandbox tools.allow" */ },
      { policy: subagentPolicy, label: String /* "subagent tools.allow" */ },
    ],
  })
  // Always normalize tool JSON Schemas before handing them to pi-agent/pi-ai.
  // Without this, some providers (notably OpenAI) will reject root-level union schemas.
  // Provider-specific cleaning: Gemini needs constraint keywords stripped, but Anthropic expects them.
  val normalized = subagentFiltered.map((tool) ->
    normalizeToolParameters(tool, {
      modelProvider: options?.modelProvider,
      modelId: options?.modelId,
      modelCompat: options?.modelCompat,
    }),
  )
  val withHooks = normalized.map((tool) ->
    wrapToolWithBeforeToolCallHook(tool, {
      agentId,
      sessionKey: options?.sessionKey,
      sessionId: options?.sessionId,
      runId: options?.runId,
      loopDetection: resolveToolLoopDetectionConfig({ cfg: options?.config, agentId }),
    }),
  )
  val withAbort = options?.abortSignal
    ? withHooks.map((tool) -> wrapToolWithAbortSignal(tool, options.abortSignal))
    : withHooks

  // NOTE: Keep canonical (lowercase) tool names here.
  // pi-ai's Anthropic OAuth transport remaps tool names to Claude Code-style names
  // on the wire and maps them back for tool dispatch.
  return withAbort
}
