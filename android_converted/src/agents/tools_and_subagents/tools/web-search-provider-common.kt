@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-search-provider-common.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO(port-deps): import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";
// TODO(port-deps): import { withTrustedWebToolsEndpoint } from "./web-guarded-fetch.js";
// TODO(port-deps): import {
// TODO(port-deps): CacheEntry,
// TODO(port-deps): DEFAULT_CACHE_TTL_MINUTES,
// TODO(port-deps): DEFAULT_TIMEOUT_SECONDS,
// TODO(port-deps): normalizeCacheKey,
// TODO(port-deps): readCache,
// TODO(port-deps): readResponseText,
// TODO(port-deps): resolveCacheTtlMs,
// TODO(port-deps): resolveTimeoutSeconds,
// TODO(port-deps): writeCache,
// TODO(port-deps): } from "./web-shared.js";

typealias SearchConfigRecord = NonNullable<OpenClawConfig["tools"]>["web"] extends infer Web
  ? Web extends { search?: infer Search }
    ? Search extends Record<string, unknown>
      ? Search
      : Record<string, unknown>
    : Record<string, unknown>
  : Record<string, unknown>;

val DEFAULT_SEARCH_COUNT = 5;
val MAX_SEARCH_COUNT = 10;

val SEARCH_CACHE_KEY = Symbol.for("openclaw.web-search.cache");

fun getSharedSearchCache(): Map<string, CacheEntry<Record<string, unknown>>> {
  val root = globalThis as Record<PropertyKey, unknown>;
  val existing = root[SEARCH_CACHE_KEY];
  if (existing instanceof Map) {
    return existing as Map<string, CacheEntry<Record<string, unknown>>>;
  }
  val next = new Map<string, CacheEntry<Record<string, unknown>>>();
  root[SEARCH_CACHE_KEY] = next;
  return next;
}

val SEARCH_CACHE = getSharedSearchCache();

fun resolveSearchTimeoutSeconds(searchConfig?: SearchConfigRecord): number {
  return resolveTimeoutSeconds(searchConfig?.timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
}

fun resolveSearchCacheTtlMs(searchConfig?: SearchConfigRecord): number {
  return resolveCacheTtlMs(searchConfig?.cacheTtlMinutes, DEFAULT_CACHE_TTL_MINUTES);
}

fun resolveSearchCount(value: unknown, fallback: number): number {
  val parsed = typeof value == "number" && Number.isFinite(value) ? value : fallback;
  val clamped = Math.max(1, Math.min(MAX_SEARCH_COUNT, Math.floor(parsed)));
  return clamped;
}

fun readConfiguredSecretString(value: unknown, path: string): string | null {
  return normalizeSecretInput(normalizeResolvedSecretInputString({ value, path })) || null;
}

fun readProviderEnvValue(envVars: string[]): string | null {
  for (val envVar of envVars) {
    val value = normalizeSecretInput(process.env[envVar]);
    if (value) {
      return value;
    }
  }
  return null;
}

suspend fun withTrustedWebSearchEndpoint<T>(
  params: {
    url: string;
    timeoutSeconds: number;
    init: RequestInit;
  },
  run: (response: Response) => Promise<T>,
): Promise<T> {
  return withTrustedWebToolsEndpoint(
    {
      url: params.url,
      init: params.init,
      timeoutSeconds: params.timeoutSeconds,
    },
    async ({ response }) => run(response),
  );
}

suspend fun throwWebSearchApiError(res: Response, providerLabel: string): Promise<never> {
  val detailResult = await readResponseText(res, { maxBytes: 64_000 });
  val detail = detailResult.text;
  throw Error(`${providerLabel} API error (${res.status}): ${detail || res.statusText}`);
}

fun resolveSiteName(url: string | null): string | null {
  if (!url) {
    return null;
  }
  try {
    return new URL(url).hostname;
  } catch {
    return null;
  }
}

val BRAVE_FRESHNESS_SHORTCUTS = new Set(["pd", "pw", "pm", "py"]);
val BRAVE_FRESHNESS_RANGE = /^(\d{4}-\d{2}-\d{2})to(\d{4}-\d{2}-\d{2})$/;
val PERPLEXITY_RECENCY_VALUES = new Set(["day", "week", "month", "year"]);

val FRESHNESS_TO_RECENCY: Record<string, string> = {
  pd: "day",
  pw: "week",
  pm: "month",
  py: "year",
};
val RECENCY_TO_FRESHNESS: Record<string, string> = {
  day: "pd",
  week: "pw",
  month: "pm",
  year: "py",
};

val ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
val PERPLEXITY_DATE_PATTERN = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/;

fun isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  val [year, month, day] = value.split("-").map((part) => Number.parseInt(part, 10));
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return false;
  }

  val date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() == year && date.getUTCMonth() == month - 1 && date.getUTCDate() == day
  );
}

fun isoToPerplexityDate(iso: string): string | null {
  val match = iso.match(ISO_DATE_PATTERN);
  if (!match) {
    return null;
  }
  val [, year, month, day] = match;
  return `${parseInt(month, 10)}/${parseInt(day, 10)}/${year}`;
}

fun normalizeToIsoDate(value: string): string | null {
  val trimmed = value.trim();
  if (ISO_DATE_PATTERN.test(trimmed)) {
    return isValidIsoDate(trimmed) ? trimmed : null;
  }
  val match = trimmed.match(PERPLEXITY_DATE_PATTERN);
  if (match) {
    val [, month, day, year] = match;
    val iso = `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
    return isValidIsoDate(iso) ? iso : null;
  }
  return null;
}

fun normalizeFreshness(
  value: string | null,
  provider: "brave" | "perplexity",
): string | null {
  if (!value) {
    return null;
  }
  val trimmed = value.trim();
  if (!trimmed) {
    return null;
  }

  val lower = trimmed.toLowerCase();
  if (BRAVE_FRESHNESS_SHORTCUTS.has(lower)) {
    return provider == "brave" ? lower : FRESHNESS_TO_RECENCY[lower];
  }
  if (PERPLEXITY_RECENCY_VALUES.has(lower)) {
    return provider == "perplexity" ? lower : RECENCY_TO_FRESHNESS[lower];
  }
  if (provider == "brave") {
    val match = trimmed.match(BRAVE_FRESHNESS_RANGE);
    if (match) {
      val [, start, end] = match;
      if (isValidIsoDate(start) && isValidIsoDate(end) && start <= end) {
        return `${start}to${end}`;
      }
    }
  }

  return null;
}

fun readCachedSearchPayload(cacheKey: string): Record<string, unknown> | null {
  val cached = readCache(SEARCH_CACHE, cacheKey);
  return cached ? { ...cached.value, cached: true } : null;
}

fun buildSearchCacheKey(parts: Array<string | number | boolean | null>): string {
  return normalizeCacheKey(
    parts.map((part) => (part == null ? "default" : String(part))).join(":"),
  );
}

fun writeCachedSearchPayload(
  cacheKey: string,
  payload: Record<string, unknown>,
  ttlMs: number,
): void {
  writeCache(SEARCH_CACHE, cacheKey, payload, ttlMs);
}
