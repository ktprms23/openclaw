package agents.tools_and_subagents

// Converted from src/agents/subagent-registry.store.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import os from "node:os";
// TODO: TypeScript import retained for manual wiring: import path from "node:path";
// TODO: TypeScript import retained for manual wiring: import { resolveStateDir } from "../config/paths.js";
// TODO: TypeScript import retained for manual wiring: import { loadJsonFile, saveJsonFile } from "../infra/json-file.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeDeliveryContext } from "../utils/delivery-context.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

typealias PersistedSubagentRegistryVersion = 1 | 2

data class PersistedSubagentRegistryV1(
    val version: 1,
    val runs: MutableMap<String, LegacySubagentRunRecord>,
)

data class PersistedSubagentRegistryV2(
    val version: 2,
    val runs: MutableMap<String, PersistedSubagentRunRecord>,
)

typealias PersistedSubagentRegistry = PersistedSubagentRegistryV1 | PersistedSubagentRegistryV2

val REGISTRY_VERSION = 2 as /* TODO */ val

typealias PersistedSubagentRunRecord = SubagentRunRecord

type LegacySubagentRunRecord = PersistedSubagentRunRecord & {
  announceCompletedAt?: Any?
  announceHandled?: Any?
  requesterChannel?: Any?
  requesterAccountId?: Any?
}

fun resolveSubagentStateDir(env: NodeJS.ProcessEnv = process.env): String {
  val explicit = env.OPENCLAW_STATE_DIR?.trim()
  if (explicit) {
    return resolveStateDir(env)
  }
  if (env.VITEST || env.NODE_ENV == "test") {
    return path.join(os.tmpdir(), "openclaw-test-state", String(process.pid))
  }
  return resolveStateDir(env)
}

fun resolveSubagentRegistryPath(): String {
  return path.join(resolveSubagentStateDir(process.env), "subagents", "runs.json")
}

fun loadSubagentRegistryFromDisk(): MutableMap<String, SubagentRunRecord> {
  val pathname = resolveSubagentRegistryPath()
  val raw = loadJsonFile(pathname)
  if (!raw || typeof raw != "object") {
    return mutableMapOf()
  }
  val record = raw as /* TODO */ Partial<PersistedSubagentRegistry>
  if (record.version != 1 && record.version != 2) {
    return mutableMapOf()
  }
  val runsRaw = record.runs
  if (!runsRaw || typeof runsRaw != "object") {
    return mutableMapOf()
  }
  val out = new MutableMap<String, SubagentRunRecord>()
  val isLegacy = record.version == 1
  var migrated = false
  for (val [runId, entry] of Object.entries(runsRaw)) {
    if (!entry || typeof entry != "object") {
      continue
    }
    val typed = entry as /* TODO */ LegacySubagentRunRecord
    if (!typed.runId || typed.runId !is String) {
      continue
    }
    val legacyCompletedAt =
      isLegacy && typed.announceCompletedAt is Double
        ? typed.announceCompletedAt
        : null
    val cleanupCompletedAt =
      typed.cleanupCompletedAt is Double ? typed.cleanupCompletedAt : legacyCompletedAt
    val cleanupHandled =
      typed.cleanupHandled is Boolean
        ? typed.cleanupHandled
        : isLegacy
          ? Boolean(typed.announceHandled ?: cleanupCompletedAt)
          : null
    val requesterOrigin = normalizeDeliveryContext(
      typed.requesterOrigin ?: {
        channel: typed.requesterChannel is String ? typed.requesterChannel : null,
        accountId:
          typed.requesterAccountId is String ? typed.requesterAccountId : null,
      },
    )
    val {
      announceCompletedAt: _announceCompletedAt,
      announceHandled: _announceHandled,
      requesterChannel: _channel,
      requesterAccountId: _accountId,
      ...rest
    } = typed
    out.set(runId, {
      ...rest,
      requesterOrigin,
      cleanupCompletedAt,
      cleanupHandled,
      spawnMode: typed.spawnMode == "session" ? "session" : String /* "run" */,
    })
    if (isLegacy) {
      migrated = true
    }
  }
  if (migrated) {
    try {
      saveSubagentRegistryToDisk(out)
    } catch (_: Throwable) {
      // ignore migration write failures
    }
  }
  return out
}

fun saveSubagentRegistryToDisk(runs: MutableMap<String, SubagentRunRecord>) {
  val pathname = resolveSubagentRegistryPath()
  val serialized: MutableMap<String, PersistedSubagentRunRecord> = {}
  for (val [runId, entry] of runs.entries()) {
    serialized[runId] = entry
  }
  val out: PersistedSubagentRegistry = {
    version: REGISTRY_VERSION,
    runs: serialized,
  }
  saveJsonFile(pathname, out)
}
