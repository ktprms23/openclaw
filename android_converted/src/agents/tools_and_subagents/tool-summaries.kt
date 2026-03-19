@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-summaries.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AgentTool } from "@mariozechner/pi-agent-core";

fun buildToolSummaryMap(tools: AgentTool[]): Record<string, string> {
  val summaries: Record<string, string> = {};
  for (val tool of tools) {
    val summary = tool.description?.trim() || tool.label?.trim();
    if (!summary) {
      continue;
    }
    summaries[tool.name.toLowerCase()] = summary;
  }
  return summaries;
}
