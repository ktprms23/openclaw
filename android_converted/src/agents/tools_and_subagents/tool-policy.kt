package agents.tools_and_subagents

// Converted from src/agents/tool-policy.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import {
  expandToolGroups,
  normalizeToolList,
  normalizeToolName,
  resolveToolProfilePolicy,
  TOOL_GROUPS,
} from "./tool-policy-shared.js"
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./tools/common.js";
{
  expandToolGroups,
  normalizeToolList,
  normalizeToolName,
  resolveToolProfilePolicy,
  TOOL_GROUPS,
} from "./tool-policy-shared.js"
type { ToolProfileId } from "./tool-policy-shared.js"

// Keep tool-policy browser-safe: do not import tools/common at runtime.
fun wrapOwnerOnlyToolExecution(tool: AnyAgentTool, senderIsOwner: Boolean): AnyAgentTool {
  if (tool.ownerOnly != true || senderIsOwner || !tool.execute) {
    return tool
  }
  return {
    ...tool,
    execute: async () {
      throw Error("Tool restricted to owner senders.")
    },
  }
}

val OWNER_ONLY_TOOL_NAME_FALLBACKS = new MutableSet<String>([
  "whatsapp_login",
  "cron",
  "gateway",
  "nodes",
])

fun isOwnerOnlyToolName(name: String) {
  return OWNER_ONLY_TOOL_NAME_FALLBACKS.has(normalizeToolName(name))
}

fun isOwnerOnlyTool(tool: AnyAgentTool) {
  return tool.ownerOnly == true || isOwnerOnlyToolName(tool.name)
}

fun applyOwnerOnlyToolPolicy(tools: List<AnyAgentTool>, senderIsOwner: Boolean) {
  val withGuard = tools.map((tool) {
    if (!isOwnerOnlyTool(tool)) {
      return tool
    }
    return wrapOwnerOnlyToolExecution(tool, senderIsOwner)
  })
  if (senderIsOwner) {
    return withGuard
  }
  return withGuard.filter((tool) -> !isOwnerOnlyTool(tool))
}

data class ToolPolicyLike(
    val allow: List<String>?,
    val deny: List<String>?,
)

data class PluginToolGroups(
    val all: List<String>,
    val byPlugin: MutableMap<String, List<String>>,
)

data class AllowlistResolution(
    val policy: ToolPolicyLike?,
    val unknownAllowlist: List<String>,
    val strippedAllowlist: Boolean,
)

fun collectExplicitAllowlist(policies: List<ToolPolicyLike?>): List<String> {
  val entries: List<String> = []
  for (val policy of policies) {
    if (!policy?.allow) {
      continue
    }
    for (val value of policy.allow) {
      if (value !is String) {
        continue
      }
      val trimmed = value.trim()
      if (trimmed) {
        entries.push(trimmed)
      }
    }
  }
  return entries
}

fun <T extends { name: String }> buildPluginToolGroups(params: {
  tools: List<T>
  toolMeta: (tool: T) { pluginId: String }?
}): PluginToolGroups {
  val all: List<String> = []
  val byPlugin = new MutableMap<String, List<String>>()
  for (val tool of params.tools) {
    val meta = params.toolMeta(tool)
    if (!meta) {
      continue
    }
    val name = normalizeToolName(tool.name)
    all.push(name)
    val pluginId = meta.pluginId.toLowerCase()
    val list = byPlugin.get(pluginId) ?: []
    list.push(name)
    byPlugin.set(pluginId, list)
  }
  return { all, byPlugin }
}

fun expandPluginGroups(
  list: List<String>?,
  groups: PluginToolGroups,
): List<String>? {
  if (!list || list.length == 0) {
    return list
  }
  val expanded: List<String> = []
  for (val entry of list) {
    val normalized = normalizeToolName(entry)
    if (normalized == "group:plugins") {
      if (groups.all.length > 0) {
        expanded.push(...groups.all)
      } else {
        expanded.push(normalized)
      }
      continue
    }
    val tools = groups.byPlugin.get(normalized)
    if (tools && tools.length > 0) {
      expanded.push(...tools)
      continue
    }
    expanded.push(normalized)
  }
  return Array.from(mutableSetOf(expanded))
}

fun expandPolicyWithPluginGroups(
  policy: ToolPolicyLike?,
  groups: PluginToolGroups,
): ToolPolicyLike? {
  if (!policy) {
    return null
  }
  return {
    allow: expandPluginGroups(policy.allow, groups),
    deny: expandPluginGroups(policy.deny, groups),
  }
}

fun stripPluginOnlyAllowlist(
  policy: ToolPolicyLike?,
  groups: PluginToolGroups,
  coreTools: MutableSet<String>,
): AllowlistResolution {
  if (!policy?.allow || policy.allow.length == 0) {
    return { policy, unknownAllowlist: [], strippedAllowlist: false }
  }
  val normalized = normalizeToolList(policy.allow)
  if (normalized.length == 0) {
    return { policy, unknownAllowlist: [], strippedAllowlist: false }
  }
  val pluginIds = mutableSetOf(groups.byPlugin.keys())
  val pluginTools = mutableSetOf(groups.all)
  val unknownAllowlist: List<String> = []
  var hasCoreEntry = false
  for (val entry of normalized) {
    if (entry == "*") {
      hasCoreEntry = true
      continue
    }
    val isPluginEntry =
      entry == "group:plugins" || pluginIds.has(entry) || pluginTools.has(entry)
    val expanded = expandToolGroups([entry])
    val isCoreEntry = expanded.some((tool) -> coreTools.has(tool))
    if (isCoreEntry) {
      hasCoreEntry = true
    }
    if (!isCoreEntry && !isPluginEntry) {
      unknownAllowlist.push(entry)
    }
  }
  val strippedAllowlist = !hasCoreEntry
  // When an allowlist contains only plugin tools, we strip it to avoid accidentally
  // disabling core tools. Users who want additive behavior should prefer `tools.alsoAllow`.
  if (strippedAllowlist) {
    // Note: logging happens in the caller (pi-tools/tools-invoke) after this fun returns.
    // We keep this note here for future maintainers.
  }
  return {
    policy: strippedAllowlist ? { ...policy, allow: null } : policy,
    unknownAllowlist: Array.from(mutableSetOf(unknownAllowlist)),
    strippedAllowlist,
  }
}

fun <TPolicy extends { allow?: List<String> }> mergeAlsoAllowPolicy(
  policy: TPolicy?,
  alsoAllow?: List<String>,
): TPolicy? {
  if (!policy?.allow || !Array.isArray(alsoAllow) || alsoAllow.length == 0) {
    return policy
  }
  return { ...policy, allow: Array.from(mutableSetOf([...policy.allow, ...alsoAllow])) }
}
