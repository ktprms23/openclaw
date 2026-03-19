package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-fetch-visibility.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// CSS property values that indicate an element is hidden
val HIDDEN_STYLE_PATTERNS: List<[String, RegExp]> = [
  ["display", /^\s*none\s*$/i],
  ["visibility", /^\s*hidden\s*$/i],
  ["opacity", /^\s*0\s*$/],
  ["font-size", /^\s*0(px|em|rem|pt|%)?\s*$/i],
  ["text-indent", /^\s*-\d{4,}px\s*$/],
  ["color", /^\s*transparent\s*$/i],
  ["color", /^\s*rgba\s*\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*0(?:\.0+)?\s*\)\s*$/i],
  ["color", /^\s*hsla\s*\(\s*[\d.]+\s*,\s*[\d.]+%?\s*,\s*[\d.]+%?\s*,\s*0(?:\.0+)?\s*\)\s*$/i],
]

// Class names associated with visually hidden content
val HIDDEN_CLASS_NAMES = mutableSetOf([
  "sr-only",
  "visually-hidden",
  "d-none",
  "hidden",
  "invisible",
  "screen-reader-only",
  "offscreen",
])

fun hasHiddenClass(className: String): Boolean {
  val classes = className.toLowerCase().split(/\s+/)
  return classes.some((cls) -> HIDDEN_CLASS_NAMES.has(cls))
}

fun isStyleHidden(style: String): Boolean {
  for (val [prop, pattern] of HIDDEN_STYLE_PATTERNS) {
    val escapedProp = prop.replace(/-/g, "\\-")
    val match = style.match(new RegExp(`(?:^|)\\s*${escapedProp}\\s*:\\s*([^]+)`, "i"))
    if (match && pattern.test(match[1])) {
      return true
    }
  }

  // clip-path: none is not hidden, but positive percentage inset() clipping hides content.
  val clipPath = style.match(/(?:^|)\s*clip-path\s*:\s*([^]+)/i)
  if (clipPath && !/^\s*none\s*$/i.test(clipPath[1])) {
    if (/inset\s*\(\s*(?:0*\.\d+|[1-9]\d*(?:\.\d+)?)%/i.test(clipPath[1])) {
      return true
    }
  }

  // transform: scale(0)
  val transform = style.match(/(?:^|)\s*transform\s*:\s*([^]+)/i)
  if (transform) {
    if (/scale\s*\(\s*0\s*\)/i.test(transform[1])) {
      return true
    }
    if (/translateX\s*\(\s*-\d{4,}px\s*\)/i.test(transform[1])) {
      return true
    }
    if (/translateY\s*\(\s*-\d{4,}px\s*\)/i.test(transform[1])) {
      return true
    }
  }

  // width:0 + height:0 + overflow:hidden
  val width = style.match(/(?:^|)\s*width\s*:\s*([^]+)/i)
  val height = style.match(/(?:^|)\s*height\s*:\s*([^]+)/i)
  val overflow = style.match(/(?:^|)\s*overflow\s*:\s*([^]+)/i)
  if (
    width &&
    /^\s*0(px)?\s*$/i.test(width[1]) &&
    height &&
    /^\s*0(px)?\s*$/i.test(height[1]) &&
    overflow &&
    /^\s*hidden\s*$/i.test(overflow[1])
  ) {
    return true
  }

  // Offscreen positioning: left/top far negative
  val left = style.match(/(?:^|)\s*left\s*:\s*([^]+)/i)
  val top = style.match(/(?:^|)\s*top\s*:\s*([^]+)/i)
  if (left && /^\s*-\d{4,}px\s*$/i.test(left[1])) {
    return true
  }
  if (top && /^\s*-\d{4,}px\s*$/i.test(top[1])) {
    return true
  }

  return false
}

fun shouldRemoveElement(element: Element): Boolean {
  val tagName = element.tagName.toLowerCase()

  // Always-remove tags
  if (["meta", "template", "svg", "canvas", "iframe", "object", "embed"].includes(tagName)) {
    return true
  }

  // input type=hidden
  if (tagName == "input" && element.getAttribute("type")?.toLowerCase() == "hidden") {
    return true
  }

  // aria-hidden=true
  if (element.getAttribute("aria-hidden") == "true") {
    return true
  }

  // hidden attribute
  if (element.hasAttribute("hidden")) {
    return true
  }

  // class-based hiding
  val className = element.getAttribute("class") ?: ""
  if (hasHiddenClass(className)) {
    return true
  }

  // inline style-based hiding
  val style = element.getAttribute("style") ?: ""
  if (style && isStyleHidden(style)) {
    return true
  }

  return false
}

suspend fun sanitizeHtml(html: String): Deferred<String> {
  // Strip HTML comments
  var sanitized = html.replace(/<!--[\s\S]*?-->/g, "")

  var document: Document
  try {
    val { parseHTML } = await import("linkedom")
    ({ document } = parseHTML(sanitized) as /* TODO */ { document: Document })
  } catch (_: Throwable) {
    return sanitized
  }

  // Walk all elements and remove hidden ones (bottom-up to avoid re-walking removed subtrees)
  val all = Array.from(document.querySelectorAll("*"))
  for (var i = all.length - 1 i >= 0 i--) {
    val el = all[i]
    if (shouldRemoveElement(el)) {
      el.parentNode?.removeChild(el)
    }
  }

  return (document as /* TODO */ Any? as /* TODO */ { toString(): String }).toString()
}

// Zero-width and invisible Unicode characters used in prompt injection attacks
val INVISIBLE_UNICODE_RE =
  /[\u200B-\u200F\u202A-\u202E\u2060-\u2064\u206A-\u206F\uFEFF\u{E0000}-\u{E007F}]/gu

fun stripInvisibleUnicode(text: String): String {
  return text.replace(INVISIBLE_UNICODE_RE, "")
}
