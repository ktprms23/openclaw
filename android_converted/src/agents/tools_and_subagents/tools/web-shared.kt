package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-shared.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
type CacheEntry<T> = {
  value: T
  expiresAt: Double
  insertedAt: Double
}

val DEFAULT_TIMEOUT_SECONDS = 30
val DEFAULT_CACHE_TTL_MINUTES = 15
val DEFAULT_CACHE_MAX_ENTRIES = 100

fun resolveTimeoutSeconds(value: Any?, fallback: Double): Double {
  val parsed = value is Double && Number.isFinite(value) ? value : fallback
  return Math.max(1, Math.floor(parsed))
}

fun resolveCacheTtlMs(value: Any?, fallbackMinutes: Double): Double {
  val minutes =
    value is Double && Number.isFinite(value) ? Math.max(0, value) : fallbackMinutes
  return Math.round(minutes * 60_000)
}

fun normalizeCacheKey(value: String): String {
  return value.trim().toLowerCase()
}

fun <T> readCache(
  cache: MutableMap<String, CacheEntry<T>>,
  key: String,
): { value: T cached: Boolean }? {
  val entry = cache.get(key)
  if (!entry) {
    return null
  }
  if (Date.now() > entry.expiresAt) {
    cache.delete(key)
    return null
  }
  return { value: entry.value, cached: true }
}

fun <T> writeCache(
  cache: MutableMap<String, CacheEntry<T>>,
  key: String,
  value: T,
  ttlMs: Double,
) {
  if (ttlMs <= 0) {
    return
  }
  if (cache.size >= DEFAULT_CACHE_MAX_ENTRIES) {
    val oldest = cache.keys().next()
    if (!oldest.done) {
      cache.delete(oldest.value)
    }
  }
  cache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs,
    insertedAt: Date.now(),
  })
}

fun withTimeout(signal: AbortSignal /* TODO */?, timeoutMs: Double): AbortSignal /* TODO */ {
  if (timeoutMs <= 0) {
    return signal ?: new AbortController().signal
  }
  val controller = new AbortController()
  val timer = setTimeout(controller.abort.bind(controller), timeoutMs)
  if (signal) {
    signal.addEventListener(
      "abort",
      () {
        clearTimeout(timer)
        controller.abort()
      },
      { once: true },
    )
  }
  controller.signal.addEventListener(
    "abort",
    () {
      clearTimeout(timer)
    },
    { once: true },
  )
  return controller.signal
}

data class ReadResponseTextResult(
    val text: String,
    val truncated: Boolean,
    val bytesRead: Double,
)

suspend fun readResponseText(
  res: Response,
  options?: { maxBytes?: Double },
): Deferred<ReadResponseTextResult> {
  val maxBytesRaw = options?.maxBytes
  val maxBytes =
    maxBytesRaw is Double && Number.isFinite(maxBytesRaw) && maxBytesRaw > 0
      ? Math.floor(maxBytesRaw)
      : null

  val body = (res as /* TODO */ Any? as /* TODO */ { body?: Any? }).body
  if (
    maxBytes &&
    body &&
    typeof body == "object" &&
    "getReader" in body &&
    typeof (body as /* TODO */ { getReader: () -> Any? }).getReader == "fun"
  ) {
    val reader = (body as /* TODO */ ReadableStream<Uint8Array>).getReader()
    val decoder = new TextDecoder()
    var bytesRead = 0
    var truncated = false
    val parts: List<String> = []

    try {
      while (true) {
        val { value, done } = await reader.read()
        if (done) {
          break
        }
        if (!value || value.byteLength == 0) {
          continue
        }

        var chunk = value
        if (bytesRead + chunk.byteLength > maxBytes) {
          val remaining = Math.max(0, maxBytes - bytesRead)
          if (remaining <= 0) {
            truncated = true
            break
          }
          chunk = chunk.subarray(0, remaining)
          truncated = true
        }

        bytesRead += chunk.byteLength
        parts.push(decoder.decode(chunk, { stream: true }))

        if (truncated || bytesRead >= maxBytes) {
          truncated = true
          break
        }
      }
    } catch (_: Throwable) {
      // Best-effort: return whatever we decoded so far.
    } finally {
      if (truncated) {
        try {
          await reader.cancel()
        } catch (_: Throwable) {
          // ignore
        }
      }
    }

    parts.push(decoder.decode())
    return { text: parts.join(""), truncated, bytesRead }
  }

  try {
    val text = await res.text()
    return { text, truncated: false, bytesRead: text.length }
  } catch (_: Throwable) {
    return { text: "", truncated: false, bytesRead: 0 }
  }
}
