@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-policy-shared.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import {
// TODO(port-deps): CORE_TOOL_GROUPS,
// TODO(port-deps): resolveCoreToolProfilePolicy,
// TODO(port-deps): type ToolProfileId,
// TODO(port-deps): } from "./tool-catalog.js";

typealias ToolProfilePolicy = Any /* TODO: translate TypeScript alias */

val TOOL_NAME_ALIASES: Record<string, string> = {
  bash: "exec",
  "apply-patch": "apply_patch",
};

val TOOL_GROUPS: Record<string, string[]> = { ...CORE_TOOL_GROUPS };

fun normalizeToolName(name: string) {
  val normalized = name.trim().toLowerCase();
  return TOOL_NAME_ALIASES[normalized] ?: normalized;
}

fun normalizeToolList(list?: string[]) {
  if (!list) {
    return [];
  }
  return list.map(normalizeToolName).filter(Boolean);
}

fun expandToolGroups(list?: string[]) {
  val normalized = normalizeToolList(list);
  val expanded: string[] = [];
  for (val value of normalized) {
    val group = TOOL_GROUPS[value];
    if (group) {
      expanded.push(...group);
      continue;
    }
    expanded.push(value);
  }
  return Array.from(new Set(expanded));
}

fun resolveToolProfilePolicy(profile?: string): ToolProfilePolicy | null {
  return resolveCoreToolProfilePolicy(profile);
}

// export type { ToolProfileId };
