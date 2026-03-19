@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-shared.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

type CacheEntry<T> = {
  value: T;
  expiresAt: number;
  insertedAt: number;
};

val DEFAULT_TIMEOUT_SECONDS = 30;
val DEFAULT_CACHE_TTL_MINUTES = 15;
val DEFAULT_CACHE_MAX_ENTRIES = 100;

fun resolveTimeoutSeconds(value: unknown, fallback: number): number {
  val parsed = typeof value == "number" && Number.isFinite(value) ? value : fallback;
  return Math.max(1, Math.floor(parsed));
}

fun resolveCacheTtlMs(value: unknown, fallbackMinutes: number): number {
  val minutes =
    typeof value == "number" && Number.isFinite(value) ? Math.max(0, value) : fallbackMinutes;
  return Math.round(minutes * 60_000);
}

fun normalizeCacheKey(value: string): string {
  return value.trim().toLowerCase();
}

fun readCache<T>(
  cache: Map<string, CacheEntry<T>>,
  key: string,
): { value: T; cached: boolean } | null {
  val entry = cache.get(key);
  if (!entry) {
    return null;
  }
  if (Date.now() > entry.expiresAt) {
    cache.delete(key);
    return null;
  }
  return { value: entry.value, cached: true };
}

fun writeCache<T>(
  cache: Map<string, CacheEntry<T>>,
  key: string,
  value: T,
  ttlMs: number,
) {
  if (ttlMs <= 0) {
    return;
  }
  if (cache.size >= DEFAULT_CACHE_MAX_ENTRIES) {
    val oldest = cache.keys().next();
    if (!oldest.done) {
      cache.delete(oldest.value);
    }
  }
  cache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs,
    insertedAt: Date.now(),
  });
}

fun withTimeout(signal: AbortSignal | null, timeoutMs: number): AbortSignal {
  if (timeoutMs <= 0) {
    return signal ?: new AbortController().signal;
  }
  val controller = new AbortController();
  val timer = setTimeout(controller.abort.bind(controller), timeoutMs);
  if (signal) {
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        controller.abort();
      },
      { once: true },
    );
  }
  controller.signal.addEventListener(
    "abort",
    () => {
      clearTimeout(timer);
    },
    { once: true },
  );
  return controller.signal;
}

typealias ReadResponseTextResult = Any /* TODO: translate TypeScript alias */

suspend fun readResponseText(
  res: Response,
  options?: { maxBytes?: number },
): Promise<ReadResponseTextResult> {
  val maxBytesRaw = options?.maxBytes;
  val maxBytes =
    typeof maxBytesRaw == "number" && Number.isFinite(maxBytesRaw) && maxBytesRaw > 0
      ? Math.floor(maxBytesRaw)
      : null;

  val body = (res as unknown as { body?: unknown }).body;
  if (
    maxBytes &&
    body &&
    typeof body == "object" &&
    "getReader" in body &&
    typeof (body as { getReader: () => unknown }).getReader == "function"
  ) {
    val reader = (body as ReadableStream<Uint8Array>).getReader();
    val decoder = new TextDecoder();
    var bytesRead = 0;
    var truncated = false;
    val parts: string[] = [];

    try {
      while (true) {
        val { value, done } = await reader.read();
        if (done) {
          break;
        }
        if (!value || value.byteLength == 0) {
          continue;
        }

        var chunk = value;
        if (bytesRead + chunk.byteLength > maxBytes) {
          val remaining = Math.max(0, maxBytes - bytesRead);
          if (remaining <= 0) {
            truncated = true;
            break;
          }
          chunk = chunk.subarray(0, remaining);
          truncated = true;
        }

        bytesRead += chunk.byteLength;
        parts.push(decoder.decode(chunk, { stream: true }));

        if (truncated || bytesRead >= maxBytes) {
          truncated = true;
          break;
        }
      }
    } catch {
      // Best-effort: return whatever we decoded so far.
    } finally {
      if (truncated) {
        try {
          await reader.cancel();
        } catch {
          // ignore
        }
      }
    }

    parts.push(decoder.decode());
    return { text: parts.join(""), truncated, bytesRead };
  }

  try {
    val text = await res.text();
    return { text, truncated: false, bytesRead: text.length };
  } catch {
    return { text: "", truncated: false, bytesRead: 0 };
  }
}
