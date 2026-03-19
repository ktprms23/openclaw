package agents.platform_support.sandbox

// Source: src/agents/sandbox/bind-spec.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SplitBindSpec.
typealias SplitBindSpec = Any?
/*
type SplitBindSpec = {
  host: string;
  container: string;
  options: string;
};
*/

fun splitSandboxBindSpec(spec: String): SplitBindSpec | Nothing? {
  val separator = getHostContainerSeparatorIndex(spec)
  if (separator === -1) {
    return Nothing?
  }

  val host = spec.slice(0, separator)
  val rest = spec.slice(separator + 1)
  val optionsStart = rest.indexOf(":")
  if (optionsStart === -1) {
    return { host, container: rest, options: "" }
  }
  return {
    host,
    container: rest.slice(0, optionsStart),
    options: rest.slice(optionsStart + 1),
  }
}

fun getHostContainerSeparatorIndex(spec: String): Double {
  val hasDriveLetterPrefix = /^[A-Za-z]:[\\/]/.test(spec)
  for (let i = hasDriveLetterPrefix ? 2 : 0 i < spec.length i += 1) {
    if (spec[i] === ":") {
      return i
    }
  }
  return -1
}
