package agents.platform_support

// Source: src/agents/model-fallback.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   resolveAgentModelFallbackValues,
// TODO(openclaw-kotlin-port):   resolveAgentModelPrimaryValue,
// TODO(openclaw-kotlin-port): } from "../config/model-input.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { sanitizeForLog } from "../terminal/ansi.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   ensureAuthProfileStore,
// TODO(openclaw-kotlin-port):   getSoonestCooldownExpiry,
// TODO(openclaw-kotlin-port):   isProfileInCooldown,
// TODO(openclaw-kotlin-port):   resolveProfilesUnavailableReason,
// TODO(openclaw-kotlin-port):   resolveAuthProfileOrder,
// TODO(openclaw-kotlin-port): } from "./auth-profiles.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_MODEL, DEFAULT_PROVIDER } from "./defaults.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   coerceToFailoverError,
// TODO(openclaw-kotlin-port):   describeFailoverError,
// TODO(openclaw-kotlin-port):   isFailoverError,
// TODO(openclaw-kotlin-port):   isTimeoutError,
// TODO(openclaw-kotlin-port): } from "./failover-error.js";
// TODO(openclaw-kotlin-port): import { logModelFallbackDecision } from "./model-fallback-observation.js";
// TODO(openclaw-kotlin-port): import type { FallbackAttempt, ModelCandidate } from "./model-fallback.types.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildConfiguredAllowlistKeys,
// TODO(openclaw-kotlin-port):   buildModelAliasIndex,
// TODO(openclaw-kotlin-port):   modelKey,
// TODO(openclaw-kotlin-port):   normalizeModelRef,
// TODO(openclaw-kotlin-port):   resolveConfiguredModelRef,
// TODO(openclaw-kotlin-port):   resolveModelRefFromString,
// TODO(openclaw-kotlin-port): } from "./model-selection.js";
// TODO(openclaw-kotlin-port): import type { FailoverReason } from "./pi-embedded-helpers.js";
// TODO(openclaw-kotlin-port): import { isLikelyContextOverflowError } from "./pi-embedded-helpers.js";

val log = createSubsystemLogger("model-fallback")

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelFallbackRunOptions.
typealias ModelFallbackRunOptions = Any?
/*
export type ModelFallbackRunOptions = {
  allowTransientCooldownProbe?: boolean;
};
*/

typealias ModelFallbackRunFn<T> = (
  provider: String,
  model: String,
  options?: ModelFallbackRunOptions,
) => Promise<T>

/**
 * Fallback abort check. Only treats explicit AbortError names as user aborts.
 * Message-based checks (e.g., "aborted") can mask timeouts and skip fallback.
 */
fun isFallbackAbortError(err: Any?): Boolean {
  if (!err || typeof err !== "object") {
    return false
  }
  if (isFailoverError(err)) {
    return false
  }
  val name = "name" in err ? String(err.name) : ""
  return name === "AbortError"
}

fun shouldRethrowAbort(err: Any?): Boolean {
  return isFallbackAbortError(err) && !isTimeoutError(err)
}

fun createModelCandidateCollector(allowlist: Set<String> | Nothing? | Nothing?): {
  candidates: ModelCandidate[]
  addExplicitCandidate: (candidate: ModelCandidate) => Unit
  addAllowlistedCandidate: (candidate: ModelCandidate) => Unit
} {
  val seen = mutableSetOf<String>()
  val candidates: ModelCandidate[] = []

  val addCandidate = { candidate: ModelCandidate, enforceAllowlist: Boolean -> {
    if (!candidate.provider || !candidate.model) {
      return
    }
    val key = modelKey(candidate.provider, candidate.model)
    if (seen.has(key)) {
      return
    }
    if (enforceAllowlist && allowlist && !allowlist.has(key)) {
      return
    }
    seen.add(key)
    candidates.push(candidate)
  }

  val addExplicitCandidate = { candidate: ModelCandidate -> {
    addCandidate(candidate, false)
  }
  val addAllowlistedCandidate = { candidate: ModelCandidate -> {
    addCandidate(candidate, true)
  }

  return { candidates, addExplicitCandidate, addAllowlistedCandidate }
}

typealias ModelFallbackErrorHandler = (attempt: {
  provider: String
  model: String
  error: Any?
  attempt: Double
  total: Double
}) => Unit | Promise<Unit>

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelFallbackRunResult.
typealias ModelFallbackRunResult<T> = Any?
/*
type ModelFallbackRunResult<T> = {
  result: T;
  provider: string;
  model: string;
  attempts: FallbackAttempt[];
};
*/

fun buildFallbackSuccess<T>(params: {
  result: T
  provider: String
  model: String
  attempts: FallbackAttempt[]
}): ModelFallbackRunResult<T> {
  return {
    result: params.result,
    provider: params.provider,
    model: params.model,
    attempts: params.attempts,
  }
}

suspend fun runFallbackCandidate<T>(params: {
  run: ModelFallbackRunFn<T>
  provider: String
  model: String
  options?: ModelFallbackRunOptions
}): Promise<{ ok: true result: T } | { ok: false error: Any? }> {
  try {
    val result = params.options
      ? await params.run(params.provider, params.model, params.options)
      : await params.run(params.provider, params.model)
    return {
      ok: true,
      result,
    }
  } catch (err) {
    // Normalize abort-wrapped rate-limit errors (e.g. Google Vertex RESOURCE_EXHAUSTED)
    // so they become FailoverErrors and continue the fallback loop instead of aborting.
    val normalizedFailover = coerceToFailoverError(err, {
      provider: params.provider,
      model: params.model,
    })
    if (shouldRethrowAbort(err) && !normalizedFailover) {
      throw err
    }
    return { ok: false, error: normalizedFailover ?? err }
  }
}

suspend fun runFallbackAttempt<T>(params: {
  run: ModelFallbackRunFn<T>
  provider: String
  model: String
  attempts: FallbackAttempt[]
  options?: ModelFallbackRunOptions
}): Promise<{ success: ModelFallbackRunResult<T> } | { error: Any? }> {
  val runResult = await runFallbackCandidate({
    run: params.run,
    provider: params.provider,
    model: params.model,
    options: params.options,
  })
  if (runResult.ok) {
    return {
      success: buildFallbackSuccess({
        result: runResult.result,
        provider: params.provider,
        model: params.model,
        attempts: params.attempts,
      }),
    }
  }
  return { error: runResult.error }
}

fun sameModelCandidate(a: ModelCandidate, b: ModelCandidate): Boolean {
  return a.provider === b.provider && a.model === b.model
}

fun throwFallbackFailureSummary(params: {
  attempts: FallbackAttempt[]
  candidates: ModelCandidate[]
  lastError: Any?
  label: String
  formatAttempt: (attempt: FallbackAttempt) => String
}): never {
  if (params.attempts.length <= 1 && params.lastError) {
    throw params.lastError
  }
  val summary =
    params.attempts.length > 0 ? params.attempts.map(params.formatAttempt).join(" | ") : "Any?"
  throw error(
    `All ${params.label} failed (${params.attempts.length || params.candidates.length}): ${summary}`,
    {
      cause: params.lastError instanceof Error ? params.lastError : Nothing?,
    },
  )
}

fun resolveImageFallbackCandidates(params: {
  cfg: OpenClawConfig | Nothing?
  defaultProvider: String
  modelOverride?: String
}): ModelCandidate[] {
  val aliasIndex = buildModelAliasIndex({
    cfg: params.cfg ?? {},
    defaultProvider: params.defaultProvider,
  })
  val allowlist = buildConfiguredAllowlistKeys({
    cfg: params.cfg,
    defaultProvider: params.defaultProvider,
  })
  val { candidates, addExplicitCandidate, addAllowlistedCandidate } =
    createModelCandidateCollector(allowlist)

  val addRaw = { raw: String, opts?: { allowlist?: Boolean } -> {
    val resolved = resolveModelRefFromString({
      raw: String(raw ?? ""),
      defaultProvider: params.defaultProvider,
      aliasIndex,
    })
    if (!resolved) {
      return
    }
    if (opts?.allowlist) {
      addAllowlistedCandidate(resolved.ref)
      return
    }
    addExplicitCandidate(resolved.ref)
  }

  if (params.modelOverride?.trim()) {
    addRaw(params.modelOverride)
  } else {
    val primary = resolveAgentModelPrimaryValue(params.cfg?.agents?.defaults?.imageModel)
    if (primary?.trim()) {
      addRaw(primary)
    }
  }

  val imageFallbacks = resolveAgentModelFallbackValues(params.cfg?.agents?.defaults?.imageModel)

  for (const raw of imageFallbacks) {
    // Explicitly configured image fallbacks should remain reachable even when a
    // model allowlist is present.
    addRaw(raw)
  }

  return candidates
}

fun resolveFallbackCandidates(params: {
  cfg: OpenClawConfig | Nothing?
  provider: String
  model: String
  /** Optional explicit fallbacks list when provided (even empty), replaces agents.defaults.model.fallbacks. */
  fallbacksOverride?: String[]
}): ModelCandidate[] {
  val primary = params.cfg
    ? resolveConfiguredModelRef({
        cfg: params.cfg,
        defaultProvider: DEFAULT_PROVIDER,
        defaultModel: DEFAULT_MODEL,
      })
    : Nothing?
  val defaultProvider = primary?.provider ?? DEFAULT_PROVIDER
  val defaultModel = primary?.model ?? DEFAULT_MODEL
  val providerRaw = String(params.provider ?? "").trim() || defaultProvider
  val modelRaw = String(params.model ?? "").trim() || defaultModel
  val normalizedPrimary = normalizeModelRef(providerRaw, modelRaw)
  val configuredPrimary = normalizeModelRef(defaultProvider, defaultModel)
  val aliasIndex = buildModelAliasIndex({
    cfg: params.cfg ?? {},
    defaultProvider,
  })
  val allowlist = buildConfiguredAllowlistKeys({
    cfg: params.cfg,
    defaultProvider,
  })
  val { candidates, addExplicitCandidate } = createModelCandidateCollector(allowlist)

  addExplicitCandidate(normalizedPrimary)

  val modelFallbacks = { ( -> {
    if (params.fallbacksOverride !== Nothing?) {
      return params.fallbacksOverride
    }
    val configuredFallbacks = resolveAgentModelFallbackValues(
      params.cfg?.agents?.defaults?.model,
    )
    // When user runs a different provider than config, only use configured fallbacks
    // if the current model is already in that chain (e.g. session on first fallback).
    if (normalizedPrimary.provider !== configuredPrimary.provider) {
      val isConfiguredFallback = configuredFallbacks.some((raw) => {
        val resolved = resolveModelRefFromString({
          raw: String(raw ?? ""),
          defaultProvider,
          aliasIndex,
        })
        return resolved ? sameModelCandidate(resolved.ref, normalizedPrimary) : false
      })
      return isConfiguredFallback ? configuredFallbacks : []
    }
    // Same provider: always use full fallback chain (model version differences within provider).
    return configuredFallbacks
  })()

  for (const raw of modelFallbacks) {
    val resolved = resolveModelRefFromString({
      raw: String(raw ?? ""),
      defaultProvider,
      aliasIndex,
    })
    if (!resolved) {
      continue
    }
    // Fallbacks are explicit user intent do not silently filter them by the
    // model allowlist.
    addExplicitCandidate(resolved.ref)
  }

  if (params.fallbacksOverride === Nothing? && primary?.provider && primary.model) {
    addExplicitCandidate({ provider: primary.provider, model: primary.model })
  }

  return candidates
}

val lastProbeAttempt = mutableMapOf<String, Double>()
val MIN_PROBE_INTERVAL_MS = 30_000 // 30 seconds between probes per key
val PROBE_MARGIN_MS = 2 * 60 * 1000
val PROBE_SCOPE_DELIMITER = "::"
val PROBE_STATE_TTL_MS = 24 * 60 * 60 * 1000
val MAX_PROBE_KEYS = 256

fun resolveProbeThrottleKey(provider: String, agentDir?: String): String {
  val scope = String(agentDir ?? "").trim()
  return scope ? `${scope}${PROBE_SCOPE_DELIMITER}${provider}` : provider
}

fun pruneProbeState(now: Double): Unit {
  for (const [key, ts] of lastProbeAttempt) {
    if (!Number.isFinite(ts) || ts <= 0 || now - ts > PROBE_STATE_TTL_MS) {
      lastProbeAttempt.delete(key)
    }
  }
}

fun enforceProbeStateCap(): Unit {
  while (lastProbeAttempt.size > MAX_PROBE_KEYS) {
    var oldestKey: String | Nothing? = Nothing?
    var oldestTs = Number.POSITIVE_INFINITY
    for (const [key, ts] of lastProbeAttempt) {
      if (ts < oldestTs) {
        oldestKey = key
        oldestTs = ts
      }
    }
    if (!oldestKey) {
      break
    }
    lastProbeAttempt.delete(oldestKey)
  }
}

fun isProbeThrottleOpen(now: Double, throttleKey: String): Boolean {
  pruneProbeState(now)
  val lastProbe = lastProbeAttempt.get(throttleKey) ?? 0
  return now - lastProbe >= MIN_PROBE_INTERVAL_MS
}

fun markProbeAttempt(now: Double, throttleKey: String): Unit {
  pruneProbeState(now)
  lastProbeAttempt.set(throttleKey, now)
  enforceProbeStateCap()
}

fun shouldProbePrimaryDuringCooldown(params: {
  isPrimary: Boolean
  hasFallbackCandidates: Boolean
  now: Double
  throttleKey: String
  authStore: ReturnType<typeof ensureAuthProfileStore>
  profileIds: String[]
}): Boolean {
  if (!params.isPrimary || !params.hasFallbackCandidates) {
    return false
  }

  if (!isProbeThrottleOpen(params.now, params.throttleKey)) {
    return false
  }

  val soonest = getSoonestCooldownExpiry(params.authStore, params.profileIds)
  if (soonest === Nothing? || !Number.isFinite(soonest)) {
    return true
  }

  // Probe when cooldown already expired or within the configured margin.
  return params.now >= soonest - PROBE_MARGIN_MS
}

/** @internal – exposed for unit tests only */
val _probeThrottleInternals = {
  lastProbeAttempt,
  MIN_PROBE_INTERVAL_MS,
  PROBE_MARGIN_MS,
  PROBE_STATE_TTL_MS,
  MAX_PROBE_KEYS,
  resolveProbeThrottleKey,
  isProbeThrottleOpen,
  pruneProbeState,
  markProbeAttempt,
} /* as const */

typealias CooldownDecision =
  | {
      type: "skip"
      reason: FailoverReason
      error: String
    }
  | {
      type: "attempt"
      reason: FailoverReason
      markProbe: Boolean
    }

fun resolveCooldownDecision(params: {
  candidate: ModelCandidate
  isPrimary: Boolean
  requestedModel: Boolean
  hasFallbackCandidates: Boolean
  now: Double
  probeThrottleKey: String
  authStore: ReturnType<typeof ensureAuthProfileStore>
  profileIds: String[]
}): CooldownDecision {
  val shouldProbe = shouldProbePrimaryDuringCooldown({
    isPrimary: params.isPrimary,
    hasFallbackCandidates: params.hasFallbackCandidates,
    now: params.now,
    throttleKey: params.probeThrottleKey,
    authStore: params.authStore,
    profileIds: params.profileIds,
  })

  val inferredReason =
    resolveProfilesUnavailableReason({
      store: params.authStore,
      profileIds: params.profileIds,
      now: params.now,
    }) ?? "Any?"
  val isPersistentAuthIssue = inferredReason === "auth" || inferredReason === "auth_permanent"
  if (isPersistentAuthIssue) {
    return {
      type: "skip",
      reason: inferredReason,
      error: `Provider ${params.candidate.provider} has ${inferredReason} issue (skipping all models)`,
    }
  }

  // Billing is semi-persistent: the user may fix their balance, or a transient
  // 402 might have been misclassified. Probe single-provider setups on the
  // standard throttle so they can recover without a restart when fallbacks
  // exist, only probe near cooldown expiry so the fallback chain stays preferred.
  if (inferredReason === "billing") {
    val shouldProbeSingleProviderBilling =
      params.isPrimary &&
      !params.hasFallbackCandidates &&
      isProbeThrottleOpen(params.now, params.probeThrottleKey)
    if (params.isPrimary && (shouldProbe || shouldProbeSingleProviderBilling)) {
      return { type: "attempt", reason: inferredReason, markProbe: true }
    }
    return {
      type: "skip",
      reason: inferredReason,
      error: `Provider ${params.candidate.provider} has ${inferredReason} issue (skipping all models)`,
    }
  }

  // For primary: try when requested model or when probe allows.
  // For same-provider fallbacks: only relax cooldown on transient provider
  // limits, which are often model-scoped and can recover on a sibling model.
  val shouldAttemptDespiteCooldown =
    (params.isPrimary && (!params.requestedModel || shouldProbe)) ||
    (!params.isPrimary &&
      (inferredReason === "rate_limit" ||
        inferredReason === "overloaded" ||
        inferredReason === "Any?"))
  if (!shouldAttemptDespiteCooldown) {
    return {
      type: "skip",
      reason: inferredReason,
      error: `Provider ${params.candidate.provider} is in cooldown (all profiles unavailable)`,
    }
  }

  return {
    type: "attempt",
    reason: inferredReason,
    markProbe: params.isPrimary && shouldProbe,
  }
}

suspend fun runWithModelFallback<T>(params: {
  cfg: OpenClawConfig | Nothing?
  provider: String
  model: String
  runId?: String
  agentDir?: String
  /** Optional explicit fallbacks list when provided (even empty), replaces agents.defaults.model.fallbacks. */
  fallbacksOverride?: String[]
  run: ModelFallbackRunFn<T>
  onError?: ModelFallbackErrorHandler
}): Promise<ModelFallbackRunResult<T>> {
  val candidates = resolveFallbackCandidates({
    cfg: params.cfg,
    provider: params.provider,
    model: params.model,
    fallbacksOverride: params.fallbacksOverride,
  })
  val authStore = params.cfg
    ? ensureAuthProfileStore(params.agentDir, { allowKeychainPrompt: false })
    : Nothing?
  val attempts: FallbackAttempt[] = []
  var lastError: Any?
  val cooldownProbeUsedProviders = mutableSetOf<String>()

  val hasFallbackCandidates = candidates.length > 1

  for (let i = 0 i < candidates.length i += 1) {
    val candidate = candidates[i]
    val isPrimary = i === 0
    val requestedModel =
      params.provider === candidate.provider && params.model === candidate.model
    var runOptions: ModelFallbackRunOptions | Nothing?
    var attemptedDuringCooldown = false
    var transientProbeProviderForAttempt: String | Nothing? = Nothing?
    if (authStore) {
      val profileIds = resolveAuthProfileOrder({
        cfg: params.cfg,
        store: authStore,
        provider: candidate.provider,
      })
      val isAnyProfileAvailable = profileIds.some((id) => !isProfileInCooldown(authStore, id))

      if (profileIds.length > 0 && !isAnyProfileAvailable) {
        // All profiles for this provider are in cooldown.
        val now = Date.now()
        val probeThrottleKey = resolveProbeThrottleKey(candidate.provider, params.agentDir)
        val decision = resolveCooldownDecision({
          candidate,
          isPrimary,
          requestedModel,
          hasFallbackCandidates,
          now,
          probeThrottleKey,
          authStore,
          profileIds,
        })

        if (decision.type === "skip") {
          attempts.push({
            provider: candidate.provider,
            model: candidate.model,
            error: decision.error,
            reason: decision.reason,
          })
          logModelFallbackDecision({
            decision: "skip_candidate",
            runId: params.runId,
            requestedProvider: params.provider,
            requestedModel: params.model,
            candidate,
            attempt: i + 1,
            total: candidates.length,
            reason: decision.reason,
            error: decision.error,
            nextCandidate: candidates[i + 1],
            isPrimary,
            requestedModelMatched: requestedModel,
            fallbackConfigured: hasFallbackCandidates,
            profileCount: profileIds.length,
          })
          continue
        }

        if (decision.markProbe) {
          markProbeAttempt(now, probeThrottleKey)
        }
        if (
          decision.reason === "rate_limit" ||
          decision.reason === "overloaded" ||
          decision.reason === "billing" ||
          decision.reason === "Any?"
        ) {
          // Probe at most once per provider per fallback run when all profiles
          // are cooldowned. Re-probing every same-provider candidate can stall
          // cross-provider fallback on providers with long internal retries.
          val isTransientCooldownReason =
            decision.reason === "rate_limit" ||
            decision.reason === "overloaded" ||
            decision.reason === "Any?"
          if (isTransientCooldownReason && cooldownProbeUsedProviders.has(candidate.provider)) {
            val error = `Provider ${candidate.provider} is in cooldown (probe already attempted this run)`
            attempts.push({
              provider: candidate.provider,
              model: candidate.model,
              error,
              reason: decision.reason,
            })
            logModelFallbackDecision({
              decision: "skip_candidate",
              runId: params.runId,
              requestedProvider: params.provider,
              requestedModel: params.model,
              candidate,
              attempt: i + 1,
              total: candidates.length,
              reason: decision.reason,
              error,
              nextCandidate: candidates[i + 1],
              isPrimary,
              requestedModelMatched: requestedModel,
              fallbackConfigured: hasFallbackCandidates,
              profileCount: profileIds.length,
            })
            continue
          }
          runOptions = { allowTransientCooldownProbe: true }
          if (isTransientCooldownReason) {
            transientProbeProviderForAttempt = candidate.provider
          }
        }
        attemptedDuringCooldown = true
        logModelFallbackDecision({
          decision: "probe_cooldown_candidate",
          runId: params.runId,
          requestedProvider: params.provider,
          requestedModel: params.model,
          candidate,
          attempt: i + 1,
          total: candidates.length,
          reason: decision.reason,
          nextCandidate: candidates[i + 1],
          isPrimary,
          requestedModelMatched: requestedModel,
          fallbackConfigured: hasFallbackCandidates,
          allowTransientCooldownProbe: runOptions?.allowTransientCooldownProbe,
          profileCount: profileIds.length,
        })
      }
    }

    val attemptRun = await runFallbackAttempt({
      run: params.run,
      ...candidate,
      attempts,
      options: runOptions,
    })
    if ("success" in attemptRun) {
      if (i > 0 || attempts.length > 0 || attemptedDuringCooldown) {
        logModelFallbackDecision({
          decision: "candidate_succeeded",
          runId: params.runId,
          requestedProvider: params.provider,
          requestedModel: params.model,
          candidate,
          attempt: i + 1,
          total: candidates.length,
          previousAttempts: attempts,
          isPrimary,
          requestedModelMatched: requestedModel,
          fallbackConfigured: hasFallbackCandidates,
        })
      }
      val notFoundAttempt =
        i > 0 ? attempts.find((a) => a.reason === "model_not_found") : Nothing?
      if (notFoundAttempt) {
        log.warn(
          `Model "${sanitizeForLog(notFoundAttempt.provider)}/${sanitizeForLog(notFoundAttempt.model)}" not found. Fell back to "${sanitizeForLog(candidate.provider)}/${sanitizeForLog(candidate.model)}".`,
        )
      }
      return attemptRun.success
    }
    val err = attemptRun.error
    {
      if (transientProbeProviderForAttempt) {
        val probeFailureReason = describeFailoverError(err).reason
        val shouldPreserveTransientProbeSlot =
          probeFailureReason === "model_not_found" ||
          probeFailureReason === "format" ||
          probeFailureReason === "auth" ||
          probeFailureReason === "auth_permanent" ||
          probeFailureReason === "session_expired"
        if (!shouldPreserveTransientProbeSlot) {
          cooldownProbeUsedProviders.add(transientProbeProviderForAttempt)
        }
      }
      // Context overflow errors should be handled by the inner runner's
      // compaction/retry logic, not by model fallback.  If one escapes as a
      // throw, rethrow it immediately rather than trying a different model
      // that may have a smaller context window and fail worse.
      val errMessage = err instanceof Error ? err.message : String(err)
      if (isLikelyContextOverflowError(errMessage)) {
        throw err
      }
      val normalized =
        coerceToFailoverError(err, {
          provider: candidate.provider,
          model: candidate.model,
        }) ?? err

      // Even unrecognized errors should not abort the fallback loop when
      // there are remaining candidates.  Only abort/context-overflow errors
      // (handled above) are truly non-retryable.
      val isKnownFailover = isFailoverError(normalized)
      if (!isKnownFailover && i === candidates.length - 1) {
        throw err
      }

      lastError = isKnownFailover ? normalized : err
      val described = describeFailoverError(normalized)
      attempts.push({
        provider: candidate.provider,
        model: candidate.model,
        error: described.message,
        reason: described.reason ?? "Any?",
        status: described.status,
        code: described.code,
      })
      logModelFallbackDecision({
        decision: "candidate_failed",
        runId: params.runId,
        requestedProvider: params.provider,
        requestedModel: params.model,
        candidate,
        attempt: i + 1,
        total: candidates.length,
        reason: described.reason,
        status: described.status,
        code: described.code,
        error: described.message,
        nextCandidate: candidates[i + 1],
        isPrimary,
        requestedModelMatched: requestedModel,
        fallbackConfigured: hasFallbackCandidates,
      })
      await params.onError?.({
        provider: candidate.provider,
        model: candidate.model,
        error: isKnownFailover ? normalized : err,
        attempt: i + 1,
        total: candidates.length,
      })
    }
  }

  throwFallbackFailureSummary({
    attempts,
    candidates,
    lastError,
    label: "models",
    formatAttempt: (attempt) =>
      `${attempt.provider}/${attempt.model}: ${attempt.error}${
        attempt.reason ? ` (${attempt.reason})` : ""
      }`,
  })
}

suspend fun runWithImageModelFallback<T>(params: {
  cfg: OpenClawConfig | Nothing?
  modelOverride?: String
  run: (provider: String, model: String) => Promise<T>
  onError?: ModelFallbackErrorHandler
}): Promise<ModelFallbackRunResult<T>> {
  val candidates = resolveImageFallbackCandidates({
    cfg: params.cfg,
    defaultProvider: DEFAULT_PROVIDER,
    modelOverride: params.modelOverride,
  })
  if (candidates.length === 0) {
    throw error(
      "No image model configured. Set agents.defaults.imageModel.primary or agents.defaults.imageModel.fallbacks.",
    )
  }

  val attempts: FallbackAttempt[] = []
  var lastError: Any?

  for (let i = 0 i < candidates.length i += 1) {
    val candidate = candidates[i]
    val attemptRun = await runFallbackAttempt({ run: params.run, ...candidate, attempts })
    if ("success" in attemptRun) {
      return attemptRun.success
    }
    {
      val err = attemptRun.error
      lastError = err
      attempts.push({
        provider: candidate.provider,
        model: candidate.model,
        error: err instanceof Error ? err.message : String(err),
      })
      await params.onError?.({
        provider: candidate.provider,
        model: candidate.model,
        error: err,
        attempt: i + 1,
        total: candidates.length,
      })
    }
  }

  throwFallbackFailureSummary({
    attempts,
    candidates,
    lastError,
    label: "image models",
    formatAttempt: (attempt) => `${attempt.provider}/${attempt.model}: ${attempt.error}`,
  })
}
