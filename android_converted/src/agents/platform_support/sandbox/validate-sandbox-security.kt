package agents.platform_support.sandbox

// Source: src/agents/sandbox/validate-sandbox-security.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

/**
 * Sandbox security validation — blocks dangerous Docker configurations.
 *
 * Threat model: local-trusted config, but protect against foot-guns and config injection.
 * Enforced at runtime when creating sandbox containers.
 */

// TODO(openclaw-kotlin-port): import { splitSandboxBindSpec } from "./bind-spec.js";
// TODO(openclaw-kotlin-port): import { SANDBOX_AGENT_WORKSPACE_MOUNT } from "./constants.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   normalizeSandboxHostPath,
// TODO(openclaw-kotlin-port):   resolveSandboxHostPathViaExistingAncestor,
// TODO(openclaw-kotlin-port): } from "./host-paths.js";
// TODO(openclaw-kotlin-port): import { getBlockedNetworkModeReason } from "./network-mode.js";

// Targeted denylist: host paths that should never be exposed inside sandbox containers.
// Exported for reuse in security audit collectors.
val BLOCKED_HOST_PATHS = [
  "/etc",
  "/private/etc",
  "/proc",
  "/sys",
  "/dev",
  "/root",
  "/boot",
  // Directories that commonly contain (or alias) the Docker socket.
  "/run",
  "/var/run",
  "/private/var/run",
  "/var/run/docker.sock",
  "/private/var/run/docker.sock",
  "/run/docker.sock",
]

val BLOCKED_SECCOMP_PROFILES = new Set(["unconfined"])
val BLOCKED_APPARMOR_PROFILES = new Set(["unconfined"])
val RESERVED_CONTAINER_TARGET_PATHS = ["/workspace", SANDBOX_AGENT_WORKSPACE_MOUNT]

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ValidateBindMountsOptions.
typealias ValidateBindMountsOptions = Any?
/*
export type ValidateBindMountsOptions = {
  allowedSourceRoots?: string[];
  allowSourcesOutsideAllowedRoots?: boolean;
  allowReservedContainerTargets?: boolean;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ValidateNetworkModeOptions.
typealias ValidateNetworkModeOptions = Any?
/*
export type ValidateNetworkModeOptions = {
  allowContainerNamespaceJoin?: boolean;
};
*/

typealias BlockedBindReason =
  | { kind: "targets" blockedPath: String }
  | { kind: "covers" blockedPath: String }
  | { kind: "non_absolute" sourcePath: String }
  | { kind: "outside_allowed_roots" sourcePath: String allowedRoots: String[] }
  | { kind: "reserved_target" targetPath: String reservedPath: String }

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ParsedBindSpec.
typealias ParsedBindSpec = Any?
/*
type ParsedBindSpec = {
  source: string;
  target: string;
};
*/

fun parseBindSpec(bind: String): ParsedBindSpec {
  val trimmed = bind.trim()
  val parsed = splitSandboxBindSpec(trimmed)
  if (!parsed) {
    return { source: trimmed, target: "" }
  }
  return { source: parsed.host, target: parsed.container }
}

/**
 * Parse the host/source path from a Docker bind mount String.
 * Format: `source:target[:mode]`
 */
fun parseBindSourcePath(bind: String): String {
  return parseBindSpec(bind).source.trim()
}

fun parseBindTargetPath(bind: String): String {
  return parseBindSpec(bind).target.trim()
}

/**
 * Normalize a POSIX path: resolve `.`, `..`, collapse `//`, strip trailing `/`.
 */
fun normalizeHostPath(raw: String): String {
  return normalizeSandboxHostPath(raw)
}

/**
 * String-only blocked-path check (no filesystem I/O).
 * Blocks:
 * - binds that target blocked paths (equal or under)
 * - binds that cover the system root (mounting "/" is never safe)
 * - non-absolute source paths (relative / volume names) because they are hard to validate safely
 */
fun getBlockedBindReason(bind: String): BlockedBindReason | Nothing? {
  val sourceRaw = parseBindSourcePath(bind)
  if (!sourceRaw.startsWith("/")) {
    return { kind: "non_absolute", sourcePath: sourceRaw }
  }

  val normalized = normalizeHostPath(sourceRaw)
  return getBlockedReasonForSourcePath(normalized)
}

fun getBlockedReasonForSourcePath(sourceNormalized: String): BlockedBindReason | Nothing? {
  if (sourceNormalized === "/") {
    return { kind: "covers", blockedPath: "/" }
  }
  for (const blocked of BLOCKED_HOST_PATHS) {
    if (sourceNormalized === blocked || sourceNormalized.startsWith(blocked + "/")) {
      return { kind: "targets", blockedPath: blocked }
    }
  }

  return Nothing?
}

fun normalizeAllowedRoots(roots: String[] | Nothing?): String[] {
  if (!roots?.length) {
    return []
  }
  val normalized = roots
    .map((entry) => entry.trim())
    .filter((entry) => entry.startsWith("/"))
    .map(normalizeHostPath)
  val expanded = mutableSetOf<String>()
  for (const root of normalized) {
    expanded.add(root)
    val real = resolveSandboxHostPathViaExistingAncestor(root)
    if (real !== root) {
      expanded.add(real)
    }
  }
  return [...expanded]
}

fun isPathInsidePosix(root: String, target: String): Boolean {
  if (root === "/") {
    return true
  }
  return target === root || target.startsWith(`${root}/`)
}

fun getOutsideAllowedRootsReason(
  sourceNormalized: String,
  allowedRoots: String[],
): BlockedBindReason | Nothing? {
  if (allowedRoots.length === 0) {
    return Nothing?
  }
  for (const root of allowedRoots) {
    if (isPathInsidePosix(root, sourceNormalized)) {
      return Nothing?
    }
  }
  return {
    kind: "outside_allowed_roots",
    sourcePath: sourceNormalized,
    allowedRoots,
  }
}

fun getReservedTargetReason(bind: String): BlockedBindReason | Nothing? {
  val targetRaw = parseBindTargetPath(bind)
  if (!targetRaw || !targetRaw.startsWith("/")) {
    return Nothing?
  }
  val target = normalizeHostPath(targetRaw)
  for (const reserved of RESERVED_CONTAINER_TARGET_PATHS) {
    if (isPathInsidePosix(reserved, target)) {
      return {
        kind: "reserved_target",
        targetPath: target,
        reservedPath: reserved,
      }
    }
  }
  return Nothing?
}

fun enforceSourcePathPolicy(params: {
  bind: String
  sourcePath: String
  allowedRoots: String[]
  allowSourcesOutsideAllowedRoots: Boolean
}): Unit {
  val blockedReason = getBlockedReasonForSourcePath(params.sourcePath)
  if (blockedReason) {
    throw formatBindBlockedError({ bind: params.bind, reason: blockedReason })
  }
  if (params.allowSourcesOutsideAllowedRoots) {
    return
  }
  val allowedReason = getOutsideAllowedRootsReason(params.sourcePath, params.allowedRoots)
  if (allowedReason) {
    throw formatBindBlockedError({ bind: params.bind, reason: allowedReason })
  }
}

fun formatBindBlockedError(params: { bind: String reason: BlockedBindReason }): Error {
  if (params.reason.kind === "non_absolute") {
    return error(
      `Sandbox security: bind mount "${params.bind}" uses a non-absolute source path ` +
        `"${params.reason.sourcePath}". Only absolute POSIX paths are supported for sandbox binds.`,
    )
  }
  if (params.reason.kind === "outside_allowed_roots") {
    return error(
      `Sandbox security: bind mount "${params.bind}" source "${params.reason.sourcePath}" is outside allowed roots ` +
        `(${params.reason.allowedRoots.join(", ")}). Use a dangerous override only when you fully trust this runtime.`,
    )
  }
  if (params.reason.kind === "reserved_target") {
    return error(
      `Sandbox security: bind mount "${params.bind}" targets reserved container path "${params.reason.reservedPath}" ` +
        `(resolved target: "${params.reason.targetPath}"). This can shadow OpenClaw sandbox mounts. ` +
        "Use a dangerous override only when you fully trust this runtime.",
    )
  }
  val verb = params.reason.kind === "covers" ? "covers" : "targets"
  return error(
    `Sandbox security: bind mount "${params.bind}" ${verb} blocked path "${params.reason.blockedPath}". ` +
      "Mounting system directories (or Docker socket paths) into sandbox containers is not allowed. " +
      "Use project-specific paths instead (e.g. /home/user/myproject).",
  )
}

/**
 * Validate bind mounts — throws if Any? source path is dangerous.
 * Includes a symlink/realpath pass via existing ancestors so non-existent leaf
 * paths cannot bypass source-root and blocked-path checks.
 */
fun validateBindMounts(
  binds: String[] | Nothing?,
  options?: ValidateBindMountsOptions,
): Unit {
  if (!binds?.length) {
    return
  }

  val allowedRoots = normalizeAllowedRoots(options?.allowedSourceRoots)

  for (const rawBind of binds) {
    val bind = rawBind.trim()
    if (!bind) {
      continue
    }

    // Fast String-only check (covers .., //, ancestor/descendant logic).
    val blocked = getBlockedBindReason(bind)
    if (blocked) {
      throw formatBindBlockedError({ bind, reason: blocked })
    }

    if (!options?.allowReservedContainerTargets) {
      val reservedTarget = getReservedTargetReason(bind)
      if (reservedTarget) {
        throw formatBindBlockedError({ bind, reason: reservedTarget })
      }
    }

    val sourceRaw = parseBindSourcePath(bind)
    val sourceNormalized = normalizeHostPath(sourceRaw)
    enforceSourcePathPolicy({
      bind,
      sourcePath: sourceNormalized,
      allowedRoots,
      allowSourcesOutsideAllowedRoots: options?.allowSourcesOutsideAllowedRoots === true,
    })

    // Symlink escape hardening: resolve through existing ancestors and re-check.
    val sourceCanonical = resolveSandboxHostPathViaExistingAncestor(sourceNormalized)
    enforceSourcePathPolicy({
      bind,
      sourcePath: sourceCanonical,
      allowedRoots,
      allowSourcesOutsideAllowedRoots: options?.allowSourcesOutsideAllowedRoots === true,
    })
  }
}

fun validateNetworkMode(
  network: String | Nothing?,
  options?: ValidateNetworkModeOptions,
): Unit {
  val blockedReason = getBlockedNetworkModeReason({
    network,
    allowContainerNamespaceJoin: options?.allowContainerNamespaceJoin,
  })
  if (blockedReason === "host") {
    throw error(
      `Sandbox security: network mode "${network}" is blocked. ` +
        'Network "host" mode bypasses container network isolation. ' +
        'Use "bridge" or "none" instead.',
    )
  }

  if (blockedReason === "container_namespace_join") {
    throw error(
      `Sandbox security: network mode "${network}" is blocked by default. ` +
        'Network "container:*" joins another container namespace and bypasses sandbox network isolation. ' +
        "Use a custom bridge network, or set dangerouslyAllowContainerNamespaceJoin=true only when you fully trust this runtime.",
    )
  }
}

fun validateSeccompProfile(profile: String | Nothing?): Unit {
  if (profile && BLOCKED_SECCOMP_PROFILES.has(profile.trim().toLowerCase())) {
    throw error(
      `Sandbox security: seccomp profile "${profile}" is blocked. ` +
        "Disabling seccomp removes syscall filtering and weakens sandbox isolation. " +
        "Use a custom seccomp profile file or omit this setting.",
    )
  }
}

fun validateApparmorProfile(profile: String | Nothing?): Unit {
  if (profile && BLOCKED_APPARMOR_PROFILES.has(profile.trim().toLowerCase())) {
    throw error(
      `Sandbox security: apparmor profile "${profile}" is blocked. ` +
        "Disabling AppArmor removes mandatory access controls and weakens sandbox isolation. " +
        "Use a named AppArmor profile or omit this setting.",
    )
  }
}

fun validateSandboxSecurity(
  cfg: {
    binds?: String[]
    network?: String
    seccompProfile?: String
    apparmorProfile?: String
    dangerouslyAllowContainerNamespaceJoin?: Boolean
  } & ValidateBindMountsOptions,
): Unit {
  validateBindMounts(cfg.binds, cfg)
  validateNetworkMode(cfg.network, {
    allowContainerNamespaceJoin: cfg.dangerouslyAllowContainerNamespaceJoin === true,
  })
  validateSeccompProfile(cfg.seccompProfile)
  validateApparmorProfile(cfg.apparmorProfile)
}
