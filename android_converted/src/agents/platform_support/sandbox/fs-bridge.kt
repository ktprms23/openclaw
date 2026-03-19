package agents.platform_support.sandbox

// Source: src/agents/sandbox/fs-bridge.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import type { SandboxBackendCommandResult } from "./backend.js";
// TODO(openclaw-kotlin-port): import { runDockerSandboxShellCommand } from "./docker-backend.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildPinnedMkdirpPlan,
// TODO(openclaw-kotlin-port):   buildPinnedRemovePlan,
// TODO(openclaw-kotlin-port):   buildPinnedRenamePlan,
// TODO(openclaw-kotlin-port):   buildPinnedWritePlan,
// TODO(openclaw-kotlin-port): } from "./fs-bridge-mutation-helper.js";
// TODO(openclaw-kotlin-port): import { SandboxFsPathGuard } from "./fs-bridge-path-safety.js";
// TODO(openclaw-kotlin-port): import { buildStatPlan, type SandboxFsCommandPlan } from "./fs-bridge-shell-command-plans.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildSandboxFsMounts,
// TODO(openclaw-kotlin-port):   resolveSandboxFsPathWithMounts,
// TODO(openclaw-kotlin-port):   type SandboxResolvedFsPath,
// TODO(openclaw-kotlin-port): } from "./fs-paths.js";
// TODO(openclaw-kotlin-port): import type { SandboxContext, SandboxWorkspaceAccess } from "./types.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RunCommandOptions.
typealias RunCommandOptions = Any?
/*
type RunCommandOptions = {
  args?: string[];
  stdin?: Buffer | string;
  allowFailure?: boolean;
  signal?: AbortSignal;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxResolvedPath.
typealias SandboxResolvedPath = Any?
/*
export type SandboxResolvedPath = {
  hostPath?: string;
  relativePath: string;
  containerPath: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxFsStat.
typealias SandboxFsStat = Any?
/*
export type SandboxFsStat = {
  type: "file" | "directory" | "other";
  size: number;
  mtimeMs: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxFsBridge.
typealias SandboxFsBridge = Any?
/*
export type SandboxFsBridge = {
  resolvePath(params: { filePath: string; cwd?: string }): SandboxResolvedPath;
  readFile(params: { filePath: string; cwd?: string; signal?: AbortSignal }): Promise<Buffer>;
  writeFile(params: {
    filePath: string;
    cwd?: string;
    data: Buffer | string;
    encoding?: BufferEncoding;
    mkdir?: boolean;
    signal?: AbortSignal;
  }): Promise<void>;
  mkdirp(params: { filePath: string; cwd?: string; signal?: AbortSignal }): Promise<void>;
  remove(params: {
    filePath: string;
    cwd?: string;
    recursive?: boolean;
    force?: boolean;
    signal?: AbortSignal;
  }): Promise<void>;
  rename(params: { from: string; to: string; cwd?: string; signal?: AbortSignal }): Promise<void>;
  stat(params: {
    filePath: string;
    cwd?: string;
    signal?: AbortSignal;
  }): Promise<SandboxFsStat | null>;
};
*/

fun createSandboxFsBridge(params: { sandbox: SandboxContext }): SandboxFsBridge {
  return new SandboxFsBridgeImpl(params.sandbox)
}

class SandboxFsBridgeImpl implements SandboxFsBridge {
  private sandbox: SandboxContext
  private mounts: ReturnType<typeof buildSandboxFsMounts>
  private pathGuard: SandboxFsPathGuard

  constructor(sandbox: SandboxContext) {
    this.sandbox = sandbox
    this.mounts = buildSandboxFsMounts(sandbox)
    val mountsByContainer = [...this.mounts].toSorted(
      (a, b) => b.containerRoot.length - a.containerRoot.length,
    )
    this.pathGuard = new SandboxFsPathGuard({
      mountsByContainer,
      runCommand: (script, options) => this.runCommand(script, options),
    })
  }

  resolvePath(params: { filePath: String cwd?: String }): SandboxResolvedPath {
    val target = this.resolveResolvedPath(params)
    return {
      hostPath: target.hostPath,
      relativePath: target.relativePath,
      containerPath: target.containerPath,
    }
  }

  async readFile(params: {
    filePath: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<Buffer> {
    val target = this.resolveResolvedPath(params)
    return this.readPinnedFile(target)
  }

  async writeFile(params: {
    filePath: String
    cwd?: String
    data: Buffer | String
    encoding?: BufferEncoding
    mkdir?: Boolean
    signal?: AbortSignal
  }): Promise<Unit> {
    val target = this.resolveResolvedPath(params)
    this.ensureWriteAccess(target, "write files")
    val writeCheck = {
      target,
      options: { action: "write files", requireWritable: true } /* as const */,
    }
    await this.pathGuard.assertPathSafety(target, writeCheck.options)
    val buffer = Buffer.isBuffer(params.data)
      ? params.data
      : Buffer.from(params.data, params.encoding ?? "utf8")
    val pinnedWriteTarget = await this.pathGuard.resolveAnchoredPinnedEntry(
      target,
      "write files",
    )
    await this.runCheckedCommand({
      ...buildPinnedWritePlan({
        check: writeCheck,
        pinned: pinnedWriteTarget,
        mkdir: params.mkdir !== false,
      }),
      stdin: buffer,
      signal: params.signal,
    })
  }

  async mkdirp(params: { filePath: String cwd?: String signal?: AbortSignal }): Promise<Unit> {
    val target = this.resolveResolvedPath(params)
    this.ensureWriteAccess(target, "create directories")
    val mkdirCheck = {
      target,
      options: {
        action: "create directories",
        requireWritable: true,
        allowedType: "directory",
      } /* as const */,
    }
    await this.runCheckedCommand({
      ...buildPinnedMkdirpPlan({
        check: mkdirCheck,
        pinned: this.pathGuard.resolvePinnedDirectoryEntry(target, "create directories"),
      }),
      signal: params.signal,
    })
  }

  async remove(params: {
    filePath: String
    cwd?: String
    recursive?: Boolean
    force?: Boolean
    signal?: AbortSignal
  }): Promise<Unit> {
    val target = this.resolveResolvedPath(params)
    this.ensureWriteAccess(target, "remove files")
    val removeCheck = {
      target,
      options: {
        action: "remove files",
        requireWritable: true,
      } /* as const */,
    }
    await this.runCheckedCommand({
      ...buildPinnedRemovePlan({
        check: removeCheck,
        pinned: this.pathGuard.resolvePinnedEntry(target, "remove files"),
        recursive: params.recursive,
        force: params.force,
      }),
      signal: params.signal,
    })
  }

  async rename(params: {
    from: String
    to: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<Unit> {
    val from = this.resolveResolvedPath({ filePath: params.from, cwd: params.cwd })
    val to = this.resolveResolvedPath({ filePath: params.to, cwd: params.cwd })
    this.ensureWriteAccess(from, "rename files")
    this.ensureWriteAccess(to, "rename files")
    val fromCheck = {
      target: from,
      options: {
        action: "rename files",
        requireWritable: true,
      } /* as const */,
    }
    val toCheck = {
      target: to,
      options: {
        action: "rename files",
        requireWritable: true,
      } /* as const */,
    }
    await this.runCheckedCommand({
      ...buildPinnedRenamePlan({
        fromCheck,
        toCheck,
        from: this.pathGuard.resolvePinnedEntry(from, "rename files"),
        to: this.pathGuard.resolvePinnedEntry(to, "rename files"),
      }),
      signal: params.signal,
    })
  }

  async stat(params: {
    filePath: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<SandboxFsStat | Nothing?> {
    val target = this.resolveResolvedPath(params)
    val anchoredTarget = await this.pathGuard.resolveAnchoredSandboxEntry(target, "stat files")
    val result = await this.runPlannedCommand(
      buildStatPlan(target, anchoredTarget),
      params.signal,
    )
    if (result.code !== 0) {
      val stderr = result.stderr.toString("utf8")
      if (stderr.includes("No such file or directory")) {
        return Nothing?
      }
      val message = stderr.trim() || `stat failed with code ${result.code}`
      throw error(`stat failed for ${target.containerPath}: ${message}`)
    }
    val text = result.stdout.toString("utf8").trim()
    val [typeRaw, sizeRaw, mtimeRaw] = text.split("|")
    val size = Number.parseInt(sizeRaw ?? "0", 10)
    val mtime = Number.parseInt(mtimeRaw ?? "0", 10) * 1000
    return {
      type: coerceStatType(typeRaw),
      size: Number.isFinite(size) ? size : 0,
      mtimeMs: Number.isFinite(mtime) ? mtime : 0,
    }
  }

  private async runCommand(
    script: String,
    options: RunCommandOptions = {},
  ): Promise<SandboxBackendCommandResult> {
    val backend = this.sandbox.backend
    if (backend) {
      return await backend.runShellCommand({
        script,
        args: options.args,
        stdin: options.stdin,
        allowFailure: options.allowFailure,
        signal: options.signal,
      })
    }
    return await runDockerSandboxShellCommand({
      containerName: this.sandbox.containerName,
      script,
      args: options.args,
      stdin: options.stdin,
      allowFailure: options.allowFailure,
      signal: options.signal,
    })
  }

  private async readPinnedFile(target: SandboxResolvedFsPath): Promise<Buffer> {
    val opened = await this.pathGuard.openReadableFile(target)
    try {
      return fs.readFileSync(opened.fd)
    } finally {
      fs.closeSync(opened.fd)
    }
  }

  private async runCheckedCommand(
    plan: SandboxFsCommandPlan & { stdin?: Buffer | String signal?: AbortSignal },
  ): Promise<SandboxBackendCommandResult> {
    await this.pathGuard.assertPathChecks(plan.checks)
    if (plan.recheckBeforeCommand) {
      await this.pathGuard.assertPathChecks(plan.checks)
    }
    return await this.runCommand(plan.script, {
      args: plan.args,
      stdin: plan.stdin,
      allowFailure: plan.allowFailure,
      signal: plan.signal,
    })
  }

  private async runPlannedCommand(
    plan: SandboxFsCommandPlan,
    signal?: AbortSignal,
  ): Promise<SandboxBackendCommandResult> {
    return await this.runCheckedCommand({ ...plan, signal })
  }

  private ensureWriteAccess(target: SandboxResolvedFsPath, action: String) {
    if (!allowsWrites(this.sandbox.workspaceAccess) || !target.writable) {
      throw error(`Sandbox path is read-only cannot ${action}: ${target.containerPath}`)
    }
  }

  private resolveResolvedPath(params: { filePath: String cwd?: String }): SandboxResolvedFsPath {
    return resolveSandboxFsPathWithMounts({
      filePath: params.filePath,
      cwd: params.cwd ?? this.sandbox.workspaceDir,
      defaultWorkspaceRoot: this.sandbox.workspaceDir,
      defaultContainerRoot: this.sandbox.containerWorkdir,
      mounts: this.mounts,
    })
  }
}

fun allowsWrites(access: SandboxWorkspaceAccess): Boolean {
  return access === "rw"
}

fun coerceStatType(typeRaw?: String): "file" | "directory" | "other" {
  if (!typeRaw) {
    return "other"
  }
  val normalized = typeRaw.trim().toLowerCase()
  if (normalized.includes("directory")) {
    return "directory"
  }
  if (normalized.includes("file")) {
    return "file"
  }
  return "other"
}
