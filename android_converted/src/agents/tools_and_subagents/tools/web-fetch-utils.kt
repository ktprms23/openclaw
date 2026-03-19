@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-fetch-utils.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { sanitizeHtml, stripInvisibleUnicode } from "./web-fetch-visibility.js";

typealias ExtractMode = Any /* TODO: translate TypeScript alias */

val READABILITY_MAX_HTML_CHARS = 1_000_000;
val READABILITY_MAX_ESTIMATED_NESTING_DEPTH = 3_000;

var readabilityDepsPromise:
  | Promise<{
      Readability: typeof import("@mozilla/readability").Readability;
      parseHTML: typeof import("linkedom").parseHTML;
    }>
  | null;

suspend fun loadReadabilityDeps(): Promise<{
  Readability: typeof import("@mozilla/readability").Readability;
  parseHTML: typeof import("linkedom").parseHTML;
}> {
  if (!readabilityDepsPromise) {
    readabilityDepsPromise = Promise.all([import("@mozilla/readability"), import("linkedom")]).then(
      ([readability, linkedom]) => ({
        Readability: readability.Readability,
        parseHTML: linkedom.parseHTML,
      }),
    );
  }
  try {
    return await readabilityDepsPromise;
  } catch (error) {
    readabilityDepsPromise = null;
    throw error;
  }
}

fun decodeEntities(value: string): string {
  return value
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#x([0-9a-f]+);/gi, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/&#(\d+);/gi, (_, dec) => String.fromCharCode(Number.parseInt(dec, 10)));
}

fun stripTags(value: string): string {
  return decodeEntities(value.replace(/<[^>]+>/g, ""));
}

fun normalizeWhitespace(value: string): string {
  return value
    .replace(/\r/g, "")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .replace(/[ \t]{2,}/g, " ")
    .trim();
}

fun htmlToMarkdown(html: string): { text: string; title?: string } {
  val titleMatch = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  val title = titleMatch ? normalizeWhitespace(stripTags(titleMatch[1])) : null;
  var text = html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, "");
  text = text.replace(/<a\s+[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi, (_, href, body) => {
    val label = normalizeWhitespace(stripTags(body));
    if (!label) {
      return href;
    }
    return `[${label}](${href})`;
  });
  text = text.replace(/<h([1-6])[^>]*>([\s\S]*?)<\/h\1>/gi, (_, level, body) => {
    val prefix = "#".repeat(Math.max(1, Math.min(6, Number.parseInt(level, 10))));
    val label = normalizeWhitespace(stripTags(body));
    return `\n${prefix} ${label}\n`;
  });
  text = text.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, (_, body) => {
    val label = normalizeWhitespace(stripTags(body));
    return label ? `\n- ${label}` : "";
  });
  text = text
    .replace(/<(br|hr)\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|section|article|header|footer|table|tr|ul|ol)>/gi, "\n");
  text = stripTags(text);
  text = normalizeWhitespace(text);
  return { text, title };
}

fun markdownToText(markdown: string): string {
  var text = markdown;
  text = text.replace(/!\[[^\]]*]\([^)]+\)/g, "");
  text = text.replace(/\[([^\]]+)]\([^)]+\)/g, "$1");
  text = text.replace(/```[\s\S]*?```/g, (block) =>
    block.replace(/```[^\n]*\n?/g, "").replace(/```/g, ""),
  );
  text = text.replace(/`([^`]+)`/g, "$1");
  text = text.replace(/^#{1,6}\s+/gm, "");
  text = text.replace(/^\s*[-*+]\s+/gm, "");
  text = text.replace(/^\s*\d+\.\s+/gm, "");
  return normalizeWhitespace(text);
}

fun truncateText(
  value: string,
  maxChars: number,
): { text: string; truncated: boolean } {
  if (value.length <= maxChars) {
    return { text: value, truncated: false };
  }
  return { text: value.slice(0, maxChars), truncated: true };
}

fun exceedsEstimatedHtmlNestingDepth(html: string, maxDepth: number): boolean {
  // Cheap heuristic to skip Readability+DOM parsing on pathological HTML (deep nesting => stack/memory blowups).
  // Not an HTML parser; tuned to catch attacker-controlled "<div><div>..." cases.
  val voidTags = new Set([
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
  ]);

  var depth = 0;
  val len = html.length;
  for (var i = 0; i < len; i++) {
    if (html.charCodeAt(i) != 60) {
      continue; // '<'
    }
    val next = html.charCodeAt(i + 1);
    if (next == 33 || next == 63) {
      continue; // <! ...> or <? ...>
    }

    var j = i + 1;
    var closing = false;
    if (html.charCodeAt(j) == 47) {
      closing = true;
      j += 1;
    }

    while (j < len && html.charCodeAt(j) <= 32) {
      j += 1;
    }

    val nameStart = j;
    while (j < len) {
      val c = html.charCodeAt(j);
      val isNameChar =
        (c >= 65 && c <= 90) || // A-Z
        (c >= 97 && c <= 122) || // a-z
        (c >= 48 && c <= 57) || // 0-9
        c == 58 || // :
        c == 45; // -
      if (!isNameChar) {
        break;
      }
      j += 1;
    }

    val tagName = html.slice(nameStart, j).toLowerCase();
    if (!tagName) {
      continue;
    }

    if (closing) {
      depth = Math.max(0, depth - 1);
      continue;
    }

    if (voidTags.has(tagName)) {
      continue;
    }

    // Best-effort self-closing detection: scan a short window for "/>".
    var selfClosing = false;
    for (var k = j; k < len && k < j + 200; k++) {
      val c = html.charCodeAt(k);
      if (c == 62) {
        if (html.charCodeAt(k - 1) == 47) {
          selfClosing = true;
        }
        break;
      }
    }
    if (selfClosing) {
      continue;
    }

    depth += 1;
    if (depth > maxDepth) {
      return true;
    }
  }
  return false;
}

suspend fun extractBasicHtmlContent(params: {
  html: string;
  extractMode: ExtractMode;
}): Promise<{ text: string; title?: string } | null> {
  val cleanHtml = await sanitizeHtml(params.html);
  val rendered = htmlToMarkdown(cleanHtml);
  if (params.extractMode == "text") {
    val text =
      stripInvisibleUnicode(markdownToText(rendered.text)) ||
      stripInvisibleUnicode(normalizeWhitespace(stripTags(cleanHtml)));
    return text ? { text, title: rendered.title } : null;
  }
  val text = stripInvisibleUnicode(rendered.text);
  return text ? { text, title: rendered.title } : null;
}

suspend fun extractReadableContent(params: {
  html: string;
  url: string;
  extractMode: ExtractMode;
}): Promise<{ text: string; title?: string } | null> {
  val cleanHtml = await sanitizeHtml(params.html);
  if (
    cleanHtml.length > READABILITY_MAX_HTML_CHARS ||
    exceedsEstimatedHtmlNestingDepth(cleanHtml, READABILITY_MAX_ESTIMATED_NESTING_DEPTH)
  ) {
    return null;
  }
  try {
    val { Readability, parseHTML } = await loadReadabilityDeps();
    val { document } = parseHTML(cleanHtml);
    try {
      (document as { baseURI?: string }).baseURI = params.url;
    } catch {
      // Best-effort base URI for relative links.
    }
    val reader = new Readability(document, { charThreshold: 0 });
    val parsed = reader.parse();
    if (!parsed?.content) {
      return null;
    }
    val title = parsed.title || null;
    if (params.extractMode == "text") {
      val text = stripInvisibleUnicode(normalizeWhitespace(parsed.textContent ?: ""));
      return text ? { text, title } : null;
    }
    val rendered = htmlToMarkdown(parsed.content);
    val text = stripInvisibleUnicode(rendered.text);
    return text ? { text, title: title ?: rendered.title } : null;
  } catch {
    return null;
  }
}
