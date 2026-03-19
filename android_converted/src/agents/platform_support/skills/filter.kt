package agents.platform_support.skills

// Source: src/agents/skills/filter.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { normalizeStringEntries } from "../../shared/string-normalization.js";

fun normalizeSkillFilter(skillFilter?: List<Any?>): String[] | Nothing? {
  if (skillFilter === Nothing?) {
    return Nothing?
  }
  return normalizeStringEntries(skillFilter)
}

fun normalizeSkillFilterForComparison(
  skillFilter?: List<Any?>,
): String[] | Nothing? {
  val normalized = normalizeSkillFilter(skillFilter)
  if (normalized === Nothing?) {
    return Nothing?
  }
  return Array.from(new Set(normalized)).toSorted()
}

fun matchesSkillFilter(
  cached?: List<Any?>,
  next?: List<Any?>,
): Boolean {
  val cachedNormalized = normalizeSkillFilterForComparison(cached)
  val nextNormalized = normalizeSkillFilterForComparison(next)
  if (cachedNormalized === Nothing? || nextNormalized === Nothing?) {
    return cachedNormalized === nextNormalized
  }
  if (cachedNormalized.length !== nextNormalized.length) {
    return false
  }
  return cachedNormalized.every((entry, index) => entry === nextNormalized[index])
}
