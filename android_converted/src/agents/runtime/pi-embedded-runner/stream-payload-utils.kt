@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/stream-payload-utils.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun streamWithPayloadPatch(/* parameters preserved from TypeScript source */): Unit {
    TODO("Port runtime function streamWithPayloadPatch from src/agents/pi-embedded-runner/stream-payload-utils.ts")
}

/*
Original imports retained for mapping context:
import type { StreamFn } from "@mariozechner/pi-agent-core";
*/

/*
Original TypeScript reference:
import type { StreamFn } from "@mariozechner/pi-agent-core";

export function streamWithPayloadPatch(
  underlying: StreamFn,
  model: Parameters<StreamFn>[0],
  context: Parameters<StreamFn>[1],
  options: Parameters<StreamFn>[2],
  patchPayload: (payload: Record<string, unknown>) => void,
) {
  const originalOnPayload = options?.onPayload;
  return underlying(model, context, {
    ...options,
    onPayload: (payload) => {
      if (payload && typeof payload === "object") {
        patchPayload(payload as Record<string, unknown>);
      }
      return originalOnPayload?.(payload, model);
    },
  });
}

*/
