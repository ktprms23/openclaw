@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/memory-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { Type } from "@sinclair/typebox";
// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import type { MemoryCitationsMode } from "../../config/types.memory.js";
// TODO(port-deps): import { resolveMemoryBackendConfig } from "../../memory/backend-config.js";
// TODO(port-deps): import { getMemorySearchManager } from "../../memory/index.js";
// TODO(port-deps): import type { MemorySearchResult } from "../../memory/types.js";
// TODO(port-deps): import { parseAgentSessionKey } from "../../routing/session-key.js";
// TODO(port-deps): import { resolveSessionAgentId } from "../agent-scope.js";
// TODO(port-deps): import { resolveMemorySearchConfig } from "../memory-search.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult, readNumberParam, readStringParam } from "./common.js";

val MemorySearchSchema = Type.Object({
  query: Type.String(),
  maxResults: Type.Optional(Type.Number()),
  minScore: Type.Optional(Type.Number()),
});

val MemoryGetSchema = Type.Object({
  path: Type.String(),
  from: Type.Optional(Type.Number()),
  lines: Type.Optional(Type.Number()),
});

fun resolveMemoryToolContext(options: { config?: OpenClawConfig; agentSessionKey?: string }) {
  val cfg = options.config;
  if (!cfg) {
    return null;
  }
  val agentId = resolveSessionAgentId({
    sessionKey: options.agentSessionKey,
    config: cfg,
  });
  if (!resolveMemorySearchConfig(cfg, agentId)) {
    return null;
  }
  return { cfg, agentId };
}

suspend fun getMemoryManagerContext(params: { cfg: OpenClawConfig; agentId: string }): Promise<
  | {
      manager: NonNullable<Awaited<ReturnType<typeof getMemorySearchManager>>["manager"]>;
    }
  | {
      error: string | null;
    }
> {
  val { manager, error } = await getMemorySearchManager({
    cfg: params.cfg,
    agentId: params.agentId,
  });
  return manager ? { manager } : { error };
}

fun createMemoryTool(params: {
  options: {
    config?: OpenClawConfig;
    agentSessionKey?: string;
  };
  label: string;
  name: string;
  description: string;
  parameters: typeof MemorySearchSchema | typeof MemoryGetSchema;
  execute: (ctx: { cfg: OpenClawConfig; agentId: string }) => AnyAgentTool["execute"];
}): AnyAgentTool | null {
  val ctx = resolveMemoryToolContext(params.options);
  if (!ctx) {
    return null;
  }
  return {
    label: params.label,
    name: params.name,
    description: params.description,
    parameters: params.parameters,
    execute: params.execute(ctx),
  };
}

fun createMemorySearchTool(options: {
  config?: OpenClawConfig;
  agentSessionKey?: string;
}): AnyAgentTool | null {
  return createMemoryTool({
    options,
    label: "Memory Search",
    name: "memory_search",
    description:
      "Mandatory recall step: semantically search MEMORY.md + memory/*.md (and optional session transcripts) before answering questions about prior work, decisions, dates, people, preferences, or todos; returns top snippets with path + lines. If response has disabled=true, memory retrieval is unavailable and should be surfaced to the user.",
    parameters: MemorySearchSchema,
    execute:
      ({ cfg, agentId }) =>
      async (_toolCallId, params) => {
        val query = readStringParam(params, "query", { required: true });
        val maxResults = readNumberParam(params, "maxResults");
        val minScore = readNumberParam(params, "minScore");
        val memory = await getMemoryManagerContext({ cfg, agentId });
        if ("error" in memory) {
          return jsonResult(buildMemorySearchUnavailableResult(memory.error));
        }
        try {
          val citationsMode = resolveMemoryCitationsMode(cfg);
          val includeCitations = shouldIncludeCitations({
            mode: citationsMode,
            sessionKey: options.agentSessionKey,
          });
          val rawResults = await memory.manager.search(query, {
            maxResults,
            minScore,
            sessionKey: options.agentSessionKey,
          });
          val status = memory.manager.status();
          val decorated = decorateCitations(rawResults, includeCitations);
          val resolved = resolveMemoryBackendConfig({ cfg, agentId });
          val results =
            status.backend == "qmd"
              ? clampResultsByInjectedChars(decorated, resolved.qmd?.limits.maxInjectedChars)
              : decorated;
          val searchMode = (status.custom as { searchMode?: string } | null)?.searchMode;
          return jsonResult({
            results,
            provider: status.provider,
            model: status.model,
            fallback: status.fallback,
            citations: citationsMode,
            mode: searchMode,
          });
        } catch (err) {
          val message = err instanceof Error ? err.message : String(err);
          return jsonResult(buildMemorySearchUnavailableResult(message));
        }
      },
  });
}

fun createMemoryGetTool(options: {
  config?: OpenClawConfig;
  agentSessionKey?: string;
}): AnyAgentTool | null {
  return createMemoryTool({
    options,
    label: "Memory Get",
    name: "memory_get",
    description:
      "Safe snippet read from MEMORY.md or memory/*.md with optional from/lines; use after memory_search to pull only the needed lines and keep context small.",
    parameters: MemoryGetSchema,
    execute:
      ({ cfg, agentId }) =>
      async (_toolCallId, params) => {
        val relPath = readStringParam(params, "path", { required: true });
        val from = readNumberParam(params, "from", { integer: true });
        val lines = readNumberParam(params, "lines", { integer: true });
        val memory = await getMemoryManagerContext({ cfg, agentId });
        if ("error" in memory) {
          return jsonResult({ path: relPath, text: "", disabled: true, error: memory.error });
        }
        try {
          val result = await memory.manager.readFile({
            relPath,
            from: from ?: null,
            lines: lines ?: null,
          });
          return jsonResult(result);
        } catch (err) {
          val message = err instanceof Error ? err.message : String(err);
          return jsonResult({ path: relPath, text: "", disabled: true, error: message });
        }
      },
  });
}

fun resolveMemoryCitationsMode(cfg: OpenClawConfig): MemoryCitationsMode {
  val mode = cfg.memory?.citations;
  if (mode == "on" || mode == "off" || mode == "auto") {
    return mode;
  }
  return "auto";
}

fun decorateCitations(results: MemorySearchResult[], include: boolean): MemorySearchResult[] {
  if (!include) {
    return results.map((entry) => ({ ...entry, citation: null }));
  }
  return results.map((entry) => {
    val citation = formatCitation(entry);
    val snippet = `${entry.snippet.trim()}\n\nSource: ${citation}`;
    return { ...entry, citation, snippet };
  });
}

fun formatCitation(entry: MemorySearchResult): string {
  val lineRange =
    entry.startLine == entry.endLine
      ? `#L${entry.startLine}`
      : `#L${entry.startLine}-L${entry.endLine}`;
  return `${entry.path}${lineRange}`;
}

fun clampResultsByInjectedChars(
  results: MemorySearchResult[],
  budget?: number,
): MemorySearchResult[] {
  if (!budget || budget <= 0) {
    return results;
  }
  var remaining = budget;
  val clamped: MemorySearchResult[] = [];
  for (val entry of results) {
    if (remaining <= 0) {
      break;
    }
    val snippet = entry.snippet ?: "";
    if (snippet.length <= remaining) {
      clamped.push(entry);
      remaining -= snippet.length;
    } else {
      val trimmed = snippet.slice(0, Math.max(0, remaining));
      clamped.push({ ...entry, snippet: trimmed });
      break;
    }
  }
  return clamped;
}

fun buildMemorySearchUnavailableResult(error: string | null) {
  val reason = (error ?: "memory search unavailable").trim() || "memory search unavailable";
  val isQuotaError = /insufficient_quota|quota|429/.test(reason.toLowerCase());
  val warning = isQuotaError
    ? "Memory search is unavailable because the embedding provider quota is exhausted."
    : "Memory search is unavailable due to an embedding/provider error.";
  val action = isQuotaError
    ? "Top up or switch embedding provider, then retry memory_search."
    : "Check embedding provider configuration and retry memory_search.";
  return {
    results: [],
    disabled: true,
    unavailable: true,
    error: reason,
    warning,
    action,
  };
}

fun shouldIncludeCitations(params: {
  mode: MemoryCitationsMode;
  sessionKey?: string;
}): boolean {
  if (params.mode == "on") {
    return true;
  }
  if (params.mode == "off") {
    return false;
  }
  // auto: show citations in direct chats; suppress in groups/channels by default.
  val chatType = deriveChatTypeFromSessionKey(params.sessionKey);
  return chatType == "direct";
}

fun deriveChatTypeFromSessionKey(sessionKey?: string): "direct" | "group" | "channel" {
  val parsed = parseAgentSessionKey(sessionKey);
  if (!parsed?.rest) {
    return "direct";
  }
  val tokens = new Set(parsed.rest.toLowerCase().split(":").filter(Boolean));
  if (tokens.has("channel")) {
    return "channel";
  }
  if (tokens.has("group")) {
    return "group";
  }
  return "direct";
}
