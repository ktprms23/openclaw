@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/run/compaction-retry-aggregate-timeout.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun waitForCompactionRetryWithAggregateTimeout(/* parameters preserved from TypeScript source */): Promise< {
    TODO("Port runtime function waitForCompactionRetryWithAggregateTimeout from src/agents/pi-embedded-runner/run/compaction-retry-aggregate-timeout.ts")
}

/*
Original TypeScript reference:
/**
 * Wait for compaction retry completion with an aggregate timeout to avoid
 * holding a session lane indefinitely when retry resolution is lost.
 * /
export async function waitForCompactionRetryWithAggregateTimeout(params: {
  waitForCompactionRetry: () => Promise<void>;
  abortable: <T>(promise: Promise<T>) => Promise<T>;
  aggregateTimeoutMs: number;
  onTimeout?: () => void;
  isCompactionStillInFlight?: () => boolean;
}): Promise<{ timedOut: boolean }> {
  const timeoutMsRaw = params.aggregateTimeoutMs;
  const timeoutMs = Number.isFinite(timeoutMsRaw) ? Math.max(1, Math.floor(timeoutMsRaw)) : 1;

  let timedOut = false;
  const waitPromise = params.waitForCompactionRetry().then(() => "done" as const);

  while (true) {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const result = await params.abortable(
        Promise.race([
          waitPromise,
          new Promise<"timeout">((resolve) => {
            timer = setTimeout(() => resolve("timeout"), timeoutMs);
          }),
        ]),
      );

      if (result === "done") {
        break;
      }

      // Keep extending the timeout window while compaction is actively running.
      // We only trigger the fallback timeout once compaction appears idle.
      if (params.isCompactionStillInFlight?.()) {
        continue;
      }

      timedOut = true;
      params.onTimeout?.();
      break;
    } finally {
      if (timer !== undefined) {
        clearTimeout(timer);
      }
    }
  }

  return { timedOut };
}

*/
