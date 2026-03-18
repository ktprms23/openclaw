@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/wait-for-idle-before-flush.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

val DEFAULT_WAIT_FOR_IDLE_TIMEOUT_MS: Any? = TODO("Port constant DEFAULT_WAIT_FOR_IDLE_TIMEOUT_MS")

fun flushPendingToolResultsAfterIdle(/* parameters preserved from TypeScript source */): Unit {
    TODO("Port runtime function flushPendingToolResultsAfterIdle from src/agents/pi-embedded-runner/wait-for-idle-before-flush.ts")
}

/*
Original TypeScript reference:
type IdleAwareAgent = {
  waitForIdle?: (() => Promise<void>) | undefined;
};

type ToolResultFlushManager = {
  flushPendingToolResults?: (() => void) | undefined;
  clearPendingToolResults?: (() => void) | undefined;
};

export const DEFAULT_WAIT_FOR_IDLE_TIMEOUT_MS = 30_000;

async function waitForAgentIdleBestEffort(
  agent: IdleAwareAgent | null | undefined,
  timeoutMs: number,
): Promise<boolean> {
  const waitForIdle = agent?.waitForIdle;
  if (typeof waitForIdle !== "function") {
    return false;
  }

  const idleResolved = Symbol("idle");
  const idleTimedOut = Symbol("timeout");
  let timeoutHandle: ReturnType<typeof setTimeout> | undefined;
  try {
    const outcome = await Promise.race([
      waitForIdle.call(agent).then(() => idleResolved),
      new Promise<symbol>((resolve) => {
        timeoutHandle = setTimeout(() => resolve(idleTimedOut), timeoutMs);
        timeoutHandle.unref?.();
      }),
    ]);
    return outcome === idleTimedOut;
  } catch {
    // Best-effort during cleanup.
    return false;
  } finally {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
  }
}

export async function flushPendingToolResultsAfterIdle(opts: {
  agent: IdleAwareAgent | null | undefined;
  sessionManager: ToolResultFlushManager | null | undefined;
  timeoutMs?: number;
  clearPendingOnTimeout?: boolean;
}): Promise<void> {
  const timedOut = await waitForAgentIdleBestEffort(
    opts.agent,
    opts.timeoutMs ?? DEFAULT_WAIT_FOR_IDLE_TIMEOUT_MS,
  );
  if (timedOut && opts.clearPendingOnTimeout && opts.sessionManager?.clearPendingToolResults) {
    opts.sessionManager.clearPendingToolResults();
    return;
  }
  opts.sessionManager?.flushPendingToolResults?.();
}

*/
