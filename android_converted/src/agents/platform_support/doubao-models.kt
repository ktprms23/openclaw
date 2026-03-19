package agents.platform_support

// Source: src/agents/doubao-models.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildVolcModelDefinition,
// TODO(openclaw-kotlin-port):   VOLC_MODEL_GLM_4_7,
// TODO(openclaw-kotlin-port):   VOLC_MODEL_KIMI_K2_5,
// TODO(openclaw-kotlin-port):   VOLC_SHARED_CODING_MODEL_CATALOG,
// TODO(openclaw-kotlin-port): } from "./volc-models.shared.js";

val DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
val DOUBAO_CODING_BASE_URL = "https://ark.cn-beijing.volces.com/api/coding/v3"
val DOUBAO_DEFAULT_MODEL_ID = "doubao-seed-1-8-251228"
val DOUBAO_CODING_DEFAULT_MODEL_ID = "ark-code-latest"
val DOUBAO_DEFAULT_MODEL_REF = `volcengine/${DOUBAO_DEFAULT_MODEL_ID}`

// Volcano Engine Doubao pricing (approximate, adjust based on actual pricing)
val DOUBAO_DEFAULT_COST = {
  input: 0.0001, // ¥0.0001 per 1K tokens
  output: 0.0002, // ¥0.0002 per 1K tokens
  cacheRead: 0,
  cacheWrite: 0,
}

/**
 * Complete catalog of Volcano Engine models.
 *
 * Volcano Engine provides access to models
 * through the API. Authentication requires a Volcano Engine API Key.
 */
val DOUBAO_MODEL_CATALOG = [
  {
    id: "doubao-seed-code-preview-251028",
    name: "doubao-seed-code-preview-251028",
    reasoning: false,
    input: ["text", "image"] /* as const */,
    contextWindow: 256000,
    maxTokens: 4096,
  },
  {
    id: "doubao-seed-1-8-251228",
    name: "Doubao Seed 1.8",
    reasoning: false,
    input: ["text", "image"] /* as const */,
    contextWindow: 256000,
    maxTokens: 4096,
  },
  VOLC_MODEL_KIMI_K2_5,
  VOLC_MODEL_GLM_4_7,
  {
    id: "deepseek-v3-2-251201",
    name: "DeepSeek V3.2",
    reasoning: false,
    input: ["text", "image"] /* as const */,
    contextWindow: 128000,
    maxTokens: 4096,
  },
] /* as const */

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for DoubaoCatalogEntry.
typealias DoubaoCatalogEntry = (typeof DOUBAO_MODEL_CATALOG)[Double]
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for DoubaoCodingCatalogEntry.
typealias DoubaoCodingCatalogEntry = (typeof DOUBAO_CODING_MODEL_CATALOG)[Double]

fun buildDoubaoModelDefinition(
  entry: DoubaoCatalogEntry | DoubaoCodingCatalogEntry,
): ModelDefinitionConfig {
  return buildVolcModelDefinition(entry, DOUBAO_DEFAULT_COST)
}

val DOUBAO_CODING_MODEL_CATALOG = [
  ...VOLC_SHARED_CODING_MODEL_CATALOG,
  {
    id: "doubao-seed-code-preview-251028",
    name: "Doubao Seed Code Preview",
    reasoning: false,
    input: ["text"] /* as const */,
    contextWindow: 256000,
    maxTokens: 4096,
  },
] /* as const */
