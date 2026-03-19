package agents.tools_and_subagents

// Converted from src/agents/tool-catalog.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
typealias ToolProfileId = "minimal" | "coding" | "messaging" | "full"

data class ToolProfilePolicy(
    val allow: List<String>?,
    val deny: List<String>?,
)

data class CoreToolSection(
    val id: String,
    val label: String,
    val tools: List<{,
    val id: String,
    val label: String,
    val description: String,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   }>;
}

data class CoreToolDefinition(
    val id: String,
    val label: String,
    val description: String,
    val sectionId: String,
    val profiles: List<ToolProfileId>,
    val includeInOpenClawGroup: Boolean?,
)

val CORE_TOOL_SECTION_ORDER: List<{ id: String label: String }> = [
  { id: String /* "fs" */, label: String /* "Files" */ },
  { id: String /* "runtime" */, label: String /* "Runtime" */ },
  { id: String /* "web" */, label: String /* "Web" */ },
  { id: String /* "memory" */, label: String /* "Memory" */ },
  { id: String /* "sessions" */, label: String /* "Sessions" */ },
  { id: String /* "ui" */, label: String /* "UI" */ },
  { id: String /* "messaging" */, label: String /* "Messaging" */ },
  { id: String /* "automation" */, label: String /* "Automation" */ },
  { id: String /* "nodes" */, label: String /* "Nodes" */ },
  { id: String /* "agents" */, label: String /* "Agents" */ },
  { id: String /* "media" */, label: String /* "Media" */ },
]

val CORE_TOOL_DEFINITIONS: List<CoreToolDefinition> = [
  {
    id: String /* "read" */,
    label: String /* "read" */,
    description: String /* "Read file contents" */,
    sectionId: String /* "fs" */,
    profiles: ["coding"],
  },
  {
    id: String /* "write" */,
    label: String /* "write" */,
    description: String /* "Create or overwrite files" */,
    sectionId: String /* "fs" */,
    profiles: ["coding"],
  },
  {
    id: String /* "edit" */,
    label: String /* "edit" */,
    description: String /* "Make precise edits" */,
    sectionId: String /* "fs" */,
    profiles: ["coding"],
  },
  {
    id: String /* "apply_patch" */,
    label: String /* "apply_patch" */,
    description: String /* "Patch files (OpenAI)" */,
    sectionId: String /* "fs" */,
    profiles: ["coding"],
  },
  {
    id: String /* "exec" */,
    label: String /* "exec" */,
    description: String /* "Run shell commands" */,
    sectionId: String /* "runtime" */,
    profiles: ["coding"],
  },
  {
    id: String /* "process" */,
    label: String /* "process" */,
    description: String /* "Manage background processes" */,
    sectionId: String /* "runtime" */,
    profiles: ["coding"],
  },
  {
    id: String /* "web_search" */,
    label: String /* "web_search" */,
    description: String /* "Search the web" */,
    sectionId: String /* "web" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "web_fetch" */,
    label: String /* "web_fetch" */,
    description: String /* "Fetch web content" */,
    sectionId: String /* "web" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "memory_search" */,
    label: String /* "memory_search" */,
    description: String /* "Semantic search" */,
    sectionId: String /* "memory" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "memory_get" */,
    label: String /* "memory_get" */,
    description: String /* "Read memory files" */,
    sectionId: String /* "memory" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "sessions_list" */,
    label: String /* "sessions_list" */,
    description: String /* "List sessions" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding", "messaging"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "sessions_history" */,
    label: String /* "sessions_history" */,
    description: String /* "Session history" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding", "messaging"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "sessions_send" */,
    label: String /* "sessions_send" */,
    description: String /* "Send to session" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding", "messaging"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "sessions_spawn" */,
    label: String /* "sessions_spawn" */,
    description: String /* "Spawn sub-agent" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "sessions_yield" */,
    label: String /* "sessions_yield" */,
    description: String /* "End turn to receive sub-agent results" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "subagents" */,
    label: String /* "subagents" */,
    description: String /* "Manage sub-agents" */,
    sectionId: String /* "sessions" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "session_status" */,
    label: String /* "session_status" */,
    description: String /* "Session status" */,
    sectionId: String /* "sessions" */,
    profiles: ["minimal", "coding", "messaging"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "browser" */,
    label: String /* "browser" */,
    description: String /* "Control web browser" */,
    sectionId: String /* "ui" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "canvas" */,
    label: String /* "canvas" */,
    description: String /* "Control canvases" */,
    sectionId: String /* "ui" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "message" */,
    label: String /* "message" */,
    description: String /* "Send messages" */,
    sectionId: String /* "messaging" */,
    profiles: ["messaging"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "cron" */,
    label: String /* "cron" */,
    description: String /* "Schedule tasks" */,
    sectionId: String /* "automation" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "gateway" */,
    label: String /* "gateway" */,
    description: String /* "Gateway control" */,
    sectionId: String /* "automation" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "nodes" */,
    label: String /* "nodes" */,
    description: String /* "Nodes + devices" */,
    sectionId: String /* "nodes" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "agents_list" */,
    label: String /* "agents_list" */,
    description: String /* "List agents" */,
    sectionId: String /* "agents" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "image" */,
    label: String /* "image" */,
    description: String /* "Image understanding" */,
    sectionId: String /* "media" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "image_generate" */,
    label: String /* "image_generate" */,
    description: String /* "Image generation" */,
    sectionId: String /* "media" */,
    profiles: ["coding"],
    includeInOpenClawGroup: true,
  },
  {
    id: String /* "tts" */,
    label: String /* "tts" */,
    description: String /* "Text-to-speech conversion" */,
    sectionId: String /* "media" */,
    profiles: [],
    includeInOpenClawGroup: true,
  },
]

val CORE_TOOL_BY_ID = new MutableMap<String, CoreToolDefinition>(
  CORE_TOOL_DEFINITIONS.map((tool) -> [tool.id, tool]),
)

fun listCoreToolIdsForProfile(profile: ToolProfileId): List<String> {
  return CORE_TOOL_DEFINITIONS.filter((tool) -> tool.profiles.includes(profile)).map(
    (tool) -> tool.id,
  )
}

val CORE_TOOL_PROFILES: MutableMap<ToolProfileId, ToolProfilePolicy> = {
  minimal: {
    allow: listCoreToolIdsForProfile("minimal"),
  },
  coding: {
    allow: listCoreToolIdsForProfile("coding"),
  },
  messaging: {
    allow: listCoreToolIdsForProfile("messaging"),
  },
  full: {},
}

fun buildCoreToolGroupMap() {
  val sectionToolMap = new MutableMap<String, List<String>>()
  for (val tool of CORE_TOOL_DEFINITIONS) {
    val groupId = `group:${tool.sectionId}`
    val list = sectionToolMap.get(groupId) ?: []
    list.push(tool.id)
    sectionToolMap.set(groupId, list)
  }
  val openclawTools = CORE_TOOL_DEFINITIONS.filter((tool) -> tool.includeInOpenClawGroup).map(
    (tool) -> tool.id,
  )
  return {
    "group:openclaw": openclawTools,
    ...Object.fromEntries(sectionToolMap.entries()),
  }
}

val CORE_TOOL_GROUPS = buildCoreToolGroupMap()

val PROFILE_OPTIONS = [
  { id: String /* "minimal" */, label: String /* "Minimal" */ },
  { id: String /* "coding" */, label: String /* "Coding" */ },
  { id: String /* "messaging" */, label: String /* "Messaging" */ },
  { id: String /* "full" */, label: String /* "Full" */ },
] as /* TODO */ val

fun resolveCoreToolProfilePolicy(profile?: String): ToolProfilePolicy? {
  if (!profile) {
    return null
  }
  val resolved = CORE_TOOL_PROFILES[profile as /* TODO */ ToolProfileId]
  if (!resolved) {
    return null
  }
  if (!resolved.allow && !resolved.deny) {
    return null
  }
  return {
    allow: resolved.allow ? [...resolved.allow] : null,
    deny: resolved.deny ? [...resolved.deny] : null,
  }
}

fun listCoreToolSections(): List<CoreToolSection> {
  return CORE_TOOL_SECTION_ORDER.map((section) -> ({
    id: section.id,
    label: section.label,
    tools: CORE_TOOL_DEFINITIONS.filter((tool) -> tool.sectionId == section.id).map((tool) -> ({
      id: tool.id,
      label: tool.label,
      description: tool.description,
    })),
  })).filter((section) -> section.tools.length > 0)
}

fun resolveCoreToolProfiles(toolId: String): List<ToolProfileId> {
  val tool = CORE_TOOL_BY_ID.get(toolId)
  if (!tool) {
    return []
  }
  return [...tool.profiles]
}

fun isKnownCoreToolId(toolId: String): Boolean {
  return CORE_TOOL_BY_ID.has(toolId)
}
