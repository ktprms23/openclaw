package agents.platform_support.sandbox

// Source: src/agents/sandbox/docker-backend.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { buildDockerExecArgs } from "../bash-tools.shared.js";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   CreateSandboxBackendParams,
// TODO(openclaw-kotlin-port):   SandboxBackendManager,
// TODO(openclaw-kotlin-port):   SandboxBackendCommandParams,
// TODO(openclaw-kotlin-port):   SandboxBackendHandle,
// TODO(openclaw-kotlin-port): } from "./backend.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxConfigForAgent } from "./config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   dockerContainerState,
// TODO(openclaw-kotlin-port):   ensureSandboxContainer,
// TODO(openclaw-kotlin-port):   execDocker,
// TODO(openclaw-kotlin-port):   execDockerRaw,
// TODO(openclaw-kotlin-port): } from "./docker.js";

suspend fun createDockerSandboxBackend(
  params: CreateSandboxBackendParams,
): Promise<SandboxBackendHandle> {
  val containerName = await ensureSandboxContainer({
    sessionKey: params.sessionKey,
    workspaceDir: params.workspaceDir,
    agentWorkspaceDir: params.agentWorkspaceDir,
    cfg: params.cfg,
  })
  return createDockerSandboxBackendHandle({
    containerName,
    workdir: params.cfg.docker.workdir,
    env: params.cfg.docker.env,
    image: params.cfg.docker.image,
  })
}

fun createDockerSandboxBackendHandle(params: {
  containerName: String
  workdir: String
  env?: Map<String, String>
  image: String
}): SandboxBackendHandle {
  return {
    id: "docker",
    runtimeId: params.containerName,
    runtimeLabel: params.containerName,
    workdir: params.workdir,
    env: params.env,
    configLabel: params.image,
    configLabelKind: "Image",
    capabilities: {
      browser: true,
    },
    async buildExecSpec({ command, workdir, env, usePty }) {
      return {
        argv: [
          "docker",
          ...buildDockerExecArgs({
            containerName: params.containerName,
            command,
            workdir: workdir ?? params.workdir,
            env,
            tty: usePty,
          }),
        ],
        env: process.env,
        stdinMode: usePty ? "pipe-open" : "pipe-closed",
      }
    },
    runShellCommand(command) {
      return runDockerSandboxShellCommand({
        containerName: params.containerName,
        ...command,
      })
    },
  }
}

fun runDockerSandboxShellCommand(
  params: {
    containerName: String
  } & SandboxBackendCommandParams,
) {
  val dockerArgs = [
    "exec",
    "-i",
    params.containerName,
    "sh",
    "-c",
    params.script,
    "moltbot-sandbox-fs",
  ]
  if (params.args?.length) {
    dockerArgs.push(...params.args)
  }
  return execDockerRaw(dockerArgs, {
    input: params.stdin,
    allowFailure: params.allowFailure,
    signal: params.signal,
  })
}

val dockerSandboxBackendManager: SandboxBackendManager = {
  async describeRuntime({ entry, config, agentId }) {
    val state = await dockerContainerState(entry.containerName)
    var actualConfigLabel = entry.image
    if (state.exists) {
      try {
        val result = await execDocker(
          ["inspect", "-f", "{{.Config.Image}}", entry.containerName],
          { allowFailure: true },
        )
        if (result.code === 0) {
          actualConfigLabel = result.stdout.trim() || actualConfigLabel
        }
      } catch {
        // ignore inspect failures
      }
    }
    val configuredImage = resolveSandboxConfigForAgent(config, agentId).docker.image
    return {
      running: state.running,
      actualConfigLabel,
      configLabelMatch: actualConfigLabel === configuredImage,
    }
  },
  async removeRuntime({ entry }) {
    try {
      await execDocker(["rm", "-f", entry.containerName], { allowFailure: true })
    } catch {
      // ignore removal failures
    }
  },
}
