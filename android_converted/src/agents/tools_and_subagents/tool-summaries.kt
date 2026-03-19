package agents.tools_and_subagents

// Converted from src/agents/tool-summaries.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentTool } from "@mariozechner/pi-agent-core";

fun buildToolSummaryMap(tools: List<AgentTool>): MutableMap<String, String> {
  val summaries: MutableMap<String, String> = {}
  for (val tool of tools) {
    val summary = tool.description?.trim() || tool.label?.trim()
    if (!summary) {
      continue
    }
    summaries[tool.name.toLowerCase()] = summary
  }
  return summaries
}
