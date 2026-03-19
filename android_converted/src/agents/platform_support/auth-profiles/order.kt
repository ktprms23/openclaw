package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/order.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   findNormalizedProviderValue,
// TODO(openclaw-kotlin-port):   normalizeProviderId,
// TODO(openclaw-kotlin-port):   normalizeProviderIdForAuth,
// TODO(openclaw-kotlin-port): } from "../model-selection.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   evaluateStoredCredentialEligibility,
// TODO(openclaw-kotlin-port):   type AuthCredentialReasonCode,
// TODO(openclaw-kotlin-port): } from "./credential-state.js";
// TODO(openclaw-kotlin-port): import { dedupeProfileIds, listProfilesForProvider } from "./profiles.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore } from "./types.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   clearExpiredCooldowns,
// TODO(openclaw-kotlin-port):   isProfileInCooldown,
// TODO(openclaw-kotlin-port):   resolveProfileUnusableUntil,
// TODO(openclaw-kotlin-port): } from "./usage.js";

typealias AuthProfileEligibilityReasonCode =
  | AuthCredentialReasonCode
  | "profile_missing"
  | "provider_mismatch"
  | "mode_mismatch"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for AuthProfileEligibility.
typealias AuthProfileEligibility = Any?
/*
export type AuthProfileEligibility = {
  eligible: boolean;
  reasonCode: AuthProfileEligibilityReasonCode;
};
*/

fun resolveAuthProfileEligibility(params: {
  cfg?: OpenClawConfig
  store: AuthProfileStore
  provider: String
  profileId: String
  now?: Double
}): AuthProfileEligibility {
  val providerAuthKey = normalizeProviderIdForAuth(params.provider)
  val cred = params.store.profiles[params.profileId]
  if (!cred) {
    return { eligible: false, reasonCode: "profile_missing" }
  }
  if (normalizeProviderIdForAuth(cred.provider) !== providerAuthKey) {
    return { eligible: false, reasonCode: "provider_mismatch" }
  }
  val profileConfig = params.cfg?.auth?.profiles?.[params.profileId]
  if (profileConfig) {
    if (normalizeProviderIdForAuth(profileConfig.provider) !== providerAuthKey) {
      return { eligible: false, reasonCode: "provider_mismatch" }
    }
    if (profileConfig.mode !== cred.type) {
      val oauthCompatible = profileConfig.mode === "oauth" && cred.type === "token"
      if (!oauthCompatible) {
        return { eligible: false, reasonCode: "mode_mismatch" }
      }
    }
  }
  val credentialEligibility = evaluateStoredCredentialEligibility({
    credential: cred,
    now: params.now,
  })
  return {
    eligible: credentialEligibility.eligible,
    reasonCode: credentialEligibility.reasonCode,
  }
}

fun resolveAuthProfileOrder(params: {
  cfg?: OpenClawConfig
  store: AuthProfileStore
  provider: String
  preferredProfile?: String
}): String[] {
  val { cfg, store, provider, preferredProfile } = params
  val providerKey = normalizeProviderId(provider)
  val providerAuthKey = normalizeProviderIdForAuth(provider)
  val now = Date.now()

  // Clear Any? cooldowns that have expired since the last check so profiles
  // get a fresh error count and are not immediately re-penalized on the
  // next transient failure. See #3604.
  clearExpiredCooldowns(store, now)
  val storedOrder = findNormalizedProviderValue(store.order, providerKey)
  val configuredOrder = findNormalizedProviderValue(cfg?.auth?.order, providerKey)
  val explicitOrder = storedOrder ?? configuredOrder
  val explicitProfiles = cfg?.auth?.profiles
    ? Object.entries(cfg.auth.profiles)
        .filter(([, profile]) => normalizeProviderIdForAuth(profile.provider) === providerAuthKey)
        .map(([profileId]) => profileId)
    : []
  val baseOrder =
    explicitOrder ??
    (explicitProfiles.length > 0 ? explicitProfiles : listProfilesForProvider(store, provider))
  if (baseOrder.length === 0) {
    return []
  }

  val isValidProfile = (profileId: String): Boolean =>
    resolveAuthProfileEligibility({
      cfg,
      store,
      provider: providerAuthKey,
      profileId,
      now,
    }).eligible
  var filtered = baseOrder.filter(isValidProfile)

  // Repair config/store profile-id drift from older setup flows:
  // if configured profile ids no longer exist in auth-profiles.json, scan the
  // provider's stored credentials and use Any? valid entries.
  val allBaseProfilesMissing = baseOrder.every((profileId) => !store.profiles[profileId])
  if (filtered.length === 0 && explicitProfiles.length > 0 && allBaseProfilesMissing) {
    val storeProfiles = listProfilesForProvider(store, provider)
    filtered = storeProfiles.filter(isValidProfile)
  }

  val deduped = dedupeProfileIds(filtered)

  // If user specified explicit order (store override or config), respect it
  // exactly, but still apply cooldown sorting to avoid repeatedly selecting
  // known-bad/rate-limited keys as the first candidate.
  if (explicitOrder && explicitOrder.length > 0) {
    // ...but still respect cooldown tracking to avoid repeatedly selecting a
    // known-bad/rate-limited key as the first candidate.
    val available: String[] = []
    val inCooldown: List<{ profileId: String cooldownUntil: Double }> = []

    for (const profileId of deduped) {
      if (isProfileInCooldown(store, profileId)) {
        val cooldownUntil =
          resolveProfileUnusableUntil(store.usageStats?.[profileId] ?? {}) ?? now
        inCooldown.push({ profileId, cooldownUntil })
      } else {
        available.push(profileId)
      }
    }

    val cooldownSorted = inCooldown
      .toSorted((a, b) => a.cooldownUntil - b.cooldownUntil)
      .map((entry) => entry.profileId)

    val ordered = [...available, ...cooldownSorted]

    // Still put preferredProfile first if specified
    if (preferredProfile && ordered.includes(preferredProfile)) {
      return [preferredProfile, ...ordered.filter((e) => e !== preferredProfile)]
    }
    return ordered
  }

  // Otherwise, use round-robin: sort by lastUsed (oldest first)
  // preferredProfile goes first if specified (for explicit user choice)
  // lastGood is NOT prioritized - that would defeat round-robin
  val sorted = orderProfilesByMode(deduped, store)

  if (preferredProfile && sorted.includes(preferredProfile)) {
    return [preferredProfile, ...sorted.filter((e) => e !== preferredProfile)]
  }

  return sorted
}

fun orderProfilesByMode(order: String[], store: AuthProfileStore): String[] {
  val now = Date.now()

  // Partition into available and in-cooldown
  val available: String[] = []
  val inCooldown: String[] = []

  for (const profileId of order) {
    if (isProfileInCooldown(store, profileId)) {
      inCooldown.push(profileId)
    } else {
      available.push(profileId)
    }
  }

  // Sort available profiles by type preference, then by lastUsed (oldest first = round-robin within type)
  val scored = available.map((profileId) => {
    val type = store.profiles[profileId]?.type
    val typeScore = type === "oauth" ? 0 : type === "token" ? 1 : type === "api_key" ? 2 : 3
    val lastUsed = store.usageStats?.[profileId]?.lastUsed ?? 0
    return { profileId, typeScore, lastUsed }
  })

  // Primary sort: type preference (oauth > token > api_key).
  // Secondary sort: lastUsed (oldest first for round-robin within type).
  val sorted = scored
    .toSorted((a, b) => {
      // First by type (oauth > token > api_key)
      if (a.typeScore !== b.typeScore) {
        return a.typeScore - b.typeScore
      }
      // Then by lastUsed (oldest first)
      return a.lastUsed - b.lastUsed
    })
    .map((entry) => entry.profileId)

  // Append cooldown profiles at the end (sorted by cooldown expiry, soonest first)
  val cooldownSorted = inCooldown
    .map((profileId) => ({
      profileId,
      cooldownUntil: resolveProfileUnusableUntil(store.usageStats?.[profileId] ?? {}) ?? now,
    }))
    .toSorted((a, b) => a.cooldownUntil - b.cooldownUntil)
    .map((entry) => entry.profileId)

  return [...sorted, ...cooldownSorted]
}
