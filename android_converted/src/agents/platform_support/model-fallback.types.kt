package agents.platform_support

// Source: src/agents/model-fallback.types.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { FailoverReason } from "./pi-embedded-helpers.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ModelCandidate.
typealias ModelCandidate = Any?
/*
export type ModelCandidate = {
  provider: string;
  model: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for FallbackAttempt.
typealias FallbackAttempt = Any?
/*
export type FallbackAttempt = {
  provider: string;
  model: string;
  error: string;
  reason?: FailoverReason;
  status?: number;
  code?: string;
};
*/
