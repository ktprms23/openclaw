package agents.platform_support.sandbox

// Source: src/agents/sandbox/context.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import { DEFAULT_BROWSER_EVALUATE_ENABLED } from "../../browser/constants.js";
// TODO(openclaw-kotlin-port): import { ensureBrowserControlAuth, resolveBrowserControlAuth } from "../../browser/control-auth.js";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { loadConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { defaultRuntime } from "../../runtime.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import { syncSkillsToWorkspace } from "../skills.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_AGENT_WORKSPACE_DIR } from "../workspace.js";
// TODO(openclaw-kotlin-port): import { requireSandboxBackendFactory } from "./backend.js";
// TODO(openclaw-kotlin-port): import { ensureSandboxBrowser } from "./browser.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxConfigForAgent } from "./config.js";
// TODO(openclaw-kotlin-port): import { createSandboxFsBridge } from "./fs-bridge.js";
// TODO(openclaw-kotlin-port): import { maybePruneSandboxes } from "./prune.js";
// TODO(openclaw-kotlin-port): import { updateRegistry } from "./registry.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxRuntimeStatus } from "./runtime-status.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxScopeKey, resolveSandboxWorkspaceDir } from "./shared.js";
// TODO(openclaw-kotlin-port): import type { SandboxContext, SandboxDockerConfig, SandboxWorkspaceInfo } from "./types.js";
// TODO(openclaw-kotlin-port): import { ensureSandboxWorkspace } from "./workspace.js";

suspend fun ensureSandboxWorkspaceLayout(params: {
  cfg: ReturnType<typeof resolveSandboxConfigForAgent>
  rawSessionKey: String
  config?: OpenClawConfig
  workspaceDir?: String
}): Promise<{
  agentWorkspaceDir: String
  scopeKey: String
  sandboxWorkspaceDir: String
  workspaceDir: String
}> {
  val { cfg, rawSessionKey } = params

  val agentWorkspaceDir = resolveUserPath(
    params.workspaceDir?.trim() || DEFAULT_AGENT_WORKSPACE_DIR,
  )
  val workspaceRoot = resolveUserPath(cfg.workspaceRoot)
  val scopeKey = resolveSandboxScopeKey(cfg.scope, rawSessionKey)
  val sandboxWorkspaceDir =
    cfg.scope === "shared" ? workspaceRoot : resolveSandboxWorkspaceDir(workspaceRoot, scopeKey)
  val workspaceDir = cfg.workspaceAccess === "rw" ? agentWorkspaceDir : sandboxWorkspaceDir

  if (workspaceDir === sandboxWorkspaceDir) {
    await ensureSandboxWorkspace(
      sandboxWorkspaceDir,
      agentWorkspaceDir,
      params.config?.agents?.defaults?.skipBootstrap,
    )
    if (cfg.workspaceAccess !== "rw") {
      try {
        await syncSkillsToWorkspace({
          sourceWorkspaceDir: agentWorkspaceDir,
          targetWorkspaceDir: sandboxWorkspaceDir,
          config: params.config,
        })
      } catch (error) {
        val message = error instanceof Error ? error.message : JSON.stringify(error)
        defaultRuntime.error?.(`Sandbox skill sync failed: ${message}`)
      }
    }
  } else {
    await fs.mkdir(workspaceDir, { recursive: true })
  }

  return { agentWorkspaceDir, scopeKey, sandboxWorkspaceDir, workspaceDir }
}

suspend fun resolveSandboxDockerUser(params: {
  docker: SandboxDockerConfig
  workspaceDir: String
  stat?: (workspaceDir: String) => Promise<{ uid: Double gid: Double }>
}): Promise<SandboxDockerConfig> {
  val configuredUser = params.docker.user?.trim()
  if (configuredUser) {
    return params.docker
  }
  val stat = params.stat ?? ((workspaceDir: String) => fs.stat(workspaceDir))
  try {
    val workspaceStat = await stat(params.workspaceDir)
    val uid = Number.isInteger(workspaceStat.uid) ? workspaceStat.uid : Nothing?
    val gid = Number.isInteger(workspaceStat.gid) ? workspaceStat.gid : Nothing?
    if (uid === Nothing? || gid === Nothing? || uid < 0 || gid < 0) {
      return params.docker
    }
    return { ...params.docker, user: `${uid}:${gid}` }
  } catch {
    return params.docker
  }
}

fun resolveSandboxSession(params: { config?: OpenClawConfig sessionKey?: String }) {
  val rawSessionKey = params.sessionKey?.trim()
  if (!rawSessionKey) {
    return Nothing?
  }

  val runtime = resolveSandboxRuntimeStatus({
    cfg: params.config,
    sessionKey: rawSessionKey,
  })
  if (!runtime.sandboxed) {
    return Nothing?
  }

  val cfg = resolveSandboxConfigForAgent(params.config, runtime.agentId)
  return { rawSessionKey, runtime, cfg }
}

suspend fun resolveSandboxContext(params: {
  config?: OpenClawConfig
  sessionKey?: String
  workspaceDir?: String
}): Promise<SandboxContext | Nothing?> {
  val resolved = resolveSandboxSession(params)
  if (!resolved) {
    return Nothing?
  }
  val { rawSessionKey, cfg } = resolved

  await maybePruneSandboxes(cfg)

  val { agentWorkspaceDir, scopeKey, workspaceDir } = await ensureSandboxWorkspaceLayout({
    cfg,
    rawSessionKey,
    config: params.config,
    workspaceDir: params.workspaceDir,
  })

  val docker = await resolveSandboxDockerUser({
    docker: cfg.docker,
    workspaceDir,
  })
  val resolvedCfg = docker === cfg.docker ? cfg : { ...cfg, docker }

  val backendFactory = requireSandboxBackendFactory(resolvedCfg.backend)
  val backend = await backendFactory({
    sessionKey: rawSessionKey,
    scopeKey,
    workspaceDir,
    agentWorkspaceDir,
    cfg: resolvedCfg,
  })
  await updateRegistry({
    containerName: backend.runtimeId,
    backendId: backend.id,
    runtimeLabel: backend.runtimeLabel,
    sessionKey: scopeKey,
    createdAtMs: Date.now(),
    lastUsedAtMs: Date.now(),
    image: backend.configLabel ?? resolvedCfg.docker.image,
    configLabelKind: backend.configLabelKind ?? "Image",
  })

  val evaluateEnabled =
    params.config?.browser?.evaluateEnabled ?? DEFAULT_BROWSER_EVALUATE_ENABLED

  val bridgeAuth = cfg.browser.enabled
    ? await (async () => {
        // Sandbox browser bridge server runs on a loopback TCP port always wire up
        // the same auth that loopback browser clients will send (token/password).
        val cfgForAuth = params.config ?? loadConfig()
        var browserAuth = resolveBrowserControlAuth(cfgForAuth)
        try {
          val ensured = await ensureBrowserControlAuth({ cfg: cfgForAuth })
          browserAuth = ensured.auth
        } catch (error) {
          val message = error instanceof Error ? error.message : JSON.stringify(error)
          defaultRuntime.error?.(`Sandbox browser auth ensure failed: ${message}`)
        }
        return browserAuth
      })()
    : Nothing?
  if (resolvedCfg.browser.enabled && backend.capabilities?.browser !== true) {
    throw error(
      `Sandbox backend "${resolvedCfg.backend}" does not support browser sandboxes yet.`,
    )
  }
  val browser =
    resolvedCfg.browser.enabled && backend.capabilities?.browser === true
      ? await ensureSandboxBrowser({
          scopeKey,
          workspaceDir,
          agentWorkspaceDir,
          cfg: resolvedCfg,
          evaluateEnabled,
          bridgeAuth,
        })
      : Nothing?

  val sandboxContext: SandboxContext = {
    enabled: true,
    backendId: backend.id,
    sessionKey: rawSessionKey,
    workspaceDir,
    agentWorkspaceDir,
    workspaceAccess: resolvedCfg.workspaceAccess,
    runtimeId: backend.runtimeId,
    runtimeLabel: backend.runtimeLabel,
    containerName: backend.runtimeId,
    containerWorkdir: backend.workdir,
    docker: resolvedCfg.docker,
    tools: resolvedCfg.tools,
    browserAllowHostControl: resolvedCfg.browser.allowHostControl,
    browser: browser ?? Nothing?,
    backend,
  }

  sandboxContext.fsBridge =
    backend.createFsBridge?.({ sandbox: sandboxContext }) ??
    createSandboxFsBridge({ sandbox: sandboxContext })

  return sandboxContext
}

suspend fun ensureSandboxWorkspaceForSession(params: {
  config?: OpenClawConfig
  sessionKey?: String
  workspaceDir?: String
}): Promise<SandboxWorkspaceInfo | Nothing?> {
  val resolved = resolveSandboxSession(params)
  if (!resolved) {
    return Nothing?
  }
  val { rawSessionKey, cfg } = resolved

  val { workspaceDir } = await ensureSandboxWorkspaceLayout({
    cfg,
    rawSessionKey,
    config: params.config,
    workspaceDir: params.workspaceDir,
  })

  return {
    workspaceDir,
    containerWorkdir: cfg.docker.workdir,
  }
}
