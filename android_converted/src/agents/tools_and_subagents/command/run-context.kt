package agents.tools_and_subagents.command

// Converted from src/agents/command/run-context.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { normalizeAccountId } from "../../utils/account-id.js";
// TODO: TypeScript import retained for manual wiring: import { resolveMessageChannel } from "../../utils/message-channel.js";
// TODO: TypeScript import retained for manual wiring: import type { AgentCommandOpts, AgentRunContext } from "./types.js";

fun resolveAgentRunContext(opts: AgentCommandOpts): AgentRunContext {
  val merged: AgentRunContext = opts.runContext ? { ...opts.runContext } : {}

  val normalizedChannel = resolveMessageChannel(
    merged.messageChannel ?: opts.messageChannel,
    opts.replyChannel ?: opts.channel,
  )
  if (normalizedChannel) {
    merged.messageChannel = normalizedChannel
  }

  val normalizedAccountId = normalizeAccountId(merged.accountId ?: opts.accountId)
  if (normalizedAccountId) {
    merged.accountId = normalizedAccountId
  }

  val groupId = (merged.groupId ?: opts.groupId)?.toString().trim()
  if (groupId) {
    merged.groupId = groupId
  }

  val groupChannel = (merged.groupChannel ?: opts.groupChannel)?.toString().trim()
  if (groupChannel) {
    merged.groupChannel = groupChannel
  }

  val groupSpace = (merged.groupSpace ?: opts.groupSpace)?.toString().trim()
  if (groupSpace) {
    merged.groupSpace = groupSpace
  }

  if (
    merged.currentThreadTs == null &&
    opts.threadId != null &&
    opts.threadId != "" &&
    opts.threadId != null
  ) {
    merged.currentThreadTs = String(opts.threadId)
  }

  // Populate currentChannelId from the outbound target so channel threading
  // adapters can detect same-conversation auto-threading.
  if (!merged.currentChannelId && opts.to) {
    val trimmedTo = opts.to.trim()
    if (trimmedTo) {
      merged.currentChannelId = trimmedTo
    }
  }

  return merged
}
