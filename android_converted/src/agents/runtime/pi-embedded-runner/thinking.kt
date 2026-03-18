@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/thinking.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun isAssistantMessageWithContent(/* parameters preserved from TypeScript source */): message is AssistantMessage {
    TODO("Port runtime function isAssistantMessageWithContent from src/agents/pi-embedded-runner/thinking.ts")
}

fun dropThinkingBlocks(/* parameters preserved from TypeScript source */): List<AgentMessage> {
    TODO("Port runtime function dropThinkingBlocks from src/agents/pi-embedded-runner/thinking.ts")
}

/*
Original imports retained for mapping context:
import type { AgentMessage } from "@mariozechner/pi-agent-core";
*/

/*
Original TypeScript reference:
import type { AgentMessage } from "@mariozechner/pi-agent-core";

type AssistantContentBlock = Extract<AgentMessage, { role: "assistant" }>["content"][number];
type AssistantMessage = Extract<AgentMessage, { role: "assistant" }>;

export function isAssistantMessageWithContent(message: AgentMessage): message is AssistantMessage {
  return (
    !!message &&
    typeof message === "object" &&
    message.role === "assistant" &&
    Array.isArray(message.content)
  );
}

/**
 * Strip all `type: "thinking"` content blocks from assistant messages.
 *
 * If an assistant message becomes empty after stripping, it is replaced with
 * a synthetic `{ type: "text", text: "" }` block to preserve turn structure
 * (some providers require strict user/assistant alternation).
 *
 * Returns the original array reference when nothing was changed (callers can
 * use reference equality to skip downstream work).
 * /
export function dropThinkingBlocks(messages: AgentMessage[]): AgentMessage[] {
  let touched = false;
  const out: AgentMessage[] = [];
  for (const msg of messages) {
    if (!isAssistantMessageWithContent(msg)) {
      out.push(msg);
      continue;
    }
    const nextContent: AssistantContentBlock[] = [];
    let changed = false;
    for (const block of msg.content) {
      if (block && typeof block === "object" && (block as { type?: unknown }).type === "thinking") {
        touched = true;
        changed = true;
        continue;
      }
      nextContent.push(block);
    }
    if (!changed) {
      out.push(msg);
      continue;
    }
    // Preserve the assistant turn even if all blocks were thinking-only.
    const content =
      nextContent.length > 0 ? nextContent : [{ type: "text", text: "" } as AssistantContentBlock];
    out.push({ ...msg, content });
  }
  return touched ? out : messages;
}

*/
