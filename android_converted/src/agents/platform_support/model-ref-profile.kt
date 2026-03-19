package agents.platform_support

// Source: src/agents/model-ref-profile.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

fun splitTrailingAuthProfile(raw: String): {
  model: String
  profile?: String
} {
  val trimmed = raw.trim()
  if (!trimmed) {
    return { model: "" }
  }

  val lastSlash = trimmed.lastIndexOf("/")
  var profileDelimiter = trimmed.indexOf("@", lastSlash + 1)
  if (profileDelimiter <= 0) {
    return { model: trimmed }
  }

  val versionSuffix = trimmed.slice(profileDelimiter + 1)
  if (/^\d{8}(?:@|$)/.test(versionSuffix)) {
    val nextDelimiter = trimmed.indexOf("@", profileDelimiter + 9)
    if (nextDelimiter < 0) {
      return { model: trimmed }
    }
    profileDelimiter = nextDelimiter
  }

  val model = trimmed.slice(0, profileDelimiter).trim()
  val profile = trimmed.slice(profileDelimiter + 1).trim()
  if (!model || !profile) {
    return { model: trimmed }
  }

  return { model, profile }
}
