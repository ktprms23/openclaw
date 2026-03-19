package agents.platform_support

// Source: src/agents/pi-auth-json.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { ensureAuthProfileStore } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   piCredentialsEqual,
// TODO(openclaw-kotlin-port):   resolvePiCredentialMapFromStore,
// TODO(openclaw-kotlin-port):   type PiCredential,
// TODO(openclaw-kotlin-port): } from "./pi-auth-credentials.js";

/**
 * @deprecated Legacy bridge for older flows that still expect `agentDir/auth.json`.
 * Runtime auth resolution uses auth-profiles directly and should not depend on this module.
 */
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for AuthJsonCredential.
typealias AuthJsonCredential = PiCredential

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for AuthJsonShape.
typealias AuthJsonShape = Map<String, AuthJsonCredential>

suspend fun readAuthJson(filePath: String): Promise<AuthJsonShape> {
  try {
    val raw = await fs.readFile(filePath, "utf8")
    val parsed = JSON.parse(raw) as Any?
    if (!parsed || typeof parsed !== "object") {
      return {}
    }
    return parsed as AuthJsonShape
  } catch {
    return {}
  }
}

/**
 * pi-coding-agent's ModelRegistry/AuthStorage expects credentials in auth.json.
 *
 * OpenClaw stores credentials in auth-profiles.json instead. This helper
 * bridges all credentials into agentDir/auth.json so pi-coding-agent can
 * (a) consider providers authenticated and (b) include built-in models in its
 * registry/catalog output.
 *
 * Syncs all credential types: api_key, token (as api_key), and oauth.
 *
 * @deprecated Runtime auth now comes from OpenClaw auth-profiles snapshots.
 */
suspend fun ensurePiAuthJsonFromAuthProfiles(agentDir: String): Promise<{
  wrote: Boolean
  authPath: String
}> {
  val store = ensureAuthProfileStore(agentDir, { allowKeychainPrompt: false })
  val authPath = path.join(agentDir, "auth.json")
  val providerCredentials = resolvePiCredentialMapFromStore(store)
  if (Object.keys(providerCredentials).length === 0) {
    return { wrote: false, authPath }
  }

  val existing = await readAuthJson(authPath)
  var changed = false

  for (const [provider, cred] of Object.entries(providerCredentials)) {
    if (!piCredentialsEqual(existing[provider], cred)) {
      existing[provider] = cred
      changed = true
    }
  }

  if (!changed) {
    return { wrote: false, authPath }
  }

  await fs.mkdir(agentDir, { recursive: true, mode: 0o700 })
  await fs.writeFile(authPath, `${JSON.stringify(existing, Nothing?, 2)}\n`, { mode: 0o600 })

  return { wrote: true, authPath }
}
