package agents.platform_support.sandbox

// Source: src/agents/sandbox/fs-bridge-path-safety.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { openBoundaryFile, type BoundaryFileOpenResult } from "../../infra/boundary-file-read.js";
// TODO(openclaw-kotlin-port): import type { PathAliasPolicy } from "../../infra/path-alias-guards.js";
// TODO(openclaw-kotlin-port): import type { SafeOpenSyncAllowedType } from "../../infra/safe-open-sync.js";
// TODO(openclaw-kotlin-port): import type { SandboxResolvedFsPath, SandboxFsMount } from "./fs-paths.js";
// TODO(openclaw-kotlin-port): import { isPathInsideContainerRoot, normalizeContainerPath } from "./path-utils.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PathSafetyOptions.
typealias PathSafetyOptions = Any?
/*
export type PathSafetyOptions = {
  action: string;
  aliasPolicy?: PathAliasPolicy;
  requireWritable?: boolean;
  allowedType?: SafeOpenSyncAllowedType;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PathSafetyCheck.
typealias PathSafetyCheck = Any?
/*
export type PathSafetyCheck = {
  target: SandboxResolvedFsPath;
  options: PathSafetyOptions;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PinnedSandboxEntry.
typealias PinnedSandboxEntry = Any?
/*
export type PinnedSandboxEntry = {
  mountRootPath: string;
  relativeParentPath: string;
  basename: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for AnchoredSandboxEntry.
typealias AnchoredSandboxEntry = Any?
/*
export type AnchoredSandboxEntry = {
  canonicalParentPath: string;
  basename: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for PinnedSandboxDirectoryEntry.
typealias PinnedSandboxDirectoryEntry = Any?
/*
export type PinnedSandboxDirectoryEntry = {
  mountRootPath: string;
  relativePath: string;
};
*/

typealias RunCommand = (
  script: String,
  options?: {
    args?: String[]
    stdin?: Buffer | String
    allowFailure?: Boolean
    signal?: AbortSignal
  },
) => Promise<{ stdout: Buffer }>

class SandboxFsPathGuard {
  private mountsByContainer: SandboxFsMount[]
  private runCommand: RunCommand

  constructor(params: { mountsByContainer: SandboxFsMount[] runCommand: RunCommand }) {
    this.mountsByContainer = params.mountsByContainer
    this.runCommand = params.runCommand
  }

  async assertPathChecks(checks: PathSafetyCheck[]): Promise<Unit> {
    for (const check of checks) {
      await this.assertPathSafety(check.target, check.options)
    }
  }

  async assertPathSafety(target: SandboxResolvedFsPath, options: PathSafetyOptions) {
    val guarded = await this.openBoundaryWithinRequiredMount(target, options.action, {
      aliasPolicy: options.aliasPolicy,
      allowedType: options.allowedType,
    })
    await this.assertGuardedPathSafety(target, options, guarded)
  }

  async openReadableFile(
    target: SandboxResolvedFsPath,
  ): Promise<BoundaryFileOpenResult & { ok: true }> {
    val opened = await this.openBoundaryWithinRequiredMount(target, "read files")
    if (!opened.ok) {
      throw opened.error instanceof Error
        ? opened.error
        : error(`Sandbox boundary checks failed cannot read files: ${target.containerPath}`)
    }
    return opened
  }

  private resolveRequiredMount(containerPath: String, action: String): SandboxFsMount {
    val lexicalMount = this.resolveMountByContainerPath(containerPath)
    if (!lexicalMount) {
      throw error(`Sandbox path escapes allowed mounts cannot ${action}: ${containerPath}`)
    }
    return lexicalMount
  }

  private finalizePinnedEntry(params: {
    mount: SandboxFsMount
    parentPath: String
    basename: String
    targetPath: String
    action: String
  }): PinnedSandboxEntry {
    val relativeParentPath = path.posix.relative(params.mount.containerRoot, params.parentPath)
    if (relativeParentPath.startsWith("..") || path.posix.isAbsolute(relativeParentPath)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot ${params.action}: ${params.targetPath}`,
      )
    }
    return {
      mountRootPath: params.mount.containerRoot,
      relativeParentPath: relativeParentPath === "." ? "" : relativeParentPath,
      basename: params.basename,
    }
  }

  private async assertGuardedPathSafety(
    target: SandboxResolvedFsPath,
    options: PathSafetyOptions,
    guarded: BoundaryFileOpenResult,
  ) {
    if (!guarded.ok) {
      if (guarded.reason !== "path") {
        val canFallbackToDirectoryStat =
          options.allowedType === "directory" && this.pathIsExistingDirectory(target.hostPath)
        if (!canFallbackToDirectoryStat) {
          throw guarded.error instanceof Error
            ? guarded.error
            : error(
                `Sandbox boundary checks failed cannot ${options.action}: ${target.containerPath}`,
              )
        }
      }
    } else {
      fs.closeSync(guarded.fd)
    }

    val canonicalContainerPath = await this.resolveCanonicalContainerPath({
      containerPath: target.containerPath,
      allowFinalSymlinkForUnlink: options.aliasPolicy?.allowFinalSymlinkForUnlink === true,
    })
    val canonicalMount = this.resolveRequiredMount(canonicalContainerPath, options.action)
    if (options.requireWritable && !canonicalMount.writable) {
      throw error(
        `Sandbox path is read-only cannot ${options.action}: ${target.containerPath}`,
      )
    }
  }

  private async openBoundaryWithinRequiredMount(
    target: SandboxResolvedFsPath,
    action: String,
    options?: {
      aliasPolicy?: PathAliasPolicy
      allowedType?: SafeOpenSyncAllowedType
    },
  ): Promise<BoundaryFileOpenResult> {
    val lexicalMount = this.resolveRequiredMount(target.containerPath, action)
    val guarded = await openBoundaryFile({
      absolutePath: target.hostPath,
      rootPath: lexicalMount.hostRoot,
      boundaryLabel: "sandbox mount root",
      aliasPolicy: options?.aliasPolicy,
      allowedType: options?.allowedType,
    })
    return guarded
  }

  resolvePinnedEntry(target: SandboxResolvedFsPath, action: String): PinnedSandboxEntry {
    val basename = path.posix.basename(target.containerPath)
    if (!basename || basename === "." || basename === "/") {
      throw error(`Invalid sandbox entry target: ${target.containerPath}`)
    }
    val parentPath = normalizeContainerPath(path.posix.dirname(target.containerPath))
    val mount = this.resolveRequiredMount(parentPath, action)
    return this.finalizePinnedEntry({
      mount,
      parentPath,
      basename,
      targetPath: target.containerPath,
      action,
    })
  }

  async resolveAnchoredSandboxEntry(
    target: SandboxResolvedFsPath,
    action: String,
  ): Promise<AnchoredSandboxEntry> {
    val basename = path.posix.basename(target.containerPath)
    if (!basename || basename === "." || basename === "/") {
      throw error(`Invalid sandbox entry target: ${target.containerPath}`)
    }
    val parentPath = normalizeContainerPath(path.posix.dirname(target.containerPath))
    val canonicalParentPath = await this.resolveCanonicalContainerPath({
      containerPath: parentPath,
      allowFinalSymlinkForUnlink: false,
    })
    this.resolveRequiredMount(canonicalParentPath, action)
    return {
      canonicalParentPath,
      basename,
    }
  }

  async resolveAnchoredPinnedEntry(
    target: SandboxResolvedFsPath,
    action: String,
  ): Promise<PinnedSandboxEntry> {
    val anchoredTarget = await this.resolveAnchoredSandboxEntry(target, action)
    val mount = this.resolveRequiredMount(anchoredTarget.canonicalParentPath, action)
    return this.finalizePinnedEntry({
      mount,
      parentPath: anchoredTarget.canonicalParentPath,
      basename: anchoredTarget.basename,
      targetPath: target.containerPath,
      action,
    })
  }

  resolvePinnedDirectoryEntry(
    target: SandboxResolvedFsPath,
    action: String,
  ): PinnedSandboxDirectoryEntry {
    val mount = this.resolveRequiredMount(target.containerPath, action)
    val relativePath = path.posix.relative(mount.containerRoot, target.containerPath)
    if (relativePath.startsWith("..") || path.posix.isAbsolute(relativePath)) {
      throw error(
        `Sandbox path escapes allowed mounts cannot ${action}: ${target.containerPath}`,
      )
    }
    return {
      mountRootPath: mount.containerRoot,
      relativePath: relativePath === "." ? "" : relativePath,
    }
  }

  private pathIsExistingDirectory(hostPath: String): Boolean {
    try {
      return fs.statSync(hostPath).isDirectory()
    } catch {
      return false
    }
  }

  private resolveMountByContainerPath(containerPath: String): SandboxFsMount | Nothing? {
    val normalized = normalizeContainerPath(containerPath)
    for (const mount of this.mountsByContainer) {
      if (isPathInsideContainerRoot(normalizeContainerPath(mount.containerRoot), normalized)) {
        return mount
      }
    }
    return Nothing?
  }

  private async resolveCanonicalContainerPath(params: {
    containerPath: String
    allowFinalSymlinkForUnlink: Boolean
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
    val result = await this.runCommand(script, {
      args: [params.containerPath, params.allowFinalSymlinkForUnlink ? "1" : "0"],
    })
    val canonical = result.stdout.toString("utf8").trim()
    if (!canonical.startsWith("/")) {
      throw error(`Failed to resolve canonical sandbox path: ${params.containerPath}`)
    }
    return normalizeContainerPath(canonical)
  }
}
