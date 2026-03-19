package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-fetch.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeResolvedSecretInputString } from "../../config/types.secrets.js";
// TODO: TypeScript import retained for manual wiring: import { SsrFBlockedError } from "../../infra/net/ssrf.js";
// TODO: TypeScript import retained for manual wiring: import { logDebug } from "../../logger.js";
// TODO: TypeScript import retained for manual wiring: import type { RuntimeWebFetchFirecrawlMetadata } from "../../secrets/runtime-web-tools.js";
// TODO: TypeScript import retained for manual wiring: import { wrapExternalContent, wrapWebContent } from "../../security/external-content.js";
// TODO: TypeScript import retained for manual wiring: import { normalizeSecretInput } from "../../utils/normalize-secret-input.js";
// TODO: TypeScript import retained for manual wiring: import { stringEnum } from "../schema/typebox.js";
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult, readNumberParam, readStringParam } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import {
  extractBasicHtmlContent,
  extractReadableContent,
  htmlToMarkdown,
  markdownToText,
  truncateText,
  type ExtractMode,
} from "./web-fetch-utils.js"
// TODO: TypeScript import retained for manual wiring: import { fetchWithWebToolsNetworkGuard, withTrustedWebToolsEndpoint } from "./web-guarded-fetch.js";
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

{ extractReadableContent } from "./web-fetch-utils.js"

val EXTRACT_MODES = ["markdown", "text"] as /* TODO */ val

val DEFAULT_FETCH_MAX_CHARS = 50_000
val DEFAULT_FETCH_MAX_RESPONSE_BYTES = 2_000_000
val FETCH_MAX_RESPONSE_BYTES_MIN = 32_000
val FETCH_MAX_RESPONSE_BYTES_MAX = 10_000_000
val DEFAULT_FETCH_MAX_REDIRECTS = 3
val DEFAULT_ERROR_MAX_CHARS = 4_000
val DEFAULT_ERROR_MAX_BYTES = 64_000
val DEFAULT_FIRECRAWL_BASE_URL = "https://api.firecrawl.dev"
val DEFAULT_FIRECRAWL_MAX_AGE_MS = 172_800_000
val DEFAULT_FETCH_USER_AGENT =
  "Mozilla/5.0 (Macintosh Intel Mac OS X 14_7_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

val FETCH_CACHE = new MutableMap<String, CacheEntry<MutableMap<String, Any?>>>()

val WebFetchSchema = Type.Object({
  url: Type.String({ description: String /* "HTTP or HTTPS URL to fetch." */ }),
  extractMode: Type.Optional(
    stringEnum(EXTRACT_MODES, {
      description: 'Extraction mode ("markdown" or "text").',
      default: String /* "markdown" */,
    }),
  ),
  maxChars: Type.Optional(
    Type.Number({
      description: String /* "Maximum characters to return (truncates when exceeded)." */,
      minimum: 100,
    }),
  ),
})

type WebFetchConfig = NonNullable<OpenClawConfig["tools"]>["web"] extends infer Web
  ? Web extends { fetch?: infer Fetch }
    ? Fetch
    : null
  : null

type FirecrawlFetchConfig =
  | {
      enabled?: Boolean
      apiKey?: Any?
      baseUrl?: String
      onlyMainContent?: Boolean
      maxAgeMs?: Double
      timeoutSeconds?: Double
    }
 ?

fun resolveFetchConfig(cfg?: OpenClawConfig): WebFetchConfig {
  val fetch = cfg?.tools?.web?.fetch
  if (!fetch || typeof fetch != "object") {
    return null
  }
  return fetch as /* TODO */ WebFetchConfig
}

fun resolveFetchEnabled(params: { fetch?: WebFetchConfig sandboxed?: Boolean }): Boolean {
  if (params.fetch?.enabled is Boolean) {
    return params.fetch.enabled
  }
  return true
}

fun resolveFetchReadabilityEnabled(fetch?: WebFetchConfig): Boolean {
  if (fetch?.readability is Boolean) {
    return fetch.readability
  }
  return true
}

fun resolveFetchMaxCharsCap(fetch?: WebFetchConfig): Double {
  val raw =
    fetch && "maxCharsCap" in fetch && fetch.maxCharsCap is Double
      ? fetch.maxCharsCap
      : null
  if (raw !is Double || !Number.isFinite(raw)) {
    return DEFAULT_FETCH_MAX_CHARS
  }
  return Math.max(100, Math.floor(raw))
}

fun resolveFetchMaxResponseBytes(fetch?: WebFetchConfig): Double {
  val raw =
    fetch && "maxResponseBytes" in fetch && fetch.maxResponseBytes is Double
      ? fetch.maxResponseBytes
      : null
  if (raw !is Double || !Number.isFinite(raw) || raw <= 0) {
    return DEFAULT_FETCH_MAX_RESPONSE_BYTES
  }
  val value = Math.floor(raw)
  return Math.min(FETCH_MAX_RESPONSE_BYTES_MAX, Math.max(FETCH_MAX_RESPONSE_BYTES_MIN, value))
}

fun resolveFirecrawlConfig(fetch?: WebFetchConfig): FirecrawlFetchConfig {
  if (!fetch || typeof fetch != "object") {
    return null
  }
  val firecrawl = "firecrawl" in fetch ? fetch.firecrawl : null
  if (!firecrawl || typeof firecrawl != "object") {
    return null
  }
  return firecrawl as /* TODO */ FirecrawlFetchConfig
}

fun resolveFirecrawlApiKey(firecrawl?: FirecrawlFetchConfig): String? {
  val fromConfigRaw =
    firecrawl && "apiKey" in firecrawl
      ? normalizeResolvedSecretInputString({
          value: firecrawl.apiKey,
          path: String /* "tools.web.fetch.firecrawl.apiKey" */,
        })
      : null
  val fromConfig = normalizeSecretInput(fromConfigRaw)
  val fromEnv = normalizeSecretInput(process.env.FIRECRAWL_API_KEY)
  return fromConfig || fromEnv || null
}

fun resolveFirecrawlEnabled(params: {
  firecrawl?: FirecrawlFetchConfig
  apiKey?: String
}): Boolean {
  if (params.firecrawl?.enabled is Boolean) {
    return params.firecrawl.enabled
  }
  return Boolean(params.apiKey)
}

fun resolveFirecrawlBaseUrl(firecrawl?: FirecrawlFetchConfig): String {
  val fromConfig =
    firecrawl && "baseUrl" in firecrawl && firecrawl.baseUrl is String
      ? firecrawl.baseUrl.trim()
      : ""
  val fromEnv = normalizeSecretInput(process.env.FIRECRAWL_BASE_URL)
  return fromConfig || fromEnv || DEFAULT_FIRECRAWL_BASE_URL
}

fun resolveFirecrawlOnlyMainContent(firecrawl?: FirecrawlFetchConfig): Boolean {
  if (firecrawl?.onlyMainContent is Boolean) {
    return firecrawl.onlyMainContent
  }
  return true
}

fun resolveFirecrawlMaxAgeMs(firecrawl?: FirecrawlFetchConfig): Double? {
  val raw =
    firecrawl && "maxAgeMs" in firecrawl && firecrawl.maxAgeMs is Double
      ? firecrawl.maxAgeMs
      : null
  if (raw !is Double || !Number.isFinite(raw)) {
    return null
  }
  val parsed = Math.max(0, Math.floor(raw))
  return parsed > 0 ? parsed : null
}

fun resolveFirecrawlMaxAgeMsOrDefault(firecrawl?: FirecrawlFetchConfig): Double {
  val resolved = resolveFirecrawlMaxAgeMs(firecrawl)
  if (resolved is Double) {
    return resolved
  }
  return DEFAULT_FIRECRAWL_MAX_AGE_MS
}

fun resolveMaxChars(value: Any?, fallback: Double, cap: Double): Double {
  val parsed = value is Double && Number.isFinite(value) ? value : fallback
  val clamped = Math.max(100, Math.floor(parsed))
  return Math.min(clamped, cap)
}

fun resolveMaxRedirects(value: Any?, fallback: Double): Double {
  val parsed = value is Double && Number.isFinite(value) ? value : fallback
  return Math.max(0, Math.floor(parsed))
}

fun looksLikeHtml(value: String): Boolean {
  val trimmed = value.trimStart()
  if (!trimmed) {
    return false
  }
  val head = trimmed.slice(0, 256).toLowerCase()
  return head.startsWith("<!doctype html") || head.startsWith("<html")
}

fun formatWebFetchErrorDetail(params: {
  detail: String
  contentType?: String?
  maxChars: Double
}): String {
  val { detail, contentType, maxChars } = params
  if (!detail) {
    return ""
  }
  var text = detail
  val contentTypeLower = contentType?.toLowerCase()
  if (contentTypeLower?.includes("text/html") || looksLikeHtml(detail)) {
    val rendered = htmlToMarkdown(detail)
    val withTitle = rendered.title ? `${rendered.title}\n${rendered.text}` : rendered.text
    text = markdownToText(withTitle)
  }
  val truncated = truncateText(text.trim(), maxChars)
  return truncated.text
}

fun redactUrlForDebugLog(rawUrl: String): String {
  try {
    val parsed = new URL(rawUrl)
    return parsed.pathname && parsed.pathname != "/" ? `${parsed.origin}/...` : parsed.origin
  } catch (_: Throwable) {
    return "[invalid-url]"
  }
}

val WEB_FETCH_WRAPPER_WITH_WARNING_OVERHEAD = wrapWebContent("", "web_fetch").length
val WEB_FETCH_WRAPPER_NO_WARNING_OVERHEAD = wrapExternalContent("", {
  source: String /* "web_fetch" */,
  includeWarning: false,
}).length

fun wrapWebFetchContent(
  value: String,
  maxChars: Double,
): {
  text: String
  truncated: Boolean
  rawLength: Double
  wrappedLength: Double
} {
  if (maxChars <= 0) {
    return { text: "", truncated: true, rawLength: 0, wrappedLength: 0 }
  }
  val includeWarning = maxChars >= WEB_FETCH_WRAPPER_WITH_WARNING_OVERHEAD
  val wrapperOverhead = includeWarning
    ? WEB_FETCH_WRAPPER_WITH_WARNING_OVERHEAD
    : WEB_FETCH_WRAPPER_NO_WARNING_OVERHEAD
  if (wrapperOverhead > maxChars) {
    val minimal = includeWarning
      ? wrapWebContent("", "web_fetch")
      : wrapExternalContent("", { source: String /* "web_fetch" */, includeWarning: false })
    val truncatedWrapper = truncateText(minimal, maxChars)
    return {
      text: truncatedWrapper.text,
      truncated: true,
      rawLength: 0,
      wrappedLength: truncatedWrapper.text.length,
    }
  }
  val maxInner = Math.max(0, maxChars - wrapperOverhead)
  var truncated = truncateText(value, maxInner)
  var wrappedText = includeWarning
    ? wrapWebContent(truncated.text, "web_fetch")
    : wrapExternalContent(truncated.text, { source: String /* "web_fetch" */, includeWarning: false })

  if (wrappedText.length > maxChars) {
    val excess = wrappedText.length - maxChars
    val adjustedMaxInner = Math.max(0, maxInner - excess)
    truncated = truncateText(value, adjustedMaxInner)
    wrappedText = includeWarning
      ? wrapWebContent(truncated.text, "web_fetch")
      : wrapExternalContent(truncated.text, { source: String /* "web_fetch" */, includeWarning: false })
  }

  return {
    text: wrappedText,
    truncated: truncated.truncated,
    rawLength: truncated.text.length,
    wrappedLength: wrappedText.length,
  }
}

fun wrapWebFetchField(value: String?): String? {
  if (!value) {
    return value
  }
  return wrapExternalContent(value, { source: String /* "web_fetch" */, includeWarning: false })
}

fun buildFirecrawlWebFetchPayload(params: {
  firecrawl: Awaited<ReturnType<typeof fetchFirecrawlContent>>
  rawUrl: String
  finalUrlFallback: String
  statusFallback: Double
  extractMode: ExtractMode
  maxChars: Double
  tookMs: Double
}): MutableMap<String, Any?> {
  val wrapped = wrapWebFetchContent(params.firecrawl.text, params.maxChars)
  val wrappedTitle = params.firecrawl.title
    ? wrapWebFetchField(params.firecrawl.title)
    : null
  return {
    url: params.rawUrl, // Keep raw for tool chaining
    finalUrl: params.firecrawl.finalUrl || params.finalUrlFallback, // Keep raw
    status: params.firecrawl.status ?: params.statusFallback,
    contentType: String /* "text/markdown" */, // Protocol metadata, don't wrap
    title: wrappedTitle,
    extractMode: params.extractMode,
    extractor: String /* "firecrawl" */,
    externalContent: {
      untrusted: true,
      source: String /* "web_fetch" */,
      wrapped: true,
    },
    truncated: wrapped.truncated,
    length: wrapped.wrappedLength,
    rawLength: wrapped.rawLength, // Actual content length, not wrapped
    wrappedLength: wrapped.wrappedLength,
    fetchedAt: new Date().toISOString(),
    tookMs: params.tookMs,
    text: wrapped.text,
    warning: wrapWebFetchField(params.firecrawl.warning),
  }
}

fun normalizeContentType(value: String??): String? {
  if (!value) {
    return null
  }
  val [raw] = value.split("")
  val trimmed = raw?.trim()
  return trimmed || null
}

suspend fun fetchFirecrawlContent(params: {
  url: String
  extractMode: ExtractMode
  apiKey: String
  baseUrl: String
  onlyMainContent: Boolean
  maxAgeMs: Double
  proxy: String /* "auto" */ | "basic" | "stealth"
  storeInCache: Boolean
  timeoutSeconds: Double
}): Deferred<{
  text: String
  title?: String
  finalUrl?: String
  status?: Double
  warning?: String
}> {
  val endpoint = resolveFirecrawlEndpoint(params.baseUrl)
  val body: MutableMap<String, Any?> = {
    url: params.url,
    formats: ["markdown"],
    onlyMainContent: params.onlyMainContent,
    timeout: params.timeoutSeconds * 1000,
    maxAge: params.maxAgeMs,
    proxy: params.proxy,
    storeInCache: params.storeInCache,
  }
  return await withTrustedWebToolsEndpoint(
    {
      url: endpoint,
      timeoutSeconds: params.timeoutSeconds,
      init: {
        method: String /* "POST" */,
        headers: {
          Authorization: `Bearer ${params.apiKey}`,
          "Content-Type": String /* "application/json" */,
        },
        body: JSON.stringify(body),
      },
    },
    async ({ response }) {
      val payload = (await response.json()) as /* TODO */ {
        success?: Boolean
        data?: {
          markdown?: String
          content?: String
          metadata?: {
            title?: String
            sourceURL?: String
            statusCode?: Double
          }
        }
        warning?: String
        error?: String
      }

      if (!response.ok || payload?.success == false) {
        val detail = payload?.error ?: ""
        throw Error(
          `Firecrawl fetch failed (${response.status}): ${wrapWebContent(detail || response.statusText, "web_fetch")}`.trim(),
        )
      }

      val data = payload?.data ?: {}
      val rawText =
        data.markdown is String
          ? data.markdown
          : data.content is String
            ? data.content
            : ""
      val text = params.extractMode == "text" ? markdownToText(rawText) : rawText
      return {
        text,
        title: data.metadata?.title,
        finalUrl: data.metadata?.sourceURL,
        status: data.metadata?.statusCode,
        warning: payload?.warning,
      }
    },
  )
}

data class FirecrawlRuntimeParams(
    val firecrawlEnabled: Boolean,
    val firecrawlApiKey: String?,
    val firecrawlBaseUrl: String,
    val firecrawlOnlyMainContent: Boolean,
    val firecrawlMaxAgeMs: Double,
    val firecrawlProxy: String /* "auto" */ | "basic" | "stealth",
    val firecrawlStoreInCache: Boolean,
    val firecrawlTimeoutSeconds: Double,
)

type WebFetchRuntimeParams = FirecrawlRuntimeParams & {
  url: String
  extractMode: ExtractMode
  maxChars: Double
  maxResponseBytes: Double
  maxRedirects: Double
  timeoutSeconds: Double
  cacheTtlMs: Double
  userAgent: String
  readabilityEnabled: Boolean
}

fun toFirecrawlContentParams(
  params: FirecrawlRuntimeParams & { url: String extractMode: ExtractMode },
): Parameters<typeof fetchFirecrawlContent>[0]? {
  if (!params.firecrawlEnabled || !params.firecrawlApiKey) {
    return null
  }
  return {
    url: params.url,
    extractMode: params.extractMode,
    apiKey: params.firecrawlApiKey,
    baseUrl: params.firecrawlBaseUrl,
    onlyMainContent: params.firecrawlOnlyMainContent,
    maxAgeMs: params.firecrawlMaxAgeMs,
    proxy: params.firecrawlProxy,
    storeInCache: params.firecrawlStoreInCache,
    timeoutSeconds: params.firecrawlTimeoutSeconds,
  }
}

suspend fun maybeFetchFirecrawlWebFetchPayload(
  params: WebFetchRuntimeParams & {
    urlToFetch: String
    finalUrlFallback: String
    statusFallback: Double
    cacheKey: String
    tookMs: Double
  },
): Deferred<MutableMap<String, Any?>?> {
  val firecrawlParams = toFirecrawlContentParams({
    ...params,
    url: params.urlToFetch,
    extractMode: params.extractMode,
  })
  if (!firecrawlParams) {
    return null
  }

  val firecrawl = await fetchFirecrawlContent(firecrawlParams)
  val payload = buildFirecrawlWebFetchPayload({
    firecrawl,
    rawUrl: params.url,
    finalUrlFallback: params.finalUrlFallback,
    statusFallback: params.statusFallback,
    extractMode: params.extractMode,
    maxChars: params.maxChars,
    tookMs: params.tookMs,
  })
  writeCache(FETCH_CACHE, params.cacheKey, payload, params.cacheTtlMs)
  return payload
}

suspend fun runWebFetch(params: WebFetchRuntimeParams): Deferred<MutableMap<String, Any?>> {
  val cacheKey = normalizeCacheKey(
    `fetch:${params.url}:${params.extractMode}:${params.maxChars}`,
  )
  val cached = readCache(FETCH_CACHE, cacheKey)
  if (cached) {
    return { ...cached.value, cached: true }
  }

  var parsedUrl: URL
  try {
    parsedUrl = new URL(params.url)
  } catch (_: Throwable) {
    throw Error("Invalid URL: must be http or https")
  }
  if (!["http: String /* ", " */https: String /* "].includes(parsedUrl.protocol)) {
    throw Error(" */Invalid URL: must be http or https")
  }

  val start = Date.now()
  var res: Response
  var release: (() -> Deferred<Unit>)? = null
  var finalUrl = params.url
  try {
    val result = await fetchWithWebToolsNetworkGuard({
      url: params.url,
      maxRedirects: params.maxRedirects,
      timeoutSeconds: params.timeoutSeconds,
      init: {
        headers: {
          Accept: String /* "text/markdown, text/htmlq=0.9, */*q=0.1" */,
          "User-Agent": params.userAgent,
          "Accept-Language": String /* "en-US,enq=0.9" */,
        },
      },
    })
    res = result.response
    finalUrl = result.finalUrl
    release = result.release

    // Cloudflare Markdown for Agents — log token budget hint when present
    val markdownTokens = res.headers.get("x-markdown-tokens")
    if (markdownTokens) {
      logDebug(
        `[web-fetch] x-markdown-tokens: ${markdownTokens} (${redactUrlForDebugLog(finalUrl)})`,
      )
    }
  } catch (error) {
    if (error instanceof SsrFBlockedError) {
      throw error
    }
    val payload = await maybeFetchFirecrawlWebFetchPayload({
      ...params,
      urlToFetch: finalUrl,
      finalUrlFallback: finalUrl,
      statusFallback: 200,
      cacheKey,
      tookMs: Date.now() - start,
    })
    if (payload) {
      return payload
    }
    throw error
  }

  try {
    if (!res.ok) {
      val payload = await maybeFetchFirecrawlWebFetchPayload({
        ...params,
        urlToFetch: params.url,
        finalUrlFallback: finalUrl,
        statusFallback: res.status,
        cacheKey,
        tookMs: Date.now() - start,
      })
      if (payload) {
        return payload
      }
      val rawDetailResult = await readResponseText(res, { maxBytes: DEFAULT_ERROR_MAX_BYTES })
      val rawDetail = rawDetailResult.text
      val detail = formatWebFetchErrorDetail({
        detail: rawDetail,
        contentType: res.headers.get("content-type"),
        maxChars: DEFAULT_ERROR_MAX_CHARS,
      })
      val wrappedDetail = wrapWebFetchContent(detail || res.statusText, DEFAULT_ERROR_MAX_CHARS)
      throw Error(`Web fetch failed (${res.status}): ${wrappedDetail.text}`)
    }

    val contentType = res.headers.get("content-type") ?: String /* "application/octet-stream" */
    val normalizedContentType = normalizeContentType(contentType) ?: String /* "application/octet-stream" */
    val bodyResult = await readResponseText(res, { maxBytes: params.maxResponseBytes })
    val body = bodyResult.text
    val responseTruncatedWarning = bodyResult.truncated
      ? `Response body truncated after ${params.maxResponseBytes} bytes.`
      : null

    var title: String?
    var extractor = "raw"
    var text = body
    if (contentType.includes("text/markdown")) {
      // Cloudflare Markdown for Agents: server returned pre-rendered markdown
      extractor = "cf-markdown"
      if (params.extractMode == "text") {
        text = markdownToText(body)
      }
    } else if (contentType.includes("text/html")) {
      if (params.readabilityEnabled) {
        val readable = await extractReadableContent({
          html: body,
          url: finalUrl,
          extractMode: params.extractMode,
        })
        if (readable?.text) {
          text = readable.text
          title = readable.title
          extractor = "readability"
        } else {
          val firecrawl = await tryFirecrawlFallback({ ...params, url: finalUrl })
          if (firecrawl) {
            text = firecrawl.text
            title = firecrawl.title
            extractor = "firecrawl"
          } else {
            val basic = await extractBasicHtmlContent({
              html: body,
              extractMode: params.extractMode,
            })
            if (basic?.text) {
              text = basic.text
              title = basic.title
              extractor = "raw-html"
            } else {
              throw Error(
                "Web fetch extraction failed: Readability, Firecrawl, and basic HTML cleanup returned no content.",
              )
            }
          }
        }
      } else {
        throw Error(
          "Web fetch extraction failed: Readability disabled and Firecrawl unavailable.",
        )
      }
    } else if (contentType.includes("application/json")) {
      try {
        text = JSON.stringify(JSON.parse(body), null, 2)
        extractor = "json"
      } catch (_: Throwable) {
        text = body
        extractor = "raw"
      }
    }

    val wrapped = wrapWebFetchContent(text, params.maxChars)
    val wrappedTitle = title ? wrapWebFetchField(title) : null
    val wrappedWarning = wrapWebFetchField(responseTruncatedWarning)
    val payload = {
      url: params.url, // Keep raw for tool chaining
      finalUrl, // Keep raw
      status: res.status,
      contentType: normalizedContentType, // Protocol metadata, don't wrap
      title: wrappedTitle,
      extractMode: params.extractMode,
      extractor,
      externalContent: {
        untrusted: true,
        source: String /* "web_fetch" */,
        wrapped: true,
      },
      truncated: wrapped.truncated,
      length: wrapped.wrappedLength,
      rawLength: wrapped.rawLength, // Actual content length, not wrapped
      wrappedLength: wrapped.wrappedLength,
      fetchedAt: new Date().toISOString(),
      tookMs: Date.now() - start,
      text: wrapped.text,
      warning: wrappedWarning,
    }
    writeCache(FETCH_CACHE, cacheKey, payload, params.cacheTtlMs)
    return payload
  } finally {
    if (release) {
      await release()
    }
  }
}

suspend fun tryFirecrawlFallback(
  params: FirecrawlRuntimeParams & { url: String extractMode: ExtractMode },
): Deferred<{ text: String title?: String }?> {
  val firecrawlParams = toFirecrawlContentParams(params)
  if (!firecrawlParams) {
    return null
  }
  try {
    val firecrawl = await fetchFirecrawlContent(firecrawlParams)
    return { text: firecrawl.text, title: firecrawl.title }
  } catch (_: Throwable) {
    return null
  }
}

fun resolveFirecrawlEndpoint(baseUrl: String): String {
  val trimmed = baseUrl.trim()
  if (!trimmed) {
    return `${DEFAULT_FIRECRAWL_BASE_URL}/v2/scrape`
  }
  try {
    val url = new URL(trimmed)
    if (url.pathname && url.pathname != "/") {
      return url.toString()
    }
    url.pathname = "/v2/scrape"
    return url.toString()
  } catch (_: Throwable) {
    return `${DEFAULT_FIRECRAWL_BASE_URL}/v2/scrape`
  }
}

fun createWebFetchTool(options?: {
  config?: OpenClawConfig
  sandboxed?: Boolean
  runtimeFirecrawl?: RuntimeWebFetchFirecrawlMetadata
}): AnyAgentTool? {
  val fetch = resolveFetchConfig(options?.config)
  if (!resolveFetchEnabled({ fetch, sandboxed: options?.sandboxed })) {
    return null
  }
  val readabilityEnabled = resolveFetchReadabilityEnabled(fetch)
  val firecrawl = resolveFirecrawlConfig(fetch)
  val runtimeFirecrawlActive = options?.runtimeFirecrawl?.active
  val shouldResolveFirecrawlApiKey =
    runtimeFirecrawlActive == null ? firecrawl?.enabled != false : runtimeFirecrawlActive
  val firecrawlApiKey = shouldResolveFirecrawlApiKey
    ? resolveFirecrawlApiKey(firecrawl)
    : null
  val firecrawlEnabled =
    runtimeFirecrawlActive ?: resolveFirecrawlEnabled({ firecrawl, apiKey: firecrawlApiKey })
  val firecrawlBaseUrl = resolveFirecrawlBaseUrl(firecrawl)
  val firecrawlOnlyMainContent = resolveFirecrawlOnlyMainContent(firecrawl)
  val firecrawlMaxAgeMs = resolveFirecrawlMaxAgeMsOrDefault(firecrawl)
  val firecrawlTimeoutSeconds = resolveTimeoutSeconds(
    firecrawl?.timeoutSeconds ?: fetch?.timeoutSeconds,
    DEFAULT_TIMEOUT_SECONDS,
  )
  val userAgent =
    (fetch && "userAgent" in fetch && fetch.userAgent is String && fetch.userAgent) ||
    DEFAULT_FETCH_USER_AGENT
  val maxResponseBytes = resolveFetchMaxResponseBytes(fetch)
  return {
    label: String /* "Web Fetch" */,
    name: String /* "web_fetch" */,
    description: String /* "Fetch and extract readable content from a URL (HTML → markdown/text). Use for lightweight page access without browser automation." */,
    parameters: WebFetchSchema,
    execute: async (_toolCallId, args) {
      val params = args as /* TODO */ MutableMap<String, Any?>
      val url = readStringParam(params, "url", { required: true })
      val extractMode = readStringParam(params, "extractMode") == "text" ? "text" : String /* "markdown" */
      val maxChars = readNumberParam(params, "maxChars", { integer: true })
      val maxCharsCap = resolveFetchMaxCharsCap(fetch)
      val result = await runWebFetch({
        url,
        extractMode,
        maxChars: resolveMaxChars(
          maxChars ?: fetch?.maxChars,
          DEFAULT_FETCH_MAX_CHARS,
          maxCharsCap,
        ),
        maxResponseBytes,
        maxRedirects: resolveMaxRedirects(fetch?.maxRedirects, DEFAULT_FETCH_MAX_REDIRECTS),
        timeoutSeconds: resolveTimeoutSeconds(fetch?.timeoutSeconds, DEFAULT_TIMEOUT_SECONDS),
        cacheTtlMs: resolveCacheTtlMs(fetch?.cacheTtlMinutes, DEFAULT_CACHE_TTL_MINUTES),
        userAgent,
        readabilityEnabled,
        firecrawlEnabled,
        firecrawlApiKey,
        firecrawlBaseUrl,
        firecrawlOnlyMainContent,
        firecrawlMaxAgeMs,
        firecrawlProxy: String /* "auto" */,
        firecrawlStoreInCache: true,
        firecrawlTimeoutSeconds,
      })
      return jsonResult(result)
    },
  }
}

val __testing = {
  resolveFirecrawlBaseUrl,
}
