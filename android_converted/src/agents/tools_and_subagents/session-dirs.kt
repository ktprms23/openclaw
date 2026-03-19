package agents.tools_and_subagents

// Converted from src/agents/session-dirs.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import fsSync, { type Dirent } from "node:fs";
// TODO: TypeScript import retained for manual wiring: import fs from "node:fs/promises";
// TODO: TypeScript import retained for manual wiring: import path from "node:path";

fun mapAgentSessionDirs(agentsDir: String, entries: List<Dirent>): List<String> {
  return entries
    .filter((entry) -> entry.isDirectory())
    .map((entry) -> path.join(agentsDir, entry.name, "sessions"))
    .toSorted((a, b) -> a.localeCompare(b))
}

suspend fun resolveAgentSessionDirsFromAgentsDir(agentsDir: String): Deferred<List<String>> {
  var entries: List<Dirent> = []
  try {
    entries = await fs.readdir(agentsDir, { withFileTypes: true })
  } catch (err) {
    val code = (err as /* TODO */ { code?: String }).code
    if (code == "ENOENT") {
      return []
    }
    throw err
  }

  return mapAgentSessionDirs(agentsDir, entries)
}

fun resolveAgentSessionDirsFromAgentsDirSync(agentsDir: String): List<String> {
  var entries: List<Dirent> = []
  try {
    entries = fsSync.readdirSync(agentsDir, { withFileTypes: true })
  } catch (err) {
    val code = (err as /* TODO */ { code?: String }).code
    if (code == "ENOENT") {
      return []
    }
    throw err
  }

  return mapAgentSessionDirs(agentsDir, entries)
}

suspend fun resolveAgentSessionDirs(stateDir: String): Deferred<List<String>> {
  return await resolveAgentSessionDirsFromAgentsDir(path.join(stateDir, "agents"))
}
