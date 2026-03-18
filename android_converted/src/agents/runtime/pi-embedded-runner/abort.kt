@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/abort.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun isRunnerAbortError(/* parameters preserved from TypeScript source */): Boolean {
    TODO("Port runtime function isRunnerAbortError from src/agents/pi-embedded-runner/abort.ts")
}

/*
Original TypeScript reference:
/**
 * Runner abort check. Catches any abort-related message for embedded runners.
 * More permissive than the core isAbortError since runners need to catch
 * various abort signals from different sources.
 * /
export function isRunnerAbortError(err: unknown): boolean {
  if (!err || typeof err !== "object") {
    return false;
  }
  const name = "name" in err ? String(err.name) : "";
  if (name === "AbortError") {
    return true;
  }
  const message =
    "message" in err && typeof err.message === "string" ? err.message.toLowerCase() : "";
  return message.includes("aborted");
}

*/
