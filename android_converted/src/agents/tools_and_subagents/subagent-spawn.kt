@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-spawn.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import { promises as fs } from "node:fs";
// TODO(port-deps): import { formatThinkingLevels, normalizeThinkLevel } from "../auto-reply/thinking.js";
// TODO(port-deps): import { DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH } from "../config/agent-limits.js";
// TODO(port-deps): import { loadConfig } from "../config/config.js";
// TODO(port-deps): import { callGateway } from "../gateway/call.js";
// TODO(port-deps): import { getGlobalHookRunner } from "../plugins/hook-runner-global.js";
// TODO(port-deps): import {
// TODO(port-deps): isValidAgentId,
// TODO(port-deps): isCronSessionKey,
// TODO(port-deps): normalizeAgentId,
// TODO(port-deps): parseAgentSessionKey,
// TODO(port-deps): } from "../routing/session-key.js";
// TODO(port-deps): import { normalizeDeliveryContext } from "../utils/delivery-context.js";
// TODO(port-deps): import { resolveAgentConfig } from "./agent-scope.js";
// TODO(port-deps): import { AGENT_LANE_SUBAGENT } from "./lanes.js";
// TODO(port-deps): import { resolveSubagentSpawnModelSelection } from "./model-selection.js";
// TODO(port-deps): import { resolveSandboxRuntimeStatus } from "./sandbox/runtime-status.js";
// TODO(port-deps): import {
// TODO(port-deps): mapToolContextToSpawnedRunMetadata,
// TODO(port-deps): normalizeSpawnedRunMetadata,
// TODO(port-deps): resolveSpawnedWorkspaceInheritance,
// TODO(port-deps): } from "./spawned-context.js";
// TODO(port-deps): import { buildSubagentSystemPrompt } from "./subagent-announce.js";
// TODO(port-deps): import {
// TODO(port-deps): decodeStrictBase64,
// TODO(port-deps): materializeSubagentAttachments,
// TODO(port-deps): type SubagentAttachmentReceiptFile,
// TODO(port-deps): } from "./subagent-attachments.js";
// TODO(port-deps): import { resolveSubagentCapabilities } from "./subagent-capabilities.js";
// TODO(port-deps): import { getSubagentDepthFromSessionStore } from "./subagent-depth.js";
// TODO(port-deps): import { countActiveRunsForSession, registerSubagentRun } from "./subagent-registry.js";
// TODO(port-deps): import { readStringParam } from "./tools/common.js";
// TODO(port-deps): import {
// TODO(port-deps): resolveDisplaySessionKey,
// TODO(port-deps): resolveInternalSessionKey,
// TODO(port-deps): resolveMainSessionAlias,
// TODO(port-deps): } from "./tools/sessions-helpers.js";

val SUBAGENT_SPAWN_MODES = ["run", "session"] as const;
typealias SpawnSubagentMode = (typeof SUBAGENT_SPAWN_MODES)[number]
val SUBAGENT_SPAWN_SANDBOX_MODES = ["inherit", "require"] as const;
typealias SpawnSubagentSandboxMode = (typeof SUBAGENT_SPAWN_SANDBOX_MODES)[number]

// export { decodeStrictBase64 };

typealias SpawnSubagentParams = Any /* TODO: translate TypeScript alias */

typealias SpawnSubagentContext = Any /* TODO: translate TypeScript alias */

val SUBAGENT_SPAWN_ACCEPTED_NOTE =
  "Auto-announce is push-based. After spawning children, do NOT call sessions_list, sessions_history, exec sleep, or any polling tool. Wait for completion events to arrive as user messages, track expected child session keys, and only send your final answer after ALL expected completions arrive. If a child completion event arrives AFTER your final answer, reply ONLY with NO_REPLY.";
val SUBAGENT_SPAWN_SESSION_ACCEPTED_NOTE =
  "thread-bound session stays active after this task; continue in-thread for follow-ups.";

typealias SpawnSubagentResult = Any /* TODO: translate TypeScript alias */

fun splitModelRef(ref?: string) {
  if (!ref) {
    return { provider: null, model: null };
  }
  val trimmed = ref.trim();
  if (!trimmed) {
    return { provider: null, model: null };
  }
  val [provider, model] = trimmed.split("/", 2);
  if (model) {
    return { provider, model };
  }
  return { provider: null, model: trimmed };
}

fun sanitizeMountPathHint(value?: string): string | null {
  val trimmed = value?.trim();
  if (!trimmed) {
    return null;
  }
  // Prevent prompt injection via control/newline characters in system prompt hints.
  // eslint-disable-next-line no-control-regex
  if (/[\r\n\u0000-\u001F\u007F\u0085\u2028\u2029]/.test(trimmed)) {
    return null;
  }
  if (!/^[A-Za-z0-9._\-/:]+$/.test(trimmed)) {
    return null;
  }
  return trimmed;
}

suspend fun cleanupProvisionalSession(
  childSessionKey: string,
  options?: {
    emitLifecycleHooks?: boolean;
    deleteTranscript?: boolean;
  },
): Promise<void> {
  try {
    await callGateway({
      method: "sessions.delete",
      params: {
        key: childSessionKey,
        emitLifecycleHooks: options?.emitLifecycleHooks == true,
        deleteTranscript: options?.deleteTranscript == true,
      },
      timeoutMs: 10_000,
    });
  } catch {
    // Best-effort cleanup only.
  }
}

suspend fun cleanupFailedSpawnBeforeAgentStart(params: {
  childSessionKey: string;
  attachmentAbsDir?: string;
  emitLifecycleHooks?: boolean;
  deleteTranscript?: boolean;
}): Promise<void> {
  if (params.attachmentAbsDir) {
    try {
      await fs.rm(params.attachmentAbsDir, { recursive: true, force: true });
    } catch {
      // Best-effort cleanup only.
    }
  }
  await cleanupProvisionalSession(params.childSessionKey, {
    emitLifecycleHooks: params.emitLifecycleHooks,
    deleteTranscript: params.deleteTranscript,
  });
}

fun resolveSpawnMode(params: {
  requestedMode?: SpawnSubagentMode;
  threadRequested: boolean;
}): SpawnSubagentMode {
  if (params.requestedMode == "run" || params.requestedMode == "session") {
    return params.requestedMode;
  }
  // Thread-bound spawns should default to persistent sessions.
  return params.threadRequested ? "session" : "run";
}

fun summarizeError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  if (typeof err == "string") {
    return err;
  }
  return "error";
}

suspend fun ensureThreadBindingForSubagentSpawn(params: {
  hookRunner: ReturnType<typeof getGlobalHookRunner>;
  childSessionKey: string;
  agentId: string;
  label?: string;
  mode: SpawnSubagentMode;
  requesterSessionKey?: string;
  requester: {
    channel?: string;
    accountId?: string;
    to?: string;
    threadId?: string | number;
  };
}): Promise<{ status: "ok" } | { status: "error"; error: string }> {
  val hookRunner = params.hookRunner;
  if (!hookRunner?.hasHooks("subagent_spawning")) {
    return {
      status: "error",
      error:
        "thread=true is unavailable because no channel plugin registered subagent_spawning hooks.",
    };
  }

  try {
    val result = await hookRunner.runSubagentSpawning(
      {
        childSessionKey: params.childSessionKey,
        agentId: params.agentId,
        label: params.label,
        mode: params.mode,
        requester: params.requester,
        threadRequested: true,
      },
      {
        childSessionKey: params.childSessionKey,
        requesterSessionKey: params.requesterSessionKey,
      },
    );
    if (result?.status == "error") {
      val error = result.error.trim();
      return {
        status: "error",
        error: error || "Failed to prepare thread binding for this subagent session.",
      };
    }
    if (result?.status != "ok" || !result.threadBindingReady) {
      return {
        status: "error",
        error:
          "Unable to create or bind a thread for this subagent session. Session mode is unavailable for this target.",
      };
    }
    return { status: "ok" };
  } catch (err) {
    return {
      status: "error",
      error: `Thread bind failed: ${summarizeError(err)}`,
    };
  }
}

suspend fun spawnSubagentDirect(
  params: SpawnSubagentParams,
  ctx: SpawnSubagentContext,
): Promise<SpawnSubagentResult> {
  val task = params.task;
  val label = params.label?.trim() || "";
  val requestedAgentId = params.agentId?.trim();

  // Reject malformed agentId before normalizeAgentId can mangle it.
  // Without this gate, error-message strings like "Agent not found: xyz" pass
  // through normalizeAgentId and become "agent-not-found--xyz", which later
  // creates ghost workspace directories and triggers cascading cron loops (#31311).
  if (requestedAgentId && !isValidAgentId(requestedAgentId)) {
    return {
      status: "error",
      error: `Invalid agentId "${requestedAgentId}". Agent IDs must match [a-z0-9][a-z0-9_-]{0,63}. Use agents_list to discover valid targets.`,
    };
  }
  val modelOverride = params.model;
  val thinkingOverrideRaw = params.thinking;
  val requestThreadBinding = params.thread == true;
  val sandboxMode = params.sandbox == "require" ? "require" : "inherit";
  val spawnMode = resolveSpawnMode({
    requestedMode: params.mode,
    threadRequested: requestThreadBinding,
  });
  if (spawnMode == "session" && !requestThreadBinding) {
    return {
      status: "error",
      error: 'mode="session" requires thread=true so the subagent can stay bound to a thread.',
    };
  }
  val cleanup =
    spawnMode == "session"
      ? "keep"
      : params.cleanup == "keep" || params.cleanup == "delete"
        ? params.cleanup
        : "keep";
  val expectsCompletionMessage = params.expectsCompletionMessage != false;
  val requesterOrigin = normalizeDeliveryContext({
    channel: ctx.agentChannel,
    accountId: ctx.agentAccountId,
    to: ctx.agentTo,
    threadId: ctx.agentThreadId,
  });
  val hookRunner = getGlobalHookRunner();
  val cfg = loadConfig();

  // When agent omits runTimeoutSeconds, use the config default.
  // Falls back to 0 (no timeout) if config key is also unset,
  // preserving current behavior for existing deployments.
  val cfgSubagentTimeout =
    typeof cfg?.agents?.defaults?.subagents?.runTimeoutSeconds == "number" &&
    Number.isFinite(cfg.agents.defaults.subagents.runTimeoutSeconds)
      ? Math.max(0, Math.floor(cfg.agents.defaults.subagents.runTimeoutSeconds))
      : 0;
  val runTimeoutSeconds =
    typeof params.runTimeoutSeconds == "number" && Number.isFinite(params.runTimeoutSeconds)
      ? Math.max(0, Math.floor(params.runTimeoutSeconds))
      : cfgSubagentTimeout;
  var modelApplied = false;
  var threadBindingReady = false;
  val { mainKey, alias } = resolveMainSessionAlias(cfg);
  val requesterSessionKey = ctx.agentSessionKey;
  val requesterInternalKey = requesterSessionKey
    ? resolveInternalSessionKey({
        key: requesterSessionKey,
        alias,
        mainKey,
      })
    : alias;
  val requesterDisplayKey = resolveDisplaySessionKey({
    key: requesterInternalKey,
    alias,
    mainKey,
  });

  val callerDepth = getSubagentDepthFromSessionStore(requesterInternalKey, { cfg });
  val maxSpawnDepth =
    cfg.agents?.defaults?.subagents?.maxSpawnDepth ?: DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH;
  if (callerDepth >= maxSpawnDepth) {
    return {
      status: "forbidden",
      error: `sessions_spawn is not allowed at this depth (current depth: ${callerDepth}, max: ${maxSpawnDepth})`,
    };
  }

  val maxChildren = cfg.agents?.defaults?.subagents?.maxChildrenPerAgent ?: 5;
  val activeChildren = countActiveRunsForSession(requesterInternalKey);
  if (activeChildren >= maxChildren) {
    return {
      status: "forbidden",
      error: `sessions_spawn has reached max active children for this session (${activeChildren}/${maxChildren})`,
    };
  }

  val requesterAgentId = normalizeAgentId(
    ctx.requesterAgentIdOverride ?: parseAgentSessionKey(requesterInternalKey)?.agentId,
  );
  val targetAgentId = requestedAgentId ? normalizeAgentId(requestedAgentId) : requesterAgentId;
  if (targetAgentId != requesterAgentId) {
    val allowAgents = resolveAgentConfig(cfg, requesterAgentId)?.subagents?.allowAgents ?: [];
    val allowAny = allowAgents.some((value) => value.trim() == "*");
    val normalizedTargetId = targetAgentId.toLowerCase();
    val allowSet = new Set(
      allowAgents
        .filter((value) => value.trim() && value.trim() != "*")
        .map((value) => normalizeAgentId(value).toLowerCase()),
    );
    if (!allowAny && !allowSet.has(normalizedTargetId)) {
      val allowedText = allowSet.size > 0 ? Array.from(allowSet).join(", ") : "none";
      return {
        status: "forbidden",
        error: `agentId is not allowed for sessions_spawn (allowed: ${allowedText})`,
      };
    }
  }
  val childSessionKey = `agent:${targetAgentId}:subagent:${crypto.randomUUID()}`;
  val requesterRuntime = resolveSandboxRuntimeStatus({
    cfg,
    sessionKey: requesterInternalKey,
  });
  val childRuntime = resolveSandboxRuntimeStatus({
    cfg,
    sessionKey: childSessionKey,
  });
  if (!childRuntime.sandboxed && (requesterRuntime.sandboxed || sandboxMode == "require")) {
    if (requesterRuntime.sandboxed) {
      return {
        status: "forbidden",
        error:
          "Sandboxed sessions cannot spawn unsandboxed subagents. Set a sandboxed target agent or use the same agent runtime.",
      };
    }
    return {
      status: "forbidden",
      error:
        'sessions_spawn sandbox="require" needs a sandboxed target runtime. Pick a sandboxed agentId or use sandbox="inherit".',
    };
  }
  val childDepth = callerDepth + 1;
  val spawnedByKey = requesterInternalKey;
  val childCapabilities = resolveSubagentCapabilities({
    depth: childDepth,
    maxSpawnDepth,
  });
  val targetAgentConfig = resolveAgentConfig(cfg, targetAgentId);
  val resolvedModel = resolveSubagentSpawnModelSelection({
    cfg,
    agentId: targetAgentId,
    modelOverride,
  });

  val resolvedThinkingDefaultRaw =
    readStringParam(targetAgentConfig?.subagents ?: {}, "thinking") ??
    readStringParam(cfg.agents?.defaults?.subagents ?: {}, "thinking");

  var thinkingOverride: string | null;
  val thinkingCandidateRaw = thinkingOverrideRaw || resolvedThinkingDefaultRaw;
  if (thinkingCandidateRaw) {
    val normalized = normalizeThinkLevel(thinkingCandidateRaw);
    if (!normalized) {
      val { provider, model } = splitModelRef(resolvedModel);
      val hint = formatThinkingLevels(provider, model);
      return {
        status: "error",
        error: `Invalid thinking level "${thinkingCandidateRaw}". Use one of: ${hint}.`,
      };
    }
    thinkingOverride = normalized;
  }
  val patchChildSession = async (patch: Record<string, unknown>): Promise<string | null> => {
    try {
      await callGateway({
        method: "sessions.patch",
        params: { key: childSessionKey, ...patch },
        timeoutMs: 10_000,
      });
      return null;
    } catch (err) {
      return err instanceof Error ? err.message : typeof err == "string" ? err : "error";
    }
  };

  val spawnDepthPatchError = await patchChildSession({
    spawnDepth: childDepth,
    subagentRole: childCapabilities.role == "main" ? null : childCapabilities.role,
    subagentControlScope: childCapabilities.controlScope,
  });
  if (spawnDepthPatchError) {
    return {
      status: "error",
      error: spawnDepthPatchError,
      childSessionKey,
    };
  }

  if (resolvedModel) {
    val modelPatchError = await patchChildSession({ model: resolvedModel });
    if (modelPatchError) {
      return {
        status: "error",
        error: modelPatchError,
        childSessionKey,
      };
    }
    modelApplied = true;
  }
  if (thinkingOverride != null) {
    val thinkingPatchError = await patchChildSession({
      thinkingLevel: thinkingOverride == "off" ? null : thinkingOverride,
    });
    if (thinkingPatchError) {
      return {
        status: "error",
        error: thinkingPatchError,
        childSessionKey,
      };
    }
  }
  if (requestThreadBinding) {
    val bindResult = await ensureThreadBindingForSubagentSpawn({
      hookRunner,
      childSessionKey,
      agentId: targetAgentId,
      label: label || null,
      mode: spawnMode,
      requesterSessionKey: requesterInternalKey,
      requester: {
        channel: requesterOrigin?.channel,
        accountId: requesterOrigin?.accountId,
        to: requesterOrigin?.to,
        threadId: requesterOrigin?.threadId,
      },
    });
    if (bindResult.status == "error") {
      try {
        await callGateway({
          method: "sessions.delete",
          params: { key: childSessionKey, emitLifecycleHooks: false },
          timeoutMs: 10_000,
        });
      } catch {
        // Best-effort cleanup only.
      }
      return {
        status: "error",
        error: bindResult.error,
        childSessionKey,
      };
    }
    threadBindingReady = true;
  }
  val mountPathHint = sanitizeMountPathHint(params.attachMountPath);

  var childSystemPrompt = buildSubagentSystemPrompt({
    requesterSessionKey,
    requesterOrigin,
    childSessionKey,
    label: label || null,
    task,
    acpEnabled: cfg.acp?.enabled != false && !childRuntime.sandboxed,
    childDepth,
    maxSpawnDepth,
  });

  var retainOnSessionKeep = false;
  var attachmentsReceipt:
    | {
        count: number;
        totalBytes: number;
        files: SubagentAttachmentReceiptFile[];
        relDir: string;
      }
    | null;
  var attachmentAbsDir: string | null;
  var attachmentRootDir: string | null;
  val materializedAttachments = await materializeSubagentAttachments({
    config: cfg,
    targetAgentId,
    attachments: params.attachments,
    mountPathHint,
  });
  if (materializedAttachments && materializedAttachments.status != "ok") {
    await cleanupProvisionalSession(childSessionKey, {
      emitLifecycleHooks: threadBindingReady,
      deleteTranscript: true,
    });
    return {
      status: materializedAttachments.status,
      error: materializedAttachments.error,
    };
  }
  if (materializedAttachments?.status == "ok") {
    retainOnSessionKeep = materializedAttachments.retainOnSessionKeep;
    attachmentsReceipt = materializedAttachments.receipt;
    attachmentAbsDir = materializedAttachments.absDir;
    attachmentRootDir = materializedAttachments.rootDir;
    childSystemPrompt = `${childSystemPrompt}\n\n${materializedAttachments.systemPromptSuffix}`;
  }

  val childTaskMessage = [
    `[Subagent Context] You are running as a subagent (depth ${childDepth}/${maxSpawnDepth}). Results auto-announce to your requester; do not busy-poll for status.`,
    spawnMode == "session"
      ? "[Subagent Context] This subagent session is persistent and remains available for thread follow-up messages."
      : null,
    `[Subagent Task]: ${task}`,
  ]
    .filter((line): line is string => Boolean(line))
    .join("\n\n");

  val toolSpawnMetadata = mapToolContextToSpawnedRunMetadata({
    agentGroupId: ctx.agentGroupId,
    agentGroupChannel: ctx.agentGroupChannel,
    agentGroupSpace: ctx.agentGroupSpace,
    workspaceDir: ctx.workspaceDir,
  });
  val spawnedMetadata = normalizeSpawnedRunMetadata({
    spawnedBy: spawnedByKey,
    ...toolSpawnMetadata,
    workspaceDir: resolveSpawnedWorkspaceInheritance({
      config: cfg,
      targetAgentId,
      // For cross-agent spawns, ignore the caller's inherited workspace;
      // var targetAgentId resolve the correct workspace instead.
      explicitWorkspaceDir:
        targetAgentId != requesterAgentId ? null : toolSpawnMetadata.workspaceDir,
    }),
  });
  val spawnLineagePatchError = await patchChildSession({
    spawnedBy: spawnedByKey,
    ...(spawnedMetadata.workspaceDir ? { spawnedWorkspaceDir: spawnedMetadata.workspaceDir } : {}),
  });
  if (spawnLineagePatchError) {
    await cleanupFailedSpawnBeforeAgentStart({
      childSessionKey,
      attachmentAbsDir,
      emitLifecycleHooks: threadBindingReady,
      deleteTranscript: true,
    });
    return {
      status: "error",
      error: spawnLineagePatchError,
      childSessionKey,
    };
  }

  val childIdem = crypto.randomUUID();
  var childRunId: string = childIdem;
  try {
    val {
      spawnedBy: _spawnedBy,
      workspaceDir: _workspaceDir,
      ...publicSpawnedMetadata
    } = spawnedMetadata;
    val response = await callGateway<{ runId: string }>({
      method: "agent",
      params: {
        message: childTaskMessage,
        sessionKey: childSessionKey,
        channel: requesterOrigin?.channel,
        to: requesterOrigin?.to ?: null,
        accountId: requesterOrigin?.accountId ?: null,
        threadId: requesterOrigin?.threadId != null ? String(requesterOrigin.threadId) : null,
        idempotencyKey: childIdem,
        deliver: false,
        lane: AGENT_LANE_SUBAGENT,
        extraSystemPrompt: childSystemPrompt,
        thinking: thinkingOverride,
        timeout: runTimeoutSeconds,
        label: label || null,
        ...publicSpawnedMetadata,
      },
      timeoutMs: 10_000,
    });
    if (typeof response?.runId == "string" && response.runId) {
      childRunId = response.runId;
    }
  } catch (err) {
    if (attachmentAbsDir) {
      try {
        await fs.rm(attachmentAbsDir, { recursive: true, force: true });
      } catch {
        // Best-effort cleanup only.
      }
    }
    if (threadBindingReady) {
      val hasEndedHook = hookRunner?.hasHooks("subagent_ended") == true;
      var endedHookEmitted = false;
      if (hasEndedHook) {
        try {
          await hookRunner?.runSubagentEnded(
            {
              targetSessionKey: childSessionKey,
              targetKind: "subagent",
              reason: "spawn-failed",
              sendFarewell: true,
              accountId: requesterOrigin?.accountId,
              runId: childRunId,
              outcome: "error",
              error: "Session failed to start",
            },
            {
              runId: childRunId,
              childSessionKey,
              requesterSessionKey: requesterInternalKey,
            },
          );
          endedHookEmitted = true;
        } catch {
          // Spawn should still return an actionable error even if cleanup hooks fail.
        }
      }
      // Always delete the provisional child session after a failed spawn attempt.
      // If we already emitted subagent_ended above, suppress a duplicate lifecycle hook.
      try {
        await callGateway({
          method: "sessions.delete",
          params: {
            key: childSessionKey,
            deleteTranscript: true,
            emitLifecycleHooks: !endedHookEmitted,
          },
          timeoutMs: 10_000,
        });
      } catch {
        // Best-effort only.
      }
    }
    val messageText = summarizeError(err);
    return {
      status: "error",
      error: messageText,
      childSessionKey,
      runId: childRunId,
    };
  }

  try {
    registerSubagentRun({
      runId: childRunId,
      childSessionKey,
      controllerSessionKey: requesterInternalKey,
      requesterSessionKey: requesterInternalKey,
      requesterOrigin,
      requesterDisplayKey,
      task,
      cleanup,
      label: label || null,
      model: resolvedModel,
      workspaceDir: spawnedMetadata.workspaceDir,
      runTimeoutSeconds,
      expectsCompletionMessage,
      spawnMode,
      attachmentsDir: attachmentAbsDir,
      attachmentsRootDir: attachmentRootDir,
      retainAttachmentsOnKeep: retainOnSessionKeep,
    });
  } catch (err) {
    if (attachmentAbsDir) {
      try {
        await fs.rm(attachmentAbsDir, { recursive: true, force: true });
      } catch {
        // Best-effort cleanup only.
      }
    }
    try {
      await callGateway({
        method: "sessions.delete",
        params: { key: childSessionKey, deleteTranscript: true, emitLifecycleHooks: false },
        timeoutMs: 10_000,
      });
    } catch {
      // Best-effort cleanup only.
    }
    return {
      status: "error",
      error: `Failed to register subagent run: ${summarizeError(err)}`,
      childSessionKey,
      runId: childRunId,
    };
  }

  if (hookRunner?.hasHooks("subagent_spawned")) {
    try {
      await hookRunner.runSubagentSpawned(
        {
          runId: childRunId,
          childSessionKey,
          agentId: targetAgentId,
          label: label || null,
          requester: {
            channel: requesterOrigin?.channel,
            accountId: requesterOrigin?.accountId,
            to: requesterOrigin?.to,
            threadId: requesterOrigin?.threadId,
          },
          threadRequested: requestThreadBinding,
          mode: spawnMode,
        },
        {
          runId: childRunId,
          childSessionKey,
          requesterSessionKey: requesterInternalKey,
        },
      );
    } catch {
      // Spawn should still return accepted if spawn lifecycle hooks fail.
    }
  }

  // Check if we're in a cron isolated session - don't add "do not poll" note
  // because cron sessions end immediately after the agent produces a response,
  // so the agent needs to wait for subagent results to keep the turn alive.
  val isCronSession = isCronSessionKey(ctx.agentSessionKey);
  val note =
    spawnMode == "session"
      ? SUBAGENT_SPAWN_SESSION_ACCEPTED_NOTE
      : isCronSession
        ? null
        : SUBAGENT_SPAWN_ACCEPTED_NOTE;

  return {
    status: "accepted",
    childSessionKey,
    runId: childRunId,
    mode: spawnMode,
    note,
    modelApplied: resolvedModel ? modelApplied : null,
    attachments: attachmentsReceipt,
  };
}
