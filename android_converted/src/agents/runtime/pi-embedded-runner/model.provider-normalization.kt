@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/model.provider-normalization.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun applyBuiltInResolvedProviderTransportNormalization(/* parameters preserved from TypeScript source */): Model<Api> {
    TODO("Port runtime function applyBuiltInResolvedProviderTransportNormalization from src/agents/pi-embedded-runner/model.provider-normalization.ts")
}

fun normalizeResolvedProviderModel(/* parameters preserved from TypeScript source */): Model<Api> {
    TODO("Port runtime function normalizeResolvedProviderModel from src/agents/pi-embedded-runner/model.provider-normalization.ts")
}

/*
Original imports retained for mapping context:
import type { Api, Model } from "@mariozechner/pi-ai";
import { normalizeModelCompat } from "../model-compat.js";
import { normalizeProviderId } from "../model-selection.js";
*/

/*
Original TypeScript reference:
import type { Api, Model } from "@mariozechner/pi-ai";
import { normalizeModelCompat } from "../model-compat.js";
import { normalizeProviderId } from "../model-selection.js";

function isOpenAIApiBaseUrl(baseUrl?: string): boolean {
  const trimmed = baseUrl?.trim();
  if (!trimmed) {
    return false;
  }
  return /^https?:\/\/api\.openai\.com(?:\/v1)?\/?$/i.test(trimmed);
}

function normalizeOpenAITransport(params: { provider: string; model: Model<Api> }): Model<Api> {
  if (normalizeProviderId(params.provider) !== "openai") {
    return params.model;
  }

  const useResponsesTransport =
    params.model.api === "openai-completions" &&
    (!params.model.baseUrl || isOpenAIApiBaseUrl(params.model.baseUrl));

  if (!useResponsesTransport) {
    return params.model;
  }

  return {
    ...params.model,
    api: "openai-responses",
  } as Model<Api>;
}

export function applyBuiltInResolvedProviderTransportNormalization(params: {
  provider: string;
  model: Model<Api>;
}): Model<Api> {
  return normalizeOpenAITransport(params);
}

export function normalizeResolvedProviderModel(params: {
  provider: string;
  model: Model<Api>;
}): Model<Api> {
  return normalizeModelCompat(applyBuiltInResolvedProviderTransportNormalization(params));
}

*/
