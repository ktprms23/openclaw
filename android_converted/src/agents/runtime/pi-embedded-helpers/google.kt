@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-helpers/google.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun isGoogleModelApi(/* parameters preserved from TypeScript source */): Boolean {
    TODO("Port runtime function isGoogleModelApi from src/agents/pi-embedded-helpers/google.ts")
}

/*
Original imports retained for mapping context:
import { sanitizeGoogleTurnOrdering } from "./bootstrap.js";
*/

/*
Original TypeScript reference:
import { sanitizeGoogleTurnOrdering } from "./bootstrap.js";

export function isGoogleModelApi(api?: string | null): boolean {
  return api === "google-gemini-cli" || api === "google-generative-ai";
}

export { sanitizeGoogleTurnOrdering };

*/
