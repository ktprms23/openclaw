@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-helpers/thinking.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun pickFallbackThinkingLevel(/* parameters preserved from TypeScript source */): Any /* ThinkLevel | undefined */ {
    TODO("Port runtime function pickFallbackThinkingLevel from src/agents/pi-embedded-helpers/thinking.ts")
}

/*
Original imports retained for mapping context:
import { normalizeThinkLevel, type ThinkLevel } from "../../auto-reply/thinking.js";
*/

/*
Original TypeScript reference:
import { normalizeThinkLevel, type ThinkLevel } from "../../auto-reply/thinking.js";

function extractSupportedValues(raw: string): string[] {
  const match =
    raw.match(/supported values are:\s*([^\n.]+)/i) ?? raw.match(/supported values:\s*([^\n.]+)/i);
  if (!match?.[1]) {
    return [];
  }
  const fragment = match[1];
  const quoted = Array.from(fragment.matchAll(/['"]([^'"]+)['"]/g)).map((entry) =>
    entry[1]?.trim(),
  );
  if (quoted.length > 0) {
    return quoted.filter((entry): entry is string => Boolean(entry));
  }
  return fragment
    .split(/,|\band\b/gi)
    .map((entry) => entry.replace(/^[^a-zA-Z]+|[^a-zA-Z]+$/g, "").trim())
    .filter(Boolean);
}

export function pickFallbackThinkingLevel(params: {
  message?: string;
  attempted: Set<ThinkLevel>;
}): ThinkLevel | undefined {
  const raw = params.message?.trim();
  if (!raw) {
    return undefined;
  }
  const supported = extractSupportedValues(raw);
  if (supported.length === 0) {
    // When the error clearly indicates the thinking level is unsupported but doesn't
    // list supported values (e.g. OpenAI's "think value \"low\" is not supported for
    // this model"), fall back to "off" to allow the request to succeed.
    // This commonly happens during model fallback when switching from Anthropic
    // (which supports thinking levels) to providers that don't.
    if (/not supported/i.test(raw) && !params.attempted.has("off")) {
      return "off";
    }
    return undefined;
  }
  for (const entry of supported) {
    const normalized = normalizeThinkLevel(entry);
    if (!normalized) {
      continue;
    }
    if (params.attempted.has(normalized)) {
      continue;
    }
    return normalized;
  }
  return undefined;
}

*/
