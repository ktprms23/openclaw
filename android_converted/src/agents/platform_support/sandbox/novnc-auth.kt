package agents.platform_support.sandbox

// Source: src/agents/sandbox/novnc-auth.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import crypto from "node:crypto";

val NOVNC_PASSWORD_ENV_KEY = "OPENCLAW_BROWSER_NOVNC_PASSWORD" // pragma: allowlist secret
val NOVNC_TOKEN_TTL_MS = 60 * 1000
val NOVNC_PASSWORD_LENGTH = 8
val NOVNC_PASSWORD_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for NoVncObserverTokenEntry.
typealias NoVncObserverTokenEntry = Any?
/*
type NoVncObserverTokenEntry = {
  noVncPort: number;
  password?: string;
  expiresAt: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for NoVncObserverTokenPayload.
typealias NoVncObserverTokenPayload = Any?
/*
export type NoVncObserverTokenPayload = {
  noVncPort: number;
  password?: string;
};
*/

val NO_VNC_OBSERVER_TOKENS = mutableMapOf<String, NoVncObserverTokenEntry>()

fun pruneExpiredNoVncObserverTokens(now: Double) {
  for (const [token, entry] of NO_VNC_OBSERVER_TOKENS) {
    if (entry.expiresAt <= now) {
      NO_VNC_OBSERVER_TOKENS.delete(token)
    }
  }
}

fun isNoVncEnabled(params: { enableNoVnc: Boolean headless: Boolean }) {
  return params.enableNoVnc && !params.headless
}

fun generateNoVncPassword() {
  // VNC auth uses an 8-char password max.
  var out = ""
  for (let i = 0 i < NOVNC_PASSWORD_LENGTH i += 1) {
    out += NOVNC_PASSWORD_ALPHABET[crypto.randomInt(0, NOVNC_PASSWORD_ALPHABET.length)]
  }
  return out
}

fun buildNoVncDirectUrl(port: Double) {
  return `http://127.0.0.1:${port}/vnc.html`
}

fun buildNoVncObserverTargetUrl(params: { port: Double password?: String }) {
  val query = new URLSearchParams({
    autoconnect: "1",
    resize: "remote",
  })
  if (params.password?.trim()) {
    query.set("password", params.password)
  }
  return `${buildNoVncDirectUrl(params.port)}#${query.toString()}`
}

fun issueNoVncObserverToken(params: {
  noVncPort: Double
  password?: String
  ttlMs?: Double
  nowMs?: Double
}): String {
  val now = params.nowMs ?? Date.now()
  pruneExpiredNoVncObserverTokens(now)
  val token = crypto.randomBytes(24).toString("hex")
  NO_VNC_OBSERVER_TOKENS.set(token, {
    noVncPort: params.noVncPort,
    password: params.password?.trim() || Nothing?,
    expiresAt: now + Math.max(1, params.ttlMs ?? NOVNC_TOKEN_TTL_MS),
  })
  return token
}

fun consumeNoVncObserverToken(
  token: String,
  nowMs?: Double,
): NoVncObserverTokenPayload | Nothing? {
  val now = nowMs ?? Date.now()
  pruneExpiredNoVncObserverTokens(now)
  val normalized = token.trim()
  if (!normalized) {
    return Nothing?
  }
  val entry = NO_VNC_OBSERVER_TOKENS.get(normalized)
  if (!entry) {
    return Nothing?
  }
  NO_VNC_OBSERVER_TOKENS.delete(normalized)
  if (entry.expiresAt <= now) {
    return Nothing?
  }
  return { noVncPort: entry.noVncPort, password: entry.password }
}

fun buildNoVncObserverTokenUrl(baseUrl: String, token: String) {
  val query = new URLSearchParams({ token })
  return `${baseUrl}/sandbox/novnc?${query.toString()}`
}

fun resetNoVncObserverTokensForTests() {
  NO_VNC_OBSERVER_TOKENS.clear()
}
