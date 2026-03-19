package agents.tools_and_subagents

// Converted from src/agents/session-slug.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
val SLUG_ADJECTIVES = [
  "amber",
  "briny",
  "brisk",
  "calm",
  "clear",
  "cool",
  "crisp",
  "dawn",
  "delta",
  "ember",
  "faint",
  "fast",
  "fresh",
  "gentle",
  "glow",
  "good",
  "grand",
  "keen",
  "kind",
  "lucky",
  "marine",
  "mellow",
  "mild",
  "neat",
  "nimble",
  "nova",
  "oceanic",
  "plaid",
  "quick",
  "quiet",
  "rapid",
  "salty",
  "sharp",
  "swift",
  "tender",
  "tidal",
  "tidy",
  "tide",
  "vivid",
  "warm",
  "wild",
  "young",
]

val SLUG_NOUNS = [
  "atlas",
  "basil",
  "bison",
  "bloom",
  "breeze",
  "canyon",
  "cedar",
  "claw",
  "cloud",
  "comet",
  "coral",
  "cove",
  "crest",
  "crustacean",
  "daisy",
  "dune",
  "ember",
  "falcon",
  "fjord",
  "forest",
  "glade",
  "gulf",
  "harbor",
  "haven",
  "kelp",
  "lagoon",
  "lobster",
  "meadow",
  "mist",
  "nudibranch",
  "nexus",
  "ocean",
  "orbit",
  "otter",
  "pine",
  "prairie",
  "reef",
  "ridge",
  "river",
  "rook",
  "sable",
  "sage",
  "seaslug",
  "shell",
  "shoal",
  "shore",
  "slug",
  "summit",
  "tidepool",
  "trail",
  "valley",
  "wharf",
  "willow",
  "zephyr",
]

fun randomChoice(values: List<String>, fallback: String) {
  return values[Math.floor(Math.random() * values.length)] ?: fallback
}

fun createSlugBase(words = 2) {
  val parts = [randomChoice(SLUG_ADJECTIVES, "steady"), randomChoice(SLUG_NOUNS, "harbor")]
  if (words > 2) {
    parts.push(randomChoice(SLUG_NOUNS, "reef"))
  }
  return parts.join("-")
}

fun createAvailableSlug(
  words: Double,
  isIdTaken: (id: String) -> Boolean,
): String? {
  for (var attempt = 0 attempt < 12 attempt += 1) {
    val base = createSlugBase(words)
    if (!isIdTaken(base)) {
      return base
    }
    for (var i = 2 i <= 12 i += 1) {
      val candidate = `${base}-${i}`
      if (!isIdTaken(candidate)) {
        return candidate
      }
    }
  }
  return null
}

fun createSessionSlug(isTaken?: (id: String) -> Boolean): String {
  val isIdTaken = isTaken ?: (() -> false)
  val twoWord = createAvailableSlug(2, isIdTaken)
  if (twoWord) {
    return twoWord
  }
  val threeWord = createAvailableSlug(3, isIdTaken)
  if (threeWord) {
    return threeWord
  }
  val fallback = `${createSlugBase(3)}-${Math.random().toString(36).slice(2, 5)}`
  return isIdTaken(fallback) ? `${fallback}-${Date.now().toString(36)}` : fallback
}
