@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/zai-stream-wrappers.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

val createZaiToolStreamWrapper: Any? = TODO("Port constant createZaiToolStreamWrapper")

fun createToolStreamWrapper(/* parameters preserved from TypeScript source */): StreamFn {
    TODO("Port runtime function createToolStreamWrapper from src/agents/pi-embedded-runner/zai-stream-wrappers.ts")
}

/*
Original imports retained for mapping context:
import type { StreamFn } from "@mariozechner/pi-agent-core";
import { streamSimple } from "@mariozechner/pi-ai";
*/

/*
Original TypeScript reference:
import type { StreamFn } from "@mariozechner/pi-agent-core";
import { streamSimple } from "@mariozechner/pi-ai";

/**
 * Inject `tool_stream=true` so tool-call deltas stream in real time.
 * Providers can disable this by setting `params.tool_stream=false`.
 * /
export function createToolStreamWrapper(
  baseStreamFn: StreamFn | undefined,
  enabled: boolean,
): StreamFn {
  const underlying = baseStreamFn ?? streamSimple;
  return (model, context, options) => {
    if (!enabled) {
      return underlying(model, context, options);
    }

    const originalOnPayload = options?.onPayload;
    return underlying(model, context, {
      ...options,
      onPayload: (payload) => {
        if (payload && typeof payload === "object") {
          (payload as Record<string, unknown>).tool_stream = true;
        }
        return originalOnPayload?.(payload, model);
      },
    });
  };
}

export const createZaiToolStreamWrapper = createToolStreamWrapper;

*/
