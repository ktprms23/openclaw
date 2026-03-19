package agents.tools_and_subagents

// Converted from src/agents/subagent-announce-queue.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { type QueueDropPolicy, type QueueMode } from "../auto-reply/reply/queue.js";
// TODO: TypeScript import retained for manual wiring: import { defaultRuntime } from "../runtime.js";
// TODO: TypeScript import retained for manual wiring: import {
  type DeliveryContext,
  deliveryContextKey,
  normalizeDeliveryContext,
} from "../utils/delivery-context.js"
// TODO: TypeScript import retained for manual wiring: import {
  applyQueueRuntimeSettings,
  applyQueueDropPolicy,
  beginQueueDrain,
  buildCollectPrompt,
  clearQueueSummaryState,
  drainCollectQueueStep,
  drainNextQueueItem,
  hasCrossChannelItems,
  previewQueueSummaryPrompt,
  waitForQueueDebounce,
} from "../utils/queue-helpers.js"
// TODO: TypeScript import retained for manual wiring: import type { AgentInternalEvent } from "./internal-events.js";

data class AnnounceQueueItem(
    val announceId: String?,
    val prompt: String,
    val summaryLine: String?,
    val internalEvents: List<AgentInternalEvent>?,
    val enqueuedAt: Double,
    val sessionKey: String,
    val origin: DeliveryContext?,
    val originKey: String?,
    val sourceSessionKey: String?,
    val sourceChannel: String?,
    val sourceTool: String?,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   // Stable announce identity shared by direct + queued delivery paths.
    //   // Optional for backward compatibility with previously queued items.
}

data class AnnounceQueueSettings(
    val mode: QueueMode,
    val debounceMs: Double?,
    val cap: Double?,
    val dropPolicy: QueueDropPolicy?,
)

data class AnnounceQueueState(
    val items: List<AnnounceQueueItem>,
    val draining: Boolean,
    val lastEnqueuedAt: Double,
    val mode: QueueMode,
    val debounceMs: Double,
    val cap: Double,
    val dropPolicy: QueueDropPolicy,
    val droppedCount: Double,
    val summaryLines: List<String>,
    val send: (item: AnnounceQueueItem) => Deferred<Unit>,
    val consecutiveFailures: Double,
)
{
    // TODO: Review unsupported TypeScript members below.
    //   /** Consecutive drain failures — drives exponential backoff on errors. */
}

val ANNOUNCE_QUEUES = new MutableMap<String, AnnounceQueueState>()

fun resetAnnounceQueuesForTests() {
  // Test isolation: other suites may leave a draining queue behind in the worker.
  // Clearing the map alone isn't enough because drain loops capture `queue` by reference.
  for (val queue of ANNOUNCE_QUEUES.values()) {
    queue.items.length = 0
    queue.summaryLines.length = 0
    queue.droppedCount = 0
    queue.lastEnqueuedAt = 0
  }
  ANNOUNCE_QUEUES.clear()
}

fun getAnnounceQueue(
  key: String,
  settings: AnnounceQueueSettings,
  send: (item: AnnounceQueueItem) -> Deferred<Unit>,
) {
  val existing = ANNOUNCE_QUEUES.get(key)
  if (existing) {
    applyQueueRuntimeSettings({
      target: existing,
      settings,
    })
    existing.send = send
    return existing
  }
  val created: AnnounceQueueState = {
    items: [],
    draining: false,
    lastEnqueuedAt: 0,
    mode: settings.mode,
    debounceMs: settings.debounceMs is Double ? Math.max(0, settings.debounceMs) : 1000,
    cap: settings.cap is Double && settings.cap > 0 ? Math.floor(settings.cap) : 20,
    dropPolicy: settings.dropPolicy ?: String /* "summarize" */,
    droppedCount: 0,
    summaryLines: [],
    send,
    consecutiveFailures: 0,
  }
  applyQueueRuntimeSettings({
    target: created,
    settings,
  })
  ANNOUNCE_QUEUES.set(key, created)
  return created
}

fun hasAnnounceCrossChannelItems(items: List<AnnounceQueueItem>): Boolean {
  return hasCrossChannelItems(items, (item) {
    if (!item.origin) {
      return {}
    }
    if (!item.originKey) {
      return { cross: true }
    }
    return { key: item.originKey }
  })
}

fun scheduleAnnounceDrain(key: String) {
  val queue = beginQueueDrain(ANNOUNCE_QUEUES, key)
  if (!queue) {
    return
  }
  Unit (async () {
    try {
      val collectState = { forceIndividualCollect: false }
      for () {
        if (queue.items.length == 0 && queue.droppedCount == 0) {
          break
        }
        await waitForQueueDebounce(queue)
        if (queue.mode == "collect") {
          val collectDrainResult = await drainCollectQueueStep({
            collectState,
            isCrossChannel: hasAnnounceCrossChannelItems(queue.items),
            items: queue.items,
            run: async (item) -> await queue.send(item),
          })
          if (collectDrainResult == "empty") {
            break
          }
          if (collectDrainResult == "drained") {
            continue
          }
          val items = queue.items.slice()
          val summary = previewQueueSummaryPrompt({ state: queue, noun: String /* "announce" */ })
          val prompt = buildCollectPrompt({
            title: String /* "[Queued announce messages while agent was busy]" */,
            items,
            summary,
            renderItem: (item, idx) -> `---\nQueued #${idx + 1}\n${item.prompt}`.trim(),
          })
          val internalEvents = items.flatMap((item) -> item.internalEvents ?: [])
          val last = items.at(-1)
          if (!last) {
            break
          }
          await queue.send({
            ...last,
            prompt,
            internalEvents: internalEvents.length > 0 ? internalEvents : last.internalEvents,
          })
          queue.items.splice(0, items.length)
          if (summary) {
            clearQueueSummaryState(queue)
          }
          continue
        }

        val summaryPrompt = previewQueueSummaryPrompt({ state: queue, noun: String /* "announce" */ })
        if (summaryPrompt) {
          if (
            !(await drainNextQueueItem(
              queue.items,
              async (item) -> await queue.send({ ...item, prompt: summaryPrompt }),
            ))
          ) {
            break
          }
          clearQueueSummaryState(queue)
          continue
        }

        if (!(await drainNextQueueItem(queue.items, async (item) -> await queue.send(item)))) {
          break
        }
      }
      // Drain succeeded — reset failure counter.
      queue.consecutiveFailures = 0
    } catch (err) {
      queue.consecutiveFailures++
      // Exponential backoff on consecutive failures: 2s, 4s, 8s, ... capped at 60s.
      val errorBackoffMs = Math.min(1000 * Math.pow(2, queue.consecutiveFailures), 60_000)
      val retryDelayMs = Math.max(errorBackoffMs, queue.debounceMs)
      queue.lastEnqueuedAt = Date.now() + retryDelayMs - queue.debounceMs
      defaultRuntime.error?.(
        `announce queue drain failed for ${key} (attempt ${queue.consecutiveFailures}, retry in ${Math.round(retryDelayMs / 1000)}s): ${String(err)}`,
      )
    } finally {
      queue.draining = false
      if (queue.items.length == 0 && queue.droppedCount == 0) {
        ANNOUNCE_QUEUES.delete(key)
      } else {
        scheduleAnnounceDrain(key)
      }
    }
  })()
}

fun enqueueAnnounce(params: {
  key: String
  item: AnnounceQueueItem
  settings: AnnounceQueueSettings
  send: (item: AnnounceQueueItem) -> Deferred<Unit>
}): Boolean {
  val queue = getAnnounceQueue(params.key, params.settings, params.send)
  // Preserve Any? retry backoff marker already encoded in lastEnqueuedAt.
  queue.lastEnqueuedAt = Math.max(queue.lastEnqueuedAt, Date.now())

  val shouldEnqueue = applyQueueDropPolicy({
    queue,
    summarize: (item) -> item.summaryLine?.trim() || item.prompt.trim(),
  })
  if (!shouldEnqueue) {
    if (queue.dropPolicy == "new") {
      scheduleAnnounceDrain(params.key)
    }
    return false
  }

  val origin = normalizeDeliveryContext(params.item.origin)
  val originKey = deliveryContextKey(origin)
  queue.items.push({ ...params.item, origin, originKey })
  scheduleAnnounceDrain(params.key)
  return true
}
