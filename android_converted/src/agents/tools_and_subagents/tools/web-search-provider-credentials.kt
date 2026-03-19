@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-search-provider-credentials.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO(port-deps): import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";

fun resolveWebSearchProviderCredential(params: {
  credentialValue: unknown;
  path: string;
  envVars: string[];
}): string | null {
  val fromConfigRaw = normalizeResolvedSecretInputString({
    value: params.credentialValue,
    path: params.path,
  });
  val fromConfig = normalizeSecretInput(fromConfigRaw);
  if (fromConfig) {
    return fromConfig;
  }

  for (val envVar of params.envVars) {
    val fromEnv = normalizeSecretInput(process.env[envVar]);
    if (fromEnv) {
      return fromEnv;
    }
  }

  return null;
}
