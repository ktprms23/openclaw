package agents.tools_and_subagents

// Converted from src/agents/timeout.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../config/config.js";

val DEFAULT_AGENT_TIMEOUT_SECONDS = 600
val MAX_SAFE_TIMEOUT_MS = 2_147_000_000

val normalizeNumber = (value: Any?): Double? ->
  value is Double && Number.isFinite(value) ? Math.floor(value) : null

fun resolveAgentTimeoutSeconds(cfg?: OpenClawConfig): Double {
  val raw = normalizeNumber(cfg?.agents?.defaults?.timeoutSeconds)
  val seconds = raw ?: DEFAULT_AGENT_TIMEOUT_SECONDS
  return Math.max(seconds, 1)
}

fun resolveAgentTimeoutMs(opts: {
  cfg?: OpenClawConfig
  overrideMs?: Double?
  overrideSeconds?: Double?
  minMs?: Double
}): Double {
  val minMs = Math.max(normalizeNumber(opts.minMs) ?: 1, 1)
  val clampTimeoutMs = (valueMs: Double) ->
    Math.min(Math.max(valueMs, minMs), MAX_SAFE_TIMEOUT_MS)
  val defaultMs = clampTimeoutMs(resolveAgentTimeoutSeconds(opts.cfg) * 1000)
  // Use the maximum timer-safe timeout to represent "no timeout" when explicitly set to 0.
  val NO_TIMEOUT_MS = MAX_SAFE_TIMEOUT_MS
  val overrideMs = normalizeNumber(opts.overrideMs)
  if (overrideMs != null) {
    if (overrideMs == 0) {
      return NO_TIMEOUT_MS
    }
    if (overrideMs < 0) {
      return defaultMs
    }
    return clampTimeoutMs(overrideMs)
  }
  val overrideSeconds = normalizeNumber(opts.overrideSeconds)
  if (overrideSeconds != null) {
    if (overrideSeconds == 0) {
      return NO_TIMEOUT_MS
    }
    if (overrideSeconds < 0) {
      return defaultMs
    }
    return clampTimeoutMs(overrideSeconds * 1000)
  }
  return defaultMs
}
