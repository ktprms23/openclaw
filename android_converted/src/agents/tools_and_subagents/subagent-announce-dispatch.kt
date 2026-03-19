@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-announce-dispatch.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

typealias SubagentDeliveryPath = Any /* TODO: translate TypeScript alias */

typealias SubagentAnnounceQueueOutcome = Any /* TODO: translate TypeScript alias */

typealias SubagentAnnounceDeliveryResult = Any /* TODO: translate TypeScript alias */

typealias SubagentAnnounceDispatchPhase = Any /* TODO: translate TypeScript alias */

typealias SubagentAnnounceDispatchPhaseResult = Any /* TODO: translate TypeScript alias */

fun mapQueueOutcomeToDeliveryResult(
  outcome: SubagentAnnounceQueueOutcome,
): SubagentAnnounceDeliveryResult {
  if (outcome == "steered") {
    return {
      delivered: true,
      path: "steered",
    };
  }
  if (outcome == "queued") {
    return {
      delivered: true,
      path: "queued",
    };
  }
  return {
    delivered: false,
    path: "none",
  };
}

suspend fun runSubagentAnnounceDispatch(params: {
  expectsCompletionMessage: boolean;
  signal?: AbortSignal;
  queue: () => Promise<SubagentAnnounceQueueOutcome>;
  direct: () => Promise<SubagentAnnounceDeliveryResult>;
}): Promise<SubagentAnnounceDeliveryResult> {
  val phases: SubagentAnnounceDispatchPhaseResult[] = [];
  val appendPhase = (
    phase: SubagentAnnounceDispatchPhase,
    result: SubagentAnnounceDeliveryResult,
  ) => {
    phases.push({
      phase,
      delivered: result.delivered,
      path: result.path,
      error: result.error,
    });
  };
  val withPhases = (result: SubagentAnnounceDeliveryResult): SubagentAnnounceDeliveryResult => ({
    ...result,
    phases,
  });

  if (params.signal?.aborted) {
    return withPhases({
      delivered: false,
      path: "none",
    });
  }

  if (!params.expectsCompletionMessage) {
    val primaryQueue = mapQueueOutcomeToDeliveryResult(await params.queue());
    appendPhase("queue-primary", primaryQueue);
    if (primaryQueue.delivered) {
      return withPhases(primaryQueue);
    }

    val primaryDirect = await params.direct();
    appendPhase("direct-primary", primaryDirect);
    return withPhases(primaryDirect);
  }

  val primaryDirect = await params.direct();
  appendPhase("direct-primary", primaryDirect);
  if (primaryDirect.delivered) {
    return withPhases(primaryDirect);
  }

  if (params.signal?.aborted) {
    return withPhases({
      delivered: false,
      path: "none",
    });
  }

  val fallbackQueue = mapQueueOutcomeToDeliveryResult(await params.queue());
  appendPhase("queue-fallback", fallbackQueue);
  if (fallbackQueue.delivered) {
    return withPhases(fallbackQueue);
  }

  return withPhases(primaryDirect);
}
