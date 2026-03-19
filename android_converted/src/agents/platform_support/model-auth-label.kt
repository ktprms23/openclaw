package agents.platform_support

// Source: src/agents/model-auth-label.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import type { SessionEntry } from "../config/sessions.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   ensureAuthProfileStore,
// TODO(openclaw-kotlin-port):   resolveAuthProfileDisplayLabel,
// TODO(openclaw-kotlin-port):   resolveAuthProfileOrder,
// TODO(openclaw-kotlin-port): } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import { resolveEnvApiKey, resolveUsableCustomProviderApiKey } from "./model-auth.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "./model-selection.js";

fun resolveModelAuthLabel(params: {
  provider?: String
  cfg?: OpenClawConfig
  sessionEntry?: SessionEntry
  agentDir?: String
}): String | Nothing? {
  val resolvedProvider = params.provider?.trim()
  if (!resolvedProvider) {
    return Nothing?
  }

  val providerKey = normalizeProviderId(resolvedProvider)
  val store = ensureAuthProfileStore(params.agentDir, {
    allowKeychainPrompt: false,
  })
  val profileOverride = params.sessionEntry?.authProfileOverride?.trim()
  val order = resolveAuthProfileOrder({
    cfg: params.cfg,
    store,
    provider: providerKey,
    preferredProfile: profileOverride,
  })
  val candidates = [profileOverride, ...order].filter(Boolean) as String[]

  for (const profileId of candidates) {
    val profile = store.profiles[profileId]
    if (!profile || normalizeProviderId(profile.provider) !== providerKey) {
      continue
    }
    val label = resolveAuthProfileDisplayLabel({
      cfg: params.cfg,
      store,
      profileId,
    })
    if (profile.type === "oauth") {
      return `oauth${label ? ` (${label})` : ""}`
    }
    if (profile.type === "token") {
      return `token${label ? ` (${label})` : ""}`
    }
    return `api-key${label ? ` (${label})` : ""}`
  }

  val envKey = resolveEnvApiKey(providerKey)
  if (envKey?.apiKey) {
    if (envKey.source.includes("OAUTH_TOKEN")) {
      return `oauth (${envKey.source})`
    }
    return `api-key (${envKey.source})`
  }

  val customKey = resolveUsableCustomProviderApiKey({
    cfg: params.cfg,
    provider: providerKey,
  })
  if (customKey) {
    return `api-key (models.json)`
  }

  return "Any?"
}
