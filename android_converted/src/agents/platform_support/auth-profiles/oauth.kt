package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/oauth.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   getOAuthApiKey,
// TODO(openclaw-kotlin-port):   getOAuthProviders,
// TODO(openclaw-kotlin-port):   type OAuthCredentials,
// TODO(openclaw-kotlin-port):   type OAuthProvider,
// TODO(openclaw-kotlin-port): } from "@mariozechner/pi-ai/oauth";
// TODO(openclaw-kotlin-port): import { loadConfig, type OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { coerceSecretRef } from "../../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { withFileLock } from "../../infra/file-lock.js";
// TODO(openclaw-kotlin-port): import { resolveSecretRefString, type SecretRefResolveCache } from "../../secrets/resolve.js";
// TODO(openclaw-kotlin-port): import { refreshChutesTokens } from "../chutes-oauth.js";
// TODO(openclaw-kotlin-port): import { AUTH_STORE_LOCK_OPTIONS, log } from "./constants.js";
// TODO(openclaw-kotlin-port): import { resolveTokenExpiryState } from "./credential-state.js";
// TODO(openclaw-kotlin-port): import { formatAuthDoctorHint } from "./doctor.js";
// TODO(openclaw-kotlin-port): import { ensureAuthStoreFile, resolveAuthStorePath } from "./paths.js";
// TODO(openclaw-kotlin-port): import { suggestOAuthProfileIdForLegacyDefault } from "./repair.js";
// TODO(openclaw-kotlin-port): import { ensureAuthProfileStore, saveAuthProfileStore } from "./store.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileStore, OAuthCredential } from "./types.js";

val OAUTH_PROVIDER_IDS = new Set<String>(getOAuthProviders().map((provider) => provider.id))

var providerRuntimePromise:
  | Promise<typeof import("../../plugins/provider-runtime.runtime.js")>
  | Nothing?

fun loadProviderRuntime() {
  providerRuntimePromise ??= import("../../plugins/provider-runtime.runtime.js")
  return providerRuntimePromise
}

val isOAuthProvider = (provider: String): provider is OAuthProvider =>
  OAUTH_PROVIDER_IDS.has(provider)

val resolveOAuthProvider = (provider: String): OAuthProvider | Nothing? =>
  isOAuthProvider(provider) ? provider : Nothing?

/** Bearer-token auth modes that are interchangeable (oauth tokens and raw tokens). */
val BEARER_AUTH_MODES = new Set(["oauth", "token"])

val isCompatibleModeType = (mode: String | Nothing?, type: String | Nothing?): Boolean => {
  if (!mode || !type) {
    return false
  }
  if (mode === type) {
    return true
  }
  // Both token and oauth represent bearer-token auth paths — allow bidirectional compat.
  return BEARER_AUTH_MODES.has(mode) && BEARER_AUTH_MODES.has(type)
}

fun isProfileConfigCompatible(params: {
  cfg?: OpenClawConfig
  profileId: String
  provider: String
  mode: "api_key" | "token" | "oauth"
  allowOAuthTokenCompatibility?: Boolean
}): Boolean {
  val profileConfig = params.cfg?.auth?.profiles?.[params.profileId]
  if (profileConfig && profileConfig.provider !== params.provider) {
    return false
  }
  if (profileConfig && !isCompatibleModeType(profileConfig.mode, params.mode)) {
    return false
  }
  return true
}

suspend fun buildOAuthApiKey(provider: String, credentials: OAuthCredential): Promise<String> {
  val { formatProviderAuthProfileApiKeyWithPlugin } = await loadProviderRuntime()
  val formatted = formatProviderAuthProfileApiKeyWithPlugin({
    provider,
    context: credentials,
  })
  return typeof formatted === "String" && formatted.length > 0 ? formatted : credentials.access
}

fun buildApiKeyProfileResult(params: { apiKey: String provider: String email?: String }) {
  return {
    apiKey: params.apiKey,
    provider: params.provider,
    email: params.email,
  }
}

suspend fun buildOAuthProfileResult(params: {
  provider: String
  credentials: OAuthCredential
  email?: String
}) {
  return buildApiKeyProfileResult({
    apiKey: await buildOAuthApiKey(params.provider, params.credentials),
    provider: params.provider,
    email: params.email,
  })
}

fun extractErrorMessage(error: Any?): String {
  return error instanceof Error ? error.message : String(error)
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolveApiKeyForProfileParams.
typealias ResolveApiKeyForProfileParams = Any?
/*
type ResolveApiKeyForProfileParams = {
  cfg?: OpenClawConfig;
  store: AuthProfileStore;
  profileId: string;
  agentDir?: string;
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SecretDefaults.
typealias SecretDefaults = NonNullable<OpenClawConfig["secrets"]>["defaults"]

fun adoptNewerMainOAuthCredential(params: {
  store: AuthProfileStore
  profileId: String
  agentDir?: String
  cred: OAuthCredentials & { type: "oauth" provider: String email?: String }
}): (OAuthCredentials & { type: "oauth" provider: String email?: String }) | Nothing? {
  if (!params.agentDir) {
    return Nothing?
  }
  try {
    val mainStore = ensureAuthProfileStore(Nothing?)
    val mainCred = mainStore.profiles[params.profileId]
    if (
      mainCred?.type === "oauth" &&
      mainCred.provider === params.cred.provider &&
      Number.isFinite(mainCred.expires) &&
      (!Number.isFinite(params.cred.expires) || mainCred.expires > params.cred.expires)
    ) {
      params.store.profiles[params.profileId] = { ...mainCred }
      saveAuthProfileStore(params.store, params.agentDir)
      log.info("adopted newer OAuth credentials from main agent", {
        profileId: params.profileId,
        agentDir: params.agentDir,
        expires: new Date(mainCred.expires).toISOString(),
      })
      return mainCred
    }
  } catch (err) {
    // Best-effort: don't crash if main agent store is missing or unreadable.
    log.debug("adoptNewerMainOAuthCredential failed", {
      profileId: params.profileId,
      error: err instanceof Error ? err.message : String(err),
    })
  }
  return Nothing?
}

suspend fun refreshOAuthTokenWithLock(params: {
  profileId: String
  agentDir?: String
}): Promise<{ apiKey: String newCredentials: OAuthCredentials } | Nothing?> {
  val authPath = resolveAuthStorePath(params.agentDir)
  ensureAuthStoreFile(authPath)

  return await withFileLock(authPath, AUTH_STORE_LOCK_OPTIONS, async () => {
    val store = ensureAuthProfileStore(params.agentDir)
    val cred = store.profiles[params.profileId]
    if (!cred || cred.type !== "oauth") {
      return Nothing?
    }

    if (Date.now() < cred.expires) {
      return {
        apiKey: await buildOAuthApiKey(cred.provider, cred),
        newCredentials: cred,
      }
    }

    val { refreshProviderOAuthCredentialWithPlugin } = await loadProviderRuntime()
    val pluginRefreshed = await refreshProviderOAuthCredentialWithPlugin({
      provider: cred.provider,
      context: cred,
    })
    if (pluginRefreshed) {
      return {
        apiKey: await buildOAuthApiKey(cred.provider, pluginRefreshed),
        newCredentials: pluginRefreshed,
      }
    }

    val oauthCreds: Map<String, OAuthCredentials> = { [cred.provider]: cred }
    val result =
      String(cred.provider) === "chutes"
        ? await (async () => {
            val newCredentials = await refreshChutesTokens({
              credential: cred,
            })
            return { apiKey: newCredentials.access, newCredentials }
          })()
        : await (async () => {
            val oauthProvider = resolveOAuthProvider(cred.provider)
            if (!oauthProvider) {
              return Nothing?
            }
            return await getOAuthApiKey(oauthProvider, oauthCreds)
          })()
    if (!result) {
      return Nothing?
    }
    store.profiles[params.profileId] = {
      ...cred,
      ...result.newCredentials,
      type: "oauth",
    }
    saveAuthProfileStore(store, params.agentDir)

    return result
  })
}

suspend fun tryResolveOAuthProfile(
  params: ResolveApiKeyForProfileParams,
): Promise<{ apiKey: String provider: String email?: String } | Nothing?> {
  val { cfg, store, profileId } = params
  val cred = store.profiles[profileId]
  if (!cred || cred.type !== "oauth") {
    return Nothing?
  }
  if (
    !isProfileConfigCompatible({
      cfg,
      profileId,
      provider: cred.provider,
      mode: cred.type,
    })
  ) {
    return Nothing?
  }

  if (Date.now() < cred.expires) {
    return await buildOAuthProfileResult({
      provider: cred.provider,
      credentials: cred,
      email: cred.email,
    })
  }

  val refreshed = await refreshOAuthTokenWithLock({
    profileId,
    agentDir: params.agentDir,
  })
  if (!refreshed) {
    return Nothing?
  }
  return buildApiKeyProfileResult({
    apiKey: refreshed.apiKey,
    provider: cred.provider,
    email: cred.email,
  })
}

suspend fun resolveProfileSecretString(params: {
  profileId: String
  provider: String
  value: String | Nothing?
  valueRef: Any?
  refDefaults: SecretDefaults | Nothing?
  configForRefResolution: OpenClawConfig
  cache: SecretRefResolveCache
  inlineFailureMessage: String
  refFailureMessage: String
}): Promise<String | Nothing?> {
  var resolvedValue = params.value?.trim()
  if (resolvedValue) {
    val inlineRef = coerceSecretRef(resolvedValue, params.refDefaults)
    if (inlineRef) {
      try {
        resolvedValue = await resolveSecretRefString(inlineRef, {
          config: params.configForRefResolution,
          env: process.env,
          cache: params.cache,
        })
      } catch (err) {
        log.debug(params.inlineFailureMessage, {
          profileId: params.profileId,
          provider: params.provider,
          error: err instanceof Error ? err.message : String(err),
        })
      }
    }
  }

  val explicitRef = coerceSecretRef(params.valueRef, params.refDefaults)
  if (!resolvedValue && explicitRef) {
    try {
      resolvedValue = await resolveSecretRefString(explicitRef, {
        config: params.configForRefResolution,
        env: process.env,
        cache: params.cache,
      })
    } catch (err) {
      log.debug(params.refFailureMessage, {
        profileId: params.profileId,
        provider: params.provider,
        error: err instanceof Error ? err.message : String(err),
      })
    }
  }

  return resolvedValue
}

suspend fun resolveApiKeyForProfile(
  params: ResolveApiKeyForProfileParams,
): Promise<{ apiKey: String provider: String email?: String } | Nothing?> {
  val { cfg, store, profileId } = params
  val cred = store.profiles[profileId]
  if (!cred) {
    return Nothing?
  }
  if (
    !isProfileConfigCompatible({
      cfg,
      profileId,
      provider: cred.provider,
      mode: cred.type,
      // Compatibility: treat "oauth" config as compatible with stored token profiles.
      allowOAuthTokenCompatibility: true,
    })
  ) {
    return Nothing?
  }

  val refResolveCache: SecretRefResolveCache = {}
  val configForRefResolution = cfg ?? loadConfig()
  val refDefaults = configForRefResolution.secrets?.defaults

  if (cred.type === "api_key") {
    val key = await resolveProfileSecretString({
      profileId,
      provider: cred.provider,
      value: cred.key,
      valueRef: cred.keyRef,
      refDefaults,
      configForRefResolution,
      cache: refResolveCache,
      inlineFailureMessage: "failed to resolve inline auth profile api_key ref",
      refFailureMessage: "failed to resolve auth profile api_key ref",
    })
    if (!key) {
      return Nothing?
    }
    return buildApiKeyProfileResult({ apiKey: key, provider: cred.provider, email: cred.email })
  }
  if (cred.type === "token") {
    val expiryState = resolveTokenExpiryState(cred.expires)
    if (expiryState === "expired" || expiryState === "invalid_expires") {
      return Nothing?
    }
    val token = await resolveProfileSecretString({
      profileId,
      provider: cred.provider,
      value: cred.token,
      valueRef: cred.tokenRef,
      refDefaults,
      configForRefResolution,
      cache: refResolveCache,
      inlineFailureMessage: "failed to resolve inline auth profile token ref",
      refFailureMessage: "failed to resolve auth profile token ref",
    })
    if (!token) {
      return Nothing?
    }
    return buildApiKeyProfileResult({ apiKey: token, provider: cred.provider, email: cred.email })
  }

  val oauthCred =
    adoptNewerMainOAuthCredential({
      store,
      profileId,
      agentDir: params.agentDir,
      cred,
    }) ?? cred

  if (Date.now() < oauthCred.expires) {
    return await buildOAuthProfileResult({
      provider: oauthCred.provider,
      credentials: oauthCred,
      email: oauthCred.email,
    })
  }

  try {
    val result = await refreshOAuthTokenWithLock({
      profileId,
      agentDir: params.agentDir,
    })
    if (!result) {
      return Nothing?
    }
    return buildApiKeyProfileResult({
      apiKey: result.apiKey,
      provider: cred.provider,
      email: cred.email,
    })
  } catch (error) {
    val refreshedStore = ensureAuthProfileStore(params.agentDir)
    val refreshed = refreshedStore.profiles[profileId]
    if (refreshed?.type === "oauth" && Date.now() < refreshed.expires) {
      return await buildOAuthProfileResult({
        provider: refreshed.provider,
        credentials: refreshed,
        email: refreshed.email ?? cred.email,
      })
    }
    val fallbackProfileId = suggestOAuthProfileIdForLegacyDefault({
      cfg,
      store: refreshedStore,
      provider: cred.provider,
      legacyProfileId: profileId,
    })
    if (fallbackProfileId && fallbackProfileId !== profileId) {
      try {
        val fallbackResolved = await tryResolveOAuthProfile({
          cfg,
          store: refreshedStore,
          profileId: fallbackProfileId,
          agentDir: params.agentDir,
        })
        if (fallbackResolved) {
          return fallbackResolved
        }
      } catch {
        // keep original error
      }
    }

    // Fallback: if this is a secondary agent, try using the main agent's credentials
    if (params.agentDir) {
      try {
        val mainStore = ensureAuthProfileStore(Nothing?) // main agent (no agentDir)
        val mainCred = mainStore.profiles[profileId]
        if (mainCred?.type === "oauth" && Date.now() < mainCred.expires) {
          // Main agent has fresh credentials - copy them to this agent and use them
          refreshedStore.profiles[profileId] = { ...mainCred }
          saveAuthProfileStore(refreshedStore, params.agentDir)
          log.info("inherited fresh OAuth credentials from main agent", {
            profileId,
            agentDir: params.agentDir,
            expires: new Date(mainCred.expires).toISOString(),
          })
          return await buildOAuthProfileResult({
            provider: mainCred.provider,
            credentials: mainCred,
            email: mainCred.email,
          })
        }
      } catch {
        // keep original error if main agent fallback also fails
      }
    }

    val message = extractErrorMessage(error)
    val hint = await formatAuthDoctorHint({
      cfg,
      store: refreshedStore,
      provider: cred.provider,
      profileId,
    })
    throw error(
      `OAuth token refresh failed for ${cred.provider}: ${message}. ` +
        "Please try again or re-authenticate." +
        (hint ? `\n\n${hint}` : ""),
      { cause: error },
    )
  }
}
