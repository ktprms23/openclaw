package agents.platform_support

// Source: src/agents/cloudflare-ai-gateway.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { ModelDefinitionConfig } from "../config/types.js";

val CLOUDFLARE_AI_GATEWAY_PROVIDER_ID = "cloudflare-ai-gateway"
val CLOUDFLARE_AI_GATEWAY_DEFAULT_MODEL_ID = "claude-sonnet-4-5"
val CLOUDFLARE_AI_GATEWAY_DEFAULT_MODEL_REF = `${CLOUDFLARE_AI_GATEWAY_PROVIDER_ID}/${CLOUDFLARE_AI_GATEWAY_DEFAULT_MODEL_ID}`

val CLOUDFLARE_AI_GATEWAY_DEFAULT_CONTEXT_WINDOW = 200_000
val CLOUDFLARE_AI_GATEWAY_DEFAULT_MAX_TOKENS = 64_000
val CLOUDFLARE_AI_GATEWAY_DEFAULT_COST = {
  input: 3,
  output: 15,
  cacheRead: 0.3,
  cacheWrite: 3.75,
}

fun buildCloudflareAiGatewayModelDefinition(params?: {
  id?: String
  name?: String
  reasoning?: Boolean
  input?: List<"text" | "image">
}): ModelDefinitionConfig {
  val id = params?.id?.trim() || CLOUDFLARE_AI_GATEWAY_DEFAULT_MODEL_ID
  return {
    id,
    name: params?.name ?? "Claude Sonnet 4.5",
    reasoning: params?.reasoning ?? true,
    input: params?.input ?? ["text", "image"],
    cost: CLOUDFLARE_AI_GATEWAY_DEFAULT_COST,
    contextWindow: CLOUDFLARE_AI_GATEWAY_DEFAULT_CONTEXT_WINDOW,
    maxTokens: CLOUDFLARE_AI_GATEWAY_DEFAULT_MAX_TOKENS,
  }
}

fun resolveCloudflareAiGatewayBaseUrl(params: {
  accountId: String
  gatewayId: String
}): String {
  val accountId = params.accountId.trim()
  val gatewayId = params.gatewayId.trim()
  if (!accountId || !gatewayId) {
    return ""
  }
  return `https://gateway.ai.cloudflare.com/v1/${accountId}/${gatewayId}/anthropic`
}
