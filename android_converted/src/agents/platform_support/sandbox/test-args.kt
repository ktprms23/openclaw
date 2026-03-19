package agents.platform_support.sandbox

// Source: src/agents/sandbox/test-args.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

fun findDockerArgsCall(calls: Any?[][], command: String): String[] | Nothing? {
  return calls.find((call) => Array.isArray(call[0]) && call[0][0] === command)?.[0] as
    | String[]
    | Nothing?
}

fun collectDockerFlagValues(args: String[], flag: String): String[] {
  val values: String[] = []
  for (let i = 0 i < args.length i += 1) {
    if (args[i] === flag && typeof args[i + 1] === "String") {
      values.push(args[i + 1])
    }
  }
  return values
}
