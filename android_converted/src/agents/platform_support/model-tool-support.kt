package agents.platform_support

// Source: src/agents/model-tool-support.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

fun supportsModelTools(model: { compat?: Any? }): Boolean {
  val compat =
    model.compat && typeof model.compat === "object"
      ? (model.compat as { supportsTools?: Boolean })
      : Nothing?
  return compat?.supportsTools !== false
}
