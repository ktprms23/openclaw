package agents.tools_and_subagents

// Converted from src/agents/tool-loop-detection.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { createHash } from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import type { ToolLoopDetectionConfig } from "../config/types.tools.js";
// TODO: TypeScript import retained for manual wiring: import type { SessionState } from "../logging/diagnostic-session-state.js";
// TODO: TypeScript import retained for manual wiring: import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO: TypeScript import retained for manual wiring: import { isPlainObject } from "../utils.js";

val log = createSubsystemLogger("agents/loop-detection")

type LoopDetectorKind =
  | "generic_repeat"
  | "known_poll_no_progress"
  | "global_circuit_breaker"
  | "ping_pong"

type LoopDetectionResult =
  | { stuck: false }
  | {
      stuck: true
      level: String /* "warning" */ | "critical"
      detector: LoopDetectorKind
      count: Double
      message: String
      pairedToolName?: String
      warningKey?: String
    }

val TOOL_CALL_HISTORY_SIZE = 30
val WARNING_THRESHOLD = 10
val CRITICAL_THRESHOLD = 20
val GLOBAL_CIRCUIT_BREAKER_THRESHOLD = 30
val DEFAULT_LOOP_DETECTION_CONFIG = {
  enabled: false,
  historySize: TOOL_CALL_HISTORY_SIZE,
  warningThreshold: WARNING_THRESHOLD,
  criticalThreshold: CRITICAL_THRESHOLD,
  globalCircuitBreakerThreshold: GLOBAL_CIRCUIT_BREAKER_THRESHOLD,
  detectors: {
    genericRepeat: true,
    knownPollNoProgress: true,
    pingPong: true,
  },
}

data class ResolvedLoopDetectionConfig(
    val enabled: Boolean,
    val historySize: Double,
    val warningThreshold: Double,
    val criticalThreshold: Double,
    val globalCircuitBreakerThreshold: Double,
    val detectors: {,
    val genericRepeat: Boolean,
    val knownPollNoProgress: Boolean,
    val pingPong: Boolean,
)

fun asPositiveInt(value: Double?, fallback: Double): Double {
  if (value !is Double || !Number.isInteger(value) || value <= 0) {
    return fallback
  }
  return value
}

fun resolveLoopDetectionConfig(config?: ToolLoopDetectionConfig): ResolvedLoopDetectionConfig {
  var warningThreshold = asPositiveInt(
    config?.warningThreshold,
    DEFAULT_LOOP_DETECTION_CONFIG.warningThreshold,
  )
  var criticalThreshold = asPositiveInt(
    config?.criticalThreshold,
    DEFAULT_LOOP_DETECTION_CONFIG.criticalThreshold,
  )
  var globalCircuitBreakerThreshold = asPositiveInt(
    config?.globalCircuitBreakerThreshold,
    DEFAULT_LOOP_DETECTION_CONFIG.globalCircuitBreakerThreshold,
  )

  if (criticalThreshold <= warningThreshold) {
    criticalThreshold = warningThreshold + 1
  }
  if (globalCircuitBreakerThreshold <= criticalThreshold) {
    globalCircuitBreakerThreshold = criticalThreshold + 1
  }

  return {
    enabled: config?.enabled ?: DEFAULT_LOOP_DETECTION_CONFIG.enabled,
    historySize: asPositiveInt(config?.historySize, DEFAULT_LOOP_DETECTION_CONFIG.historySize),
    warningThreshold,
    criticalThreshold,
    globalCircuitBreakerThreshold,
    detectors: {
      genericRepeat:
        config?.detectors?.genericRepeat ?: DEFAULT_LOOP_DETECTION_CONFIG.detectors.genericRepeat,
      knownPollNoProgress:
        config?.detectors?.knownPollNoProgress ??
        DEFAULT_LOOP_DETECTION_CONFIG.detectors.knownPollNoProgress,
      pingPong: config?.detectors?.pingPong ?: DEFAULT_LOOP_DETECTION_CONFIG.detectors.pingPong,
    },
  }
}

/**
 * Hash a tool call for pattern matching.
 * Uses tool name + deterministic JSON serialization digest of params.
 */
fun hashToolCall(toolName: String, params: Any?): String {
  return `${toolName}:${digestStable(params)}`
}

fun stableStringify(value: Any?): String {
  if (value == null || typeof value != "object") {
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`
  }
  val obj = value as /* TODO */ MutableMap<String, Any?>
  val keys = Object.keys(obj).toSorted()
  return `{${keys.map((k) -> `${JSON.stringify(k)}:${stableStringify(obj[k])}`).join(",")}}`
}

fun digestStable(value: Any?): String {
  val serialized = stableStringifyFallback(value)
  return createHash("sha256").update(serialized).digest("hex")
}

fun stableStringifyFallback(value: Any?): String {
  try {
    return stableStringify(value)
  } catch (_: Throwable) {
    if (value == null || value == null) {
      return `${value}`
    }
    if (value is String) {
      return value
    }
    if (value is Double || value is Boolean || typeof value == "bigint") {
      return `${value}`
    }
    if (value instanceof Error) {
      return `${value.name}:${value.message}`
    }
    return Object.prototype.toString.call(value)
  }
}

fun isKnownPollToolCall(toolName: String, params: Any?): Boolean {
  if (toolName == "command_status") {
    return true
  }
  if (toolName != "process" || !isPlainObject(params)) {
    return false
  }
  val action = params.action
  return action == "poll" || action == "log"
}

fun extractTextContent(result: Any?): String {
  if (!isPlainObject(result) || !Array.isArray(result.content)) {
    return ""
  }
  return result.content
    .filter(
      (entry): entry is { type: String text: String } ->
        isPlainObject(entry) && entry.type is String && entry.text is String,
    )
    .map((entry) -> entry.text)
    .join("\n")
    .trim()
}

fun formatErrorForHash(error: Any?): String {
  if (error instanceof Error) {
    return error.message || error.name
  }
  if (error is String) {
    return error
  }
  if (error is Double || error is Boolean || typeof error == "bigint") {
    return `${error}`
  }
  return stableStringify(error)
}

fun hashToolOutcome(
  toolName: String,
  params: Any?,
  result: Any?,
  error: Any?,
): String? {
  if (error != null) {
    return `error:${digestStable(formatErrorForHash(error))}`
  }
  if (!isPlainObject(result)) {
    return result == null ? null : digestStable(result)
  }

  val details = isPlainObject(result.details) ? result.details : {}
  val text = extractTextContent(result)
  if (isKnownPollToolCall(toolName, params) && toolName == "process" && isPlainObject(params)) {
    val action = params.action
    if (action == "poll") {
      return digestStable({
        action,
        status: details.status,
        exitCode: details.exitCode ?: null,
        exitSignal: details.exitSignal ?: null,
        aggregated: details.aggregated ?: null,
        text,
      })
    }
    if (action == "log") {
      return digestStable({
        action,
        status: details.status,
        totalLines: details.totalLines ?: null,
        totalChars: details.totalChars ?: null,
        truncated: details.truncated ?: null,
        exitCode: details.exitCode ?: null,
        exitSignal: details.exitSignal ?: null,
        text,
      })
    }
  }

  return digestStable({
    details,
    text,
  })
}

fun getNoProgressStreak(
  history: List<{ toolName: String argsHash: String resultHash?: String }>,
  toolName: String,
  argsHash: String,
): { count: Double latestResultHash?: String } {
  var streak = 0
  var latestResultHash: String?

  for (var i = history.length - 1 i >= 0 i -= 1) {
    val record = history[i]
    if (!record || record.toolName != toolName || record.argsHash != argsHash) {
      continue
    }
    if (record.resultHash !is String || !record.resultHash) {
      continue
    }
    if (!latestResultHash) {
      latestResultHash = record.resultHash
      streak = 1
      continue
    }
    if (record.resultHash != latestResultHash) {
      break
    }
    streak += 1
  }

  return { count: streak, latestResultHash }
}

fun getPingPongStreak(
  history: List<{ toolName: String argsHash: String resultHash?: String }>,
  currentSignature: String,
): {
  count: Double
  pairedToolName?: String
  pairedSignature?: String
  noProgressEvidence: Boolean
} {
  val last = history.at(-1)
  if (!last) {
    return { count: 0, noProgressEvidence: false }
  }

  var otherSignature: String?
  var otherToolName: String?
  for (var i = history.length - 2 i >= 0 i -= 1) {
    val call = history[i]
    if (!call) {
      continue
    }
    if (call.argsHash != last.argsHash) {
      otherSignature = call.argsHash
      otherToolName = call.toolName
      break
    }
  }

  if (!otherSignature || !otherToolName) {
    return { count: 0, noProgressEvidence: false }
  }

  var alternatingTailCount = 0
  for (var i = history.length - 1 i >= 0 i -= 1) {
    val call = history[i]
    if (!call) {
      continue
    }
    val expected = alternatingTailCount % 2 == 0 ? last.argsHash : otherSignature
    if (call.argsHash != expected) {
      break
    }
    alternatingTailCount += 1
  }

  if (alternatingTailCount < 2) {
    return { count: 0, noProgressEvidence: false }
  }

  val expectedCurrentSignature = otherSignature
  if (currentSignature != expectedCurrentSignature) {
    return { count: 0, noProgressEvidence: false }
  }

  val tailStart = Math.max(0, history.length - alternatingTailCount)
  var firstHashA: String?
  var firstHashB: String?
  var noProgressEvidence = true
  for (var i = tailStart i < history.length i += 1) {
    val call = history[i]
    if (!call) {
      continue
    }
    if (!call.resultHash) {
      noProgressEvidence = false
      break
    }
    if (call.argsHash == last.argsHash) {
      if (!firstHashA) {
        firstHashA = call.resultHash
      } else if (firstHashA != call.resultHash) {
        noProgressEvidence = false
        break
      }
      continue
    }
    if (call.argsHash == otherSignature) {
      if (!firstHashB) {
        firstHashB = call.resultHash
      } else if (firstHashB != call.resultHash) {
        noProgressEvidence = false
        break
      }
      continue
    }
    noProgressEvidence = false
    break
  }

  // Need repeated stable outcomes on both sides before treating ping-pong as /* TODO */ no-progress.
  if (!firstHashA || !firstHashB) {
    noProgressEvidence = false
  }

  return {
    count: alternatingTailCount + 1,
    pairedToolName: last.toolName,
    pairedSignature: last.argsHash,
    noProgressEvidence,
  }
}

fun canonicalPairKey(signatureA: String, signatureB: String): String {
  return [signatureA, signatureB].toSorted().join("|")
}

/**
 * Detect if an agent is stuck in a repetitive tool call loop.
 * Checks if the same tool+params combination has been called excessively.
 */
fun detectToolCallLoop(
  state: SessionState,
  toolName: String,
  params: Any?,
  config?: ToolLoopDetectionConfig,
): LoopDetectionResult {
  val resolvedConfig = resolveLoopDetectionConfig(config)
  if (!resolvedConfig.enabled) {
    return { stuck: false }
  }
  val history = state.toolCallHistory ?: []
  val currentHash = hashToolCall(toolName, params)
  val noProgress = getNoProgressStreak(history, toolName, currentHash)
  val noProgressStreak = noProgress.count
  val knownPollTool = isKnownPollToolCall(toolName, params)
  val pingPong = getPingPongStreak(history, currentHash)

  if (noProgressStreak >= resolvedConfig.globalCircuitBreakerThreshold) {
    log.error(
      `Global circuit breaker triggered: ${toolName} repeated ${noProgressStreak} times with no progress`,
    )
    return {
      stuck: true,
      level: String /* "critical" */,
      detector: String /* "global_circuit_breaker" */,
      count: noProgressStreak,
      message: `CRITICAL: ${toolName} has repeated identical no-progress outcomes ${noProgressStreak} times. Session execution blocked by global circuit breaker to prevent runaway loops.`,
      warningKey: `global:${toolName}:${currentHash}:${noProgress.latestResultHash ?: String /* "none" */}`,
    }
  }

  if (
    knownPollTool &&
    resolvedConfig.detectors.knownPollNoProgress &&
    noProgressStreak >= resolvedConfig.criticalThreshold
  ) {
    log.error(`Critical polling loop detected: ${toolName} repeated ${noProgressStreak} times`)
    return {
      stuck: true,
      level: String /* "critical" */,
      detector: String /* "known_poll_no_progress" */,
      count: noProgressStreak,
      message: `CRITICAL: Called ${toolName} with identical arguments and no progress ${noProgressStreak} times. This appears to be a stuck polling loop. Session execution blocked to prevent resource waste.`,
      warningKey: `poll:${toolName}:${currentHash}:${noProgress.latestResultHash ?: String /* "none" */}`,
    }
  }

  if (
    knownPollTool &&
    resolvedConfig.detectors.knownPollNoProgress &&
    noProgressStreak >= resolvedConfig.warningThreshold
  ) {
    log.warn(`Polling loop warning: ${toolName} repeated ${noProgressStreak} times`)
    return {
      stuck: true,
      level: String /* "warning" */,
      detector: String /* "known_poll_no_progress" */,
      count: noProgressStreak,
      message: `WARNING: You have called ${toolName} ${noProgressStreak} times with identical arguments and no progress. Stop polling and either (1) increase wait time between checks, or (2) report the task as /* TODO */ failed if the process is stuck.`,
      warningKey: `poll:${toolName}:${currentHash}:${noProgress.latestResultHash ?: String /* "none" */}`,
    }
  }

  val pingPongWarningKey = pingPong.pairedSignature
    ? `pingpong:${canonicalPairKey(currentHash, pingPong.pairedSignature)}`
    : `pingpong:${toolName}:${currentHash}`

  if (
    resolvedConfig.detectors.pingPong &&
    pingPong.count >= resolvedConfig.criticalThreshold &&
    pingPong.noProgressEvidence
  ) {
    log.error(
      `Critical ping-pong loop detected: alternating calls count=${pingPong.count} currentTool=${toolName}`,
    )
    return {
      stuck: true,
      level: String /* "critical" */,
      detector: String /* "ping_pong" */,
      count: pingPong.count,
      message: `CRITICAL: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls) with no progress. This appears to be a stuck ping-pong loop. Session execution blocked to prevent resource waste.`,
      pairedToolName: pingPong.pairedToolName,
      warningKey: pingPongWarningKey,
    }
  }

  if (resolvedConfig.detectors.pingPong && pingPong.count >= resolvedConfig.warningThreshold) {
    log.warn(
      `Ping-pong loop warning: alternating calls count=${pingPong.count} currentTool=${toolName}`,
    )
    return {
      stuck: true,
      level: String /* "warning" */,
      detector: String /* "ping_pong" */,
      count: pingPong.count,
      message: `WARNING: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls). This looks like a ping-pong loop stop retrying and report the task as /* TODO */ failed.`,
      pairedToolName: pingPong.pairedToolName,
      warningKey: pingPongWarningKey,
    }
  }

  // Generic detector: warn-only for repeated identical calls.
  val recentCount = history.filter(
    (h) -> h.toolName == toolName && h.argsHash == currentHash,
  ).length

  if (
    !knownPollTool &&
    resolvedConfig.detectors.genericRepeat &&
    recentCount >= resolvedConfig.warningThreshold
  ) {
    log.warn(`Loop warning: ${toolName} called ${recentCount} times with identical arguments`)
    return {
      stuck: true,
      level: String /* "warning" */,
      detector: String /* "generic_repeat" */,
      count: recentCount,
      message: `WARNING: You have called ${toolName} ${recentCount} times with identical arguments. If this is not making progress, stop retrying and report the task as /* TODO */ failed.`,
      warningKey: `generic:${toolName}:${currentHash}`,
    }
  }

  return { stuck: false }
}

/**
 * Record a tool call in the session's history for loop detection.
 * Maintains sliding window of last N calls.
 */
fun recordToolCall(
  state: SessionState,
  toolName: String,
  params: Any?,
  toolCallId?: String,
  config?: ToolLoopDetectionConfig,
): Unit {
  val resolvedConfig = resolveLoopDetectionConfig(config)
  if (!state.toolCallHistory) {
    state.toolCallHistory = []
  }

  state.toolCallHistory.push({
    toolName,
    argsHash: hashToolCall(toolName, params),
    toolCallId,
    timestamp: Date.now(),
  })

  if (state.toolCallHistory.length > resolvedConfig.historySize) {
    state.toolCallHistory.shift()
  }
}

/**
 * Record a completed tool call outcome so loop detection can identify no-progress repeats.
 */
fun recordToolCallOutcome(
  state: SessionState,
  params: {
    toolName: String
    toolParams: Any?
    toolCallId?: String
    result?: Any?
    error?: Any?
    config?: ToolLoopDetectionConfig
  },
): Unit {
  val resolvedConfig = resolveLoopDetectionConfig(params.config)
  val resultHash = hashToolOutcome(
    params.toolName,
    params.toolParams,
    params.result,
    params.error,
  )
  if (!resultHash) {
    return
  }

  if (!state.toolCallHistory) {
    state.toolCallHistory = []
  }

  val argsHash = hashToolCall(params.toolName, params.toolParams)
  var matched = false
  for (var i = state.toolCallHistory.length - 1 i >= 0 i -= 1) {
    val call = state.toolCallHistory[i]
    if (!call) {
      continue
    }
    if (params.toolCallId && call.toolCallId != params.toolCallId) {
      continue
    }
    if (call.toolName != params.toolName || call.argsHash != argsHash) {
      continue
    }
    if (call.resultHash != null) {
      continue
    }
    call.resultHash = resultHash
    matched = true
    break
  }

  if (!matched) {
    state.toolCallHistory.push({
      toolName: params.toolName,
      argsHash,
      toolCallId: params.toolCallId,
      resultHash,
      timestamp: Date.now(),
    })
  }

  if (state.toolCallHistory.length > resolvedConfig.historySize) {
    state.toolCallHistory.splice(0, state.toolCallHistory.length - resolvedConfig.historySize)
  }
}

/**
 * Get current tool call statistics for a session (for debugging/monitoring).
 */
fun getToolCallStats(state: SessionState): {
  totalCalls: Double
  uniquePatterns: Double
  mostFrequent: { toolName: String count: Double }?
} {
  val history = state.toolCallHistory ?: []
  val patterns = new MutableMap<String, { toolName: String count: Double }>()

  for (val call of history) {
    val key = call.argsHash
    val existing = patterns.get(key)
    if (existing) {
      existing.count += 1
    } else {
      patterns.set(key, { toolName: call.toolName, count: 1 })
    }
  }

  var mostFrequent: { toolName: String count: Double }? = null
  for (val pattern of patterns.values()) {
    if (!mostFrequent || pattern.count > mostFrequent.count) {
      mostFrequent = pattern
    }
  }

  return {
    totalCalls: history.length,
    uniquePatterns: patterns.size,
    mostFrequent,
  }
}
