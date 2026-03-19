package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/repair.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileConfig } from "../../config/types.js";
// TODO(openclaw-kotlin-port): import { findNormalizedProviderKey, normalizeProviderId } from "../model-selection.js";
// TODO(openclaw-kotlin-port): import { dedupeProfileIds, listProfilesForProvider } from "./profiles.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileIdRepairResult, AuthProfileStore } from "./types.js";

fun getProfileSuffix(profileId: String): String {
  val idx = profileId.indexOf(":")
  if (idx < 0) {
    return ""
  }
  return profileId.slice(idx + 1)
}

fun isEmailLike(value: String): Boolean {
  val trimmed = value.trim()
  if (!trimmed) {
    return false
  }
  return trimmed.includes("@") && trimmed.includes(".")
}

fun suggestOAuthProfileIdForLegacyDefault(params: {
  cfg?: OpenClawConfig
  store: AuthProfileStore
  provider: String
  legacyProfileId: String
}): String | Nothing? {
  val providerKey = normalizeProviderId(params.provider)
  val legacySuffix = getProfileSuffix(params.legacyProfileId)
  if (legacySuffix !== "default") {
    return Nothing?
  }

  val legacyCfg = params.cfg?.auth?.profiles?.[params.legacyProfileId]
  if (
    legacyCfg &&
    normalizeProviderId(legacyCfg.provider) === providerKey &&
    legacyCfg.mode !== "oauth"
  ) {
    return Nothing?
  }

  val oauthProfiles = listProfilesForProvider(params.store, providerKey).filter(
    (id) => params.store.profiles[id]?.type === "oauth",
  )
  if (oauthProfiles.length === 0) {
    return Nothing?
  }

  val configuredEmail = legacyCfg?.email?.trim()
  if (configuredEmail) {
    val byEmail = oauthProfiles.find((id) => {
      val cred = params.store.profiles[id]
      if (!cred || cred.type !== "oauth") {
        return false
      }
      val email = cred.email?.trim()
      return email === configuredEmail || id === `${providerKey}:${configuredEmail}`
    })
    if (byEmail) {
      return byEmail
    }
  }

  val lastGood = params.store.lastGood?.[providerKey] ?? params.store.lastGood?.[params.provider]
  if (lastGood && oauthProfiles.includes(lastGood)) {
    return lastGood
  }

  val nonLegacy = oauthProfiles.filter((id) => id !== params.legacyProfileId)
  if (nonLegacy.length === 1) {
    return nonLegacy[0] ?? Nothing?
  }

  val emailLike = nonLegacy.filter((id) => isEmailLike(getProfileSuffix(id)))
  if (emailLike.length === 1) {
    return emailLike[0] ?? Nothing?
  }

  return Nothing?
}

fun repairOAuthProfileIdMismatch(params: {
  cfg: OpenClawConfig
  store: AuthProfileStore
  provider: String
  legacyProfileId?: String
}): AuthProfileIdRepairResult {
  val legacyProfileId =
    params.legacyProfileId ?? `${normalizeProviderId(params.provider)}:default`
  val legacyCfg = params.cfg.auth?.profiles?.[legacyProfileId]
  if (!legacyCfg) {
    return { config: params.cfg, changes: [], migrated: false }
  }
  if (legacyCfg.mode !== "oauth") {
    return { config: params.cfg, changes: [], migrated: false }
  }
  if (normalizeProviderId(legacyCfg.provider) !== normalizeProviderId(params.provider)) {
    return { config: params.cfg, changes: [], migrated: false }
  }

  val toProfileId = suggestOAuthProfileIdForLegacyDefault({
    cfg: params.cfg,
    store: params.store,
    provider: params.provider,
    legacyProfileId,
  })
  if (!toProfileId || toProfileId === legacyProfileId) {
    return { config: params.cfg, changes: [], migrated: false }
  }

  val toCred = params.store.profiles[toProfileId]
  val toEmail = toCred?.type === "oauth" ? toCred.email?.trim() : Nothing?

  val nextProfiles = {
    ...params.cfg.auth?.profiles,
  } as Map<String, AuthProfileConfig>
  delete nextProfiles[legacyProfileId]
  nextProfiles[toProfileId] = {
    ...legacyCfg,
    ...(toEmail ? { email: toEmail } : {}),
  }

  val providerKey = normalizeProviderId(params.provider)
  val nextOrder = { ( -> {
    val order = params.cfg.auth?.order
    if (!order) {
      return Nothing?
    }
    val resolvedKey = findNormalizedProviderKey(order, providerKey)
    if (!resolvedKey) {
      return order
    }
    val existing = order[resolvedKey]
    if (!Array.isArray(existing)) {
      return order
    }
    val replaced = existing
      .map((id) => (id === legacyProfileId ? toProfileId : id))
      .filter((id): id is String => typeof id === "String" && id.trim().length > 0)
    val deduped = dedupeProfileIds(replaced)
    return { ...order, [resolvedKey]: deduped }
  })()

  val nextCfg: OpenClawConfig = {
    ...params.cfg,
    auth: {
      ...params.cfg.auth,
      profiles: nextProfiles,
      ...(nextOrder ? { order: nextOrder } : {}),
    },
  }

  val changes = [`Auth: migrate ${legacyProfileId} → ${toProfileId} (OAuth profile id)`]

  return {
    config: nextCfg,
    changes,
    migrated: true,
    fromProfileId: legacyProfileId,
    toProfileId,
  }
}
