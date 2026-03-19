package agents.platform_support.sandbox

// Source: src/agents/sandbox/remote-fs-bridge.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { isPathInside } from "../../infra/path-guards.js";
// TODO(openclaw-kotlin-port): import type { SandboxBackendCommandParams, SandboxBackendCommandResult } from "./backend.js";
// TODO(openclaw-kotlin-port): import { SANDBOX_PINNED_MUTATION_PYTHON } from "./fs-bridge-mutation-helper.js";
// TODO(openclaw-kotlin-port): import type { SandboxFsBridge, SandboxFsStat, SandboxResolvedPath } from "./fs-bridge.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   isPathInsideContainerRoot,
// TODO(openclaw-kotlin-port):   normalizeContainerPath as normalizeSandboxContainerPath,
// TODO(openclaw-kotlin-port): } from "./path-utils.js";
// TODO(openclaw-kotlin-port): import type { SandboxContext } from "./types.js";

typealias ResolvedRemotePath = SandboxResolvedPath & {
  writable: Boolean
  mountRootPath: String
  source: "workspace" | "agent"
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for MountInfo.
typealias MountInfo = Any?
/*
type MountInfo = {
  containerRoot: string;
  writable: boolean;
  source: "workspace" | "agent";
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RemoteShellSandboxHandle.
typealias RemoteShellSandboxHandle = Any?
/*
export type RemoteShellSandboxHandle = {
  remoteWorkspaceDir: string;
  remoteAgentWorkspaceDir: string;
  runRemoteShellScript(params: SandboxBackendCommandParams): Promise<SandboxBackendCommandResult>;
};
*/

fun createRemoteShellSandboxFsBridge(params: {
  sandbox: SandboxContext
  runtime: RemoteShellSandboxHandle
}): SandboxFsBridge {
  return new RemoteShellSandboxFsBridge(params.sandbox, params.runtime)
}

class RemoteShellSandboxFsBridge implements SandboxFsBridge {
  constructor(
    private sandbox: SandboxContext,
    private runtime: RemoteShellSandboxHandle,
  ) {}

  resolvePath(params: { filePath: String cwd?: String }): SandboxResolvedPath {
    val target = this.resolveTarget(params)
    return {
      relativePath: target.relativePath,
      containerPath: target.containerPath,
    }
  }

  async readFile(params: {
    filePath: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<Buffer> {
    val target = this.resolveTarget(params)
    val canonical = await this.resolveCanonicalPath({
      containerPath: target.containerPath,
      action: "read files",
      signal: params.signal,
    })
    await this.assertNoHardlinkedFile({
      containerPath: canonical,
      action: "read files",
      signal: params.signal,
    })
    val result = await this.runRemoteScript({
      script: 'set -eu\ncat -- "$1"',
      args: [canonical],
      signal: params.signal,
    })
    return result.stdout
  }

  async writeFile(params: {
    filePath: String
    cwd?: String
    data: Buffer | String
    encoding?: BufferEncoding
    mkdir?: Boolean
    signal?: AbortSignal
  }): Promise<Unit> {
    val target = this.resolveTarget(params)
    this.ensureWritable(target, "write files")
    val pinned = await this.resolvePinnedParent({
      containerPath: target.containerPath,
      action: "write files",
      requireWritable: true,
    })
    await this.assertNoHardlinkedFile({
      containerPath: target.containerPath,
      action: "write files",
      signal: params.signal,
    })
    val buffer = Buffer.isBuffer(params.data)
      ? params.data
      : Buffer.from(params.data, params.encoding ?? "utf8")
    await this.runMutation({
      args: [
        "write",
        pinned.mountRootPath,
        pinned.relativeParentPath,
        pinned.basename,
        params.mkdir !== false ? "1" : "0",
      ],
      stdin: buffer,
      signal: params.signal,
    })
  }

  async mkdirp(params: { filePath: String cwd?: String signal?: AbortSignal }): Promise<Unit> {
    val target = this.resolveTarget(params)
    this.ensureWritable(target, "create directories")
    val relativePath = path.posix.relative(target.mountRootPath, target.containerPath)
    if (relativePath.startsWith("..") || path.posix.isAbsolute(relativePath)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot create directories: ${target.containerPath}`,
      )
    }
    await this.runMutation({
      args: ["mkdirp", target.mountRootPath, relativePath === "." ? "" : relativePath],
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
    val target = this.resolveTarget(params)
    this.ensureWritable(target, "remove files")
    val exists = await this.remotePathExists(target.containerPath, params.signal)
    if (!exists) {
      if (params.force === false) {
        throw error(`Sandbox path not found cannot remove files: ${target.containerPath}`)
      }
      return
    }
    val pinned = await this.resolvePinnedParent({
      containerPath: target.containerPath,
      action: "remove files",
      requireWritable: true,
      allowFinalSymlinkForUnlink: true,
    })
    await this.runMutation({
      args: [
        "remove",
        pinned.mountRootPath,
        pinned.relativeParentPath,
        pinned.basename,
        params.recursive ? "1" : "0",
        params.force === false ? "0" : "1",
      ],
      signal: params.signal,
      allowFailure: params.force !== false,
    })
  }

  async rename(params: {
    from: String
    to: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<Unit> {
    val from = this.resolveTarget({ filePath: params.from, cwd: params.cwd })
    val to = this.resolveTarget({ filePath: params.to, cwd: params.cwd })
    this.ensureWritable(from, "rename files")
    this.ensureWritable(to, "rename files")
    val fromPinned = await this.resolvePinnedParent({
      containerPath: from.containerPath,
      action: "rename files",
      requireWritable: true,
      allowFinalSymlinkForUnlink: true,
    })
    val toPinned = await this.resolvePinnedParent({
      containerPath: to.containerPath,
      action: "rename files",
      requireWritable: true,
    })
    await this.runMutation({
      args: [
        "rename",
        fromPinned.mountRootPath,
        fromPinned.relativeParentPath,
        fromPinned.basename,
        toPinned.mountRootPath,
        toPinned.relativeParentPath,
        toPinned.basename,
        "1",
      ],
      signal: params.signal,
    })
  }

  async stat(params: {
    filePath: String
    cwd?: String
    signal?: AbortSignal
  }): Promise<SandboxFsStat | Nothing?> {
    val target = this.resolveTarget(params)
    val exists = await this.remotePathExists(target.containerPath, params.signal)
    if (!exists) {
      return Nothing?
    }
    val canonical = await this.resolveCanonicalPath({
      containerPath: target.containerPath,
      action: "stat files",
      signal: params.signal,
    })
    await this.assertNoHardlinkedFile({
      containerPath: canonical,
      action: "stat files",
      signal: params.signal,
    })
    val result = await this.runRemoteScript({
      script: 'set -eu\nstat -c "%F|%s|%Y" -- "$1"',
      args: [canonical],
      signal: params.signal,
    })
    val output = result.stdout.toString("utf8").trim()
    val [kindRaw = "", sizeRaw = "0", mtimeRaw = "0"] = output.split("|")
    return {
      type: kindRaw === "directory" ? "directory" : kindRaw === "regular file" ? "file" : "other",
      size: Number(sizeRaw),
      mtimeMs: Number(mtimeRaw) * 1000,
    }
  }

  private getMounts(): MountInfo[] {
    val mounts: MountInfo[] = [
      {
        containerRoot: normalizeContainerPath(this.runtime.remoteWorkspaceDir),
        writable: this.sandbox.workspaceAccess === "rw",
        source: "workspace",
      },
    ]
    if (
      this.sandbox.workspaceAccess !== "none" &&
      path.resolve(this.sandbox.agentWorkspaceDir) !== path.resolve(this.sandbox.workspaceDir)
    ) {
      mounts.push({
        containerRoot: normalizeContainerPath(this.runtime.remoteAgentWorkspaceDir),
        writable: this.sandbox.workspaceAccess === "rw",
        source: "agent",
      })
    }
    return mounts
  }

  private resolveTarget(params: { filePath: String cwd?: String }): ResolvedRemotePath {
    val workspaceRoot = path.resolve(this.sandbox.workspaceDir)
    val agentRoot = path.resolve(this.sandbox.agentWorkspaceDir)
    val workspaceContainerRoot = normalizeContainerPath(this.runtime.remoteWorkspaceDir)
    val agentContainerRoot = normalizeContainerPath(this.runtime.remoteAgentWorkspaceDir)
    val mounts = this.getMounts()
    val input = params.filePath.trim()
    val inputPosix = input.replace(/\\/g, "/")
    val maybeContainerMount = path.posix.isAbsolute(inputPosix)
      ? this.resolveMountByContainerPath(mounts, normalizeContainerPath(inputPosix))
      : Nothing?
    if (maybeContainerMount) {
      return this.toResolvedPath({
        mount: maybeContainerMount,
        containerPath: normalizeContainerPath(inputPosix),
      })
    }

    val hostCwd = params.cwd ? path.resolve(params.cwd) : workspaceRoot
    val hostCandidate = path.isAbsolute(input)
      ? path.resolve(input)
      : path.resolve(hostCwd, input)
    if (isPathInside(workspaceRoot, hostCandidate)) {
      val relative = toPosixRelative(workspaceRoot, hostCandidate)
      return this.toResolvedPath({
        mount: mounts[0],
        containerPath: relative
          ? path.posix.join(workspaceContainerRoot, relative)
          : workspaceContainerRoot,
      })
    }
    if (mounts[1] && isPathInside(agentRoot, hostCandidate)) {
      val relative = toPosixRelative(agentRoot, hostCandidate)
      return this.toResolvedPath({
        mount: mounts[1],
        containerPath: relative
          ? path.posix.join(agentContainerRoot, relative)
          : agentContainerRoot,
      })
    }

    if (params.cwd) {
      val cwdPosix = params.cwd.replace(/\\/g, "/")
      if (path.posix.isAbsolute(cwdPosix)) {
        val cwdContainer = normalizeContainerPath(cwdPosix)
        val cwdMount = this.resolveMountByContainerPath(mounts, cwdContainer)
        if (cwdMount) {
          return this.toResolvedPath({
            mount: cwdMount,
            containerPath: normalizeContainerPath(path.posix.resolve(cwdContainer, inputPosix)),
          })
        }
      }
    }

    throw error(`Sandbox path escapes allowed mounts cannot access: ${params.filePath}`)
  }

  private toResolvedPath(params: { mount: MountInfo containerPath: String }): ResolvedRemotePath {
    val relative = path.posix.relative(params.mount.containerRoot, params.containerPath)
    if (relative.startsWith("..") || path.posix.isAbsolute(relative)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot access: ${params.containerPath}`,
      )
    }
    return {
      relativePath:
        params.mount.source === "workspace"
          ? relative === "."
            ? ""
            : relative
          : relative === "."
            ? params.mount.containerRoot
            : `${params.mount.containerRoot}/${relative}`,
      containerPath: params.containerPath,
      writable: params.mount.writable,
      mountRootPath: params.mount.containerRoot,
      source: params.mount.source,
    }
  }

  private resolveMountByContainerPath(
    mounts: MountInfo[],
    containerPath: String,
  ): MountInfo | Nothing? {
    val ordered = [...mounts].toSorted((a, b) => b.containerRoot.length - a.containerRoot.length)
    for (const mount of ordered) {
      if (isPathInsideContainerRoot(mount.containerRoot, containerPath)) {
        return mount
      }
    }
    return Nothing?
  }

  private ensureWritable(target: ResolvedRemotePath, action: String) {
    if (this.sandbox.workspaceAccess !== "rw" || !target.writable) {
      throw error(`Sandbox path is read-only cannot ${action}: ${target.containerPath}`)
    }
  }

  private async remotePathExists(containerPath: String, signal?: AbortSignal): Promise<Boolean> {
    val result = await this.runRemoteScript({
      script: 'if [ -e "$1" ] || [ -L "$1" ] then printf "1\\n" else printf "0\\n" fi',
      args: [containerPath],
      signal,
    })
    return result.stdout.toString("utf8").trim() === "1"
  }

  private async resolveCanonicalPath(params: {
    containerPath: String
    action: String
    allowFinalSymlinkForUnlink?: Boolean
    signal?: AbortSignal
  }): Promise<String> {
    val script = [
      "set -eu",
      'target="$1"',
      'allow_final="$2"',
      'suffix=""',
      'probe="$target"',
      'if [ "$allow_final" = "1" ] && [ -L "$target" ] then probe=$(dirname -- "$target") fi',
      'cursor="$probe"',
      'while [ ! -e "$cursor" ] && [ ! -L "$cursor" ] do',
      '  parent=$(dirname -- "$cursor")',
      '  if [ "$parent" = "$cursor" ] then break fi',
      '  base=$(basename -- "$cursor")',
      '  suffix="/$base$suffix"',
      '  cursor="$parent"',
      "done",
      'canonical=$(readlink -f -- "$cursor")',
      'printf "%s%s\\n" "$canonical" "$suffix"',
    ].join("\n")
    val result = await this.runRemoteScript({
      script,
      args: [params.containerPath, params.allowFinalSymlinkForUnlink ? "1" : "0"],
      signal: params.signal,
    })
    val canonical = normalizeContainerPath(result.stdout.toString("utf8").trim())
    if (!this.resolveMountByContainerPath(this.getMounts(), canonical)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot ${params.action}: ${params.containerPath}`,
      )
    }
    return canonical
  }

  private async assertNoHardlinkedFile(params: {
    containerPath: String
    action: String
    signal?: AbortSignal
  }): Promise<Unit> {
    val result = await this.runRemoteScript({
      script: [
        'if [ ! -e "$1" ] && [ ! -L "$1" ] then exit 0 fi',
        'stats=$(stat -c "%F|%h" -- "$1")',
        'printf "%s\\n" "$stats"',
      ].join("\n"),
      args: [params.containerPath],
      signal: params.signal,
      allowFailure: true,
    })
    val output = result.stdout.toString("utf8").trim()
    if (!output) {
      return
    }
    val [kind = "", linksRaw = "1"] = output.split("|")
    if (kind === "regular file" && Number(linksRaw) > 1) {
      throw error(
        `Hardlinked path is not allowed under sandbox mount root: ${params.containerPath}`,
      )
    }
  }

  private async resolvePinnedParent(params: {
    containerPath: String
    action: String
    requireWritable?: Boolean
    allowFinalSymlinkForUnlink?: Boolean
  }): Promise<{ mountRootPath: String relativeParentPath: String basename: String }> {
    val basename = path.posix.basename(params.containerPath)
    if (!basename || basename === "." || basename === "/") {
      throw error(`Invalid sandbox entry target: ${params.containerPath}`)
    }
    val canonicalParent = await this.resolveCanonicalPath({
      containerPath: normalizeContainerPath(path.posix.dirname(params.containerPath)),
      action: params.action,
      allowFinalSymlinkForUnlink: params.allowFinalSymlinkForUnlink,
    })
    val mount = this.resolveMountByContainerPath(this.getMounts(), canonicalParent)
    if (!mount) {
      throw error(
        `Sandbox path escapes allowed mounts cannot ${params.action}: ${params.containerPath}`,
      )
    }
    if (params.requireWritable && !mount.writable) {
      throw error(
        `Sandbox path is read-only cannot ${params.action}: ${params.containerPath}`,
      )
    }
    val relativeParentPath = path.posix.relative(mount.containerRoot, canonicalParent)
    if (relativeParentPath.startsWith("..") || path.posix.isAbsolute(relativeParentPath)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot ${params.action}: ${params.containerPath}`,
      )
    }
    return {
      mountRootPath: mount.containerRoot,
      relativeParentPath: relativeParentPath === "." ? "" : relativeParentPath,
      basename,
    }
  }

  private async runMutation(params: {
    args: String[]
    stdin?: Buffer | String
    signal?: AbortSignal
    allowFailure?: Boolean
  }) {
    await this.runRemoteScript({
      script: [
        "set -eu",
        "python3 /dev/fd/3 \"$@\" 3<<'PY'",
        SANDBOX_PINNED_MUTATION_PYTHON,
        "PY",
      ].join("\n"),
      args: params.args,
      stdin: params.stdin,
      signal: params.signal,
      allowFailure: params.allowFailure,
    })
  }

  private async runRemoteScript(params: {
    script: String
    args?: String[]
    stdin?: Buffer | String
    signal?: AbortSignal
    allowFailure?: Boolean
  }) {
    return await this.runtime.runRemoteShellScript({
      script: params.script,
      args: params.args,
      stdin: params.stdin,
      signal: params.signal,
      allowFailure: params.allowFailure,
    })
  }
}

fun normalizeContainerPath(value: String): String {
  val normalized = normalizeSandboxContainerPath(value.trim() || "/")
  return normalized.startsWith("/") ? normalized : `/${normalized}`
}

fun toPosixRelative(root: String, candidate: String): String {
  return path.relative(root, candidate).split(path.sep).filter(Boolean).join(path.posix.sep)
}
