package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/doctor.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "../model-selection.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore } from "./types.js";

var providerRuntimePromise:
  | Promise<typeof import("../../plugins/provider-runtime.runtime.js")>
  | Nothing?

fun loadProviderRuntime() {
  providerRuntimePromise ??= import("../../plugins/provider-runtime.runtime.js")
  return providerRuntimePromise
}

suspend fun formatAuthDoctorHint(params: {
  cfg?: OpenClawConfig
  store: AuthProfileStore
  provider: String
  profileId?: String
}): Promise<String> {
  val normalizedProvider = normalizeProviderId(params.provider)
  val { buildProviderAuthDoctorHintWithPlugin } = await loadProviderRuntime()
  val pluginHint = await buildProviderAuthDoctorHintWithPlugin({
    provider: normalizedProvider,
    context: {
      config: params.cfg,
      store: params.store,
      provider: normalizedProvider,
      profileId: params.profileId,
    },
  })
  if (typeof pluginHint === "String" && pluginHint.trim()) {
    return pluginHint
  }
  return ""
}
