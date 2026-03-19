@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/session-tool-result-state.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

typealias PendingToolCall = Any /* TODO: translate TypeScript alias */
typealias PendingToolCallState = Any /* TODO: translate TypeScript alias */

fun createPendingToolCallState(): PendingToolCallState {
  val pending = new Map<string, string | null>();

  return {
    size: () => pending.size,
    entries: () => pending.entries(),
    getToolName: (id: string) => pending.get(id),
    delete: (id: string) => {
      pending.delete(id);
    },
    clear: () => {
      pending.clear();
    },
    trackToolCalls: (calls: PendingToolCall[]) => {
      for (val call of calls) {
        pending.set(call.id, call.name);
      }
    },
    getPendingIds: () => Array.from(pending.keys()),
    shouldFlushForSanitizedDrop: () => pending.size > 0,
    shouldFlushBeforeNonToolResult: (nextRole: unknown, toolCallCount: number) =>
      pending.size > 0 && (toolCallCount == 0 || nextRole != "assistant"),
    shouldFlushBeforeNewToolCalls: (toolCallCount: number) => pending.size > 0 && toolCallCount > 0,
  };
}
