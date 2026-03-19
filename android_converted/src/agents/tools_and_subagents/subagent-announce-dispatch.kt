package agents.tools_and_subagents

// Converted from src/agents/subagent-announce-dispatch.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
typealias SubagentDeliveryPath = "queued" | "steered" | "direct" | "none"

typealias SubagentAnnounceQueueOutcome = "steered" | "queued" | "none"

data class SubagentAnnounceDeliveryResult(
    val delivered: Boolean,
    val path: SubagentDeliveryPath,
    val error: String?,
    val phases: List<SubagentAnnounceDispatchPhaseResult>?,
)

typealias SubagentAnnounceDispatchPhase = "queue-primary" | "direct-primary" | "queue-fallback"

data class SubagentAnnounceDispatchPhaseResult(
    val phase: SubagentAnnounceDispatchPhase,
    val delivered: Boolean,
    val path: SubagentDeliveryPath,
    val error: String?,
)

fun mapQueueOutcomeToDeliveryResult(
  outcome: SubagentAnnounceQueueOutcome,
): SubagentAnnounceDeliveryResult {
  if (outcome == "steered") {
    return {
      delivered: true,
      path: String /* "steered" */,
    }
  }
  if (outcome == "queued") {
    return {
      delivered: true,
      path: String /* "queued" */,
    }
  }
  return {
    delivered: false,
    path: String /* "none" */,
  }
}

suspend fun runSubagentAnnounceDispatch(params: {
  expectsCompletionMessage: Boolean
  signal?: AbortSignal /* TODO */
  queue: () -> Deferred<SubagentAnnounceQueueOutcome>
  direct: () -> Deferred<SubagentAnnounceDeliveryResult>
}): Deferred<SubagentAnnounceDeliveryResult> {
  val phases: List<SubagentAnnounceDispatchPhaseResult> = []
  val appendPhase = (
    phase: SubagentAnnounceDispatchPhase,
    result: SubagentAnnounceDeliveryResult,
  ) {
    phases.push({
      phase,
      delivered: result.delivered,
      path: result.path,
      error: result.error,
    })
  }
  val withPhases = (result: SubagentAnnounceDeliveryResult): SubagentAnnounceDeliveryResult -> ({
    ...result,
    phases,
  })

  if (params.signal?.aborted) {
    return withPhases({
      delivered: false,
      path: String /* "none" */,
    })
  }

  if (!params.expectsCompletionMessage) {
    val primaryQueue = mapQueueOutcomeToDeliveryResult(await params.queue())
    appendPhase("queue-primary", primaryQueue)
    if (primaryQueue.delivered) {
      return withPhases(primaryQueue)
    }

    val primaryDirect = await params.direct()
    appendPhase("direct-primary", primaryDirect)
    return withPhases(primaryDirect)
  }

  val primaryDirect = await params.direct()
  appendPhase("direct-primary", primaryDirect)
  if (primaryDirect.delivered) {
    return withPhases(primaryDirect)
  }

  if (params.signal?.aborted) {
    return withPhases({
      delivered: false,
      path: String /* "none" */,
    })
  }

  val fallbackQueue = mapQueueOutcomeToDeliveryResult(await params.queue())
  appendPhase("queue-fallback", fallbackQueue)
  if (fallbackQueue.delivered) {
    return withPhases(fallbackQueue)
  }

  return withPhases(primaryDirect)
}
