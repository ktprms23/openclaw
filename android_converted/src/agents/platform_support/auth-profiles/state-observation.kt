package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/state-observation.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { redactIdentifier } from "../../logging/redact-identifier.js";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import { sanitizeForConsole } from "../pi-embedded-error-observation.js";
// TODO(openclaw-kotlin-port): import type { AuthProfileFailureReason, ProfileUsageStats } from "./types.js";

val observationLog = createSubsystemLogger("agent/embedded")

fun logAuthProfileFailureStateChange(params: {
  runId?: String
  profileId: String
  provider: String
  reason: AuthProfileFailureReason
  previous: ProfileUsageStats | Nothing?
  next: ProfileUsageStats
  now: Double
}): Unit {
  val windowType =
    params.reason === "billing" || params.reason === "auth_permanent" ? "disabled" : "cooldown"
  val previousCooldownUntil = params.previous?.cooldownUntil
  val previousDisabledUntil = params.previous?.disabledUntil
  // Active cooldown/disable windows are intentionally immutable log whether this
  // update reused the existing window instead of extending it.
  val windowReused =
    windowType === "disabled"
      ? typeof previousDisabledUntil === "Double" &&
        Number.isFinite(previousDisabledUntil) &&
        previousDisabledUntil > params.now &&
        previousDisabledUntil === params.next.disabledUntil
      : typeof previousCooldownUntil === "Double" &&
        Number.isFinite(previousCooldownUntil) &&
        previousCooldownUntil > params.now &&
        previousCooldownUntil === params.next.cooldownUntil
  val safeProfileId = redactIdentifier(params.profileId, { len: 12 })
  val safeRunId = sanitizeForConsole(params.runId) ?? "-"
  val safeProvider = sanitizeForConsole(params.provider) ?? "-"

  observationLog.warn("auth profile failure state updated", {
    event: "auth_profile_failure_state_updated",
    tags: ["error_handling", "auth_profiles", windowType],
    runId: params.runId,
    profileId: safeProfileId,
    provider: params.provider,
    reason: params.reason,
    windowType,
    windowReused,
    previousErrorCount: params.previous?.errorCount,
    errorCount: params.next.errorCount,
    previousCooldownUntil,
    cooldownUntil: params.next.cooldownUntil,
    previousDisabledUntil,
    disabledUntil: params.next.disabledUntil,
    previousDisabledReason: params.previous?.disabledReason,
    disabledReason: params.next.disabledReason,
    failureCounts: params.next.failureCounts,
    consoleMessage:
      `auth profile failure state updated: runId=${safeRunId} profile=${safeProfileId} provider=${safeProvider} ` +
      `reason=${params.reason} window=${windowType} reused=${String(windowReused)}`,
  })
}
