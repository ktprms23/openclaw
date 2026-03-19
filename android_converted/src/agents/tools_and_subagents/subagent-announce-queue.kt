@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-announce-queue.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { type QueueDropPolicy, type QueueMode } from "../auto-reply/reply/queue.js";
// TODO(port-deps): import { defaultRuntime } from "../runtime.js";
// TODO(port-deps): import {
// TODO(port-deps): type DeliveryContext,
// TODO(port-deps): deliveryContextKey,
// TODO(port-deps): normalizeDeliveryContext,
// TODO(port-deps): } from "../utils/delivery-context.js";
// TODO(port-deps): import {
// TODO(port-deps): applyQueueRuntimeSettings,
// TODO(port-deps): applyQueueDropPolicy,
// TODO(port-deps): beginQueueDrain,
// TODO(port-deps): buildCollectPrompt,
// TODO(port-deps): clearQueueSummaryState,
// TODO(port-deps): drainCollectQueueStep,
// TODO(port-deps): drainNextQueueItem,
// TODO(port-deps): hasCrossChannelItems,
// TODO(port-deps): previewQueueSummaryPrompt,
// TODO(port-deps): waitForQueueDebounce,
// TODO(port-deps): } from "../utils/queue-helpers.js";
// TODO(port-deps): import type { AgentInternalEvent } from "./internal-events.js";

typealias AnnounceQueueItem = Any /* TODO: translate TypeScript alias */

typealias AnnounceQueueSettings = Any /* TODO: translate TypeScript alias */

typealias AnnounceQueueState = Any /* TODO: translate TypeScript alias */

val ANNOUNCE_QUEUES = new Map<string, AnnounceQueueState>();

fun resetAnnounceQueuesForTests() {
  // Test isolation: other suites may leave a draining queue behind in the worker.
  // Clearing the map alone isn't enough because drain loops capture `queue` by reference.
  for (val queue of ANNOUNCE_QUEUES.values()) {
    queue.items.length = 0;
    queue.summaryLines.length = 0;
    queue.droppedCount = 0;
    queue.lastEnqueuedAt = 0;
  }
  ANNOUNCE_QUEUES.clear();
}

fun getAnnounceQueue(
  key: string,
  settings: AnnounceQueueSettings,
  send: (item: AnnounceQueueItem) => Promise<void>,
) {
  val existing = ANNOUNCE_QUEUES.get(key);
  if (existing) {
    applyQueueRuntimeSettings({
      target: existing,
      settings,
    });
    existing.send = send;
    return existing;
  }
  val created: AnnounceQueueState = {
    items: [],
    draining: false,
    lastEnqueuedAt: 0,
    mode: settings.mode,
    debounceMs: typeof settings.debounceMs == "number" ? Math.max(0, settings.debounceMs) : 1000,
    cap: typeof settings.cap == "number" && settings.cap > 0 ? Math.floor(settings.cap) : 20,
    dropPolicy: settings.dropPolicy ?: "summarize",
    droppedCount: 0,
    summaryLines: [],
    send,
    consecutiveFailures: 0,
  };
  applyQueueRuntimeSettings({
    target: created,
    settings,
  });
  ANNOUNCE_QUEUES.set(key, created);
  return created;
}

fun hasAnnounceCrossChannelItems(items: AnnounceQueueItem[]): boolean {
  return hasCrossChannelItems(items, (item) => {
    if (!item.origin) {
      return {};
    }
    if (!item.originKey) {
      return { cross: true };
    }
    return { key: item.originKey };
  });
}

fun scheduleAnnounceDrain(key: string) {
  val queue = beginQueueDrain(ANNOUNCE_QUEUES, key);
  if (!queue) {
    return;
  }
  void (async () => {
    try {
      val collectState = { forceIndividualCollect: false };
      for (;;) {
        if (queue.items.length == 0 && queue.droppedCount == 0) {
          break;
        }
        await waitForQueueDebounce(queue);
        if (queue.mode == "collect") {
          val collectDrainResult = await drainCollectQueueStep({
            collectState,
            isCrossChannel: hasAnnounceCrossChannelItems(queue.items),
            items: queue.items,
            run: async (item) => await queue.send(item),
          });
          if (collectDrainResult == "empty") {
            break;
          }
          if (collectDrainResult == "drained") {
            continue;
          }
          val items = queue.items.slice();
          val summary = previewQueueSummaryPrompt({ state: queue, noun: "announce" });
          val prompt = buildCollectPrompt({
            title: "[Queued announce messages while agent was busy]",
            items,
            summary,
            renderItem: (item, idx) => `---\nQueued #${idx + 1}\n${item.prompt}`.trim(),
          });
          val internalEvents = items.flatMap((item) => item.internalEvents ?: []);
          val last = items.at(-1);
          if (!last) {
            break;
          }
          await queue.send({
            ...last,
            prompt,
            internalEvents: internalEvents.length > 0 ? internalEvents : last.internalEvents,
          });
          queue.items.splice(0, items.length);
          if (summary) {
            clearQueueSummaryState(queue);
          }
          continue;
        }

        val summaryPrompt = previewQueueSummaryPrompt({ state: queue, noun: "announce" });
        if (summaryPrompt) {
          if (
            !(await drainNextQueueItem(
              queue.items,
              async (item) => await queue.send({ ...item, prompt: summaryPrompt }),
            ))
          ) {
            break;
          }
          clearQueueSummaryState(queue);
          continue;
        }

        if (!(await drainNextQueueItem(queue.items, async (item) => await queue.send(item)))) {
          break;
        }
      }
      // Drain succeeded — reset failure counter.
      queue.consecutiveFailures = 0;
    } catch (err) {
      queue.consecutiveFailures++;
      // Exponential backoff on consecutive failures: 2s, 4s, 8s, ... capped at 60s.
      val errorBackoffMs = Math.min(1000 * Math.pow(2, queue.consecutiveFailures), 60_000);
      val retryDelayMs = Math.max(errorBackoffMs, queue.debounceMs);
      queue.lastEnqueuedAt = Date.now() + retryDelayMs - queue.debounceMs;
      defaultRuntime.error?.(
        `announce queue drain failed for ${key} (attempt ${queue.consecutiveFailures}, retry in ${Math.round(retryDelayMs / 1000)}s): ${String(err)}`,
      );
    } finally {
      queue.draining = false;
      if (queue.items.length == 0 && queue.droppedCount == 0) {
        ANNOUNCE_QUEUES.delete(key);
      } else {
        scheduleAnnounceDrain(key);
      }
    }
  })();
}

fun enqueueAnnounce(params: {
  key: string;
  item: AnnounceQueueItem;
  settings: AnnounceQueueSettings;
  send: (item: AnnounceQueueItem) => Promise<void>;
}): boolean {
  val queue = getAnnounceQueue(params.key, params.settings, params.send);
  // Preserve any retry backoff marker already encoded in lastEnqueuedAt.
  queue.lastEnqueuedAt = Math.max(queue.lastEnqueuedAt, Date.now());

  val shouldEnqueue = applyQueueDropPolicy({
    queue,
    summarize: (item) => item.summaryLine?.trim() || item.prompt.trim(),
  });
  if (!shouldEnqueue) {
    if (queue.dropPolicy == "new") {
      scheduleAnnounceDrain(params.key);
    }
    return false;
  }

  val origin = normalizeDeliveryContext(params.item.origin);
  val originKey = deliveryContextKey(origin);
  queue.items.push({ ...params.item, origin, originKey });
  scheduleAnnounceDrain(params.key);
  return true;
}
