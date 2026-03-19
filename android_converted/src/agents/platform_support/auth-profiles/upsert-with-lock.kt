package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/upsert-with-lock.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { withFileLock } from "../../infra/file-lock.js";
// TODO(openclaw-kotlin-port): import { loadJsonFile, saveJsonFile } from "../../infra/json-file.js";
// TODO(openclaw-kotlin-port): import { AUTH_STORE_LOCK_OPTIONS, AUTH_STORE_VERSION } from "./constants.js";
// TODO(openclaw-kotlin-port): import { ensureAuthStoreFile, resolveAuthStorePath } from "./paths.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileCredential, AuthProfileStore, ProfileUsageStats } from "./types.js";

fun coerceAuthProfileStore(raw: Any?): AuthProfileStore {
  val record = raw && typeof raw === "object" ? (raw as Map<String, Any?>) : {}
  val profiles =
    record.profiles && typeof record.profiles === "object" && !Array.isArray(record.profiles)
      ? { ...(record.profiles as Map<String, AuthProfileCredential>) }
      : {}
  val order =
    record.order && typeof record.order === "object" && !Array.isArray(record.order)
      ? (record.order as Map<String, String[]>)
      : Nothing?
  val lastGood =
    record.lastGood && typeof record.lastGood === "object" && !Array.isArray(record.lastGood)
      ? (record.lastGood as Map<String, String>)
      : Nothing?
  val usageStats =
    record.usageStats && typeof record.usageStats === "object" && !Array.isArray(record.usageStats)
      ? (record.usageStats as Map<String, ProfileUsageStats>)
      : Nothing?

  return {
    version:
      typeof record.version === "Double" && Number.isFinite(record.version)
        ? record.version
        : AUTH_STORE_VERSION,
    profiles,
    ...(order ? { order } : {}),
    ...(lastGood ? { lastGood } : {}),
    ...(usageStats ? { usageStats } : {}),
  }
}

suspend fun upsertAuthProfileWithLock(params: {
  profileId: String
  credential: AuthProfileCredential
  agentDir?: String
}): Promise<AuthProfileStore | Nothing?> {
  val authPath = resolveAuthStorePath(params.agentDir)
  ensureAuthStoreFile(authPath)

  try {
    return await withFileLock(authPath, AUTH_STORE_LOCK_OPTIONS, async () => {
      val store = coerceAuthProfileStore(loadJsonFile(authPath))
      store.profiles[params.profileId] = params.credential
      saveJsonFile(authPath, store)
      return store
    })
  } catch {
    return Nothing?
  }
}
