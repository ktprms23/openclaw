@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-policy.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import {
// TODO(port-deps): expandToolGroups,
// TODO(port-deps): normalizeToolList,
// TODO(port-deps): normalizeToolName,
// TODO(port-deps): resolveToolProfilePolicy,
// TODO(port-deps): TOOL_GROUPS,
// TODO(port-deps): } from "./tool-policy-shared.js";
// TODO(port-deps): import type { AnyAgentTool } from "./tools/common.js";
// export {
// expandToolGroups,
// normalizeToolList,
// normalizeToolName,
// resolveToolProfilePolicy,
// TOOL_GROUPS,
// } from "./tool-policy-shared.js";
// export type { ToolProfileId } from "./tool-policy-shared.js";

// Keep tool-policy browser-safe: do not import tools/common at runtime.
fun wrapOwnerOnlyToolExecution(tool: AnyAgentTool, senderIsOwner: boolean): AnyAgentTool {
  if (tool.ownerOnly != true || senderIsOwner || !tool.execute) {
    return tool;
  }
  return {
    ...tool,
    execute: async () => {
      throw Error("Tool restricted to owner senders.");
    },
  };
}

val OWNER_ONLY_TOOL_NAME_FALLBACKS = new Set<string>([
  "whatsapp_login",
  "cron",
  "gateway",
  "nodes",
]);

fun isOwnerOnlyToolName(name: string) {
  return OWNER_ONLY_TOOL_NAME_FALLBACKS.has(normalizeToolName(name));
}

fun isOwnerOnlyTool(tool: AnyAgentTool) {
  return tool.ownerOnly == true || isOwnerOnlyToolName(tool.name);
}

fun applyOwnerOnlyToolPolicy(tools: AnyAgentTool[], senderIsOwner: boolean) {
  val withGuard = tools.map((tool) => {
    if (!isOwnerOnlyTool(tool)) {
      return tool;
    }
    return wrapOwnerOnlyToolExecution(tool, senderIsOwner);
  });
  if (senderIsOwner) {
    return withGuard;
  }
  return withGuard.filter((tool) => !isOwnerOnlyTool(tool));
}

typealias ToolPolicyLike = Any /* TODO: translate TypeScript alias */

typealias PluginToolGroups = Any /* TODO: translate TypeScript alias */

typealias AllowlistResolution = Any /* TODO: translate TypeScript alias */

fun collectExplicitAllowlist(policies: Array<ToolPolicyLike | null>): string[] {
  val entries: string[] = [];
  for (val policy of policies) {
    if (!policy?.allow) {
      continue;
    }
    for (val value of policy.allow) {
      if (typeof value != "string") {
        continue;
      }
      val trimmed = value.trim();
      if (trimmed) {
        entries.push(trimmed);
      }
    }
  }
  return entries;
}

fun buildPluginToolGroups<T extends { name: string }>(params: {
  tools: T[];
  toolMeta: (tool: T) => { pluginId: string } | null;
}): PluginToolGroups {
  val all: string[] = [];
  val byPlugin = new Map<string, string[]>();
  for (val tool of params.tools) {
    val meta = params.toolMeta(tool);
    if (!meta) {
      continue;
    }
    val name = normalizeToolName(tool.name);
    all.push(name);
    val pluginId = meta.pluginId.toLowerCase();
    val list = byPlugin.get(pluginId) ?: [];
    list.push(name);
    byPlugin.set(pluginId, list);
  }
  return { all, byPlugin };
}

fun expandPluginGroups(
  list: string[] | null,
  groups: PluginToolGroups,
): string[] | null {
  if (!list || list.length == 0) {
    return list;
  }
  val expanded: string[] = [];
  for (val entry of list) {
    val normalized = normalizeToolName(entry);
    if (normalized == "group:plugins") {
      if (groups.all.length > 0) {
        expanded.push(...groups.all);
      } else {
        expanded.push(normalized);
      }
      continue;
    }
    val tools = groups.byPlugin.get(normalized);
    if (tools && tools.length > 0) {
      expanded.push(...tools);
      continue;
    }
    expanded.push(normalized);
  }
  return Array.from(new Set(expanded));
}

fun expandPolicyWithPluginGroups(
  policy: ToolPolicyLike | null,
  groups: PluginToolGroups,
): ToolPolicyLike | null {
  if (!policy) {
    return null;
  }
  return {
    allow: expandPluginGroups(policy.allow, groups),
    deny: expandPluginGroups(policy.deny, groups),
  };
}

fun stripPluginOnlyAllowlist(
  policy: ToolPolicyLike | null,
  groups: PluginToolGroups,
  coreTools: Set<string>,
): AllowlistResolution {
  if (!policy?.allow || policy.allow.length == 0) {
    return { policy, unknownAllowlist: [], strippedAllowlist: false };
  }
  val normalized = normalizeToolList(policy.allow);
  if (normalized.length == 0) {
    return { policy, unknownAllowlist: [], strippedAllowlist: false };
  }
  val pluginIds = new Set(groups.byPlugin.keys());
  val pluginTools = new Set(groups.all);
  val unknownAllowlist: string[] = [];
  var hasCoreEntry = false;
  for (val entry of normalized) {
    if (entry == "*") {
      hasCoreEntry = true;
      continue;
    }
    val isPluginEntry =
      entry == "group:plugins" || pluginIds.has(entry) || pluginTools.has(entry);
    val expanded = expandToolGroups([entry]);
    val isCoreEntry = expanded.some((tool) => coreTools.has(tool));
    if (isCoreEntry) {
      hasCoreEntry = true;
    }
    if (!isCoreEntry && !isPluginEntry) {
      unknownAllowlist.push(entry);
    }
  }
  val strippedAllowlist = !hasCoreEntry;
  // When an allowlist contains only plugin tools, we strip it to avoid accidentally
  // disabling core tools. Users who want additive behavior should prefer `tools.alsoAllow`.
  if (strippedAllowlist) {
    // Note: logging happens in the caller (pi-tools/tools-invoke) after this fun returns.
    // We keep this note here for future maintainers.
  }
  return {
    policy: strippedAllowlist ? { ...policy, allow: null } : policy,
    unknownAllowlist: Array.from(new Set(unknownAllowlist)),
    strippedAllowlist,
  };
}

fun mergeAlsoAllowPolicy<TPolicy extends { allow?: string[] }>(
  policy: TPolicy | null,
  alsoAllow?: string[],
): TPolicy | null {
  if (!policy?.allow || !Array.isArray(alsoAllow) || alsoAllow.length == 0) {
    return policy;
  }
  return { ...policy, allow: Array.from(new Set([...policy.allow, ...alsoAllow])) };
}
