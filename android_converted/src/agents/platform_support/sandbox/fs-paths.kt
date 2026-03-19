package agents.platform_support.sandbox

// Source: src/agents/sandbox/fs-paths.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { resolveSandboxInputPath, resolveSandboxPath } from "../sandbox-paths.js";
// TODO(openclaw-kotlin-port): import { splitSandboxBindSpec } from "./bind-spec.js";
// TODO(openclaw-kotlin-port): import { SANDBOX_AGENT_WORKSPACE_MOUNT } from "./constants.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxHostPathViaExistingAncestor } from "./host-paths.js";
// TODO(openclaw-kotlin-port): import { isPathInsideContainerRoot, normalizeContainerPath } from "./path-utils.js";
// TODO(openclaw-kotlin-port): import type { SandboxContext } from "./types.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxFsMount.
typealias SandboxFsMount = Any?
/*
export type SandboxFsMount = {
  hostRoot: string;
  containerRoot: string;
  writable: boolean;
  source: "workspace" | "agent" | "bind";
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxResolvedFsPath.
typealias SandboxResolvedFsPath = Any?
/*
export type SandboxResolvedFsPath = {
  hostPath: string;
  relativePath: string;
  containerPath: string;
  writable: boolean;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ParsedBindMount.
typealias ParsedBindMount = Any?
/*
type ParsedBindMount = {
  hostRoot: string;
  containerRoot: string;
  writable: boolean;
};
*/

fun parseSandboxBindMount(spec: String): ParsedBindMount | Nothing? {
  val trimmed = spec.trim()
  if (!trimmed) {
    return Nothing?
  }

  val parsed = splitSandboxBindSpec(trimmed)
  if (!parsed) {
    return Nothing?
  }

  val hostToken = parsed.host.trim()
  val containerToken = parsed.container.trim()
  if (!hostToken || !containerToken || !path.posix.isAbsolute(containerToken)) {
    return Nothing?
  }
  val optionsToken = parsed.options.trim().toLowerCase()
  val optionParts = optionsToken
    ? optionsToken
        .split(",")
        .map((entry) => entry.trim())
        .filter(Boolean)
    : []
  val writable = !optionParts.includes("ro")
  return {
    hostRoot: path.resolve(hostToken),
    containerRoot: normalizeContainerPath(containerToken),
    writable,
  }
}

fun buildSandboxFsMounts(sandbox: SandboxContext): SandboxFsMount[] {
  val mounts: SandboxFsMount[] = [
    {
      hostRoot: path.resolve(sandbox.workspaceDir),
      containerRoot: normalizeContainerPath(sandbox.containerWorkdir),
      writable: sandbox.workspaceAccess === "rw",
      source: "workspace",
    },
  ]

  if (
    sandbox.workspaceAccess !== "none" &&
    path.resolve(sandbox.agentWorkspaceDir) !== path.resolve(sandbox.workspaceDir)
  ) {
    mounts.push({
      hostRoot: path.resolve(sandbox.agentWorkspaceDir),
      containerRoot: SANDBOX_AGENT_WORKSPACE_MOUNT,
      writable: sandbox.workspaceAccess === "rw",
      source: "agent",
    })
  }

  for (const bind of sandbox.docker.binds ?? []) {
    val parsed = parseSandboxBindMount(bind)
    if (!parsed) {
      continue
    }
    mounts.push({
      hostRoot: parsed.hostRoot,
      containerRoot: parsed.containerRoot,
      writable: parsed.writable,
      source: "bind",
    })
  }

  return dedupeMounts(mounts)
}

fun resolveSandboxFsPathWithMounts(params: {
  filePath: String
  cwd: String
  defaultWorkspaceRoot: String
  defaultContainerRoot: String
  mounts: SandboxFsMount[]
}): SandboxResolvedFsPath {
  val mountsByContainer = [...params.mounts].toSorted(compareMountsByContainerPath)
  val mountsByHost = [...params.mounts].toSorted(compareMountsByHostPath)
  val input = params.filePath
  val inputPosix = normalizePosixInput(input)

  if (path.posix.isAbsolute(inputPosix)) {
    val containerMount = findMountByContainerPath(mountsByContainer, inputPosix)
    if (containerMount) {
      val rel = path.posix.relative(containerMount.containerRoot, inputPosix)
      val hostPath = rel
        ? path.resolve(containerMount.hostRoot, ...toHostSegments(rel))
        : containerMount.hostRoot
      return {
        hostPath,
        containerPath: rel
          ? path.posix.join(containerMount.containerRoot, rel)
          : containerMount.containerRoot,
        relativePath: toDisplayRelative({
          containerPath: rel
            ? path.posix.join(containerMount.containerRoot, rel)
            : containerMount.containerRoot,
          defaultContainerRoot: params.defaultContainerRoot,
        }),
        writable: containerMount.writable,
      }
    }
  }

  val hostResolved = resolveSandboxInputPath(input, params.cwd)
  val hostMount = findMountByHostPath(mountsByHost, hostResolved)
  if (hostMount) {
    val relHost = path.relative(hostMount.hostRoot, hostResolved)
    val relPosix = relHost ? relHost.split(path.sep).join(path.posix.sep) : ""
    val containerPath = relPosix
      ? path.posix.join(hostMount.containerRoot, relPosix)
      : hostMount.containerRoot
    return {
      hostPath: hostResolved,
      containerPath,
      relativePath: toDisplayRelative({
        containerPath,
        defaultContainerRoot: params.defaultContainerRoot,
      }),
      writable: hostMount.writable,
    }
  }

  // Preserve legacy error wording for out-of-sandbox paths.
  resolveSandboxPath({
    filePath: input,
    cwd: params.cwd,
    root: params.defaultWorkspaceRoot,
  })
  throw error(`Path escapes sandbox root (${params.defaultWorkspaceRoot}): ${input}`)
}

fun compareMountsByContainerPath(a: SandboxFsMount, b: SandboxFsMount): Double {
  val byLength = b.containerRoot.length - a.containerRoot.length
  if (byLength !== 0) {
    return byLength
  }
  // Keep resolver ordering aligned with docker mount precedence: custom binds can
  // intentionally shadow default workspace mounts at the same container path.
  return mountSourcePriority(b.source) - mountSourcePriority(a.source)
}

fun compareMountsByHostPath(a: SandboxFsMount, b: SandboxFsMount): Double {
  val byLength = b.hostRoot.length - a.hostRoot.length
  if (byLength !== 0) {
    return byLength
  }
  return mountSourcePriority(b.source) - mountSourcePriority(a.source)
}

fun mountSourcePriority(source: SandboxFsMount["source"]): Double {
  if (source === "bind") {
    return 2
  }
  if (source === "agent") {
    return 1
  }
  return 0
}

fun dedupeMounts(mounts: SandboxFsMount[]): SandboxFsMount[] {
  val seen = mutableSetOf<String>()
  val deduped: SandboxFsMount[] = []
  for (const mount of mounts) {
    val key = `${mount.hostRoot}=>${mount.containerRoot}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    deduped.push(mount)
  }
  return deduped
}

fun findMountByContainerPath(mounts: SandboxFsMount[], target: String): SandboxFsMount | Nothing? {
  for (const mount of mounts) {
    if (isPathInsideContainerRoot(mount.containerRoot, target)) {
      return mount
    }
  }
  return Nothing?
}

fun findMountByHostPath(mounts: SandboxFsMount[], target: String): SandboxFsMount | Nothing? {
  for (const mount of mounts) {
    if (isPathInsideHost(mount.hostRoot, target)) {
      return mount
    }
  }
  return Nothing?
}

fun isPathInsideHost(root: String, target: String): Boolean {
  val canonicalRoot = resolveSandboxHostPathViaExistingAncestor(path.resolve(root))
  val resolvedTarget = path.resolve(target)
  // Preserve the final path segment so pre-existing symlink leaves are validated
  // by the dedicated symlink guard later in the bridge flow.
  val canonicalTargetParent = resolveSandboxHostPathViaExistingAncestor(
    path.dirname(resolvedTarget),
  )
  val canonicalTarget = path.resolve(canonicalTargetParent, path.basename(resolvedTarget))
  val rel = path.relative(canonicalRoot, canonicalTarget)
  if (!rel) {
    return true
  }
  return !(rel.startsWith("..") || path.isAbsolute(rel))
}

fun toHostSegments(relativePosix: String): String[] {
  return relativePosix.split("/").filter(Boolean)
}

fun toDisplayRelative(params: {
  containerPath: String
  defaultContainerRoot: String
}): String {
  val rel = path.posix.relative(params.defaultContainerRoot, params.containerPath)
  if (!rel) {
    return ""
  }
  if (!rel.startsWith("..") && !path.posix.isAbsolute(rel)) {
    return rel
  }
  return params.containerPath
}

fun normalizePosixInput(value: String): String {
  return value.replace(/\\/g, "/").trim()
}
