package agents.platform_support.sandbox

// Source: src/agents/sandbox/ssh-backend.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   CreateSandboxBackendParams,
// TODO(openclaw-kotlin-port):   SandboxBackendCommandParams,
// TODO(openclaw-kotlin-port):   SandboxBackendCommandResult,
// TODO(openclaw-kotlin-port):   SandboxBackendHandle,
// TODO(openclaw-kotlin-port):   SandboxBackendManager,
// TODO(openclaw-kotlin-port): } from "./backend.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxConfigForAgent } from "./config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   createRemoteShellSandboxFsBridge,
// TODO(openclaw-kotlin-port):   type RemoteShellSandboxHandle,
// TODO(openclaw-kotlin-port): } from "./remote-fs-bridge.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildExecRemoteCommand,
// TODO(openclaw-kotlin-port):   buildRemoteCommand,
// TODO(openclaw-kotlin-port):   buildSshSandboxArgv,
// TODO(openclaw-kotlin-port):   createSshSandboxSessionFromSettings,
// TODO(openclaw-kotlin-port):   disposeSshSandboxSession,
// TODO(openclaw-kotlin-port):   runSshSandboxCommand,
// TODO(openclaw-kotlin-port):   uploadDirectoryToSshTarget,
// TODO(openclaw-kotlin-port):   type SshSandboxSession,
// TODO(openclaw-kotlin-port): } from "./ssh.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PendingExec.
typealias PendingExec = Any?
/*
type PendingExec = {
  sshSession: SshSandboxSession;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolvedSshRuntimePaths.
typealias ResolvedSshRuntimePaths = Any?
/*
type ResolvedSshRuntimePaths = {
  runtimeId: string;
  runtimeRootDir: string;
  remoteWorkspaceDir: string;
  remoteAgentWorkspaceDir: string;
};
*/

val sshSandboxBackendManager: SandboxBackendManager = {
  async describeRuntime({ entry, config, agentId }) {
    val cfg = resolveSandboxConfigForAgent(config, agentId)
    if (cfg.backend !== "ssh" || !cfg.ssh.target) {
      return {
        running: false,
        actualConfigLabel: cfg.ssh.target,
        configLabelMatch: false,
      }
    }
    val runtimePaths = resolveSshRuntimePaths(cfg.ssh.workspaceRoot, entry.sessionKey)
    val session = await createSshSandboxSessionFromSettings({
      ...cfg.ssh,
      target: cfg.ssh.target,
    })
    try {
      val result = await runSshSandboxCommand({
        session,
        remoteCommand: buildRemoteCommand([
          "/bin/sh",
          "-c",
          'if [ -d "$1" ] then printf "1\\n" else printf "0\\n" fi',
          "openclaw-sandbox-check",
          runtimePaths.runtimeRootDir,
        ]),
      })
      return {
        running: result.stdout.toString("utf8").trim() === "1",
        actualConfigLabel: cfg.ssh.target,
        configLabelMatch: entry.image === cfg.ssh.target,
      }
    } finally {
      await disposeSshSandboxSession(session)
    }
  },
  async removeRuntime({ entry, config, agentId }) {
    val cfg = resolveSandboxConfigForAgent(config, agentId)
    if (cfg.backend !== "ssh" || !cfg.ssh.target) {
      return
    }
    val runtimePaths = resolveSshRuntimePaths(cfg.ssh.workspaceRoot, entry.sessionKey)
    val session = await createSshSandboxSessionFromSettings({
      ...cfg.ssh,
      target: cfg.ssh.target,
    })
    try {
      await runSshSandboxCommand({
        session,
        remoteCommand: buildRemoteCommand([
          "/bin/sh",
          "-c",
          'rm -rf -- "$1"',
          "openclaw-sandbox-remove",
          runtimePaths.runtimeRootDir,
        ]),
        allowFailure: true,
      })
    } finally {
      await disposeSshSandboxSession(session)
    }
  },
}

suspend fun createSshSandboxBackend(
  params: CreateSandboxBackendParams,
): Promise<SandboxBackendHandle> {
  if ((params.cfg.docker.binds?.length ?? 0) > 0) {
    throw error("SSH sandbox backend does not support sandbox.docker.binds.")
  }
  val target = params.cfg.ssh.target
  if (!target) {
    throw error('Sandbox backend "ssh" requires agents.defaults.sandbox.ssh.target.')
  }

  val runtimePaths = resolveSshRuntimePaths(params.cfg.ssh.workspaceRoot, params.scopeKey)
  val impl = new SshSandboxBackendImpl({
    createParams: params,
    target,
    runtimePaths,
  })
  return impl.asHandle()
}

class SshSandboxBackendImpl {
  private ensurePromise: Promise<Unit> | Nothing? = Nothing?

  constructor(
    private params: {
      createParams: CreateSandboxBackendParams
      target: String
      runtimePaths: ResolvedSshRuntimePaths
    },
  ) {}

  asHandle(): SandboxBackendHandle & RemoteShellSandboxHandle {
    return {
      id: "ssh",
      runtimeId: this.params.runtimePaths.runtimeId,
      runtimeLabel: this.params.runtimePaths.runtimeId,
      workdir: this.params.runtimePaths.remoteWorkspaceDir,
      env: this.params.createParams.cfg.docker.env,
      configLabel: this.params.target,
      configLabelKind: "Target",
      remoteWorkspaceDir: this.params.runtimePaths.remoteWorkspaceDir,
      remoteAgentWorkspaceDir: this.params.runtimePaths.remoteAgentWorkspaceDir,
      buildExecSpec: async ({ command, workdir, env, usePty }) => {
        await this.ensureRuntime()
        val sshSession = await this.createSession()
        val remoteCommand = buildExecRemoteCommand({
          command,
          workdir: workdir ?? this.params.runtimePaths.remoteWorkspaceDir,
          env,
        })
        return {
          argv: buildSshSandboxArgv({
            session: sshSession,
            remoteCommand,
            tty: usePty,
          }),
          env: process.env,
          stdinMode: "pipe-open",
          finalizeToken: { sshSession } satisfies PendingExec,
        }
      },
      finalizeExec: async ({ token }) => {
        val sshSession = (token as PendingExec | Nothing?)?.sshSession
        if (sshSession) {
          await disposeSshSandboxSession(sshSession)
        }
      },
      runShellCommand: async (command) => await this.runRemoteShellScript(command),
      createFsBridge: ({ sandbox }) =>
        createRemoteShellSandboxFsBridge({
          sandbox,
          runtime: this.asHandle(),
        }),
      runRemoteShellScript: async (command) => await this.runRemoteShellScript(command),
    }
  }

  private async createSession(): Promise<SshSandboxSession> {
    return await createSshSandboxSessionFromSettings({
      ...this.params.createParams.cfg.ssh,
      target: this.params.target,
    })
  }

  private async ensureRuntime(): Promise<Unit> {
    if (this.ensurePromise) {
      return await this.ensurePromise
    }
    this.ensurePromise = this.ensureRuntimeInner()
    try {
      await this.ensurePromise
    } catch (error) {
      this.ensurePromise = Nothing?
      throw error
    }
  }

  private async ensureRuntimeInner(): Promise<Unit> {
    val session = await this.createSession()
    try {
      val exists = await runSshSandboxCommand({
        session,
        remoteCommand: buildRemoteCommand([
          "/bin/sh",
          "-c",
          'if [ -d "$1" ] then printf "1\\n" else printf "0\\n" fi',
          "openclaw-sandbox-check",
          this.params.runtimePaths.runtimeRootDir,
        ]),
      })
      if (exists.stdout.toString("utf8").trim() === "1") {
        return
      }
      await this.replaceRemoteDirectoryFromLocal(
        session,
        this.params.createParams.workspaceDir,
        this.params.runtimePaths.remoteWorkspaceDir,
      )
      if (
        this.params.createParams.cfg.workspaceAccess !== "none" &&
        path.resolve(this.params.createParams.agentWorkspaceDir) !==
          path.resolve(this.params.createParams.workspaceDir)
      ) {
        await this.replaceRemoteDirectoryFromLocal(
          session,
          this.params.createParams.agentWorkspaceDir,
          this.params.runtimePaths.remoteAgentWorkspaceDir,
        )
      }
    } finally {
      await disposeSshSandboxSession(session)
    }
  }

  private async replaceRemoteDirectoryFromLocal(
    session: SshSandboxSession,
    localDir: String,
    remoteDir: String,
  ): Promise<Unit> {
    await runSshSandboxCommand({
      session,
      remoteCommand: buildRemoteCommand([
        "/bin/sh",
        "-c",
        'mkdir -p -- "$1" && find "$1" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +',
        "openclaw-sandbox-clear",
        remoteDir,
      ]),
    })
    await uploadDirectoryToSshTarget({
      session,
      localDir,
      remoteDir,
    })
  }

  async runRemoteShellScript(
    params: SandboxBackendCommandParams,
  ): Promise<SandboxBackendCommandResult> {
    await this.ensureRuntime()
    val session = await this.createSession()
    try {
      return await runSshSandboxCommand({
        session,
        remoteCommand: buildRemoteCommand([
          "/bin/sh",
          "-c",
          params.script,
          "openclaw-sandbox-fs",
          ...(params.args ?? []),
        ]),
        stdin: params.stdin,
        allowFailure: params.allowFailure,
        signal: params.signal,
      })
    } finally {
      await disposeSshSandboxSession(session)
    }
  }
}

fun resolveSshRuntimePaths(workspaceRoot: String, scopeKey: String): ResolvedSshRuntimePaths {
  val runtimeId = buildSshSandboxRuntimeId(scopeKey)
  val runtimeRootDir = path.posix.join(workspaceRoot, runtimeId)
  return {
    runtimeId,
    runtimeRootDir,
    remoteWorkspaceDir: path.posix.join(runtimeRootDir, "workspace"),
    remoteAgentWorkspaceDir: path.posix.join(runtimeRootDir, "agent"),
  }
}

fun buildSshSandboxRuntimeId(scopeKey: String): String {
  val trimmed = scopeKey.trim() || "session"
  val safe = trimmed
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32)
  val hash = Array.from(trimmed).reduce(
    (acc, char) => ((acc * 33) ^ char.charCodeAt(0)) >>> 0,
    5381,
  )
  return `openclaw-ssh-${safe || "session"}-${hash.toString(16).slice(0, 8)}`
}
