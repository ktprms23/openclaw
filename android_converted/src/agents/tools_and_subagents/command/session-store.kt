@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/command/session-store.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): mergeSessionEntry,
// TODO(port-deps): setSessionRuntimeModel,
// TODO(port-deps): type SessionEntry,
// TODO(port-deps): updateSessionStore,
// TODO(port-deps): } from "../../config/sessions.js";
// TODO(port-deps): import { setCliSessionId } from "../cli-session.js";
// TODO(port-deps): import { resolveContextTokensForModel } from "../context.js";
// TODO(port-deps): import { DEFAULT_CONTEXT_TOKENS } from "../defaults.js";
// TODO(port-deps): import { isCliProvider } from "../model-selection.js";
// TODO(port-deps): import { deriveSessionTotalTokens, hasNonzeroUsage } from "../usage.js";

typealias RunResult = Awaited<ReturnType<(typeof import("../pi-embedded.js"))["runEmbeddedPiAgent"]>>

suspend fun updateSessionStoreAfterAgentRun(params: {
  cfg: OpenClawConfig;
  contextTokensOverride?: number;
  sessionId: string;
  sessionKey: string;
  storePath: string;
  sessionStore: Record<string, SessionEntry>;
  defaultProvider: string;
  defaultModel: string;
  fallbackProvider?: string;
  fallbackModel?: string;
  result: RunResult;
}) {
  val {
    cfg,
    sessionId,
    sessionKey,
    storePath,
    sessionStore,
    defaultProvider,
    defaultModel,
    fallbackProvider,
    fallbackModel,
    result,
  } = params;

  val usage = result.meta.agentMeta?.usage;
  val promptTokens = result.meta.agentMeta?.promptTokens;
  val compactionsThisRun = Math.max(0, result.meta.agentMeta?.compactionCount ?: 0);
  val modelUsed = result.meta.agentMeta?.model ?: fallbackModel ?: defaultModel;
  val providerUsed = result.meta.agentMeta?.provider ?: fallbackProvider ?: defaultProvider;
  val contextTokens =
    resolveContextTokensForModel({
      cfg,
      provider: providerUsed,
      model: modelUsed,
      contextTokensOverride: params.contextTokensOverride,
      fallbackContextTokens: DEFAULT_CONTEXT_TOKENS,
    }) ?: DEFAULT_CONTEXT_TOKENS;

  val entry = sessionStore[sessionKey] ?: {
    sessionId,
    updatedAt: Date.now(),
  };
  val next: SessionEntry = {
    ...entry,
    sessionId,
    updatedAt: Date.now(),
    contextTokens,
  };
  setSessionRuntimeModel(next, {
    provider: providerUsed,
    model: modelUsed,
  });
  if (isCliProvider(providerUsed, cfg)) {
    val cliSessionId = result.meta.agentMeta?.sessionId?.trim();
    if (cliSessionId) {
      setCliSessionId(next, providerUsed, cliSessionId);
    }
  }
  next.abortedLastRun = result.meta.aborted ?: false;
  if (result.meta.systemPromptReport) {
    next.systemPromptReport = result.meta.systemPromptReport;
  }
  if (hasNonzeroUsage(usage)) {
    val input = usage.input ?: 0;
    val output = usage.output ?: 0;
    val totalTokens = deriveSessionTotalTokens({
      usage,
      contextTokens,
      promptTokens,
    });
    next.inputTokens = input;
    next.outputTokens = output;
    if (typeof totalTokens == "number" && Number.isFinite(totalTokens) && totalTokens > 0) {
      next.totalTokens = totalTokens;
      next.totalTokensFresh = true;
    } else {
      next.totalTokens = null;
      next.totalTokensFresh = false;
    }
    next.cacheRead = usage.cacheRead ?: 0;
    next.cacheWrite = usage.cacheWrite ?: 0;
  }
  if (compactionsThisRun > 0) {
    next.compactionCount = (entry.compactionCount ?: 0) + compactionsThisRun;
  }
  val persisted = await updateSessionStore(storePath, (store) => {
    val merged = mergeSessionEntry(store[sessionKey], next);
    store[sessionKey] = merged;
    return merged;
  });
  sessionStore[sessionKey] = persisted;
}
