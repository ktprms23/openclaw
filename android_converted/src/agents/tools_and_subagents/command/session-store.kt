package agents.tools_and_subagents.command

// Converted from src/agents/command/session-store.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  mergeSessionEntry,
  setSessionRuntimeModel,
  type SessionEntry,
  updateSessionStore,
} from "../../config/sessions.js"
// TODO: TypeScript import retained for manual wiring: import { setCliSessionId } from "../cli-session.js";
// TODO: TypeScript import retained for manual wiring: import { resolveContextTokensForModel } from "../context.js";
// TODO: TypeScript import retained for manual wiring: import { DEFAULT_CONTEXT_TOKENS } from "../defaults.js";
// TODO: TypeScript import retained for manual wiring: import { isCliProvider } from "../model-selection.js";
// TODO: TypeScript import retained for manual wiring: import { deriveSessionTotalTokens, hasNonzeroUsage } from "../usage.js";

typealias RunResult = Awaited<ReturnType<(typeof import("../pi-embedded.js"))["runEmbeddedPiAgent"]>>

suspend fun updateSessionStoreAfterAgentRun(params: {
  cfg: OpenClawConfig
  contextTokensOverride?: Double
  sessionId: String
  sessionKey: String
  storePath: String
  sessionStore: MutableMap<String, SessionEntry>
  defaultProvider: String
  defaultModel: String
  fallbackProvider?: String
  fallbackModel?: String
  result: RunResult
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
  } = params

  val usage = result.meta.agentMeta?.usage
  val promptTokens = result.meta.agentMeta?.promptTokens
  val compactionsThisRun = Math.max(0, result.meta.agentMeta?.compactionCount ?: 0)
  val modelUsed = result.meta.agentMeta?.model ?: fallbackModel ?: defaultModel
  val providerUsed = result.meta.agentMeta?.provider ?: fallbackProvider ?: defaultProvider
  val contextTokens =
    resolveContextTokensForModel({
      cfg,
      provider: providerUsed,
      model: modelUsed,
      contextTokensOverride: params.contextTokensOverride,
      fallbackContextTokens: DEFAULT_CONTEXT_TOKENS,
    }) ?: DEFAULT_CONTEXT_TOKENS

  val entry = sessionStore[sessionKey] ?: {
    sessionId,
    updatedAt: Date.now(),
  }
  val next: SessionEntry = {
    ...entry,
    sessionId,
    updatedAt: Date.now(),
    contextTokens,
  }
  setSessionRuntimeModel(next, {
    provider: providerUsed,
    model: modelUsed,
  })
  if (isCliProvider(providerUsed, cfg)) {
    val cliSessionId = result.meta.agentMeta?.sessionId?.trim()
    if (cliSessionId) {
      setCliSessionId(next, providerUsed, cliSessionId)
    }
  }
  next.abortedLastRun = result.meta.aborted ?: false
  if (result.meta.systemPromptReport) {
    next.systemPromptReport = result.meta.systemPromptReport
  }
  if (hasNonzeroUsage(usage)) {
    val input = usage.input ?: 0
    val output = usage.output ?: 0
    val totalTokens = deriveSessionTotalTokens({
      usage,
      contextTokens,
      promptTokens,
    })
    next.inputTokens = input
    next.outputTokens = output
    if (totalTokens is Double && Number.isFinite(totalTokens) && totalTokens > 0) {
      next.totalTokens = totalTokens
      next.totalTokensFresh = true
    } else {
      next.totalTokens = null
      next.totalTokensFresh = false
    }
    next.cacheRead = usage.cacheRead ?: 0
    next.cacheWrite = usage.cacheWrite ?: 0
  }
  if (compactionsThisRun > 0) {
    next.compactionCount = (entry.compactionCount ?: 0) + compactionsThisRun
  }
  val persisted = await updateSessionStore(storePath, (store) {
    val merged = mergeSessionEntry(store[sessionKey], next)
    store[sessionKey] = merged
    return merged
  })
  sessionStore[sessionKey] = persisted
}
