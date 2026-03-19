package agents.platform_support

// Source: src/agents/byteplus-models.ts
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

val BYTEPLUS_BASE_URL = "https://ark.ap-southeast.bytepluses.com/api/v3"
val BYTEPLUS_CODING_BASE_URL = "https://ark.ap-southeast.bytepluses.com/api/coding/v3"
val BYTEPLUS_DEFAULT_MODEL_ID = "seed-1-8-251228"
val BYTEPLUS_CODING_DEFAULT_MODEL_ID = "ark-code-latest"
val BYTEPLUS_DEFAULT_MODEL_REF = `byteplus/${BYTEPLUS_DEFAULT_MODEL_ID}`

// BytePlus pricing (approximate, adjust based on actual pricing)
val BYTEPLUS_DEFAULT_COST = {
  input: 0.0001, // $0.0001 per 1K tokens
  output: 0.0002, // $0.0002 per 1K tokens
  cacheRead: 0,
  cacheWrite: 0,
}

/**
 * Complete catalog of BytePlus ARK models.
 *
 * BytePlus ARK provides access to various models
 * through the ARK API. Authentication requires a BYTEPLUS_API_KEY.
 */
val BYTEPLUS_MODEL_CATALOG = [
  {
    id: "seed-1-8-251228",
    name: "Seed 1.8",
    reasoning: false,
    input: ["text", "image"] /* as const */,
    contextWindow: 256000,
    maxTokens: 4096,
  },
  VOLC_MODEL_KIMI_K2_5,
  VOLC_MODEL_GLM_4_7,
] /* as const */

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for BytePlusCatalogEntry.
typealias BytePlusCatalogEntry = (typeof BYTEPLUS_MODEL_CATALOG)[Double]
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for BytePlusCodingCatalogEntry.
typealias BytePlusCodingCatalogEntry = (typeof BYTEPLUS_CODING_MODEL_CATALOG)[Double]

fun buildBytePlusModelDefinition(
  entry: BytePlusCatalogEntry | BytePlusCodingCatalogEntry,
): ModelDefinitionConfig {
  return buildVolcModelDefinition(entry, BYTEPLUS_DEFAULT_COST)
}

val BYTEPLUS_CODING_MODEL_CATALOG = VOLC_SHARED_CODING_MODEL_CATALOG
