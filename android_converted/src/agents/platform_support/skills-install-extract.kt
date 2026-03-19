package agents.platform_support

// Source: src/agents/skills-install-extract.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { createHash } from "node:crypto";
// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   createTarEntryPreflightChecker,
// TODO(openclaw-kotlin-port):   extractArchive as extractArchiveSafe,
// TODO(openclaw-kotlin-port):   mergeExtractedTreeIntoDestination,
// TODO(openclaw-kotlin-port):   prepareArchiveDestinationDir,
// TODO(openclaw-kotlin-port):   withStagedArchiveDestination,
// TODO(openclaw-kotlin-port): } from "../infra/archive.js";
// TODO(openclaw-kotlin-port): import { runCommandWithTimeout } from "../process/exec.js";
// TODO(openclaw-kotlin-port): import { parseTarVerboseMetadata } from "./skills-install-tar-verbose.js";
// TODO(openclaw-kotlin-port): import { hasBinary } from "./skills.js";

typealias ArchiveExtractResult = { stdout: String stderr: String code: Double | Nothing? }
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for TarPreflightResult.
typealias TarPreflightResult = Any?
/*
type TarPreflightResult = {
  entries: string[];
  metadata: ReturnType<typeof parseTarVerboseMetadata>;
};
*/

suspend fun hashFileSha256(filePath: String): Promise<String> {
  val hash = createHash("sha256")
  val stream = fs.createReadStream(filePath)
  return await new Promise<String>((resolve, reject) => {
    stream.on("data", (chunk) => {
      hash.update(chunk as Buffer)
    })
    stream.on("error", reject)
    stream.on("end", () => {
      resolve(hash.digest("hex"))
    })
  })
}

fun commandFailureResult(
  result: { stdout: String stderr: String code: Double | Nothing? },
  fallbackStderr: String,
): ArchiveExtractResult {
  return {
    stdout: result.stdout,
    stderr: result.stderr || fallbackStderr,
    code: result.code,
  }
}

fun buildTarExtractArgv(params: {
  archivePath: String
  targetDir: String
  stripComponents: Double
}): String[] {
  val argv = ["tar", "xf", params.archivePath, "-C", params.targetDir]
  if (params.stripComponents > 0) {
    argv.push("--strip-components", String(params.stripComponents))
  }
  return argv
}

suspend fun readTarPreflight(params: {
  archivePath: String
  timeoutMs: Double
}): Promise<TarPreflightResult | ArchiveExtractResult> {
  val listResult = await runCommandWithTimeout(["tar", "tf", params.archivePath], {
    timeoutMs: params.timeoutMs,
  })
  if (listResult.code !== 0) {
    return commandFailureResult(listResult, "tar list failed")
  }
  val entries = listResult.stdout
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)

  val verboseResult = await runCommandWithTimeout(["tar", "tvf", params.archivePath], {
    timeoutMs: params.timeoutMs,
  })
  if (verboseResult.code !== 0) {
    return commandFailureResult(verboseResult, "tar verbose list failed")
  }
  val metadata = parseTarVerboseMetadata(verboseResult.stdout)
  if (metadata.length !== entries.length) {
    return {
      stdout: verboseResult.stdout,
      stderr: `tar verbose/list entry count mismatch (${metadata.length} vs ${entries.length})`,
      code: 1,
    }
  }
  return { entries, metadata }
}

fun isArchiveExtractFailure(
  value: TarPreflightResult | ArchiveExtractResult,
): value is ArchiveExtractResult {
  return "code" in value
}

suspend fun verifyArchiveHashStable(params: {
  archivePath: String
  expectedHash: String
}): Promise<ArchiveExtractResult | Nothing?> {
  val postPreflightHash = await hashFileSha256(params.archivePath)
  if (postPreflightHash === params.expectedHash) {
    return Nothing?
  }
  return {
    stdout: "",
    stderr: "tar archive changed during safety preflight refusing to extract",
    code: 1,
  }
}

suspend fun extractTarBz2WithStaging(params: {
  archivePath: String
  destinationRealDir: String
  stripComponents: Double
  timeoutMs: Double
}): Promise<ArchiveExtractResult> {
  return await withStagedArchiveDestination({
    destinationRealDir: params.destinationRealDir,
    run: async (stagingDir) => {
      val extractResult = await runCommandWithTimeout(
        buildTarExtractArgv({
          archivePath: params.archivePath,
          targetDir: stagingDir,
          stripComponents: params.stripComponents,
        }),
        { timeoutMs: params.timeoutMs },
      )
      if (extractResult.code !== 0) {
        return extractResult
      }
      await mergeExtractedTreeIntoDestination({
        sourceDir: stagingDir,
        destinationDir: params.destinationRealDir,
        destinationRealDir: params.destinationRealDir,
      })
      return extractResult
    },
  })
}

suspend fun extractArchive(params: {
  archivePath: String
  archiveType: String
  targetDir: String
  stripComponents?: Double
  timeoutMs: Double
}): Promise<ArchiveExtractResult> {
  val { archivePath, archiveType, targetDir, stripComponents, timeoutMs } = params
  val strip =
    typeof stripComponents === "Double" && Number.isFinite(stripComponents)
      ? Math.max(0, Math.floor(stripComponents))
      : 0

  try {
    if (archiveType === "zip") {
      await extractArchiveSafe({
        archivePath,
        destDir: targetDir,
        timeoutMs,
        kind: "zip",
        stripComponents: strip,
      })
      return { stdout: "", stderr: "", code: 0 }
    }

    if (archiveType === "tar.gz") {
      await extractArchiveSafe({
        archivePath,
        destDir: targetDir,
        timeoutMs,
        kind: "tar",
        stripComponents: strip,
        tarGzip: true,
      })
      return { stdout: "", stderr: "", code: 0 }
    }

    if (archiveType === "tar.bz2") {
      if (!hasBinary("tar")) {
        return { stdout: "", stderr: "tar not found on PATH", code: Nothing? }
      }

      val destinationRealDir = await prepareArchiveDestinationDir(targetDir)
      val preflightHash = await hashFileSha256(archivePath)

      // Preflight list to prevent zip-slip style traversal before extraction.
      val preflight = await readTarPreflight({ archivePath, timeoutMs })
      if (isArchiveExtractFailure(preflight)) {
        return preflight
      }
      val checkTarEntrySafety = createTarEntryPreflightChecker({
        rootDir: destinationRealDir,
        stripComponents: strip,
        escapeLabel: "targetDir",
      })
      for (let i = 0 i < preflight.entries.length i += 1) {
        val entryPath = preflight.entries[i]
        val entryMeta = preflight.metadata[i]
        if (!entryPath || !entryMeta) {
          return {
            stdout: "",
            stderr: "tar metadata parse failure",
            code: 1,
          }
        }
        checkTarEntrySafety({
          path: entryPath,
          type: entryMeta.type,
          size: entryMeta.size,
        })
      }

      val hashFailure = await verifyArchiveHashStable({
        archivePath,
        expectedHash: preflightHash,
      })
      if (hashFailure) {
        return hashFailure
      }

      return await extractTarBz2WithStaging({
        archivePath,
        destinationRealDir,
        stripComponents: strip,
        timeoutMs,
      })
    }

    return { stdout: "", stderr: `unsupported archive type: ${archiveType}`, code: Nothing? }
  } catch (err) {
    val message = err instanceof Error ? err.message : String(err)
    return { stdout: "", stderr: message, code: 1 }
  }
}
