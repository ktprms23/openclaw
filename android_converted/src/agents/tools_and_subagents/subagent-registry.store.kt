@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry.store.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import os from "node:os";
// TODO(port-deps): import path from "node:path";
// TODO(port-deps): import { resolveStateDir } from "../config/paths.js";
// TODO(port-deps): import { loadJsonFile, saveJsonFile } from "../infra/json-file.js";
// TODO(port-deps): import { normalizeDeliveryContext } from "../utils/delivery-context.js";
// TODO(port-deps): import type { SubagentRunRecord } from "./subagent-registry.types.js";

typealias PersistedSubagentRegistryVersion = Any /* TODO: translate TypeScript alias */

typealias PersistedSubagentRegistryV1 = Any /* TODO: translate TypeScript alias */

typealias PersistedSubagentRegistryV2 = Any /* TODO: translate TypeScript alias */

typealias PersistedSubagentRegistry = Any /* TODO: translate TypeScript alias */

val REGISTRY_VERSION = 2 as const;

typealias PersistedSubagentRunRecord = SubagentRunRecord

typealias LegacySubagentRunRecord = Any /* TODO: translate TypeScript alias */

fun resolveSubagentStateDir(env: NodeJS.ProcessEnv = process.env): string {
  val explicit = env.OPENCLAW_STATE_DIR?.trim();
  if (explicit) {
    return resolveStateDir(env);
  }
  if (env.VITEST || env.NODE_ENV == "test") {
    return path.join(os.tmpdir(), "openclaw-test-state", String(process.pid));
  }
  return resolveStateDir(env);
}

fun resolveSubagentRegistryPath(): string {
  return path.join(resolveSubagentStateDir(process.env), "subagents", "runs.json");
}

fun loadSubagentRegistryFromDisk(): Map<string, SubagentRunRecord> {
  val pathname = resolveSubagentRegistryPath();
  val raw = loadJsonFile(pathname);
  if (!raw || typeof raw != "object") {
    return new Map();
  }
  val record = raw as Partial<PersistedSubagentRegistry>;
  if (record.version != 1 && record.version != 2) {
    return new Map();
  }
  val runsRaw = record.runs;
  if (!runsRaw || typeof runsRaw != "object") {
    return new Map();
  }
  val out = new Map<string, SubagentRunRecord>();
  val isLegacy = record.version == 1;
  var migrated = false;
  for (val [runId, entry] of Object.entries(runsRaw)) {
    if (!entry || typeof entry != "object") {
      continue;
    }
    val typed = entry as LegacySubagentRunRecord;
    if (!typed.runId || typeof typed.runId != "string") {
      continue;
    }
    val legacyCompletedAt =
      isLegacy && typeof typed.announceCompletedAt == "number"
        ? typed.announceCompletedAt
        : null;
    val cleanupCompletedAt =
      typeof typed.cleanupCompletedAt == "number" ? typed.cleanupCompletedAt : legacyCompletedAt;
    val cleanupHandled =
      typeof typed.cleanupHandled == "boolean"
        ? typed.cleanupHandled
        : isLegacy
          ? Boolean(typed.announceHandled ?: cleanupCompletedAt)
          : null;
    val requesterOrigin = normalizeDeliveryContext(
      typed.requesterOrigin ?: {
        channel: typeof typed.requesterChannel == "string" ? typed.requesterChannel : null,
        accountId:
          typeof typed.requesterAccountId == "string" ? typed.requesterAccountId : null,
      },
    );
    val {
      announceCompletedAt: _announceCompletedAt,
      announceHandled: _announceHandled,
      requesterChannel: _channel,
      requesterAccountId: _accountId,
      ...rest
    } = typed;
    out.set(runId, {
      ...rest,
      requesterOrigin,
      cleanupCompletedAt,
      cleanupHandled,
      spawnMode: typed.spawnMode == "session" ? "session" : "run",
    });
    if (isLegacy) {
      migrated = true;
    }
  }
  if (migrated) {
    try {
      saveSubagentRegistryToDisk(out);
    } catch {
      // ignore migration write failures
    }
  }
  return out;
}

fun saveSubagentRegistryToDisk(runs: Map<string, SubagentRunRecord>) {
  val pathname = resolveSubagentRegistryPath();
  val serialized: Record<string, PersistedSubagentRunRecord> = {};
  for (val [runId, entry] of runs.entries()) {
    serialized[runId] = entry;
  }
  val out: PersistedSubagentRegistry = {
    version: REGISTRY_VERSION,
    runs: serialized,
  };
  saveJsonFile(pathname, out);
}
