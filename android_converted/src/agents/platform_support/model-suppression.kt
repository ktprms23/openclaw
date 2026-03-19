package agents.platform_support

// Source: src/agents/model-suppression.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { resolveProviderBuiltInModelSuppression } from "../plugins/provider-runtime.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "./provider-id.js";

fun resolveBuiltInModelSuppression(params: { provider?: String | Nothing? id?: String | Nothing? }) {
  val provider = normalizeProviderId(params.provider?.trim().toLowerCase() ?? "")
  val modelId = params.id?.trim().toLowerCase() ?? ""
  if (!provider || !modelId) {
    return Nothing?
  }
  return resolveProviderBuiltInModelSuppression({
    env: process.env,
    context: {
      env: process.env,
      provider,
      modelId,
    },
  })
}

fun shouldSuppressBuiltInModel(params: {
  provider?: String | Nothing?
  id?: String | Nothing?
}) {
  return resolveBuiltInModelSuppression(params)?.suppress ?? false
}

fun buildSuppressedBuiltInModelError(params: {
  provider?: String | Nothing?
  id?: String | Nothing?
}): String | Nothing? {
  return resolveBuiltInModelSuppression(params)?.errorMessage
}
