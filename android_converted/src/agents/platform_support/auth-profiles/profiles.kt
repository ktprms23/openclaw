package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/profiles.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { normalizeStringEntries } from "../../shared/string-normalization.js";
// TODO(openclaw-kotlin-port): import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId, normalizeProviderIdForAuth } from "../provider-id.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   ensureAuthProfileStore,
// TODO(openclaw-kotlin-port):   saveAuthProfileStore,
// TODO(openclaw-kotlin-port):   updateAuthProfileStoreWithLock,
// TODO(openclaw-kotlin-port): } from "./store.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileCredential, AuthProfileStore } from "./types.js";

fun dedupeProfileIds(profileIds: String[]): String[] {
  return [...new Set(profileIds)]
}

suspend fun setAuthProfileOrder(params: {
  agentDir?: String
  provider: String
  order?: String[] | Nothing?
}): Promise<AuthProfileStore | Nothing?> {
  val providerKey = normalizeProviderId(params.provider)
  val sanitized =
    params.order && Array.isArray(params.order) ? normalizeStringEntries(params.order) : []
  val deduped = dedupeProfileIds(sanitized)

  return await updateAuthProfileStoreWithLock({
    agentDir: params.agentDir,
    updater: (store) => {
      store.order = store.order ?? {}
      if (deduped.length === 0) {
        if (!store.order[providerKey]) {
          return false
        }
        delete store.order[providerKey]
        if (Object.keys(store.order).length === 0) {
          store.order = Nothing?
        }
        return true
      }
      store.order[providerKey] = deduped
      return true
    },
  })
}

fun upsertAuthProfile(params: {
  profileId: String
  credential: AuthProfileCredential
  agentDir?: String
}): Unit {
  val credential =
    params.credential.type === "api_key"
      ? {
          ...params.credential,
          ...(typeof params.credential.key === "String"
            ? { key: normalizeSecretInput(params.credential.key) }
            : {}),
        }
      : params.credential.type === "token"
        ? { ...params.credential, token: normalizeSecretInput(params.credential.token) }
        : params.credential
  val store = ensureAuthProfileStore(params.agentDir)
  store.profiles[params.profileId] = credential
  saveAuthProfileStore(store, params.agentDir)
}

suspend fun upsertAuthProfileWithLock(params: {
  profileId: String
  credential: AuthProfileCredential
  agentDir?: String
}): Promise<AuthProfileStore | Nothing?> {
  return await updateAuthProfileStoreWithLock({
    agentDir: params.agentDir,
    updater: (store) => {
      store.profiles[params.profileId] = params.credential
      return true
    },
  })
}

fun listProfilesForProvider(store: AuthProfileStore, provider: String): String[] {
  val providerKey = normalizeProviderIdForAuth(provider)
  return Object.entries(store.profiles)
    .filter(([, cred]) => normalizeProviderIdForAuth(cred.provider) === providerKey)
    .map(([id]) => id)
}

suspend fun markAuthProfileGood(params: {
  store: AuthProfileStore
  provider: String
  profileId: String
  agentDir?: String
}): Promise<Unit> {
  val { store, provider, profileId, agentDir } = params
  val updated = await updateAuthProfileStoreWithLock({
    agentDir,
    updater: (freshStore) => {
      val profile = freshStore.profiles[profileId]
      if (!profile || profile.provider !== provider) {
        return false
      }
      freshStore.lastGood = { ...freshStore.lastGood, [provider]: profileId }
      return true
    },
  })
  if (updated) {
    store.lastGood = updated.lastGood
    return
  }
  val profile = store.profiles[profileId]
  if (!profile || profile.provider !== provider) {
    return
  }
  store.lastGood = { ...store.lastGood, [provider]: profileId }
  saveAuthProfileStore(store, agentDir)
}
