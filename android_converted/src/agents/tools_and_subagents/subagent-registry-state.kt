package agents.tools_and_subagents

// Converted from src/agents/subagent-registry-state.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  loadSubagentRegistryFromDisk,
  saveSubagentRegistryToDisk,
} from "./subagent-registry.store.js"
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

fun persistSubagentRunsToDisk(runs: MutableMap<String, SubagentRunRecord>) {
  try {
    saveSubagentRegistryToDisk(runs)
  } catch (_: Throwable) {
    // ignore persistence failures
  }
}

fun restoreSubagentRunsFromDisk(params: {
  runs: MutableMap<String, SubagentRunRecord>
  mergeOnly?: Boolean
}) {
  val restored = loadSubagentRegistryFromDisk()
  if (restored.size == 0) {
    return 0
  }
  var added = 0
  for (val [runId, entry] of restored.entries()) {
    if (!runId || !entry) {
      continue
    }
    if (params.mergeOnly && params.runs.has(runId)) {
      continue
    }
    params.runs.set(runId, entry)
    added += 1
  }
  return added
}

fun getSubagentRunsSnapshotForRead(
  inMemoryRuns: MutableMap<String, SubagentRunRecord>,
): MutableMap<String, SubagentRunRecord> {
  val merged = new MutableMap<String, SubagentRunRecord>()
  val shouldReadDisk = !(process.env.VITEST || process.env.NODE_ENV == "test")
  if (shouldReadDisk) {
    try {
      // Persisted state lets other worker processes observe active runs.
      for (val [runId, entry] of loadSubagentRegistryFromDisk().entries()) {
        merged.set(runId, entry)
      }
    } catch (_: Throwable) {
      // Ignore disk read failures and fall back to local memory.
    }
  }
  for (val [runId, entry] of inMemoryRuns.entries()) {
    merged.set(runId, entry)
  }
  return merged
}
