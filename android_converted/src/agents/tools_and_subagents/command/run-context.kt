@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/command/run-context.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { normalizeAccountId } from "../../utils/account-id.js";
// TODO(port-deps): import { resolveMessageChannel } from "../../utils/message-channel.js";
// TODO(port-deps): import type { AgentCommandOpts, AgentRunContext } from "./types.js";

fun resolveAgentRunContext(opts: AgentCommandOpts): AgentRunContext {
  val merged: AgentRunContext = opts.runContext ? { ...opts.runContext } : {};

  val normalizedChannel = resolveMessageChannel(
    merged.messageChannel ?: opts.messageChannel,
    opts.replyChannel ?: opts.channel,
  );
  if (normalizedChannel) {
    merged.messageChannel = normalizedChannel;
  }

  val normalizedAccountId = normalizeAccountId(merged.accountId ?: opts.accountId);
  if (normalizedAccountId) {
    merged.accountId = normalizedAccountId;
  }

  val groupId = (merged.groupId ?: opts.groupId)?.toString().trim();
  if (groupId) {
    merged.groupId = groupId;
  }

  val groupChannel = (merged.groupChannel ?: opts.groupChannel)?.toString().trim();
  if (groupChannel) {
    merged.groupChannel = groupChannel;
  }

  val groupSpace = (merged.groupSpace ?: opts.groupSpace)?.toString().trim();
  if (groupSpace) {
    merged.groupSpace = groupSpace;
  }

  if (
    merged.currentThreadTs == null &&
    opts.threadId != null &&
    opts.threadId != "" &&
    opts.threadId != null
  ) {
    merged.currentThreadTs = String(opts.threadId);
  }

  // Populate currentChannelId from the outbound target so channel threading
  // adapters can detect same-conversation auto-threading.
  if (!merged.currentChannelId && opts.to) {
    val trimmedTo = opts.to.trim();
    if (trimmedTo) {
      merged.currentChannelId = trimmedTo;
    }
  }

  return merged;
}
