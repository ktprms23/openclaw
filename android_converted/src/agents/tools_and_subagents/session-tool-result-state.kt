package agents.tools_and_subagents

// Converted from src/agents/session-tool-result-state.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
typealias PendingToolCall = { id: String; name?: String }

data class PendingToolCallState(
    val size: () => Double,
    val entries: () => IterableIterator<[String, String?]>,
    val getToolName: (id: String) => String?,
    val delete: (id: String) => Unit,
    val clear: () => Unit,
    val trackToolCalls: (calls: List<PendingToolCall>) => Unit,
    val getPendingIds: () => List<String>,
    val shouldFlushForSanitizedDrop: () => Boolean,
    val shouldFlushBeforeNonToolResult: (nextRole: Any?, toolCallCount: Double) => Boolean,
    val shouldFlushBeforeNewToolCalls: (toolCallCount: Double) => Boolean,
)

fun createPendingToolCallState(): PendingToolCallState {
  val pending = new MutableMap<String, String?>()

  return {
    size: () -> pending.size,
    entries: () -> pending.entries(),
    getToolName: (id: String) -> pending.get(id),
    delete: (id: String) {
      pending.delete(id)
    },
    clear: () {
      pending.clear()
    },
    trackToolCalls: (calls: List<PendingToolCall>) {
      for (val call of calls) {
        pending.set(call.id, call.name)
      }
    },
    getPendingIds: () -> Array.from(pending.keys()),
    shouldFlushForSanitizedDrop: () -> pending.size > 0,
    shouldFlushBeforeNonToolResult: (nextRole: Any?, toolCallCount: Double) ->
      pending.size > 0 && (toolCallCount == 0 || nextRole != "assistant"),
    shouldFlushBeforeNewToolCalls: (toolCallCount: Double) -> pending.size > 0 && toolCallCount > 0,
  }
}
