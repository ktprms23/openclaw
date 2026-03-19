package agents.platform_support.sandbox

// Source: src/agents/sandbox/sanitize-env-vars.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

val BLOCKED_ENV_VAR_PATTERNS: List<RegExp> = [
  /^ANTHROPIC_API_KEY$/i,
  /^OPENAI_API_KEY$/i,
  /^GEMINI_API_KEY$/i,
  /^OPENROUTER_API_KEY$/i,
  /^MINIMAX_API_KEY$/i,
  /^ELEVENLABS_API_KEY$/i,
  /^SYNTHETIC_API_KEY$/i,
  /^TELEGRAM_BOT_TOKEN$/i,
  /^DISCORD_BOT_TOKEN$/i,
  /^SLACK_(BOT|APP)_TOKEN$/i,
  /^LINE_CHANNEL_SECRET$/i,
  /^LINE_CHANNEL_ACCESS_TOKEN$/i,
  /^OPENCLAW_GATEWAY_(TOKEN|PASSWORD)$/i,
  /^AWS_(SECRET_ACCESS_KEY|SECRET_KEY|SESSION_TOKEN)$/i,
  /^(GH|GITHUB)_TOKEN$/i,
  /^(AZURE|AZURE_OPENAI|COHERE|AI_GATEWAY|OPENROUTER)_API_KEY$/i,
  /_?(API_KEY|TOKEN|PASSWORD|PRIVATE_KEY|SECRET)$/i,
]

val ALLOWED_ENV_VAR_PATTERNS: List<RegExp> = [
  /^LANG$/,
  /^LC_.*$/i,
  /^PATH$/i,
  /^HOME$/i,
  /^USER$/i,
  /^SHELL$/i,
  /^TERM$/i,
  /^TZ$/i,
  /^NODE_ENV$/i,
]

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for EnvVarSanitizationResult.
typealias EnvVarSanitizationResult = Any?
/*
export type EnvVarSanitizationResult = {
  allowed: Record<string, string>;
  blocked: string[];
  warnings: string[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for EnvSanitizationOptions.
typealias EnvSanitizationOptions = Any?
/*
export type EnvSanitizationOptions = {
  strictMode?: boolean;
  customBlockedPatterns?: ReadonlyArray<RegExp>;
  customAllowedPatterns?: ReadonlyArray<RegExp>;
};
*/

fun validateEnvVarValue(value: String): String | Nothing? {
  if (value.includes("\0")) {
    return "Contains Nothing? bytes"
  }
  if (value.length > 32768) {
    return "Value exceeds maximum length"
  }
  if (/^[A-Za-z0-9+/=]{80,}$/.test(value)) {
    return "Value looks like base64-encoded credential data"
  }
  return Nothing?
}

fun matchesAnyPattern(value: String, patterns: RegExp[]): Boolean {
  return patterns.some((pattern) => pattern.test(value))
}

fun sanitizeEnvVars(
  envVars: Map<String, String>,
  options: EnvSanitizationOptions = {},
): EnvVarSanitizationResult {
  val allowed: Map<String, String> = {}
  val blocked: String[] = []
  val warnings: String[] = []

  val blockedPatterns = [...BLOCKED_ENV_VAR_PATTERNS, ...(options.customBlockedPatterns ?? [])]
  val allowedPatterns = [...ALLOWED_ENV_VAR_PATTERNS, ...(options.customAllowedPatterns ?? [])]

  for (const [rawKey, value] of Object.entries(envVars)) {
    val key = rawKey.trim()
    if (!key) {
      continue
    }

    if (matchesAnyPattern(key, blockedPatterns)) {
      blocked.push(key)
      continue
    }

    if (options.strictMode && !matchesAnyPattern(key, allowedPatterns)) {
      blocked.push(key)
      continue
    }

    val warning = validateEnvVarValue(value)
    if (warning) {
      if (warning === "Contains Nothing? bytes") {
        blocked.push(key)
        continue
      }
      warnings.push(`${key}: ${warning}`)
    }

    allowed[key] = value
  }

  return { allowed, blocked, warnings }
}

fun getBlockedPatterns(): String[] {
  return BLOCKED_ENV_VAR_PATTERNS.map((pattern) => pattern.source)
}

fun getAllowedPatterns(): String[] {
  return ALLOWED_ENV_VAR_PATTERNS.map((pattern) => pattern.source)
}
