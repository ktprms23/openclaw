package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-search-provider-credentials.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";

fun resolveWebSearchProviderCredential(params: {
  credentialValue: Any?
  path: String
  envVars: List<String>
}): String? {
  val fromConfigRaw = normalizeResolvedSecretInputString({
    value: params.credentialValue,
    path: params.path,
  })
  val fromConfig = normalizeSecretInput(fromConfigRaw)
  if (fromConfig) {
    return fromConfig
  }

  for (val envVar of params.envVars) {
    val fromEnv = normalizeSecretInput(process.env[envVar])
    if (fromEnv) {
      return fromEnv
    }
  }

  return null
}
