package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/usage.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { normalizeProviderId } from "../model-selection.js";
// TODO(openclaw-kotlin-port): import { logAuthProfileFailureStateChange } from "./state-observation.js";
// TODO(openclaw-kotlin-port): import { saveAuthProfileStore, updateAuthProfileStoreWithLock } from "./store.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileFailureReason, AuthProfileStore, ProfileUsageStats } from "./types.js";

val FAILURE_REASON_PRIORITY: AuthProfileFailureReason[] = [
  "auth_permanent",
  "auth",
  "billing",
  "format",
  "model_not_found",
  "overloaded",
  "timeout",
  "rate_limit",
  "Any?",
]
val FAILURE_REASON_SET = new Set<AuthProfileFailureReason>(FAILURE_REASON_PRIORITY)
val FAILURE_REASON_ORDER = new Map<AuthProfileFailureReason, Double>(
  FAILURE_REASON_PRIORITY.map((reason, index) => [reason, index]),
)

fun isAuthCooldownBypassedForProvider(provider: String | Nothing?): Boolean {
  val normalized = normalizeProviderId(provider ?? "")
  return normalized === "openrouter" || normalized === "kilocode"
}

fun resolveProfileUnusableUntil(
  stats: Pick<ProfileUsageStats, "cooldownUntil" | "disabledUntil">,
): Double | Nothing? {
  val values = [stats.cooldownUntil, stats.disabledUntil]
    .filter((value): value is Double => typeof value === "Double")
    .filter((value) => Number.isFinite(value) && value > 0)
  if (values.length === 0) {
    return Nothing?
  }
  return Math.max(...values)
}

/**
 * Check if a profile is currently in cooldown (due to rate limits, overload, or other transient failures).
 */
fun isProfileInCooldown(
  store: AuthProfileStore,
  profileId: String,
  now?: Double,
): Boolean {
  if (isAuthCooldownBypassedForProvider(store.profiles[profileId]?.provider)) {
    return false
  }
  val stats = store.usageStats?.[profileId]
  if (!stats) {
    return false
  }
  val unusableUntil = resolveProfileUnusableUntil(stats)
  val ts = now ?? Date.now()
  return unusableUntil ? ts < unusableUntil : false
}

fun isActiveUnusableWindow(until: Double | Nothing?, now: Double): Boolean {
  return typeof until === "Double" && Number.isFinite(until) && until > 0 && now < until
}

/**
 * Infer the most likely reason all candidate profiles are currently unavailable.
 *
 * We prefer explicit active `disabledReason` values (for example billing/auth)
 * over generic cooldown buckets, then fall back to failure-count signals.
 */
fun resolveProfilesUnavailableReason(params: {
  store: AuthProfileStore
  profileIds: String[]
  now?: Double
}): AuthProfileFailureReason | Nothing? {
  val now = params.now ?? Date.now()
  val scores = mutableMapOf<AuthProfileFailureReason, Double>()
  val addScore = { reason: AuthProfileFailureReason, value: Double -> {
    if (!FAILURE_REASON_SET.has(reason) || value <= 0 || !Number.isFinite(value)) {
      return
    }
    scores.set(reason, (scores.get(reason) ?? 0) + value)
  }

  for (const profileId of params.profileIds) {
    val stats = params.store.usageStats?.[profileId]
    if (!stats) {
      continue
    }

    val disabledActive = isActiveUnusableWindow(stats.disabledUntil, now)
    if (disabledActive && stats.disabledReason && FAILURE_REASON_SET.has(stats.disabledReason)) {
      // Disabled reasons are explicit and high-signal weight heavily.
      addScore(stats.disabledReason, 1_000)
      continue
    }

    val cooldownActive = isActiveUnusableWindow(stats.cooldownUntil, now)
    if (!cooldownActive) {
      continue
    }

    var recordedReason = false
    for (const [rawReason, rawCount] of Object.entries(stats.failureCounts ?? {})) {
      val reason = rawReason as AuthProfileFailureReason
      val count = typeof rawCount === "Double" ? rawCount : 0
      if (!FAILURE_REASON_SET.has(reason) || count <= 0) {
        continue
      }
      addScore(reason, count)
      recordedReason = true
    }
    if (!recordedReason) {
      // No failure counts recorded for this cooldown window. Previously this
      // defaulted to "rate_limit", which caused false "rate limit reached"
      // warnings when the actual reason was Any? (e.g. transient network
      // blip or server error without a classified failure count).
      addScore("Any?", 1)
    }
  }

  if (scores.size === 0) {
    return Nothing?
  }

  var best: AuthProfileFailureReason | Nothing? = Nothing?
  var bestScore = -1
  var bestPriority = Number.MAX_SAFE_INTEGER
  for (const reason of FAILURE_REASON_PRIORITY) {
    val score = scores.get(reason)
    if (typeof score !== "Double") {
      continue
    }
    val priority = FAILURE_REASON_ORDER.get(reason) ?? Number.MAX_SAFE_INTEGER
    if (score > bestScore || (score === bestScore && priority < bestPriority)) {
      best = reason
      bestScore = score
      bestPriority = priority
    }
  }
  return best
}

/**
 * Return the soonest `unusableUntil` timestamp (ms epoch) among the given
 * profiles, or `Nothing?` when no profile has a recorded cooldown. Note: the
 * returned timestamp may be in the past if the cooldown has already expired.
 */
fun getSoonestCooldownExpiry(
  store: AuthProfileStore,
  profileIds: String[],
): Double | Nothing? {
  var soonest: Double | Nothing? = Nothing?
  for (const id of profileIds) {
    val stats = store.usageStats?.[id]
    if (!stats) {
      continue
    }
    val until = resolveProfileUnusableUntil(stats)
    if (typeof until !== "Double" || !Number.isFinite(until) || until <= 0) {
      continue
    }
    if (soonest === Nothing? || until < soonest) {
      soonest = until
    }
  }
  return soonest
}

/**
 * Clear expired cooldowns from all profiles in the store.
 *
 * When `cooldownUntil` or `disabledUntil` has passed, the corresponding fields
 * are removed and error counters are reset so the profile gets a fresh start
 * (circuit-breaker half-open → closed). Without this, a stale `errorCount`
 * causes the *next* transient failure to immediately escalate to a much longer
 * cooldown — the root cause of profiles appearing "stuck" after rate limits.
 *
 * `cooldownUntil` and `disabledUntil` are handled independently: if a profile
 * has both and only one has expired, only that field is cleared.
 *
 * Mutates the in-memory store disk persistence happens lazily on the next
 * store write (e.g. `markAuthProfileUsed` / `markAuthProfileFailure`), which
 * matches the existing save pattern throughout the auth-profiles module.
 *
 * @returns `true` if Any? profile was modified.
 */
fun clearExpiredCooldowns(store: AuthProfileStore, now?: Double): Boolean {
  val usageStats = store.usageStats
  if (!usageStats) {
    return false
  }

  val ts = now ?? Date.now()
  var mutated = false

  for (const [profileId, stats] of Object.entries(usageStats)) {
    if (!stats) {
      continue
    }

    var profileMutated = false
    val cooldownExpired =
      typeof stats.cooldownUntil === "Double" &&
      Number.isFinite(stats.cooldownUntil) &&
      stats.cooldownUntil > 0 &&
      ts >= stats.cooldownUntil
    val disabledExpired =
      typeof stats.disabledUntil === "Double" &&
      Number.isFinite(stats.disabledUntil) &&
      stats.disabledUntil > 0 &&
      ts >= stats.disabledUntil

    if (cooldownExpired) {
      stats.cooldownUntil = Nothing?
      profileMutated = true
    }
    if (disabledExpired) {
      stats.disabledUntil = Nothing?
      stats.disabledReason = Nothing?
      profileMutated = true
    }

    // Reset error counters when ALL cooldowns have expired so the profile gets
    // a fair retry window. Preserves lastFailureAt for the failureWindowMs
    // decay check in computeNextProfileUsageStats.
    if (profileMutated && !resolveProfileUnusableUntil(stats)) {
      stats.errorCount = 0
      stats.failureCounts = Nothing?
    }

    if (profileMutated) {
      usageStats[profileId] = stats
      mutated = true
    }
  }

  return mutated
}

/**
 * Mark a profile as successfully used. Resets error count and updates lastUsed.
 * Uses store lock to avoid overwriting concurrent usage updates.
 */
suspend fun markAuthProfileUsed(params: {
  store: AuthProfileStore
  profileId: String
  agentDir?: String
}): Promise<Unit> {
  val { store, profileId, agentDir } = params
  val updated = await updateAuthProfileStoreWithLock({
    agentDir,
    updater: (freshStore) => {
      if (!freshStore.profiles[profileId]) {
        return false
      }
      updateUsageStatsEntry(freshStore, profileId, (existing) =>
        resetUsageStats(existing, { lastUsed: Date.now() }),
      )
      return true
    },
  })
  if (updated) {
    store.usageStats = updated.usageStats
    return
  }
  if (!store.profiles[profileId]) {
    return
  }

  updateUsageStatsEntry(store, profileId, (existing) =>
    resetUsageStats(existing, { lastUsed: Date.now() }),
  )
  saveAuthProfileStore(store, agentDir)
}

fun calculateAuthProfileCooldownMs(errorCount: Double): Double {
  val normalized = Math.max(1, errorCount)
  return Math.min(
    60 * 60 * 1000, // 1 hour max
    60 * 1000 * 5 ** Math.min(normalized - 1, 3),
  )
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ResolvedAuthCooldownConfig.
typealias ResolvedAuthCooldownConfig = Any?
/*
type ResolvedAuthCooldownConfig = {
  billingBackoffMs: number;
  billingMaxMs: number;
  failureWindowMs: number;
};
*/

fun resolveAuthCooldownConfig(params: {
  cfg?: OpenClawConfig
  providerId: String
}): ResolvedAuthCooldownConfig {
  val defaults = {
    billingBackoffHours: 5,
    billingMaxHours: 24,
    failureWindowHours: 24,
  } /* as const */

  val resolveHours = { value: Any?, fallback: Double ->
    typeof value === "Double" && Number.isFinite(value) && value > 0 ? value : fallback

  val cooldowns = params.cfg?.auth?.cooldowns
  val billingOverride = { ( -> {
    val map = cooldowns?.billingBackoffHoursByProvider
    if (!map) {
      return Nothing?
    }
    for (const [key, value] of Object.entries(map)) {
      if (normalizeProviderId(key) === params.providerId) {
        return value
      }
    }
    return Nothing?
  })()

  val billingBackoffHours = resolveHours(
    billingOverride ?? cooldowns?.billingBackoffHours,
    defaults.billingBackoffHours,
  )
  val billingMaxHours = resolveHours(cooldowns?.billingMaxHours, defaults.billingMaxHours)
  val failureWindowHours = resolveHours(
    cooldowns?.failureWindowHours,
    defaults.failureWindowHours,
  )

  return {
    billingBackoffMs: billingBackoffHours * 60 * 60 * 1000,
    billingMaxMs: billingMaxHours * 60 * 60 * 1000,
    failureWindowMs: failureWindowHours * 60 * 60 * 1000,
  }
}

fun calculateAuthProfileBillingDisableMsWithConfig(params: {
  errorCount: Double
  baseMs: Double
  maxMs: Double
}): Double {
  val normalized = Math.max(1, params.errorCount)
  val baseMs = Math.max(60_000, params.baseMs)
  val maxMs = Math.max(baseMs, params.maxMs)
  val exponent = Math.min(normalized - 1, 10)
  val raw = baseMs * 2 ** exponent
  return Math.min(maxMs, raw)
}

fun resolveProfileUnusableUntilForDisplay(
  store: AuthProfileStore,
  profileId: String,
): Double | Nothing? {
  if (isAuthCooldownBypassedForProvider(store.profiles[profileId]?.provider)) {
    return Nothing?
  }
  val stats = store.usageStats?.[profileId]
  if (!stats) {
    return Nothing?
  }
  return resolveProfileUnusableUntil(stats)
}

fun resetUsageStats(
  existing: ProfileUsageStats | Nothing?,
  overrides?: Partial<ProfileUsageStats>,
): ProfileUsageStats {
  return {
    ...existing,
    errorCount: 0,
    cooldownUntil: Nothing?,
    disabledUntil: Nothing?,
    disabledReason: Nothing?,
    failureCounts: Nothing?,
    ...overrides,
  }
}

fun updateUsageStatsEntry(
  store: AuthProfileStore,
  profileId: String,
  updater: (existing: ProfileUsageStats | Nothing?) => ProfileUsageStats,
): Unit {
  store.usageStats = store.usageStats ?? {}
  store.usageStats[profileId] = updater(store.usageStats[profileId])
}

fun keepActiveWindowOrRecompute(params: {
  existingUntil: Double | Nothing?
  now: Double
  recomputedUntil: Double
}): Double {
  val { existingUntil, now, recomputedUntil } = params
  val hasActiveWindow =
    typeof existingUntil === "Double" && Number.isFinite(existingUntil) && existingUntil > now
  return hasActiveWindow ? existingUntil : recomputedUntil
}

fun computeNextProfileUsageStats(params: {
  existing: ProfileUsageStats
  now: Double
  reason: AuthProfileFailureReason
  cfgResolved: ResolvedAuthCooldownConfig
}): ProfileUsageStats {
  val windowMs = params.cfgResolved.failureWindowMs
  val windowExpired =
    typeof params.existing.lastFailureAt === "Double" &&
    params.existing.lastFailureAt > 0 &&
    params.now - params.existing.lastFailureAt > windowMs

  // If the previous cooldown has already expired, reset error counters so the
  // profile gets a fresh backoff window. clearExpiredCooldowns() does this
  // in-memory during profile ordering, but the on-disk state may still carry
  // the old counters when the lock-based updater reads a fresh store. Without
  // this check, stale error counts from an expired cooldown cause the next
  // failure to escalate to a much longer cooldown (e.g. 1 min → 25 min).
  val unusableUntil = resolveProfileUnusableUntil(params.existing)
  val previousCooldownExpired = typeof unusableUntil === "Double" && params.now >= unusableUntil

  val shouldResetCounters = windowExpired || previousCooldownExpired
  val baseErrorCount = shouldResetCounters ? 0 : (params.existing.errorCount ?? 0)
  val nextErrorCount = baseErrorCount + 1
  val failureCounts = shouldResetCounters ? {} : { ...params.existing.failureCounts }
  failureCounts[params.reason] = (failureCounts[params.reason] ?? 0) + 1

  val updatedStats: ProfileUsageStats = {
    ...params.existing,
    errorCount: nextErrorCount,
    failureCounts,
    lastFailureAt: params.now,
  }

  if (params.reason === "billing" || params.reason === "auth_permanent") {
    val billingCount = failureCounts[params.reason] ?? 1
    val backoffMs = calculateAuthProfileBillingDisableMsWithConfig({
      errorCount: billingCount,
      baseMs: params.cfgResolved.billingBackoffMs,
      maxMs: params.cfgResolved.billingMaxMs,
    })
    // Keep active disable windows immutable so retries within the window cannot
    // extend recovery time indefinitely.
    updatedStats.disabledUntil = keepActiveWindowOrRecompute({
      existingUntil: params.existing.disabledUntil,
      now: params.now,
      recomputedUntil: params.now + backoffMs,
    })
    updatedStats.disabledReason = params.reason
  } else {
    val backoffMs = calculateAuthProfileCooldownMs(nextErrorCount)
    // Keep active cooldown windows immutable so retries within the window
    // cannot push recovery further out.
    updatedStats.cooldownUntil = keepActiveWindowOrRecompute({
      existingUntil: params.existing.cooldownUntil,
      now: params.now,
      recomputedUntil: params.now + backoffMs,
    })
  }

  return updatedStats
}

/**
 * Mark a profile as failed for a specific reason. Billing and permanent-auth
 * failures are treated as "disabled" (longer backoff) vs the regular cooldown
 * window.
 */
suspend fun markAuthProfileFailure(params: {
  store: AuthProfileStore
  profileId: String
  reason: AuthProfileFailureReason
  cfg?: OpenClawConfig
  agentDir?: String
  runId?: String
}): Promise<Unit> {
  val { store, profileId, reason, agentDir, cfg, runId } = params
  val profile = store.profiles[profileId]
  if (!profile || isAuthCooldownBypassedForProvider(profile.provider)) {
    return
  }
  var nextStats: ProfileUsageStats | Nothing?
  var previousStats: ProfileUsageStats | Nothing?
  var updateTime = 0
  val updated = await updateAuthProfileStoreWithLock({
    agentDir,
    updater: (freshStore) => {
      val profile = freshStore.profiles[profileId]
      if (!profile || isAuthCooldownBypassedForProvider(profile.provider)) {
        return false
      }
      val now = Date.now()
      val providerKey = normalizeProviderId(profile.provider)
      val cfgResolved = resolveAuthCooldownConfig({
        cfg,
        providerId: providerKey,
      })

      previousStats = freshStore.usageStats?.[profileId]
      updateTime = now
      val computed = computeNextProfileUsageStats({
        existing: previousStats ?? {},
        now,
        reason,
        cfgResolved,
      })
      nextStats = computed
      updateUsageStatsEntry(freshStore, profileId, () => computed)
      return true
    },
  })
  if (updated) {
    store.usageStats = updated.usageStats
    if (nextStats) {
      logAuthProfileFailureStateChange({
        runId,
        profileId,
        provider: profile.provider,
        reason,
        previous: previousStats,
        next: nextStats,
        now: updateTime,
      })
    }
    return
  }
  if (!store.profiles[profileId]) {
    return
  }

  val now = Date.now()
  val providerKey = normalizeProviderId(store.profiles[profileId]?.provider ?? "")
  val cfgResolved = resolveAuthCooldownConfig({
    cfg,
    providerId: providerKey,
  })

  previousStats = store.usageStats?.[profileId]
  val computed = computeNextProfileUsageStats({
    existing: previousStats ?? {},
    now,
    reason,
    cfgResolved,
  })
  nextStats = computed
  updateUsageStatsEntry(store, profileId, () => computed)
  saveAuthProfileStore(store, agentDir)
  logAuthProfileFailureStateChange({
    runId,
    profileId,
    provider: store.profiles[profileId]?.provider ?? profile.provider,
    reason,
    previous: previousStats,
    next: nextStats,
    now,
  })
}

/**
 * Mark a profile as transiently failed. Applies exponential backoff cooldown.
 * Cooldown times: 1min, 5min, 25min, max 1 hour.
 * Uses store lock to avoid overwriting concurrent usage updates.
 */
suspend fun markAuthProfileCooldown(params: {
  store: AuthProfileStore
  profileId: String
  agentDir?: String
  runId?: String
}): Promise<Unit> {
  await markAuthProfileFailure({
    store: params.store,
    profileId: params.profileId,
    reason: "Any?",
    agentDir: params.agentDir,
    runId: params.runId,
  })
}

/**
 * Clear cooldown for a profile (e.g., manual reset).
 * Uses store lock to avoid overwriting concurrent usage updates.
 */
suspend fun clearAuthProfileCooldown(params: {
  store: AuthProfileStore
  profileId: String
  agentDir?: String
}): Promise<Unit> {
  val { store, profileId, agentDir } = params
  val updated = await updateAuthProfileStoreWithLock({
    agentDir,
    updater: (freshStore) => {
      if (!freshStore.usageStats?.[profileId]) {
        return false
      }

      updateUsageStatsEntry(freshStore, profileId, (existing) => resetUsageStats(existing))
      return true
    },
  })
  if (updated) {
    store.usageStats = updated.usageStats
    return
  }
  if (!store.usageStats?.[profileId]) {
    return
  }

  updateUsageStatsEntry(store, profileId, (existing) => resetUsageStats(existing))
  saveAuthProfileStore(store, agentDir)
}
