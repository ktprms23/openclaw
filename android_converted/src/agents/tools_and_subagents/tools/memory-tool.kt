package agents.tools_and_subagents.tools

// Converted from src/agents/tools/memory-tool.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import type { MemoryCitationsMode } from "../../config/types.memory.js";
// TODO: TypeScript import retained for manual wiring: import { resolveMemoryBackendConfig } from "../../memory/backend-config.js";
// TODO: TypeScript import retained for manual wiring: import { getMemorySearchManager } from "../../memory/index.js";
// TODO: TypeScript import retained for manual wiring: import type { MemorySearchResult } from "../../memory/types.js";
// TODO: TypeScript import retained for manual wiring: import { parseAgentSessionKey } from "../../routing/session-key.js";
// TODO: TypeScript import retained for manual wiring: import { resolveSessionAgentId } from "../agent-scope.js";
// TODO: TypeScript import retained for manual wiring: import { resolveMemorySearchConfig } from "../memory-search.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readNumberParam, readStringParam } from "./common.js";

val MemorySearchSchema = Type.Object({
  query: Type.String(),
  maxResults: Type.Optional(Type.Number()),
  minScore: Type.Optional(Type.Number()),
})

val MemoryGetSchema = Type.Object({
  path: Type.String(),
  from: Type.Optional(Type.Number()),
  lines: Type.Optional(Type.Number()),
})

fun resolveMemoryToolContext(options: { config?: OpenClawConfig agentSessionKey?: String }) {
  val cfg = options.config
  if (!cfg) {
    return null
  }
  val agentId = resolveSessionAgentId({
    sessionKey: options.agentSessionKey,
    config: cfg,
  })
  if (!resolveMemorySearchConfig(cfg, agentId)) {
    return null
  }
  return { cfg, agentId }
}

suspend fun getMemoryManagerContext(params: { cfg: OpenClawConfig agentId: String }): Deferred<
  | {
      manager: NonNullable<Awaited<ReturnType<typeof getMemorySearchManager>>["manager"]>
    }
  | {
      error: String?
    }
> {
  val { manager, error } = await getMemorySearchManager({
    cfg: params.cfg,
    agentId: params.agentId,
  })
  return manager ? { manager } : { error }
}

fun createMemoryTool(params: {
  options: {
    config?: OpenClawConfig
    agentSessionKey?: String
  }
  label: String
  name: String
  description: String
  parameters: typeof MemorySearchSchema | typeof MemoryGetSchema
  execute: (ctx: { cfg: OpenClawConfig agentId: String }) -> AnyAgentTool["execute"]
}): AnyAgentTool? {
  val ctx = resolveMemoryToolContext(params.options)
  if (!ctx) {
    return null
  }
  return {
    label: params.label,
    name: params.name,
    description: params.description,
    parameters: params.parameters,
    execute: params.execute(ctx),
  }
}

fun createMemorySearchTool(options: {
  config?: OpenClawConfig
  agentSessionKey?: String
}): AnyAgentTool? {
  return createMemoryTool({
    options,
    label: String /* "Memory Search" */,
    name: String /* "memory_search" */,
    description: String /* "Mandatory recall step: semantically search MEMORY.md + memory/*.md (and optional session transcripts) before answering questions about prior work, decisions, dates, people, preferences, or todos returns top snippets with path + lines. If response has disabled=true, memory retrieval is unavailable and should be surfaced to the user." */,
    parameters: MemorySearchSchema,
    execute:
      ({ cfg, agentId }) ->
      async (_toolCallId, params) {
        val query = readStringParam(params, "query", { required: true })
        val maxResults = readNumberParam(params, "maxResults")
        val minScore = readNumberParam(params, "minScore")
        val memory = await getMemoryManagerContext({ cfg, agentId })
        if ("error" in memory) {
          return jsonResult(buildMemorySearchUnavailableResult(memory.error))
        }
        try {
          val citationsMode = resolveMemoryCitationsMode(cfg)
          val includeCitations = shouldIncludeCitations({
            mode: citationsMode,
            sessionKey: options.agentSessionKey,
          })
          val rawResults = await memory.manager.search(query, {
            maxResults,
            minScore,
            sessionKey: options.agentSessionKey,
          })
          val status = memory.manager.status()
          val decorated = decorateCitations(rawResults, includeCitations)
          val resolved = resolveMemoryBackendConfig({ cfg, agentId })
          val results =
            status.backend == "qmd"
              ? clampResultsByInjectedChars(decorated, resolved.qmd?.limits.maxInjectedChars)
              : decorated
          val searchMode = (status.custom as /* TODO */ { searchMode?: String }?)?.searchMode
          return jsonResult({
            results,
            provider: status.provider,
            model: status.model,
            fallback: status.fallback,
            citations: citationsMode,
            mode: searchMode,
          })
        } catch (err) {
          val message = err instanceof Error ? err.message : String(err)
          return jsonResult(buildMemorySearchUnavailableResult(message))
        }
      },
  })
}

fun createMemoryGetTool(options: {
  config?: OpenClawConfig
  agentSessionKey?: String
}): AnyAgentTool? {
  return createMemoryTool({
    options,
    label: String /* "Memory Get" */,
    name: String /* "memory_get" */,
    description: String /* "Safe snippet read from MEMORY.md or memory/*.md with optional from/lines use after memory_search to pull only the needed lines and keep context small." */,
    parameters: MemoryGetSchema,
    execute:
      ({ cfg, agentId }) ->
      async (_toolCallId, params) {
        val relPath = readStringParam(params, "path", { required: true })
        val from = readNumberParam(params, "from", { integer: true })
        val lines = readNumberParam(params, "lines", { integer: true })
        val memory = await getMemoryManagerContext({ cfg, agentId })
        if ("error" in memory) {
          return jsonResult({ path: relPath, text: "", disabled: true, error: memory.error })
        }
        try {
          val result = await memory.manager.readFile({
            relPath,
            from: from ?: null,
            lines: lines ?: null,
          })
          return jsonResult(result)
        } catch (err) {
          val message = err instanceof Error ? err.message : String(err)
          return jsonResult({ path: relPath, text: "", disabled: true, error: message })
        }
      },
  })
}

fun resolveMemoryCitationsMode(cfg: OpenClawConfig): MemoryCitationsMode {
  val mode = cfg.memory?.citations
  if (mode == "on" || mode == "off" || mode == "auto") {
    return mode
  }
  return "auto"
}

fun decorateCitations(results: List<MemorySearchResult>, include: Boolean): List<MemorySearchResult> {
  if (!include) {
    return results.map((entry) -> ({ ...entry, citation: null }))
  }
  return results.map((entry) {
    val citation = formatCitation(entry)
    val snippet = `${entry.snippet.trim()}\n\nSource: ${citation}`
    return { ...entry, citation, snippet }
  })
}

fun formatCitation(entry: MemorySearchResult): String {
  val lineRange =
    entry.startLine == entry.endLine
      ? `#L${entry.startLine}`
      : `#L${entry.startLine}-L${entry.endLine}`
  return `${entry.path}${lineRange}`
}

fun clampResultsByInjectedChars(
  results: List<MemorySearchResult>,
  budget?: Double,
): List<MemorySearchResult> {
  if (!budget || budget <= 0) {
    return results
  }
  var remaining = budget
  val clamped: List<MemorySearchResult> = []
  for (val entry of results) {
    if (remaining <= 0) {
      break
    }
    val snippet = entry.snippet ?: ""
    if (snippet.length <= remaining) {
      clamped.push(entry)
      remaining -= snippet.length
    } else {
      val trimmed = snippet.slice(0, Math.max(0, remaining))
      clamped.push({ ...entry, snippet: trimmed })
      break
    }
  }
  return clamped
}

fun buildMemorySearchUnavailableResult(error: String?) {
  val reason = (error ?: String /* "memory search unavailable" */).trim() || "memory search unavailable"
  val isQuotaError = /insufficient_quota|quota|429/.test(reason.toLowerCase())
  val warning = isQuotaError
    ? "Memory search is unavailable because the embedding provider quota is exhausted."
    : String /* "Memory search is unavailable due to an embedding/provider error." */
  val action = isQuotaError
    ? "Top up or switch embedding provider, then retry memory_search."
    : String /* "Check embedding provider configuration and retry memory_search." */
  return {
    results: [],
    disabled: true,
    unavailable: true,
    error: reason,
    warning,
    action,
  }
}

fun shouldIncludeCitations(params: {
  mode: MemoryCitationsMode
  sessionKey?: String
}): Boolean {
  if (params.mode == "on") {
    return true
  }
  if (params.mode == "off") {
    return false
  }
  // auto: show citations in direct chats suppress in groups/channels by default.
  val chatType = deriveChatTypeFromSessionKey(params.sessionKey)
  return chatType == "direct"
}

fun deriveChatTypeFromSessionKey(sessionKey?: String): String /* "direct" */ | "group" | "channel" {
  val parsed = parseAgentSessionKey(sessionKey)
  if (!parsed?.rest) {
    return "direct"
  }
  val tokens = mutableSetOf(parsed.rest.toLowerCase().split(": String /* ").filter(Boolean))
  if (tokens.has(" */channel")) {
    return "channel"
  }
  if (tokens.has("group")) {
    return "group"
  }
  return "direct"
}
