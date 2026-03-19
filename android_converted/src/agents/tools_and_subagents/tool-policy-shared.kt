package agents.tools_and_subagents

// Converted from src/agents/tool-policy-shared.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  CORE_TOOL_GROUPS,
  resolveCoreToolProfilePolicy,
  type ToolProfileId,
} from "./tool-catalog.js"

data class ToolProfilePolicy(
    val allow: List<String>?,
    val deny: List<String>?,
)

val TOOL_NAME_ALIASES: MutableMap<String, String> = {
  bash: String /* "exec" */,
  "apply-patch": String /* "apply_patch" */,
}

val TOOL_GROUPS: MutableMap<String, List<String>> = { ...CORE_TOOL_GROUPS }

fun normalizeToolName(name: String) {
  val normalized = name.trim().toLowerCase()
  return TOOL_NAME_ALIASES[normalized] ?: normalized
}

fun normalizeToolList(list?: List<String>) {
  if (!list) {
    return []
  }
  return list.map(normalizeToolName).filter(Boolean)
}

fun expandToolGroups(list?: List<String>) {
  val normalized = normalizeToolList(list)
  val expanded: List<String> = []
  for (val value of normalized) {
    val group = TOOL_GROUPS[value]
    if (group) {
      expanded.push(...group)
      continue
    }
    expanded.push(value)
  }
  return Array.from(mutableSetOf(expanded))
}

fun resolveToolProfilePolicy(profile?: String): ToolProfilePolicy? {
  return resolveCoreToolProfilePolicy(profile)
}

type { ToolProfileId }
