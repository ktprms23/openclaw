package agents.tools_and_subagents

// Converted from src/agents/tool-policy-pipeline.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { filterToolsByPolicy } from "./pi-tools.policy.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./pi-tools.types.js";
// TODO: TypeScript import retained for manual wiring: import { isKnownCoreToolId } from "./tool-catalog.js";
// TODO: TypeScript import retained for manual wiring: import {
  buildPluginToolGroups,
  expandPolicyWithPluginGroups,
  normalizeToolName,
  stripPluginOnlyAllowlist,
  type ToolPolicyLike,
} from "./tool-policy.js"

data class ToolPolicyPipelineStep(
    val policy: ToolPolicyLike?,
    val label: String,
    val stripPluginOnlyAllowlist: Boolean?,
)

fun buildDefaultToolPolicyPipelineSteps(params: {
  profilePolicy?: ToolPolicyLike
  profile?: String
  providerProfilePolicy?: ToolPolicyLike
  providerProfile?: String
  globalPolicy?: ToolPolicyLike
  globalProviderPolicy?: ToolPolicyLike
  agentPolicy?: ToolPolicyLike
  agentProviderPolicy?: ToolPolicyLike
  groupPolicy?: ToolPolicyLike
  agentId?: String
}): List<ToolPolicyPipelineStep> {
  val agentId = params.agentId?.trim()
  val profile = params.profile?.trim()
  val providerProfile = params.providerProfile?.trim()
  return [
    {
      policy: params.profilePolicy,
      label: profile ? `tools.profile (${profile})` : String /* "tools.profile" */,
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.providerProfilePolicy,
      label: providerProfile
        ? `tools.byProvider.profile (${providerProfile})`
        : String /* "tools.byProvider.profile" */,
      stripPluginOnlyAllowlist: true,
    },
    { policy: params.globalPolicy, label: String /* "tools.allow" */, stripPluginOnlyAllowlist: true },
    {
      policy: params.globalProviderPolicy,
      label: String /* "tools.byProvider.allow" */,
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.agentPolicy,
      label: agentId ? `agents.${agentId}.tools.allow` : String /* "agent tools.allow" */,
      stripPluginOnlyAllowlist: true,
    },
    {
      policy: params.agentProviderPolicy,
      label: agentId ? `agents.${agentId}.tools.byProvider.allow` : String /* "agent tools.byProvider.allow" */,
      stripPluginOnlyAllowlist: true,
    },
    { policy: params.groupPolicy, label: String /* "group tools.allow" */, stripPluginOnlyAllowlist: true },
  ]
}

fun applyToolPolicyPipeline(params: {
  tools: List<AnyAgentTool>
  toolMeta: (tool: AnyAgentTool) { pluginId: String }?
  warn: (message: String) -> Unit
  steps: List<ToolPolicyPipelineStep>
}): List<AnyAgentTool> {
  val coreToolNames = mutableSetOf(
    params.tools
      .filter((tool) -> !params.toolMeta(tool))
      .map((tool) -> normalizeToolName(tool.name))
      .filter(Boolean),
  )

  val pluginGroups = buildPluginToolGroups({
    tools: params.tools,
    toolMeta: params.toolMeta,
  })

  var filtered = params.tools
  for (val step of params.steps) {
    if (!step.policy) {
      continue
    }

    var policy: ToolPolicyLike? = step.policy
    if (step.stripPluginOnlyAllowlist) {
      val resolved = stripPluginOnlyAllowlist(policy, pluginGroups, coreToolNames)
      if (resolved.unknownAllowlist.length > 0) {
        val entries = resolved.unknownAllowlist.join(", ")
        val gatedCoreEntries = resolved.unknownAllowlist.filter((entry) ->
          isKnownCoreToolId(entry),
        )
        val otherEntries = resolved.unknownAllowlist.filter((entry) -> !isKnownCoreToolId(entry))
        val suffix = describeUnknownAllowlistSuffix({
          strippedAllowlist: resolved.strippedAllowlist,
          hasGatedCoreEntries: gatedCoreEntries.length > 0,
          hasOtherEntries: otherEntries.length > 0,
        })
        params.warn(
          `tools: ${step.label} allowlist contains Any? entries (${entries}). ${suffix}`,
        )
      }
      policy = resolved.policy
    }

    val expanded = expandPolicyWithPluginGroups(policy, pluginGroups)
    filtered = expanded ? filterToolsByPolicy(filtered, expanded) : filtered
  }
  return filtered
}

fun describeUnknownAllowlistSuffix(params: {
  strippedAllowlist: Boolean
  hasGatedCoreEntries: Boolean
  hasOtherEntries: Boolean
}): String {
  val preface = params.strippedAllowlist
    ? "Ignoring allowlist so core tools remain available."
    : ""
  val detail =
    params.hasGatedCoreEntries && params.hasOtherEntries
      ? "Some entries are shipped core tools but unavailable in the current runtime/provider/model/config other entries won't match Any? tool unless the plugin is enabled."
      : params.hasGatedCoreEntries
        ? "These entries are shipped core tools but unavailable in the current runtime/provider/model/config."
        : String /* "These entries won't match Any? tool unless the plugin is enabled." */
  return preface ? `${preface} ${detail}` : detail
}
