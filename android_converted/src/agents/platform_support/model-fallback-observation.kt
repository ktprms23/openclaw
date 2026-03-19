package agents.platform_support

// Source: src/agents/model-fallback-observation.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { sanitizeForLog } from "../terminal/ansi.js";
// TODO(openclaw-kotlin-port): import type { FallbackAttempt, ModelCandidate } from "./model-fallback.types.js";
// TODO(openclaw-kotlin-port): import { buildTextObservationFields } from "./pi-embedded-error-observation.js";
// TODO(openclaw-kotlin-port): import type { FailoverReason } from "./pi-embedded-helpers.js";

val decisionLog = createSubsystemLogger("model-fallback").child("decision")

fun buildErrorObservationFields(error?: String): {
  errorPreview?: String
  errorHash?: String
  errorFingerprint?: String
  httpCode?: String
  providerErrorType?: String
  providerErrorMessagePreview?: String
  requestIdHash?: String
} {
  val observed = buildTextObservationFields(error)
  return {
    errorPreview: observed.textPreview,
    errorHash: observed.textHash,
    errorFingerprint: observed.textFingerprint,
    httpCode: observed.httpCode,
    providerErrorType: observed.providerErrorType,
    providerErrorMessagePreview: observed.providerErrorMessagePreview,
    requestIdHash: observed.requestIdHash,
  }
}

fun logModelFallbackDecision(params: {
  decision:
    | "skip_candidate"
    | "probe_cooldown_candidate"
    | "candidate_failed"
    | "candidate_succeeded"
  runId?: String
  requestedProvider: String
  requestedModel: String
  candidate: ModelCandidate
  attempt?: Double
  total?: Double
  reason?: FailoverReason | Nothing?
  status?: Double
  code?: String
  error?: String
  nextCandidate?: ModelCandidate
  isPrimary?: Boolean
  requestedModelMatched?: Boolean
  fallbackConfigured?: Boolean
  allowTransientCooldownProbe?: Boolean
  profileCount?: Double
  previousAttempts?: FallbackAttempt[]
}): Unit {
  val nextText = params.nextCandidate
    ? `${sanitizeForLog(params.nextCandidate.provider)}/${sanitizeForLog(params.nextCandidate.model)}`
    : "none"
  val reasonText = params.reason ?? "Any?"
  val observedError = buildErrorObservationFields(params.error)
  decisionLog.warn("model fallback decision", {
    event: "model_fallback_decision",
    tags: ["error_handling", "model_fallback", params.decision],
    runId: params.runId,
    decision: params.decision,
    requestedProvider: params.requestedProvider,
    requestedModel: params.requestedModel,
    candidateProvider: params.candidate.provider,
    candidateModel: params.candidate.model,
    attempt: params.attempt,
    total: params.total,
    reason: params.reason,
    status: params.status,
    code: params.code,
    ...observedError,
    nextCandidateProvider: params.nextCandidate?.provider,
    nextCandidateModel: params.nextCandidate?.model,
    isPrimary: params.isPrimary,
    requestedModelMatched: params.requestedModelMatched,
    fallbackConfigured: params.fallbackConfigured,
    allowTransientCooldownProbe: params.allowTransientCooldownProbe,
    profileCount: params.profileCount,
    previousAttempts: params.previousAttempts?.map((attempt) => ({
      provider: attempt.provider,
      model: attempt.model,
      reason: attempt.reason,
      status: attempt.status,
      code: attempt.code,
      ...buildErrorObservationFields(attempt.error),
    })),
    consoleMessage:
      `model fallback decision: decision=${params.decision} requested=${sanitizeForLog(params.requestedProvider)}/${sanitizeForLog(params.requestedModel)} ` +
      `candidate=${sanitizeForLog(params.candidate.provider)}/${sanitizeForLog(params.candidate.model)} reason=${reasonText} next=${nextText}`,
  })
}
