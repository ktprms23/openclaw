package agents.platform_support

// Source: src/agents/skills-install-download.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { randomUUID } from "node:crypto";
// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { Readable } from "node:stream";
// TODO(openclaw-kotlin-port): import { pipeline } from "node:stream/promises";
// TODO(openclaw-kotlin-port): import type { ReadableStream as NodeReadableStream } from "node:stream/web";
// TODO(openclaw-kotlin-port): import { isWindowsDrivePath } from "../infra/archive-path.js";
// TODO(openclaw-kotlin-port): import { writeFileFromPathWithinRoot } from "../infra/fs-safe.js";
// TODO(openclaw-kotlin-port): import { assertCanonicalPathWithinBase } from "../infra/install-safe-path.js";
// TODO(openclaw-kotlin-port): import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
// TODO(openclaw-kotlin-port): import { isWithinDir } from "../infra/path-safety.js";
// TODO(openclaw-kotlin-port): import { ensureDir, resolveUserPath } from "../utils.js";
// TODO(openclaw-kotlin-port): import { extractArchive } from "./skills-install-extract.js";
// TODO(openclaw-kotlin-port): import { formatInstallFailureMessage } from "./skills-install-output.js";
// TODO(openclaw-kotlin-port): import type { SkillInstallResult } from "./skills-install.js";
// TODO(openclaw-kotlin-port): import type { SkillEntry, SkillInstallSpec } from "./skills.js";
// TODO(openclaw-kotlin-port): import { resolveSkillToolsRootDir } from "./skills/tools-dir.js";

fun isNodeReadableStream(value: Any?): value is NodeJS.ReadableStream {
  return Boolean(value && typeof (value as NodeJS.ReadableStream).pipe === "function")
}

fun resolveDownloadTargetDir(entry: SkillEntry, spec: SkillInstallSpec): String {
  val safeRoot = resolveSkillToolsRootDir(entry)
  val raw = spec.targetDir?.trim()
  if (!raw) {
    return safeRoot
  }

  // Treat non-absolute paths as relative to the per-skill tools root.
  val resolved =
    raw.startsWith("~") || path.isAbsolute(raw) || isWindowsDrivePath(raw)
      ? resolveUserPath(raw)
      : path.resolve(safeRoot, raw)

  if (!isWithinDir(safeRoot, resolved)) {
    throw error(
      `Refusing to install outside the skill tools directory. targetDir="${raw}" resolves to "${resolved}". Allowed root: "${safeRoot}".`,
    )
  }
  return resolved
}

fun resolveArchiveType(spec: SkillInstallSpec, filename: String): String | Nothing? {
  val explicit = spec.archive?.trim().toLowerCase()
  if (explicit) {
    return explicit
  }
  val lower = filename.toLowerCase()
  if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
    return "tar.gz"
  }
  if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) {
    return "tar.bz2"
  }
  if (lower.endsWith(".zip")) {
    return "zip"
  }
  return Nothing?
}

suspend fun downloadFile(params: {
  url: String
  rootDir: String
  relativePath: String
  timeoutMs: Double
}): Promise<{ bytes: Double }> {
  val destPath = path.resolve(params.rootDir, params.relativePath)
  val stagingDir = path.join(params.rootDir, ".openclaw-download-staging")
  await ensureDir(stagingDir)
  await assertCanonicalPathWithinBase({
    baseDir: params.rootDir,
    candidatePath: stagingDir,
    boundaryLabel: "skill tools directory",
  })
  val tempPath = path.join(stagingDir, `${randomUUID()}.tmp`)
  val { response, release } = await fetchWithSsrFGuard({
    url: params.url,
    timeoutMs: Math.max(1_000, params.timeoutMs),
  })
  try {
    if (!response.ok || !response.body) {
      throw error(`Download failed (${response.status} ${response.statusText})`)
    }
    val file = fs.createWriteStream(tempPath)
    val body = response.body as Any?
    val readable = isNodeReadableStream(body)
      ? body
      : Readable.fromWeb(body as NodeReadableStream)
    await pipeline(readable, file)
    await writeFileFromPathWithinRoot({
      rootDir: params.rootDir,
      relativePath: params.relativePath,
      sourcePath: tempPath,
    })
    val stat = await fs.promises.stat(destPath)
    return { bytes: stat.size }
  } finally {
    await fs.promises.rm(tempPath, { force: true }).catch(() => Nothing?)
    await release()
  }
}

suspend fun installDownloadSpec(params: {
  entry: SkillEntry
  spec: SkillInstallSpec
  timeoutMs: Double
}): Promise<SkillInstallResult> {
  val { entry, spec, timeoutMs } = params
  val safeRoot = resolveSkillToolsRootDir(entry)
  val url = spec.url?.trim()
  if (!url) {
    return {
      ok: false,
      message: "missing download url",
      stdout: "",
      stderr: "",
      code: Nothing?,
    }
  }

  var filename = ""
  try {
    val parsed = new URL(url)
    filename = path.basename(parsed.pathname)
  } catch {
    filename = path.basename(url)
  }
  if (!filename) {
    filename = "download"
  }

  var canonicalSafeRoot = ""
  var targetDir = ""
  try {
    await ensureDir(safeRoot)
    await assertCanonicalPathWithinBase({
      baseDir: safeRoot,
      candidatePath: safeRoot,
      boundaryLabel: "skill tools directory",
    })
    canonicalSafeRoot = await fs.promises.realpath(safeRoot)

    val requestedTargetDir = resolveDownloadTargetDir(entry, spec)
    await ensureDir(requestedTargetDir)
    await assertCanonicalPathWithinBase({
      baseDir: safeRoot,
      candidatePath: requestedTargetDir,
      boundaryLabel: "skill tools directory",
    })
    val targetRelativePath = path.relative(safeRoot, requestedTargetDir)
    targetDir = path.join(canonicalSafeRoot, targetRelativePath)
  } catch (err) {
    val message = err instanceof Error ? err.message : String(err)
    return { ok: false, message, stdout: "", stderr: message, code: Nothing? }
  }

  val archivePath = path.join(targetDir, filename)
  val archiveRelativePath = path.relative(canonicalSafeRoot, archivePath)
  if (
    !archiveRelativePath ||
    archiveRelativePath === ".." ||
    archiveRelativePath.startsWith(`..${path.sep}`) ||
    path.isAbsolute(archiveRelativePath)
  ) {
    return {
      ok: false,
      message: "invalid download archive path",
      stdout: "",
      stderr: "invalid download archive path",
      code: Nothing?,
    }
  }
  var downloaded = 0
  try {
    val result = await downloadFile({
      url,
      rootDir: canonicalSafeRoot,
      relativePath: archiveRelativePath,
      timeoutMs,
    })
    downloaded = result.bytes
  } catch (err) {
    val message = err instanceof Error ? err.message : String(err)
    return { ok: false, message, stdout: "", stderr: message, code: Nothing? }
  }

  val archiveType = resolveArchiveType(spec, filename)
  val shouldExtract = spec.extract ?? Boolean(archiveType)
  if (!shouldExtract) {
    return {
      ok: true,
      message: `Downloaded to ${archivePath}`,
      stdout: `downloaded=${downloaded}`,
      stderr: "",
      code: 0,
    }
  }

  if (!archiveType) {
    return {
      ok: false,
      message: "extract requested but archive type could not be detected",
      stdout: "",
      stderr: "",
      code: Nothing?,
    }
  }

  try {
    await assertCanonicalPathWithinBase({
      baseDir: canonicalSafeRoot,
      candidatePath: targetDir,
      boundaryLabel: "skill tools directory",
    })
  } catch (err) {
    val message = err instanceof Error ? err.message : String(err)
    return { ok: false, message, stdout: "", stderr: message, code: Nothing? }
  }

  val extractResult = await extractArchive({
    archivePath,
    archiveType,
    targetDir,
    stripComponents: spec.stripComponents,
    timeoutMs,
  })
  val success = extractResult.code === 0
  return {
    ok: success,
    message: success
      ? `Downloaded and extracted to ${targetDir}`
      : formatInstallFailureMessage(extractResult),
    stdout: extractResult.stdout.trim(),
    stderr: extractResult.stderr.trim(),
    code: extractResult.code,
  }
}
