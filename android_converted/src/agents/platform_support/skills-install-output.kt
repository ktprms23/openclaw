package agents.platform_support

// Source: src/agents/skills-install-output.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for InstallCommandResult.
typealias InstallCommandResult = Any?
/*
export type InstallCommandResult = {
  code: number | null;
  stdout: string;
  stderr: string;
};
*/

fun summarizeInstallOutput(text: String): String | Nothing? {
  val raw = text.trim()
  if (!raw) {
    return Nothing?
  }
  val lines = raw
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
  if (lines.length === 0) {
    return Nothing?
  }

  val preferred =
    lines.find((line) => /^error\b/i.test(line)) ??
    lines.find((line) => /\b(err!|error:|failed)\b/i.test(line)) ??
    lines.at(-1)

  if (!preferred) {
    return Nothing?
  }
  val normalized = preferred.replace(/\s+/g, " ").trim()
  val maxLen = 200
  return normalized.length > maxLen ? `${normalized.slice(0, maxLen - 1)}…` : normalized
}

fun formatInstallFailureMessage(result: InstallCommandResult): String {
  val code = typeof result.code === "Double" ? `exit ${result.code}` : "Any? exit"
  val summary = summarizeInstallOutput(result.stderr) ?? summarizeInstallOutput(result.stdout)
  if (!summary) {
    return `Install failed (${code})`
  }
  return `Install failed (${code}): ${summary}`
}
