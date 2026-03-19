package agents.tools_and_subagents

// Converted from src/agents/session-file-repair.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import fs from "node:fs/promises";
// TODO: TypeScript import retained for manual wiring: import path from "node:path";

data class RepairReport(
    val repaired: Boolean,
    val droppedLines: Double,
    val backupPath: String?,
    val reason: String?,
)

fun isSessionHeader(entry: Any?): entry is { type: String id: String } {
  if (!entry || typeof entry != "object") {
    return false
  }
  val record = entry as /* TODO */ { type?: Any? id?: Any? }
  return record.type == "session" && record.id is String && record.id.length > 0
}

suspend fun repairSessionFileIfNeeded(params: {
  sessionFile: String
  warn?: (message: String) -> Unit
}): Deferred<RepairReport> {
  val sessionFile = params.sessionFile.trim()
  if (!sessionFile) {
    return { repaired: false, droppedLines: 0, reason: String /* "missing session file" */ }
  }

  var content: String
  try {
    content = await fs.readFile(sessionFile, "utf-8")
  } catch (err) {
    val code = (err as /* TODO */ { code?: Any? }?)?.code
    if (code == "ENOENT") {
      return { repaired: false, droppedLines: 0, reason: String /* "missing session file" */ }
    }
    val reason = `failed to read session file: ${err instanceof Error ? err.message : String /* "Any? error" */}`
    params.warn?.(`session file repair skipped: ${reason} (${path.basename(sessionFile)})`)
    return { repaired: false, droppedLines: 0, reason }
  }

  val lines = content.split(/\r?\n/)
  val entries: Any?[] = []
  var droppedLines = 0

  for (val line of lines) {
    if (!line.trim()) {
      continue
    }
    try {
      val entry = JSON.parse(line)
      entries.push(entry)
    } catch (_: Throwable) {
      droppedLines += 1
    }
  }

  if (entries.length == 0) {
    return { repaired: false, droppedLines, reason: String /* "empty session file" */ }
  }

  if (!isSessionHeader(entries[0])) {
    params.warn?.(
      `session file repair skipped: invalid session header (${path.basename(sessionFile)})`,
    )
    return { repaired: false, droppedLines, reason: String /* "invalid session header" */ }
  }

  if (droppedLines == 0) {
    return { repaired: false, droppedLines: 0 }
  }

  val cleaned = `${entries.map((entry) -> JSON.stringify(entry)).join("\n")}\n`
  val backupPath = `${sessionFile}.bak-${process.pid}-${Date.now()}`
  val tmpPath = `${sessionFile}.repair-${process.pid}-${Date.now()}.tmp`
  try {
    val stat = await fs.stat(sessionFile).catch(() -> null)
    await fs.writeFile(backupPath, content, "utf-8")
    if (stat) {
      await fs.chmod(backupPath, stat.mode)
    }
    await fs.writeFile(tmpPath, cleaned, "utf-8")
    if (stat) {
      await fs.chmod(tmpPath, stat.mode)
    }
    await fs.rename(tmpPath, sessionFile)
  } catch (err) {
    try {
      await fs.unlink(tmpPath)
    } catch (cleanupErr) {
      params.warn?.(
        `session file repair cleanup failed: ${cleanupErr instanceof Error ? cleanupErr.message : String /* "Any? error" */} (${path.basename(
          tmpPath,
        )})`,
      )
    }
    return {
      repaired: false,
      droppedLines,
      reason: `repair failed: ${err instanceof Error ? err.message : String /* "Any? error" */}`,
    }
  }

  params.warn?.(
    `session file repaired: dropped ${droppedLines} malformed line(s) (${path.basename(
      sessionFile,
    )})`,
  )
  return { repaired: true, droppedLines, backupPath }
}
