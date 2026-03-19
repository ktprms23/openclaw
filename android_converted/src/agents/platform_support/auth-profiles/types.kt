package agents.platform_support.auth_profiles

// Source: src/agents/auth-profiles/types.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OAuthCredentials } from "@mariozechner/pi-ai";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import type { SecretRef } from "../../config/types.secrets.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ApiKeyCredential.
typealias ApiKeyCredential = Any?
/*
export type ApiKeyCredential = {
  type: "api_key";
  provider: string;
  key?: string;
  keyRef?: SecretRef;
  email?: string;
  /** Optional provider-specific metadata (e.g., account IDs, gateway IDs). */
  metadata?: Record<string, string>;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for TokenCredential.
typealias TokenCredential = Any?
/*
export type TokenCredential = {
  /**
   * Static bearer-style token (often OAuth access token / PAT).
   * Not refreshable by OpenClaw (unlike `type: "oauth"`).
   */
  type: "token";
  provider: string;
  token?: string;
  tokenRef?: SecretRef;
  /** Optional expiry timestamp (ms since epoch). */
  expires?: number;
  email?: string;
};
*/

typealias OAuthCredential = OAuthCredentials & {
  type: "oauth"
  provider: String
  clientId?: String
  email?: String
}

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for AuthProfileCredential.
typealias AuthProfileCredential = ApiKeyCredential | TokenCredential | OAuthCredential

typealias AuthProfileFailureReason =
  | "auth"
  | "auth_permanent"
  | "format"
  | "overloaded"
  | "rate_limit"
  | "billing"
  | "timeout"
  | "model_not_found"
  | "session_expired"
  | "Any?"

/** Per-profile usage statistics for round-robin and cooldown tracking */
// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ProfileUsageStats.
typealias ProfileUsageStats = Any?
/*
export type ProfileUsageStats = {
  lastUsed?: number;
  cooldownUntil?: number;
  disabledUntil?: number;
  disabledReason?: AuthProfileFailureReason;
  errorCount?: number;
  failureCounts?: Partial<Record<AuthProfileFailureReason, number>>;
  lastFailureAt?: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for AuthProfileStore.
typealias AuthProfileStore = Any?
/*
export type AuthProfileStore = {
  version: number;
  profiles: Record<string, AuthProfileCredential>;
  /**
   * Optional per-agent preferred profile order overrides.
   * This lets you lock/override auth rotation for a specific agent without
   * changing the global config.
   */
  order?: Record<string, string[]>;
  lastGood?: Record<string, string>;
  /** Usage statistics per profile for round-robin rotation */
  usageStats?: Record<string, ProfileUsageStats>;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for AuthProfileIdRepairResult.
typealias AuthProfileIdRepairResult = Any?
/*
export type AuthProfileIdRepairResult = {
  config: OpenClawConfig;
  changes: string[];
  migrated: boolean;
  fromProfileId?: string;
  toProfileId?: string;
};
*/
