@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-file-repair.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import fs from "node:fs/promises";
// TODO(port-deps): import path from "node:path";

typealias RepairReport = Any /* TODO: translate TypeScript alias */

fun isSessionHeader(entry: unknown): entry is { type: string; id: string } {
  if (!entry || typeof entry != "object") {
    return false;
  }
  val record = entry as { type?: unknown; id?: unknown };
  return record.type == "session" && typeof record.id == "string" && record.id.length > 0;
}

suspend fun repairSessionFileIfNeeded(params: {
  sessionFile: string;
  warn?: (message: string) => void;
}): Promise<RepairReport> {
  val sessionFile = params.sessionFile.trim();
  if (!sessionFile) {
    return { repaired: false, droppedLines: 0, reason: "missing session file" };
  }

  var content: string;
  try {
    content = await fs.readFile(sessionFile, "utf-8");
  } catch (err) {
    val code = (err as { code?: unknown } | null)?.code;
    if (code == "ENOENT") {
      return { repaired: false, droppedLines: 0, reason: "missing session file" };
    }
    val reason = `failed to read session file: ${err instanceof Error ? err.message : "unknown error"}`;
    params.warn?.(`session file repair skipped: ${reason} (${path.basename(sessionFile)})`);
    return { repaired: false, droppedLines: 0, reason };
  }

  val lines = content.split(/\r?\n/);
  val entries: unknown[] = [];
  var droppedLines = 0;

  for (val line of lines) {
    if (!line.trim()) {
      continue;
    }
    try {
      val entry = JSON.parse(line);
      entries.push(entry);
    } catch {
      droppedLines += 1;
    }
  }

  if (entries.length == 0) {
    return { repaired: false, droppedLines, reason: "empty session file" };
  }

  if (!isSessionHeader(entries[0])) {
    params.warn?.(
      `session file repair skipped: invalid session header (${path.basename(sessionFile)})`,
    );
    return { repaired: false, droppedLines, reason: "invalid session header" };
  }

  if (droppedLines == 0) {
    return { repaired: false, droppedLines: 0 };
  }

  val cleaned = `${entries.map((entry) => JSON.stringify(entry)).join("\n")}\n`;
  val backupPath = `${sessionFile}.bak-${process.pid}-${Date.now()}`;
  val tmpPath = `${sessionFile}.repair-${process.pid}-${Date.now()}.tmp`;
  try {
    val stat = await fs.stat(sessionFile).catch(() => null);
    await fs.writeFile(backupPath, content, "utf-8");
    if (stat) {
      await fs.chmod(backupPath, stat.mode);
    }
    await fs.writeFile(tmpPath, cleaned, "utf-8");
    if (stat) {
      await fs.chmod(tmpPath, stat.mode);
    }
    await fs.rename(tmpPath, sessionFile);
  } catch (err) {
    try {
      await fs.unlink(tmpPath);
    } catch (cleanupErr) {
      params.warn?.(
        `session file repair cleanup failed: ${cleanupErr instanceof Error ? cleanupErr.message : "unknown error"} (${path.basename(
          tmpPath,
        )})`,
      );
    }
    return {
      repaired: false,
      droppedLines,
      reason: `repair failed: ${err instanceof Error ? err.message : "unknown error"}`,
    };
  }

  params.warn?.(
    `session file repaired: dropped ${droppedLines} malformed line(s) (${path.basename(
      sessionFile,
    )})`,
  );
  return { repaired: true, droppedLines, backupPath };
}
