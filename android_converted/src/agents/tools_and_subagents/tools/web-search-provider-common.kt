package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-search-provider-common.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";
// TODO: TypeScript import retained for manual wiring: import { withTrustedWebToolsEndpoint } from "./web-guarded-fetch.js";
// TODO: TypeScript import retained for manual wiring: import {
  CacheEntry,
  DEFAULT_CACHE_TTL_MINUTES,
  DEFAULT_TIMEOUT_SECONDS,
  normalizeCacheKey,
  readCache,
  readResponseText,
  resolveCacheTtlMs,
  resolveTimeoutSeconds,
  writeCache,
} from "./web-shared.js"

type SearchConfigRecord = NonNullable<OpenClawConfig["tools"]>["web"] extends infer Web
  ? Web extends { search?: infer Search }
    ? Search extends MutableMap<String, Any?>
      ? Search
      : MutableMap<String, Any?>
    : MutableMap<String, Any?>
  : MutableMap<String, Any?>

val DEFAULT_SEARCH_COUNT = 5
val MAX_SEARCH_COUNT = 10

val SEARCH_CACHE_KEY = Symbol.for("openclaw.web-search.cache")

fun getSharedSearchCache(): MutableMap<String, CacheEntry<MutableMap<String, Any?>>> {
  val root = globalThis as /* TODO */ MutableMap<PropertyKey, Any?>
  val existing = root[SEARCH_CACHE_KEY]
  if (existing instanceof Map) {
    return existing as /* TODO */ MutableMap<String, CacheEntry<MutableMap<String, Any?>>>
  }
  val next = new MutableMap<String, CacheEntry<MutableMap<String, Any?>>>()
  root[SEARCH_CACHE_KEY] = next
  return next
}

val SEARCH_CACHE = getSharedSearchCache()

fun resolveSearchTimeoutSeconds(searchConfig?: SearchConfigRecord): Double {
  return resolveTimeoutSeconds(searchConfig?.timeoutSeconds, DEFAULT_TIMEOUT_SECONDS)
}

fun resolveSearchCacheTtlMs(searchConfig?: SearchConfigRecord): Double {
  return resolveCacheTtlMs(searchConfig?.cacheTtlMinutes, DEFAULT_CACHE_TTL_MINUTES)
}

fun resolveSearchCount(value: Any?, fallback: Double): Double {
  val parsed = value is Double && Number.isFinite(value) ? value : fallback
  val clamped = Math.max(1, Math.min(MAX_SEARCH_COUNT, Math.floor(parsed)))
  return clamped
}

fun readConfiguredSecretString(value: Any?, path: String): String? {
  return normalizeSecretInput(normalizeResolvedSecretInputString({ value, path })) || null
}

fun readProviderEnvValue(envVars: List<String>): String? {
  for (val envVar of envVars) {
    val value = normalizeSecretInput(process.env[envVar])
    if (value) {
      return value
    }
  }
  return null
}

suspend fun <T> withTrustedWebSearchEndpoint(
  params: {
    url: String
    timeoutSeconds: Double
    init: RequestInit
  },
  run: (response: Response) -> Deferred<T>,
): Deferred<T> {
  return withTrustedWebToolsEndpoint(
    {
      url: params.url,
      init: params.init,
      timeoutSeconds: params.timeoutSeconds,
    },
    async ({ response }) -> run(response),
  )
}

suspend fun throwWebSearchApiError(res: Response, providerLabel: String): Deferred<Nothing> {
  val detailResult = await readResponseText(res, { maxBytes: 64_000 })
  val detail = detailResult.text
  throw Error(`${providerLabel} API error (${res.status}): ${detail || res.statusText}`)
}

fun resolveSiteName(url: String?): String? {
  if (!url) {
    return null
  }
  try {
    return new URL(url).hostname
  } catch (_: Throwable) {
    return null
  }
}

val BRAVE_FRESHNESS_SHORTCUTS = mutableSetOf(["pd", "pw", "pm", "py"])
val BRAVE_FRESHNESS_RANGE = /^(\d{4}-\d{2}-\d{2})to(\d{4}-\d{2}-\d{2})$/
val PERPLEXITY_RECENCY_VALUES = mutableSetOf(["day", "week", "month", "year"])

val FRESHNESS_TO_RECENCY: MutableMap<String, String> = {
  pd: String /* "day" */,
  pw: String /* "week" */,
  pm: String /* "month" */,
  py: String /* "year" */,
}
val RECENCY_TO_FRESHNESS: MutableMap<String, String> = {
  day: String /* "pd" */,
  week: String /* "pw" */,
  month: String /* "pm" */,
  year: String /* "py" */,
}

val ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
val PERPLEXITY_DATE_PATTERN = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/

fun isValidIsoDate(value: String): Boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false
  }
  val [year, month, day] = value.split("-").map((part) -> Number.parseInt(part, 10))
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return false
  }

  val date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() == year && date.getUTCMonth() == month - 1 && date.getUTCDate() == day
  )
}

fun isoToPerplexityDate(iso: String): String? {
  val match = iso.match(ISO_DATE_PATTERN)
  if (!match) {
    return null
  }
  val [, year, month, day] = match
  return `${parseInt(month, 10)}/${parseInt(day, 10)}/${year}`
}

fun normalizeToIsoDate(value: String): String? {
  val trimmed = value.trim()
  if (ISO_DATE_PATTERN.test(trimmed)) {
    return isValidIsoDate(trimmed) ? trimmed : null
  }
  val match = trimmed.match(PERPLEXITY_DATE_PATTERN)
  if (match) {
    val [, month, day, year] = match
    val iso = `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`
    return isValidIsoDate(iso) ? iso : null
  }
  return null
}

fun normalizeFreshness(
  value: String?,
  provider: String /* "brave" */ | "perplexity",
): String? {
  if (!value) {
    return null
  }
  val trimmed = value.trim()
  if (!trimmed) {
    return null
  }

  val lower = trimmed.toLowerCase()
  if (BRAVE_FRESHNESS_SHORTCUTS.has(lower)) {
    return provider == "brave" ? lower : FRESHNESS_TO_RECENCY[lower]
  }
  if (PERPLEXITY_RECENCY_VALUES.has(lower)) {
    return provider == "perplexity" ? lower : RECENCY_TO_FRESHNESS[lower]
  }
  if (provider == "brave") {
    val match = trimmed.match(BRAVE_FRESHNESS_RANGE)
    if (match) {
      val [, start, end] = match
      if (isValidIsoDate(start) && isValidIsoDate(end) && start <= end) {
        return `${start}to${end}`
      }
    }
  }

  return null
}

fun readCachedSearchPayload(cacheKey: String): MutableMap<String, Any?>? {
  val cached = readCache(SEARCH_CACHE, cacheKey)
  return cached ? { ...cached.value, cached: true } : null
}

fun buildSearchCacheKey(parts: List<String | Double | Boolean?>): String {
  return normalizeCacheKey(
    parts.map((part) -> (part == null ? "default" : String(part))).join(":"),
  )
}

fun writeCachedSearchPayload(
  cacheKey: String,
  payload: MutableMap<String, Any?>,
  ttlMs: Double,
): Unit {
  writeCache(SEARCH_CACHE, cacheKey, payload, ttlMs)
}
