@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-policy.conformance.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

/**
 * Conformance snapshot for tool policy.
 *
 * Security note:
 * - This is static, build-time information (no runtime I/O, no network exposure).
 * - Intended for CI/tools to detect drift between the implementation policy and
 *   the formal models/extractors.
 */

// TODO(port-deps): import { TOOL_GROUPS } from "./tool-policy.js";

// Tool name aliases are intentionally not exported from tool-policy today.
// Keep the conformance snapshot focused on exported policy constants.

val TOOL_POLICY_CONFORMANCE = {
  toolGroups: TOOL_GROUPS,
} as const;
