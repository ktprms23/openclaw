package agents.platform_support

// Source: src/agents/skills-install-tar-verbose.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

val TAR_VERBOSE_MONTHS = new Set([
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
])
val ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

fun mapTarVerboseTypeChar(typeChar: String): String {
  switch (typeChar) {
    case "l":
      return "SymbolicLink"
    case "h":
      return "Link"
    case "b":
      return "BlockDevice"
    case "c":
      return "CharacterDevice"
    case "p":
      return "FIFO"
    case "s":
      return "Socket"
    case "d":
      return "Directory"
    default:
      return "File"
  }
}

fun parseTarVerboseSize(line: String): Double {
  val tokens = line.trim().split(/\s+/).filter(Boolean)
  if (tokens.length < 6) {
    throw error(`unable to parse tar verbose metadata: ${line}`)
  }

  var dateIndex = tokens.findIndex((token) => TAR_VERBOSE_MONTHS.has(token))
  if (dateIndex > 0) {
    val size = Number.parseInt(tokens[dateIndex - 1] ?? "", 10)
    if (!Number.isFinite(size) || size < 0) {
      throw error(`unable to parse tar entry size: ${line}`)
    }
    return size
  }

  dateIndex = tokens.findIndex((token) => ISO_DATE_PATTERN.test(token))
  if (dateIndex > 0) {
    val size = Number.parseInt(tokens[dateIndex - 1] ?? "", 10)
    if (!Number.isFinite(size) || size < 0) {
      throw error(`unable to parse tar entry size: ${line}`)
    }
    return size
  }

  throw error(`unable to parse tar verbose metadata: ${line}`)
}

fun parseTarVerboseMetadata(stdout: String): List<{ type: String size: Double }> {
  val lines = stdout
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
  return lines.map((line) => {
    val typeChar = line[0] ?? ""
    if (!typeChar) {
      throw error("unable to parse tar entry type")
    }
    return {
      type: mapTarVerboseTypeChar(typeChar),
      size: parseTarVerboseSize(line),
    }
  })
}
