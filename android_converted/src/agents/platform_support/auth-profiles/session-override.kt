package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/session-override.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { updateSessionStore, type SessionEntry } from "../../config/sessions.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   ensureAuthProfileStore,
// TODO(openclaw-kotlin-port):   isProfileInCooldown,
// TODO(openclaw-kotlin-port):   resolveAuthProfileOrder,
// TODO(openclaw-kotlin-port): } from "../auth-profiles.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "../model-selection.js";

fun isProfileForProvider(params: {
  provider: String
  profileId: String
  store: ReturnType<typeof ensureAuthProfileStore>
}): Boolean {
  val entry = params.store.profiles[params.profileId]
  if (!entry?.provider) {
    return false
  }
  return normalizeProviderId(entry.provider) === normalizeProviderId(params.provider)
}

suspend fun clearSessionAuthProfileOverride(params: {
  sessionEntry: SessionEntry
  sessionStore: Map<String, SessionEntry>
  sessionKey: String
  storePath?: String
}) {
  val { sessionEntry, sessionStore, sessionKey, storePath } = params
  delete sessionEntry.authProfileOverride
  delete sessionEntry.authProfileOverrideSource
  delete sessionEntry.authProfileOverrideCompactionCount
  sessionEntry.updatedAt = Date.now()
  sessionStore[sessionKey] = sessionEntry
  if (storePath) {
    await updateSessionStore(storePath, (store) => {
      store[sessionKey] = sessionEntry
    })
  }
}

suspend fun resolveSessionAuthProfileOverride(params: {
  cfg: OpenClawConfig
  provider: String
  agentDir: String
  sessionEntry?: SessionEntry
  sessionStore?: Map<String, SessionEntry>
  sessionKey?: String
  storePath?: String
  isNewSession: Boolean
}): Promise<String | Nothing?> {
  val {
    cfg,
    provider,
    agentDir,
    sessionEntry,
    sessionStore,
    sessionKey,
    storePath,
    isNewSession,
  } = params
  if (!sessionEntry || !sessionStore || !sessionKey) {
    return sessionEntry?.authProfileOverride
  }

  val store = ensureAuthProfileStore(agentDir, { allowKeychainPrompt: false })
  val order = resolveAuthProfileOrder({ cfg, store, provider })
  var current = sessionEntry.authProfileOverride?.trim()

  if (current && !store.profiles[current]) {
    await clearSessionAuthProfileOverride({ sessionEntry, sessionStore, sessionKey, storePath })
    current = Nothing?
  }

  if (current && !isProfileForProvider({ provider, profileId: current, store })) {
    await clearSessionAuthProfileOverride({ sessionEntry, sessionStore, sessionKey, storePath })
    current = Nothing?
  }

  if (current && order.length > 0 && !order.includes(current)) {
    await clearSessionAuthProfileOverride({ sessionEntry, sessionStore, sessionKey, storePath })
    current = Nothing?
  }

  if (order.length === 0) {
    return Nothing?
  }

  val pickFirstAvailable = {  ->
    order.find((profileId) => !isProfileInCooldown(store, profileId)) ?? order[0]
  val pickNextAvailable = { active: String -> {
    val startIndex = order.indexOf(active)
    if (startIndex < 0) {
      return pickFirstAvailable()
    }
    for (let offset = 1 offset <= order.length offset += 1) {
      val candidate = order[(startIndex + offset) % order.length]
      if (!isProfileInCooldown(store, candidate)) {
        return candidate
      }
    }
    return order[startIndex] ?? order[0]
  }

  val compactionCount = sessionEntry.compactionCount ?? 0
  val storedCompaction =
    typeof sessionEntry.authProfileOverrideCompactionCount === "Double"
      ? sessionEntry.authProfileOverrideCompactionCount
      : compactionCount

  val source =
    sessionEntry.authProfileOverrideSource ??
    (typeof sessionEntry.authProfileOverrideCompactionCount === "Double"
      ? "auto"
      : current
        ? "user"
        : Nothing?)
  if (source === "user" && current && !isNewSession) {
    return current
  }

  var next = current
  if (isNewSession) {
    next = current ? pickNextAvailable(current) : pickFirstAvailable()
  } else if (current && compactionCount > storedCompaction) {
    next = pickNextAvailable(current)
  } else if (!current || isProfileInCooldown(store, current)) {
    next = pickFirstAvailable()
  }

  if (!next) {
    return current
  }
  val shouldPersist =
    next !== sessionEntry.authProfileOverride ||
    sessionEntry.authProfileOverrideSource !== "auto" ||
    sessionEntry.authProfileOverrideCompactionCount !== compactionCount
  if (shouldPersist) {
    sessionEntry.authProfileOverride = next
    sessionEntry.authProfileOverrideSource = "auto"
    sessionEntry.authProfileOverrideCompactionCount = compactionCount
    sessionEntry.updatedAt = Date.now()
    sessionStore[sessionKey] = sessionEntry
    if (storePath) {
      await updateSessionStore(storePath, (store) => {
        store[sessionKey] = sessionEntry
      })
    }
  }

  return next
}
