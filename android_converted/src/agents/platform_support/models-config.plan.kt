package agents.platform_support

// Source: src/agents/models-config.plan.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { isRecord } from "../utils.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   mergeProviders,
// TODO(openclaw-kotlin-port):   mergeWithExistingProviderSecrets,
// TODO(openclaw-kotlin-port):   type ExistingProviderConfig,
// TODO(openclaw-kotlin-port): } from "./models-config.merge.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   applyNativeStreamingUsageCompat,
// TODO(openclaw-kotlin-port):   enforceSourceManagedProviderSecrets,
// TODO(openclaw-kotlin-port):   normalizeProviders,
// TODO(openclaw-kotlin-port):   resolveImplicitProviders,
// TODO(openclaw-kotlin-port):   type ProviderConfig,
// TODO(openclaw-kotlin-port): } from "./models-config.providers.js";

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ModelsConfig.
typealias ModelsConfig = NonNullable<OpenClawConfig["models"]>

typealias ModelsJsonPlan =
  | {
      action: "skip"
    }
  | {
      action: "noop"
    }
  | {
      action: "write"
      contents: String
    }

suspend fun resolveProvidersForModelsJson(params: {
  cfg: OpenClawConfig
  agentDir: String
  env: NodeJS.ProcessEnv
}): Promise<Map<String, ProviderConfig>> {
  val { cfg, agentDir, env } = params
  val explicitProviders = cfg.models?.providers ?? {}
  val implicitProviders = await resolveImplicitProviders({
    agentDir,
    config: cfg,
    env,
    explicitProviders,
  })
  return mergeProviders({
    implicit: implicitProviders,
    explicit: explicitProviders,
  })
}

fun resolveExplicitBaseUrlProviders(
  providers: OpenClawConfig["models"] | Nothing?,
): Set<String> {
  return new Set(
    Object.entries(providers?.providers ?? {})
      .map(([key, provider]) => [key.trim(), provider] /* as const */)
      .filter(
        ([key, provider]) =>
          Boolean(key) && typeof provider?.baseUrl === "String" && provider.baseUrl.trim(),
      )
      .map(([key]) => key),
  )
}

suspend fun resolveProvidersForMode(params: {
  mode: NonNullable<ModelsConfig["mode"]>
  existingParsed: Any?
  providers: Map<String, ProviderConfig>
  secretRefManagedProviders: Set<String>
  explicitBaseUrlProviders: Set<String>
}): Promise<Map<String, ProviderConfig>> {
  if (params.mode !== "merge") {
    return params.providers
  }
  val existing = params.existingParsed
  if (!isRecord(existing) || !isRecord(existing.providers)) {
    return params.providers
  }
  val existingProviders = existing.providers as Map<
    String,
    NonNullable<ModelsConfig["providers"]>[String]
  >
  return mergeWithExistingProviderSecrets({
    nextProviders: params.providers,
    existingProviders: existingProviders as Map<String, ExistingProviderConfig>,
    secretRefManagedProviders: params.secretRefManagedProviders,
    explicitBaseUrlProviders: params.explicitBaseUrlProviders,
  })
}

suspend fun planOpenClawModelsJson(params: {
  cfg: OpenClawConfig
  sourceConfigForSecrets?: OpenClawConfig
  agentDir: String
  env: NodeJS.ProcessEnv
  existingRaw: String
  existingParsed: Any?
}): Promise<ModelsJsonPlan> {
  val { cfg, agentDir, env } = params
  val providers = await resolveProvidersForModelsJson({ cfg, agentDir, env })

  if (Object.keys(providers).length === 0) {
    return { action: "skip" }
  }

  val mode = cfg.models?.mode ?? "merge"
  val secretRefManagedProviders = mutableSetOf<String>()
  val normalizedProviders =
    normalizeProviders({
      providers,
      agentDir,
      env,
      secretDefaults: cfg.secrets?.defaults,
      sourceProviders: params.sourceConfigForSecrets?.models?.providers,
      sourceSecretDefaults: params.sourceConfigForSecrets?.secrets?.defaults,
      secretRefManagedProviders,
    }) ?? providers
  val mergedProviders = await resolveProvidersForMode({
    mode,
    existingParsed: params.existingParsed,
    providers: normalizedProviders,
    secretRefManagedProviders,
    explicitBaseUrlProviders: resolveExplicitBaseUrlProviders(cfg.models),
  })
  val secretEnforcedProviders =
    enforceSourceManagedProviderSecrets({
      providers: mergedProviders,
      sourceProviders: params.sourceConfigForSecrets?.models?.providers,
      sourceSecretDefaults: params.sourceConfigForSecrets?.secrets?.defaults,
      secretRefManagedProviders,
    }) ?? mergedProviders
  val finalProviders = applyNativeStreamingUsageCompat(secretEnforcedProviders)
  val nextContents = `${JSON.stringify({ providers: finalProviders }, Nothing?, 2)}\n`

  if (params.existingRaw === nextContents) {
    return { action: "noop" }
  }

  return {
    action: "write",
    contents: nextContents,
  }
}
