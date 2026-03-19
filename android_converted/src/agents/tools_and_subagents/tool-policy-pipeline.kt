@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-policy-pipeline.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { filterToolsByPolicy } from "./pi-tools.policy.js";
// TODO(port-deps): import type { AnyAgentTool } from "./pi-tools.types.js";
// TODO(port-deps): import { isKnownCoreToolId } from "./tool-catalog.js";
// TODO(port-deps): import {
// TODO(port-deps): buildPluginToolGroups,
// TODO(port-deps): expandPolicyWithPluginGroups,
// TODO(port-deps): normalizeToolName,
// TODO(port-deps): stripPluginOnlyAllowlist,
// TODO(port-deps): type ToolPolicyLike,
// TODO(port-deps): } from "./tool-policy.js";

typealias ToolPolicyPipelineStep = Any /* TODO: translate TypeScript alias */

fun buildDefaultToolPolicyPipelineSteps(params: {
  profilePolicy?: ToolPolicyLike;
  profile?: string;
  providerProfilePolicy?: ToolPolicyLike;
  providerProfile?: string;
  globalPolicy?: ToolPolicyLike;
  globalProviderPolicy?: ToolPolicyLike;
  agentPolicy?: ToolPolicyLike;
  agentProviderPolicy?: ToolPolicyLike;
  groupPolicy?: ToolPolicyLike;
  agentId?: string;
}): ToolPolicyPipelineStep[] {
  val agentId = params.agentId?.trim();
  val profile = params.profile?.trim();
  val providerProfile = params.providerProfile?.trim();
  return [
    {
      policy: params.profilePolicy,
      label: profile ? `tools.profile (${profile})` : "tools.profile",
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.providerProfilePolicy,
      label: providerProfile
        ? `tools.byProvider.profile (${providerProfile})`
        : "tools.byProvider.profile",
      stripPluginOnlyAllowlist: true,
    },
    { policy: params.globalPolicy, label: "tools.allow", stripPluginOnlyAllowlist: true },
    {
      policy: params.globalProviderPolicy,
      label: "tools.byProvider.allow",
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.agentPolicy,
      label: agentId ? `agents.${agentId}.tools.allow` : "agent tools.allow",
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.agentProviderPolicy,
      label: agentId ? `agents.${agentId}.tools.byProvider.allow` : "agent tools.byProvider.allow",
      stripPluginOnlyAllowlist: true,
    },
    { policy: params.groupPolicy, label: "group tools.allow", stripPluginOnlyAllowlist: true },
  ];
}

fun applyToolPolicyPipeline(params: {
  tools: AnyAgentTool[];
  toolMeta: (tool: AnyAgentTool) => { pluginId: string } | null;
  warn: (message: string) => void;
  steps: ToolPolicyPipelineStep[];
}): AnyAgentTool[] {
  val coreToolNames = new Set(
    params.tools
      .filter((tool) => !params.toolMeta(tool))
      .map((tool) => normalizeToolName(tool.name))
      .filter(Boolean),
  );

  val pluginGroups = buildPluginToolGroups({
    tools: params.tools,
    toolMeta: params.toolMeta,
  });

  var filtered = params.tools;
  for (val step of params.steps) {
    if (!step.policy) {
      continue;
    }

    var policy: ToolPolicyLike | null = step.policy;
    if (step.stripPluginOnlyAllowlist) {
      val resolved = stripPluginOnlyAllowlist(policy, pluginGroups, coreToolNames);
      if (resolved.unknownAllowlist.length > 0) {
        val entries = resolved.unknownAllowlist.join(", ");
        val gatedCoreEntries = resolved.unknownAllowlist.filter((entry) =>
          isKnownCoreToolId(entry),
        );
        val otherEntries = resolved.unknownAllowlist.filter((entry) => !isKnownCoreToolId(entry));
        val suffix = describeUnknownAllowlistSuffix({
          strippedAllowlist: resolved.strippedAllowlist,
          hasGatedCoreEntries: gatedCoreEntries.length > 0,
          hasOtherEntries: otherEntries.length > 0,
        });
        params.warn(
          `tools: ${step.label} allowlist contains unknown entries (${entries}). ${suffix}`,
        );
      }
      policy = resolved.policy;
    }

    val expanded = expandPolicyWithPluginGroups(policy, pluginGroups);
    filtered = expanded ? filterToolsByPolicy(filtered, expanded) : filtered;
  }
  return filtered;
}

fun describeUnknownAllowlistSuffix(params: {
  strippedAllowlist: boolean;
  hasGatedCoreEntries: boolean;
  hasOtherEntries: boolean;
}): string {
  val preface = params.strippedAllowlist
    ? "Ignoring allowlist so core tools remain available."
    : "";
  val detail =
    params.hasGatedCoreEntries && params.hasOtherEntries
      ? "Some entries are shipped core tools but unavailable in the current runtime/provider/model/config; other entries won't match any tool unless the plugin is enabled."
      : params.hasGatedCoreEntries
        ? "These entries are shipped core tools but unavailable in the current runtime/provider/model/config."
        : "These entries won't match any tool unless the plugin is enabled.";
  return preface ? `${preface} ${detail}` : detail;
}
