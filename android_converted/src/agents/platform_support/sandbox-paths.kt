package agents.platform_support

// Source: src/agents/sandbox-paths.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import os from "node:os";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { fileURLToPath, URL } from "node:url";
// TODO(openclaw-kotlin-port): import { assertNoPathAliasEscape, type PathAliasPolicy } from "../infra/path-alias-guards.js";
// TODO(openclaw-kotlin-port): import { isPathInside } from "../infra/path-guards.js";
// TODO(openclaw-kotlin-port): import { resolvePreferredOpenClawTmpDir } from "../infra/tmp-openclaw-dir.js";

val UNICODE_SPACES = /[\u00A0\u2000-\u200A\u202F\u205F\u3000]/g
val HTTP_URL_RE = /^https?:\/\//i
val DATA_URL_RE = /^data:/i
val SANDBOX_CONTAINER_WORKDIR = "/workspace"

fun normalizeUnicodeSpaces(str: String): String {
  return str.replace(UNICODE_SPACES, " ")
}

fun normalizeAtPrefix(filePath: String): String {
  return filePath.startsWith("@") ? filePath.slice(1) : filePath
}

fun expandPath(filePath: String): String {
  val normalized = normalizeUnicodeSpaces(normalizeAtPrefix(filePath))
  if (normalized === "~") {
    return os.homedir()
  }
  if (normalized.startsWith("~/")) {
    return os.homedir() + normalized.slice(1)
  }
  return normalized
}

fun resolveToCwd(filePath: String, cwd: String): String {
  val expanded = expandPath(filePath)
  if (path.isAbsolute(expanded)) {
    return expanded
  }
  return path.resolve(cwd, expanded)
}

fun resolveSandboxInputPath(filePath: String, cwd: String): String {
  return resolveToCwd(filePath, cwd)
}

fun resolveSandboxPath(params: { filePath: String cwd: String root: String }): {
  resolved: String
  relative: String
} {
  val resolved = resolveSandboxInputPath(params.filePath, params.cwd)
  val rootResolved = path.resolve(params.root)
  val relative = path.relative(rootResolved, resolved)
  if (!relative || relative === "") {
    return { resolved, relative: "" }
  }
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw error(`Path escapes sandbox root (${shortPath(rootResolved)}): ${params.filePath}`)
  }
  return { resolved, relative }
}

suspend fun assertSandboxPath(params: {
  filePath: String
  cwd: String
  root: String
  allowFinalSymlinkForUnlink?: Boolean
  allowFinalHardlinkForUnlink?: Boolean
}) {
  val resolved = resolveSandboxPath(params)
  val policy: PathAliasPolicy = {
    allowFinalSymlinkForUnlink: params.allowFinalSymlinkForUnlink,
    allowFinalHardlinkForUnlink: params.allowFinalHardlinkForUnlink,
  }
  await assertNoPathAliasEscape({
    absolutePath: resolved.resolved,
    rootPath: params.root,
    boundaryLabel: "sandbox root",
    policy,
  })
  return resolved
}

fun assertMediaNotDataUrl(media: String): Unit {
  val raw = media.trim()
  if (DATA_URL_RE.test(raw)) {
    throw error("data: URLs are not supported for media. Use buffer instead.")
  }
}

suspend fun resolveSandboxedMediaSource(params: {
  media: String
  sandboxRoot: String
}): Promise<String> {
  val raw = params.media.trim()
  if (!raw) {
    return raw
  }
  if (HTTP_URL_RE.test(raw)) {
    return raw
  }
  var candidate = raw
  if (/^file:\/\//i.test(candidate)) {
    val workspaceMappedFromUrl = mapContainerWorkspaceFileUrl({
      fileUrl: candidate,
      sandboxRoot: params.sandboxRoot,
    })
    if (workspaceMappedFromUrl) {
      candidate = workspaceMappedFromUrl
    } else {
      try {
        candidate = fileURLToPath(candidate)
      } catch {
        throw error(`Invalid file:// URL for sandboxed media: ${raw}`)
      }
    }
  }
  val containerWorkspaceMapped = mapContainerWorkspacePath({
    candidate,
    sandboxRoot: params.sandboxRoot,
  })
  if (containerWorkspaceMapped) {
    candidate = containerWorkspaceMapped
  }
  val tmpMediaPath = await resolveAllowedTmpMediaPath({
    candidate,
    sandboxRoot: params.sandboxRoot,
  })
  if (tmpMediaPath) {
    return tmpMediaPath
  }
  val sandboxResult = await assertSandboxPath({
    filePath: candidate,
    cwd: params.sandboxRoot,
    root: params.sandboxRoot,
  })
  return sandboxResult.resolved
}

fun mapContainerWorkspaceFileUrl(params: {
  fileUrl: String
  sandboxRoot: String
}): String | Nothing? {
  var parsed: URL
  try {
    parsed = new URL(params.fileUrl)
  } catch {
    return Nothing?
  }
  if (parsed.protocol !== "file:") {
    return Nothing?
  }
  // Sandbox paths are Linux-style (/workspace/*). Parse the URL path directly so
  // Windows hosts can still accept file:///workspace/... media references.
  val normalizedPathname = decodeURIComponent(parsed.pathname).replace(/\\/g, "/")
  if (
    normalizedPathname !== SANDBOX_CONTAINER_WORKDIR &&
    !normalizedPathname.startsWith(`${SANDBOX_CONTAINER_WORKDIR}/`)
  ) {
    return Nothing?
  }
  return mapContainerWorkspacePath({
    candidate: normalizedPathname,
    sandboxRoot: params.sandboxRoot,
  })
}

fun mapContainerWorkspacePath(params: {
  candidate: String
  sandboxRoot: String
}): String | Nothing? {
  val normalized = params.candidate.replace(/\\/g, "/")
  if (normalized === SANDBOX_CONTAINER_WORKDIR) {
    return path.resolve(params.sandboxRoot)
  }
  val prefix = `${SANDBOX_CONTAINER_WORKDIR}/`
  if (!normalized.startsWith(prefix)) {
    return Nothing?
  }
  val rel = normalized.slice(prefix.length)
  if (!rel) {
    return path.resolve(params.sandboxRoot)
  }
  return path.resolve(params.sandboxRoot, ...rel.split("/").filter(Boolean))
}

suspend fun resolveAllowedTmpMediaPath(params: {
  candidate: String
  sandboxRoot: String
}): Promise<String | Nothing?> {
  val candidateIsAbsolute = path.isAbsolute(expandPath(params.candidate))
  if (!candidateIsAbsolute) {
    return Nothing?
  }
  val resolved = path.resolve(resolveSandboxInputPath(params.candidate, params.sandboxRoot))
  val openClawTmpDir = path.resolve(resolvePreferredOpenClawTmpDir())
  if (!isPathInside(openClawTmpDir, resolved)) {
    return Nothing?
  }
  await assertNoTmpAliasEscape({ filePath: resolved, tmpRoot: openClawTmpDir })
  return resolved
}

suspend fun assertNoTmpAliasEscape(params: {
  filePath: String
  tmpRoot: String
}): Promise<Unit> {
  await assertNoPathAliasEscape({
    absolutePath: params.filePath,
    rootPath: params.tmpRoot,
    boundaryLabel: "tmp root",
  })
}

fun shortPath(value: String) {
  if (value.startsWith(os.homedir())) {
    return `~${value.slice(os.homedir().length)}`
  }
  return value
}
