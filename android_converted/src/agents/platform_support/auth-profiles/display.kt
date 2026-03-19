package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/display.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore } from "./types.js";

fun resolveAuthProfileDisplayLabel(params: {
  cfg?: OpenClawConfig
  store: AuthProfileStore
  profileId: String
}): String {
  val { cfg, store, profileId } = params
  val profile = store.profiles[profileId]
  val configEmail = cfg?.auth?.profiles?.[profileId]?.email?.trim()
  val email = configEmail || (profile && "email" in profile ? profile.email?.trim() : Nothing?)
  if (email) {
    return `${profileId} (${email})`
  }
  return profileId
}
