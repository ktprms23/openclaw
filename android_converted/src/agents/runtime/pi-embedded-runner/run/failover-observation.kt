@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/run/failover-observation.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

data class FailoverDecisionLoggerInput(
    val stage: Any /* "prompt" | "assistant" */ = TODO("Port default"),
    val decision: Any /* "rotate_profile" | "fallback_model" | "surface_error" */ = TODO("Port default"),
    val runId: String? = TODO("Port default"),
    val rawError: String? = TODO("Port default"),
    val failoverReason: Any /* FailoverReason | null */ = TODO("Port default"),
    val profileFailureReason: Any /* AuthProfileFailureReason | null */? = TODO("Port default"),
    val provider: String = TODO("Port default"),
    val model: String = TODO("Port default"),
    val profileId: String? = TODO("Port default"),
    val fallbackConfigured: Boolean = TODO("Port default"),
    val timedOut: Boolean? = TODO("Port default"),
    val aborted: Boolean? = TODO("Port default"),
    val status: Double? = TODO("Port default")
)

typealias FailoverDecisionLoggerBase = Any /* Omit<FailoverDecisionLoggerInput, "decision" | "status"> */

fun normalizeFailoverDecisionObservationBase(/* parameters preserved from TypeScript source */): FailoverDecisionLoggerBase {
    TODO("Port runtime function normalizeFailoverDecisionObservationBase from src/agents/pi-embedded-runner/run/failover-observation.ts")
}

fun createFailoverDecisionLogger(/* parameters preserved from TypeScript source */): ( decision: FailoverDecisionLoggerInput["decision"], extra?: Pick<FailoverDecisionLoggerInput, "status">, ) => void {
    TODO("Port runtime function createFailoverDecisionLogger from src/agents/pi-embedded-runner/run/failover-observation.ts")
}

/*
Original imports retained for mapping context:
import { redactIdentifier } from "../../../logging/redact-identifier.js";
import type { AuthProfileFailureReason } from "../../auth-profiles.js";
import {
import type { FailoverReason } from "../../pi-embedded-helpers.js";
import { log } from "../logger.js";
*/

/*
Original TypeScript reference:
import { redactIdentifier } from "../../../logging/redact-identifier.js";
import type { AuthProfileFailureReason } from "../../auth-profiles.js";
import {
  buildApiErrorObservationFields,
  sanitizeForConsole,
} from "../../pi-embedded-error-observation.js";
import type { FailoverReason } from "../../pi-embedded-helpers.js";
import { log } from "../logger.js";

export type FailoverDecisionLoggerInput = {
  stage: "prompt" | "assistant";
  decision: "rotate_profile" | "fallback_model" | "surface_error";
  runId?: string;
  rawError?: string;
  failoverReason: FailoverReason | null;
  profileFailureReason?: AuthProfileFailureReason | null;
  provider: string;
  model: string;
  profileId?: string;
  fallbackConfigured: boolean;
  timedOut?: boolean;
  aborted?: boolean;
  status?: number;
};

export type FailoverDecisionLoggerBase = Omit<FailoverDecisionLoggerInput, "decision" | "status">;

export function normalizeFailoverDecisionObservationBase(
  base: FailoverDecisionLoggerBase,
): FailoverDecisionLoggerBase {
  return {
    ...base,
    failoverReason: base.failoverReason ?? (base.timedOut ? "timeout" : null),
    profileFailureReason: base.profileFailureReason ?? (base.timedOut ? "timeout" : null),
  };
}

export function createFailoverDecisionLogger(
  base: FailoverDecisionLoggerBase,
): (
  decision: FailoverDecisionLoggerInput["decision"],
  extra?: Pick<FailoverDecisionLoggerInput, "status">,
) => void {
  const normalizedBase = normalizeFailoverDecisionObservationBase(base);
  const safeProfileId = normalizedBase.profileId
    ? redactIdentifier(normalizedBase.profileId, { len: 12 })
    : undefined;
  const safeRunId = sanitizeForConsole(normalizedBase.runId) ?? "-";
  const safeProvider = sanitizeForConsole(normalizedBase.provider) ?? "-";
  const safeModel = sanitizeForConsole(normalizedBase.model) ?? "-";
  const profileText = safeProfileId ?? "-";
  const reasonText = normalizedBase.failoverReason ?? "none";
  return (decision, extra) => {
    const observedError = buildApiErrorObservationFields(normalizedBase.rawError);
    log.warn("embedded run failover decision", {
      event: "embedded_run_failover_decision",
      tags: ["error_handling", "failover", normalizedBase.stage, decision],
      runId: normalizedBase.runId,
      stage: normalizedBase.stage,
      decision,
      failoverReason: normalizedBase.failoverReason,
      profileFailureReason: normalizedBase.profileFailureReason,
      provider: normalizedBase.provider,
      model: normalizedBase.model,
      profileId: safeProfileId,
      fallbackConfigured: normalizedBase.fallbackConfigured,
      timedOut: normalizedBase.timedOut,
      aborted: normalizedBase.aborted,
      status: extra?.status,
      ...observedError,
      consoleMessage:
        `embedded run failover decision: runId=${safeRunId} stage=${normalizedBase.stage} decision=${decision} ` +
        `reason=${reasonText} provider=${safeProvider}/${safeModel} profile=${profileText}`,
    });
  };
}

*/
