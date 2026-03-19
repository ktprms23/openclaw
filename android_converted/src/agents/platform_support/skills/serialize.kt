package agents.platform_support.skills

// Source: src/agents/skills/serialize.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

val SKILLS_SYNC_QUEUE = new Map<String, Promise<Any?>>()

suspend fun serializeByKey<T>(key: String, task: () => Promise<T>) {
  val prev = SKILLS_SYNC_QUEUE.get(key) ?? Promise.resolve()
  val next = prev.then(task, task)
  SKILLS_SYNC_QUEUE.set(key, next)
  try {
    return await next
  } finally {
    if (SKILLS_SYNC_QUEUE.get(key) === next) {
      SKILLS_SYNC_QUEUE.delete(key)
    }
  }
}
