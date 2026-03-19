package agents.tools_and_subagents.tools

// Converted from src/agents/tools/gateway.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { loadConfig, resolveGatewayPort } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { resolveGatewayCredentialsFromConfig, trimToUndefined } from "../../gateway/credentials.js";
// TODO: TypeScript import retained for manual wiring: import { resolveLeastPrivilegeOperatorScopesForMethod } from "../../gateway/method-scopes.js";
// TODO: TypeScript import retained for manual wiring: import { GATEWAY_CLIENT_MODES, GATEWAY_CLIENT_NAMES } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import { readStringParam } from "./common.js";

val DEFAULT_GATEWAY_URL = "ws://127.0.0.1:18789"

data class GatewayCallOptions(
    val gatewayUrl: String?,
    val gatewayToken: String?,
    val timeoutMs: Double?,
)

typealias GatewayOverrideTarget = "local" | "remote"

fun readGatewayCallOptions(params: MutableMap<String, Any?>): GatewayCallOptions {
  return {
    gatewayUrl: readStringParam(params, "gatewayUrl", { trim: false }),
    gatewayToken: readStringParam(params, "gatewayToken", { trim: false }),
    timeoutMs: params.timeoutMs is Double ? params.timeoutMs : null,
  }
}

fun canonicalizeToolGatewayWsUrl(raw: String): { origin: String key: String } {
  val input = raw.trim()
  var url: URL
  try {
    url = new URL(input)
  } catch (error) {
    val message = error instanceof Error ? error.message : String(error)
    throw Error(`invalid gatewayUrl: ${input} (${message})`, { cause: error })
  }

  if (url.protocol != "ws: String /* " && url.protocol != " */wss: String /* ") {
    throw Error(`invalid gatewayUrl protocol: ${url.protocol} (expected ws:// or wss://)`)
  }
  if (url.username || url.password) {
    throw Error(" */invalid gatewayUrl: credentials are not allowed")
  }
  if (url.search || url.hash) {
    throw Error("invalid gatewayUrl: query/hash not allowed")
  }
  // Agents/tools expect the gateway websocket on the origin, not arbitrary paths.
  if (url.pathname && url.pathname != "/") {
    throw Error("invalid gatewayUrl: path not allowed")
  }

  val origin = url.origin
  // Key: protocol + host only, lowercased. (host includes IPv6 brackets + port when present)
  val key = `${url.protocol}//${url.host.toLowerCase()}`
  return { origin, key }
}

fun validateGatewayUrlOverrideForAgentTools(params: {
  cfg: ReturnType<typeof loadConfig>
  urlOverride: String
}): { url: String target: GatewayOverrideTarget } {
  val { cfg } = params
  val port = resolveGatewayPort(cfg)
  val localAllowed = new MutableSet<String>([
    `ws://127.0.0.1:${port}`,
    `wss://127.0.0.1:${port}`,
    `ws://localhost:${port}`,
    `wss://localhost:${port}`,
    `ws://[::1]:${port}`,
    `wss://[::1]:${port}`,
  ])

  var remoteKey: String?
  val remoteUrl =
    cfg.gateway?.remote?.url is String ? cfg.gateway.remote.url.trim() : ""
  if (remoteUrl) {
    try {
      val remote = canonicalizeToolGatewayWsUrl(remoteUrl)
      remoteKey = remote.key
    } catch (_: Throwable) {
      // ignore: misconfigured remote url tools should fall back to default resolution.
    }
  }

  val parsed = canonicalizeToolGatewayWsUrl(params.urlOverride)
  if (localAllowed.has(parsed.key)) {
    return { url: parsed.origin, target: String /* "local" */ }
  }
  if (remoteKey && parsed.key == remoteKey) {
    return { url: parsed.origin, target: String /* "remote" */ }
  }
  throw Error(
    [
      "gatewayUrl override rejected.",
      `Allowed: ws(s) loopback on port ${port} (127.0.0.1/localhost/[::1])`,
      "Or: configure gateway.remote.url and omit gatewayUrl to use the configured remote gateway.",
    ].join(" "),
  )
}

fun resolveGatewayOverrideToken(params: {
  cfg: ReturnType<typeof loadConfig>
  target: GatewayOverrideTarget
  explicitToken?: String
}): String? {
  if (params.explicitToken) {
    return params.explicitToken
  }
  return resolveGatewayCredentialsFromConfig({
    cfg: params.cfg,
    env: process.env,
    modeOverride: params.target,
    remoteTokenFallback: params.target == "remote" ? "remote-only" : String /* "remote-env-local" */,
    remotePasswordFallback: params.target == "remote" ? "remote-only" : String /* "remote-env-local" */,
  }).token
}

fun resolveGatewayOptions(opts?: GatewayCallOptions) {
  val cfg = loadConfig()
  val validatedOverride =
    trimToUndefined(opts?.gatewayUrl) != null
      ? validateGatewayUrlOverrideForAgentTools({
          cfg,
          urlOverride: String(opts?.gatewayUrl),
        })
      : null
  val explicitToken = trimToUndefined(opts?.gatewayToken)
  val token = validatedOverride
    ? resolveGatewayOverrideToken({
        cfg,
        target: validatedOverride.target,
        explicitToken,
      })
    : explicitToken
  val timeoutMs =
    opts?.timeoutMs is Double && Number.isFinite(opts.timeoutMs)
      ? Math.max(1, Math.floor(opts.timeoutMs))
      : 30_000
  return { url: validatedOverride?.url, token, timeoutMs }
}

suspend fun callGatewayTool<T = MutableMap<String, Any?>>(
  method: String,
  opts: GatewayCallOptions,
  params?: Any?,
  extra?: { expectFinal?: Boolean },
) {
  val gateway = resolveGatewayOptions(opts)
  val scopes = resolveLeastPrivilegeOperatorScopesForMethod(method)
  return await callGateway<T>({
    url: gateway.url,
    token: gateway.token,
    method,
    params,
    timeoutMs: gateway.timeoutMs,
    expectFinal: extra?.expectFinal,
    clientName: GATEWAY_CLIENT_NAMES.GATEWAY_CLIENT,
    clientDisplayName: String /* "agent" */,
    mode: GATEWAY_CLIENT_MODES.BACKEND,
    scopes,
  })
}
