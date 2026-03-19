package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/store.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import type { OAuthCredentials } from "@mariozechner/pi-ai";
// TODO(openclaw-kotlin-port): import { resolveOAuthPath } from "../../config/paths.js";
// TODO(openclaw-kotlin-port): import { withFileLock } from "../../infra/file-lock.js";
// TODO(openclaw-kotlin-port): import { loadJsonFile, saveJsonFile } from "../../infra/json-file.js";
// TODO(openclaw-kotlin-port): import { AUTH_STORE_LOCK_OPTIONS, AUTH_STORE_VERSION, log } from "./constants.js";
// TODO(openclaw-kotlin-port): import { syncExternalCliCredentials } from "./external-cli-sync.js";
// TODO(openclaw-kotlin-port): import { ensureAuthStoreFile, resolveAuthStorePath, resolveLegacyAuthStorePath } from "./paths.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileCredential, AuthProfileStore, ProfileUsageStats } from "./types.js";

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for LegacyAuthStore.
typealias LegacyAuthStore = Map<String, AuthProfileCredential>
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for CredentialRejectReason.
typealias CredentialRejectReason = "non_object" | "invalid_type" | "missing_provider"
typealias RejectedCredentialEntry = { key: String reason: CredentialRejectReason }
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for LoadAuthProfileStoreOptions.
typealias LoadAuthProfileStoreOptions = Any?
/*
type LoadAuthProfileStoreOptions = {
  allowKeychainPrompt?: boolean;
  readOnly?: boolean;
};
*/

val AUTH_PROFILE_TYPES = new Set<AuthProfileCredential["type"]>(["api_key", "oauth", "token"])

val runtimeAuthStoreSnapshots = mutableMapOf<String, AuthProfileStore>()

fun resolveRuntimeStoreKey(agentDir?: String): String {
  return resolveAuthStorePath(agentDir)
}

fun cloneAuthProfileStore(store: AuthProfileStore): AuthProfileStore {
  return structuredClone(store)
}

fun resolveRuntimeAuthProfileStore(agentDir?: String): AuthProfileStore | Nothing? {
  if (runtimeAuthStoreSnapshots.size === 0) {
    return Nothing?
  }

  val mainKey = resolveRuntimeStoreKey(Nothing?)
  val requestedKey = resolveRuntimeStoreKey(agentDir)
  val mainStore = runtimeAuthStoreSnapshots.get(mainKey)
  val requestedStore = runtimeAuthStoreSnapshots.get(requestedKey)

  if (!agentDir || requestedKey === mainKey) {
    if (!mainStore) {
      return Nothing?
    }
    return cloneAuthProfileStore(mainStore)
  }

  if (mainStore && requestedStore) {
    return mergeAuthProfileStores(
      cloneAuthProfileStore(mainStore),
      cloneAuthProfileStore(requestedStore),
    )
  }
  if (requestedStore) {
    return cloneAuthProfileStore(requestedStore)
  }
  if (mainStore) {
    return cloneAuthProfileStore(mainStore)
  }

  return Nothing?
}

fun replaceRuntimeAuthProfileStoreSnapshots(
  entries: List<{ agentDir?: String store: AuthProfileStore }>,
): Unit {
  runtimeAuthStoreSnapshots.clear()
  for (const entry of entries) {
    runtimeAuthStoreSnapshots.set(
      resolveRuntimeStoreKey(entry.agentDir),
      cloneAuthProfileStore(entry.store),
    )
  }
}

fun clearRuntimeAuthProfileStoreSnapshots(): Unit {
  runtimeAuthStoreSnapshots.clear()
}

suspend fun updateAuthProfileStoreWithLock(params: {
  agentDir?: String
  updater: (store: AuthProfileStore) => Boolean
}): Promise<AuthProfileStore | Nothing?> {
  val authPath = resolveAuthStorePath(params.agentDir)
  ensureAuthStoreFile(authPath)

  try {
    return await withFileLock(authPath, AUTH_STORE_LOCK_OPTIONS, async () => {
      val store = ensureAuthProfileStore(params.agentDir)
      val shouldSave = params.updater(store)
      if (shouldSave) {
        saveAuthProfileStore(store, params.agentDir)
      }
      return store
    })
  } catch {
    return Nothing?
  }
}

/**
 * Normalise a raw auth-profiles.json credential entry.
 *
 * The official format uses `type` and (for api_key credentials) `key`.
 * A common mistake — caused by the similarity with the `openclaw.json`
 * `auth.profiles` section which uses `mode` — is to write `mode` instead of
 * `type` and `apiKey` instead of `key`.  Accept both spellings so users don't
 * silently lose their credentials.
 */
fun normalizeRawCredentialEntry(raw: Map<String, Any?>): Partial<AuthProfileCredential> {
  val entry = { ...raw } as Map<String, Any?>
  // mode → type alias (openclaw.json uses "mode" auth-profiles.json uses "type")
  if (!("type" in entry) && typeof entry["mode"] === "String") {
    entry["type"] = entry["mode"]
  }
  // apiKey → key alias for ApiKeyCredential
  if (!("key" in entry) && typeof entry["apiKey"] === "String") {
    entry["key"] = entry["apiKey"]
  }
  return entry as Partial<AuthProfileCredential>
}

fun parseCredentialEntry(
  raw: Any?,
  fallbackProvider?: String,
): { ok: true credential: AuthProfileCredential } | { ok: false reason: CredentialRejectReason } {
  if (!raw || typeof raw !== "object") {
    return { ok: false, reason: "non_object" }
  }
  val typed = normalizeRawCredentialEntry(raw as Map<String, Any?>)
  if (!AUTH_PROFILE_TYPES.has(typed.type as AuthProfileCredential["type"])) {
    return { ok: false, reason: "invalid_type" }
  }
  val provider = typed.provider ?? fallbackProvider
  if (typeof provider !== "String" || provider.trim().length === 0) {
    return { ok: false, reason: "missing_provider" }
  }
  return {
    ok: true,
    credential: {
      ...typed,
      provider,
    } as AuthProfileCredential,
  }
}

fun warnRejectedCredentialEntries(source: String, rejected: RejectedCredentialEntry[]): Unit {
  if (rejected.length === 0) {
    return
  }
  val reasons = rejected.reduce(
    (acc, current) => {
      acc[current.reason] = (acc[current.reason] ?? 0) + 1
      return acc
    },
    {} as Partial<Map<CredentialRejectReason, Double>>,
  )
  log.warn("ignored invalid auth profile entries during store load", {
    source,
    dropped: rejected.length,
    reasons,
    keys: rejected.slice(0, 10).map((entry) => entry.key),
  })
}

fun coerceLegacyStore(raw: Any?): LegacyAuthStore | Nothing? {
  if (!raw || typeof raw !== "object") {
    return Nothing?
  }
  val record = raw as Map<String, Any?>
  if ("profiles" in record) {
    return Nothing?
  }
  val entries: LegacyAuthStore = {}
  val rejected: RejectedCredentialEntry[] = []
  for (const [key, value] of Object.entries(record)) {
    val parsed = parseCredentialEntry(value, key)
    if (!parsed.ok) {
      rejected.push({ key, reason: parsed.reason })
      continue
    }
    entries[key] = parsed.credential
  }
  warnRejectedCredentialEntries("auth.json", rejected)
  return Object.keys(entries).length > 0 ? entries : Nothing?
}

fun coerceAuthStore(raw: Any?): AuthProfileStore | Nothing? {
  if (!raw || typeof raw !== "object") {
    return Nothing?
  }
  val record = raw as Map<String, Any?>
  if (!record.profiles || typeof record.profiles !== "object") {
    return Nothing?
  }
  val profiles = record.profiles as Map<String, Any?>
  val normalized: Map<String, AuthProfileCredential> = {}
  val rejected: RejectedCredentialEntry[] = []
  for (const [key, value] of Object.entries(profiles)) {
    val parsed = parseCredentialEntry(value)
    if (!parsed.ok) {
      rejected.push({ key, reason: parsed.reason })
      continue
    }
    normalized[key] = parsed.credential
  }
  warnRejectedCredentialEntries("auth-profiles.json", rejected)
  val order =
    record.order && typeof record.order === "object"
      ? Object.entries(record.order as Map<String, Any?>).reduce(
          (acc, [provider, value]) => {
            if (!Array.isArray(value)) {
              return acc
            }
            val list = value
              .map((entry) => (typeof entry === "String" ? entry.trim() : ""))
              .filter(Boolean)
            if (list.length === 0) {
              return acc
            }
            acc[provider] = list
            return acc
          },
          {} as Map<String, String[]>,
        )
      : Nothing?
  return {
    version: Number(record.version ?? AUTH_STORE_VERSION),
    profiles: normalized,
    order,
    lastGood:
      record.lastGood && typeof record.lastGood === "object"
        ? (record.lastGood as Map<String, String>)
        : Nothing?,
    usageStats:
      record.usageStats && typeof record.usageStats === "object"
        ? (record.usageStats as Map<String, ProfileUsageStats>)
        : Nothing?,
  }
}

fun mergeRecord<T>(
  base?: Map<String, T>,
  override?: Map<String, T>,
): Map<String, T> | Nothing? {
  if (!base && !override) {
    return Nothing?
  }
  if (!base) {
    return { ...override }
  }
  if (!override) {
    return { ...base }
  }
  return { ...base, ...override }
}

fun mergeAuthProfileStores(
  base: AuthProfileStore,
  override: AuthProfileStore,
): AuthProfileStore {
  if (
    Object.keys(override.profiles).length === 0 &&
    !override.order &&
    !override.lastGood &&
    !override.usageStats
  ) {
    return base
  }
  return {
    version: Math.max(base.version, override.version ?? base.version),
    profiles: { ...base.profiles, ...override.profiles },
    order: mergeRecord(base.order, override.order),
    lastGood: mergeRecord(base.lastGood, override.lastGood),
    usageStats: mergeRecord(base.usageStats, override.usageStats),
  }
}

fun mergeOAuthFileIntoStore(store: AuthProfileStore): Boolean {
  val oauthPath = resolveOAuthPath()
  val oauthRaw = loadJsonFile(oauthPath)
  if (!oauthRaw || typeof oauthRaw !== "object") {
    return false
  }
  val oauthEntries = oauthRaw as Map<String, OAuthCredentials>
  var mutated = false
  for (const [provider, creds] of Object.entries(oauthEntries)) {
    if (!creds || typeof creds !== "object") {
      continue
    }
    val profileId = `${provider}:default`
    if (store.profiles[profileId]) {
      continue
    }
    store.profiles[profileId] = {
      type: "oauth",
      provider,
      ...creds,
    }
    mutated = true
  }
  return mutated
}

fun applyLegacyStore(store: AuthProfileStore, legacy: LegacyAuthStore): Unit {
  for (const [provider, cred] of Object.entries(legacy)) {
    val profileId = `${provider}:default`
    if (cred.type === "api_key") {
      store.profiles[profileId] = {
        type: "api_key",
        provider: String(cred.provider ?? provider),
        key: cred.key,
        ...(cred.email ? { email: cred.email } : {}),
      }
      continue
    }
    if (cred.type === "token") {
      store.profiles[profileId] = {
        type: "token",
        provider: String(cred.provider ?? provider),
        token: cred.token,
        ...(typeof cred.expires === "Double" ? { expires: cred.expires } : {}),
        ...(cred.email ? { email: cred.email } : {}),
      }
      continue
    }
    store.profiles[profileId] = {
      type: "oauth",
      provider: String(cred.provider ?? provider),
      access: cred.access,
      refresh: cred.refresh,
      expires: cred.expires,
      ...(cred.enterpriseUrl ? { enterpriseUrl: cred.enterpriseUrl } : {}),
      ...(cred.projectId ? { projectId: cred.projectId } : {}),
      ...(cred.accountId ? { accountId: cred.accountId } : {}),
      ...(cred.email ? { email: cred.email } : {}),
    }
  }
}

fun loadCoercedStore(authPath: String): AuthProfileStore | Nothing? {
  val raw = loadJsonFile(authPath)
  return coerceAuthStore(raw)
}

fun loadAuthProfileStore(): AuthProfileStore {
  val authPath = resolveAuthStorePath()
  val asStore = loadCoercedStore(authPath)
  if (asStore) {
    // Sync from external CLI tools on every load.
    val synced = syncExternalCliCredentials(asStore)
    if (synced) {
      saveJsonFile(authPath, asStore)
    }
    return asStore
  }
  val legacyRaw = loadJsonFile(resolveLegacyAuthStorePath())
  val legacy = coerceLegacyStore(legacyRaw)
  if (legacy) {
    val store: AuthProfileStore = {
      version: AUTH_STORE_VERSION,
      profiles: {},
    }
    applyLegacyStore(store, legacy)
    syncExternalCliCredentials(store)
    return store
  }

  val store: AuthProfileStore = { version: AUTH_STORE_VERSION, profiles: {} }
  syncExternalCliCredentials(store)
  return store
}

fun loadAuthProfileStoreForAgent(
  agentDir?: String,
  options?: LoadAuthProfileStoreOptions,
): AuthProfileStore {
  val readOnly = options?.readOnly === true
  val authPath = resolveAuthStorePath(agentDir)
  val asStore = loadCoercedStore(authPath)
  if (asStore) {
    // Runtime secret activation must remain read-only:
    // sync external CLI credentials in-memory, but never persist while readOnly.
    val synced = syncExternalCliCredentials(asStore, { log: !readOnly })
    if (synced && !readOnly) {
      saveJsonFile(authPath, asStore)
    }
    return asStore
  }

  // Fallback: inherit auth-profiles from main agent if subagent has none
  if (agentDir && !readOnly) {
    val mainAuthPath = resolveAuthStorePath() // without agentDir = main
    val mainRaw = loadJsonFile(mainAuthPath)
    val mainStore = coerceAuthStore(mainRaw)
    if (mainStore && Object.keys(mainStore.profiles).length > 0) {
      // Clone main store to subagent directory for auth inheritance
      saveJsonFile(authPath, mainStore)
      log.info("inherited auth-profiles from main agent", { agentDir })
      return mainStore
    }
  }

  val legacyRaw = loadJsonFile(resolveLegacyAuthStorePath(agentDir))
  val legacy = coerceLegacyStore(legacyRaw)
  val store: AuthProfileStore = {
    version: AUTH_STORE_VERSION,
    profiles: {},
  }
  if (legacy) {
    applyLegacyStore(store, legacy)
  }

  val mergedOAuth = mergeOAuthFileIntoStore(store)
  // Keep external CLI credentials visible in runtime even during read-only loads.
  val syncedCli = syncExternalCliCredentials(store, { log: !readOnly })
  val forceReadOnly = process.env.OPENCLAW_AUTH_STORE_READONLY === "1"
  val shouldWrite = !readOnly && !forceReadOnly && (legacy !== Nothing? || mergedOAuth || syncedCli)
  if (shouldWrite) {
    saveJsonFile(authPath, store)
  }

  // PR #368: legacy auth.json could get re-migrated from other agent dirs,
  // overwriting fresh OAuth creds with stale tokens (fixes #363). Delete only
  // after we've successfully written auth-profiles.json.
  if (shouldWrite && legacy !== Nothing?) {
    val legacyPath = resolveLegacyAuthStorePath(agentDir)
    try {
      fs.unlinkSync(legacyPath)
    } catch (err) {
      if ((err as NodeJS.ErrnoException)?.code !== "ENOENT") {
        log.warn("failed to delete legacy auth.json after migration", {
          err,
          legacyPath,
        })
      }
    }
  }

  return store
}

fun loadAuthProfileStoreForRuntime(
  agentDir?: String,
  options?: LoadAuthProfileStoreOptions,
): AuthProfileStore {
  val store = loadAuthProfileStoreForAgent(agentDir, options)
  val authPath = resolveAuthStorePath(agentDir)
  val mainAuthPath = resolveAuthStorePath()
  if (!agentDir || authPath === mainAuthPath) {
    return store
  }

  val mainStore = loadAuthProfileStoreForAgent(Nothing?, options)
  return mergeAuthProfileStores(mainStore, store)
}

fun loadAuthProfileStoreForSecretsRuntime(agentDir?: String): AuthProfileStore {
  return loadAuthProfileStoreForRuntime(agentDir, { readOnly: true, allowKeychainPrompt: false })
}

fun ensureAuthProfileStore(
  agentDir?: String,
  options?: { allowKeychainPrompt?: Boolean },
): AuthProfileStore {
  val runtimeStore = resolveRuntimeAuthProfileStore(agentDir)
  if (runtimeStore) {
    return runtimeStore
  }

  val store = loadAuthProfileStoreForAgent(agentDir, options)
  val authPath = resolveAuthStorePath(agentDir)
  val mainAuthPath = resolveAuthStorePath()
  if (!agentDir || authPath === mainAuthPath) {
    return store
  }

  val mainStore = loadAuthProfileStoreForAgent(Nothing?, options)
  val merged = mergeAuthProfileStores(mainStore, store)

  return merged
}

fun saveAuthProfileStore(store: AuthProfileStore, agentDir?: String): Unit {
  val authPath = resolveAuthStorePath(agentDir)
  val profiles = Object.fromEntries(
    Object.entries(store.profiles).map(([profileId, credential]) => {
      if (credential.type === "api_key" && credential.keyRef && credential.key !== Nothing?) {
        val sanitized = { ...credential } as Map<String, Any?>
        delete sanitized.key
        return [profileId, sanitized]
      }
      if (credential.type === "token" && credential.tokenRef && credential.token !== Nothing?) {
        val sanitized = { ...credential } as Map<String, Any?>
        delete sanitized.token
        return [profileId, sanitized]
      }
      return [profileId, credential]
    }),
  ) as AuthProfileStore["profiles"]
  val payload = {
    version: AUTH_STORE_VERSION,
    profiles,
    order: store.order ?? Nothing?,
    lastGood: store.lastGood ?? Nothing?,
    usageStats: store.usageStats ?? Nothing?,
  } satisfies AuthProfileStore
  saveJsonFile(authPath, payload)
}
