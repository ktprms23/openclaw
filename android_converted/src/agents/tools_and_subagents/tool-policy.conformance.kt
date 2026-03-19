package agents.tools_and_subagents

// Converted from src/agents/tool-policy.conformance.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
/**
 * Conformance snapshot for tool policy.
 *
 * Security note:
 * - This is static, build-time information (no runtime I/O, no network exposure).
 * - Intended for CI/tools to detect drift between the implementation policy and
 *   the formal models/extractors.
 */

// TODO: TypeScript import retained for manual wiring: import { TOOL_GROUPS } from "./tool-policy.js";

// Tool name aliases are intentionally not exported from tool-policy today.
// Keep the conformance snapshot focused on exported policy constants.

val TOOL_POLICY_CONFORMANCE = {
  toolGroups: TOOL_GROUPS,
} as /* TODO */ val
