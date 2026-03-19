package agents.tools_and_subagents.command

// Converted from src/agents/command/types.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentInternalEvent } from "../../agents/internal-events.js";
// TODO: TypeScript import retained for manual wiring: import type { ClientToolDefinition } from "../../agents/pi-embedded-runner/run/params.js";
// TODO: TypeScript import retained for manual wiring: import type { SpawnedRunMetadata } from "../../agents/spawned-context.js";
// TODO: TypeScript import retained for manual wiring: import type { ChannelOutboundTargetMode } from "../../channels/plugins/types.js";
// TODO: TypeScript import retained for manual wiring: import type { InputProvenance } from "../../sessions/input-provenance.js";

/** Image content block for Claude API multimodal messages. */
data class ImageContent(
    val type: String /* "image" */,
    val data: String,
    val mimeType: String,
)

data class AgentStreamParams(
    val temperature: Double?,
    val maxTokens: Double?,
    val fastMode: Boolean?,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   /** Provider stream params override (best-effort). */
    //   /** Provider fast-mode override (best-effort). */
}

data class AgentRunContext(
    val messageChannel: String?,
    val accountId: String?,
    val groupId: String?,
    val groupChannel: String?,
    val groupSpace: String?,
    val currentChannelId: String?,
    val currentThreadTs: String?,
    val replyToMode: String /* "off" */ | "first" | "all"?,
    val hasRepliedRef: { value: Boolean }?,
)

data class AgentCommandOpts(
    val message: String,
    val images: List<ImageContent>?,
    val clientTools: List<ClientToolDefinition>?,
    val agentId: String?,
    val provider: String?,
    val model: String?,
    val to: String?,
    val sessionId: String?,
    val sessionKey: String?,
    val thinking: String?,
    val thinkingOnce: String?,
    val verbose: String?,
    val json: Boolean?,
    val timeout: String?,
    val deliver: Boolean?,
    val replyTo: String?,
    val replyChannel: String?,
    val replyAccountId: String?,
    val threadId: String | Double?,
    val messageChannel: String?,
    val channel: String; // delivery channel (whatsapp|telegram|...)?,
    val accountId: String?,
    val runContext: AgentRunContext?,
    val senderIsOwner: Boolean?,
    val allowModelOverride: Boolean?,
    val groupId: SpawnedRunMetadata["groupId"]?,
    val groupChannel: SpawnedRunMetadata["groupChannel"]?,
    val groupSpace: SpawnedRunMetadata["groupSpace"]?,
    val spawnedBy: SpawnedRunMetadata["spawnedBy"]?,
    val deliveryTargetMode: ChannelOutboundTargetMode?,
    val bestEffortDeliver: Boolean?,
    val abortSignal: AbortSignal /* TODO */?,
    val lane: String?,
    val runId: String?,
    val extraSystemPrompt: String?,
    val internalEvents: List<AgentInternalEvent>?,
    val inputProvenance: InputProvenance?,
    val streamParams: AgentStreamParams?,
    val workspaceDir: SpawnedRunMetadata["workspaceDir"]?,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   /** Optional image attachments for multimodal messages. */
    //   /** Optional client-provided tools (OpenResponses hosted tools). */
    //   /** Agent id override (must exist in config). */
    //   /** Per-run provider override. */
    //   /** Per-run model override. */
    //   /** Override delivery target (separate from session routing). */
    //   /** Override delivery channel (separate from session routing). */
    //   /** Override delivery account id (separate from session routing). */
    //   /** Override delivery thread/topic id (separate from session routing). */
    //   /** Message channel context (webchat|voicewake|whatsapp|...). */
    //   /** Account ID for multi-account channel routing (e.g., WhatsApp account). */
    //   /** Context for embedded run routing (channel/account/thread). */
    //   /** Whether this caller is authorized for owner-only tools (defaults true for local CLI calls). */
    //   /** Whether this caller is authorized to use provider/model per-run overrides. */
    //   /** Group/spawn metadata for subagent policy inheritance and routing context. */
    //   /** Per-call stream param overrides (best-effort). */
    //   /** Explicit workspace directory override (for subagents to inherit parent workspace). */
}

type AgentCommandIngressOpts = Omit<
  AgentCommandOpts,
  "senderIsOwner" | "allowModelOverride"
> & {
  /** Ingress callsites must always pass explicit owner-tool authorization state. */
  senderIsOwner: Boolean
  /** Ingress callsites must always pass explicit model-override authorization state. */
  allowModelOverride: Boolean
}
