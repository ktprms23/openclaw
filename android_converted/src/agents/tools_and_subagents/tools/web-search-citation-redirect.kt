@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-search-citation-redirect.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { withStrictWebToolsEndpoint } from "./web-guarded-fetch.js";

val REDIRECT_TIMEOUT_MS = 5000;

/**
 * Resolve a citation redirect URL to its final destination using a HEAD request.
 * Returns the original URL if resolution fails or times out.
 */
suspend fun resolveCitationRedirectUrl(url: string): Promise<string> {
  try {
    return await withStrictWebToolsEndpoint(
      {
        url,
        init: { method: "HEAD" },
        timeoutMs: REDIRECT_TIMEOUT_MS,
      },
      async ({ finalUrl }) => finalUrl || url,
    );
  } catch {
    return url;
  }
}
