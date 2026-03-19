package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-guarded-fetch.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  fetchWithSsrFGuard,
  type GuardedFetchOptions,
  type GuardedFetchResult,
  withStrictGuardedFetchMode,
  withTrustedEnvProxyGuardedFetchMode,
} from "../../infra/net/fetch-guard.js"
// TODO: TypeScript import retained for manual wiring: import type { SsrFPolicy } from "../../infra/net/ssrf.js";

val WEB_TOOLS_TRUSTED_NETWORK_SSRF_POLICY: SsrFPolicy = {
  dangerouslyAllowPrivateNetwork: true,
  allowRfc2544BenchmarkRange: true,
}

type WebToolGuardedFetchOptions = Omit<
  GuardedFetchOptions,
  "mode" | "proxy" | "dangerouslyAllowEnvProxyWithoutPinnedDns"
> & {
  timeoutSeconds?: Double
  useEnvProxy?: Boolean
}
typealias WebToolEndpointFetchOptions = Omit<WebToolGuardedFetchOptions, "policy" | "useEnvProxy">

fun resolveTimeoutMs(params: {
  timeoutMs?: Double
  timeoutSeconds?: Double
}): Double? {
  if (params.timeoutMs is Double && Number.isFinite(params.timeoutMs)) {
    return params.timeoutMs
  }
  if (params.timeoutSeconds is Double && Number.isFinite(params.timeoutSeconds)) {
    return params.timeoutSeconds * 1000
  }
  return null
}

suspend fun fetchWithWebToolsNetworkGuard(
  params: WebToolGuardedFetchOptions,
): Deferred<GuardedFetchResult> {
  val { timeoutSeconds, useEnvProxy, ...rest } = params
  val resolved = {
    ...rest,
    timeoutMs: resolveTimeoutMs({ timeoutMs: rest.timeoutMs, timeoutSeconds }),
  }
  return fetchWithSsrFGuard(
    useEnvProxy
      ? withTrustedEnvProxyGuardedFetchMode(resolved)
      : withStrictGuardedFetchMode(resolved),
  )
}

suspend fun <T> withWebToolsNetworkGuard(
  params: WebToolGuardedFetchOptions,
  run: (result: { response: Response finalUrl: String }) -> Deferred<T>,
): Deferred<T> {
  val { response, finalUrl, release } = await fetchWithWebToolsNetworkGuard(params)
  try {
    return await run({ response, finalUrl })
  } finally {
    await release()
  }
}

suspend fun <T> withTrustedWebToolsEndpoint(
  params: WebToolEndpointFetchOptions,
  run: (result: { response: Response finalUrl: String }) -> Deferred<T>,
): Deferred<T> {
  return await withWebToolsNetworkGuard(
    {
      ...params,
      policy: WEB_TOOLS_TRUSTED_NETWORK_SSRF_POLICY,
      useEnvProxy: true,
    },
    run,
  )
}

suspend fun <T> withStrictWebToolsEndpoint(
  params: WebToolEndpointFetchOptions,
  run: (result: { response: Response finalUrl: String }) -> Deferred<T>,
): Deferred<T> {
  return await withWebToolsNetworkGuard(params, run)
}
