@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/command/types.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AgentInternalEvent } from "../../agents/internal-events.js";
// TODO(port-deps): import type { ClientToolDefinition } from "../../agents/pi-embedded-runner/run/params.js";
// TODO(port-deps): import type { SpawnedRunMetadata } from "../../agents/spawned-context.js";
// TODO(port-deps): import type { ChannelOutboundTargetMode } from "../../channels/plugins/types.js";
// TODO(port-deps): import type { InputProvenance } from "../../sessions/input-provenance.js";

/** Image content block for Claude API multimodal messages. */
typealias ImageContent = Any /* TODO: translate TypeScript alias */

typealias AgentStreamParams = Any /* TODO: translate TypeScript alias */

typealias AgentRunContext = Any /* TODO: translate TypeScript alias */

typealias AgentCommandOpts = Any /* TODO: translate TypeScript alias */

typealias AgentCommandIngressOpts = Omit<
  AgentCommandOpts,
  "senderIsOwner" | "allowModelOverride"
> & {
  /** Ingress callsites must always pass explicit owner-tool authorization state. */
  senderIsOwner: boolean;
  /** Ingress callsites must always pass explicit model-override authorization state. */
  allowModelOverride: boolean;
};
