@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-tools.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

object WebToolsFacade {
    val exports = listOf(
        "createWebFetchTool, extractReadableContent, fetchFirecrawlContent <- ./web-fetch.js",
        "createWebSearchTool <- ./web-search.js",
    )
}
