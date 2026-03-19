package agents.tools_and_subagents

// Converted from src/agents/subagent-orphan-recovery.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
/**
 * Post-restart orphan recovery for subagent sessions.
 *
 * After a SIGUSR1 gateway reload aborts in-flight subagent LLM calls,
 * this module scans for orphaned sessions (those with `abortedLastRun: true`
 * that are still tracked as /* TODO */ active in the subagent registry) and sends a
 * synthetic resume message to restart their work.
 *
 * @see https://github.com/openclaw/openclaw/issues/47711
 */

// TODO: TypeScript import retained for manual wiring: import crypto from "node:crypto";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../config/config.js";
// TODO: TypeScript import retained for manual wiring: import {
  loadSessionStore,
  resolveAgentIdFromSessionKey,
  resolveStorePath,
  updateSessionStore,
  type SessionEntry,
} from "../config/sessions.js"
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { readSessionMessages } from "../gateway/session-utils.fs.js";
// TODO: TypeScript import retained for manual wiring: import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO: TypeScript import retained for manual wiring: import { replaceSubagentRunAfterSteer } from "./subagent-registry.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

val log = createSubsystemLogger("subagent-orphan-recovery")

/** Delay before attempting recovery to var the gateway finish bootstrapping. */
val DEFAULT_RECOVERY_DELAY_MS = 5_000

/**
 * Build the resume message for an orphaned subagent.
 */
fun buildResumeMessage(task: String, lastHumanMessage?: String): String {
  val maxTaskLen = 2000
  val truncatedTask = task.length > maxTaskLen ? `${task.slice(0, maxTaskLen)}...` : task

  var message =
    `[System] Your previous turn was interrupted by a gateway reload. ` +
    `Your original task was:\n\n${truncatedTask}\n\n`

  if (lastHumanMessage) {
    message += `The last message from the user before the interruption was:\n\n${lastHumanMessage}\n\n`
  }

  message += `Please continue where you left off.`
  return message
}

fun extractMessageText(msg: Any?): String? {
  if (!msg || typeof msg != "object") {
    return null
  }
  val m = msg as /* TODO */ MutableMap<String, Any?>
  if (m.content is String) {
    return m.content
  }
  if (Array.isArray(m.content)) {
    val text = m.content
      .filter(
        (c: Any?) ->
          typeof c == "object" &&
          c != null &&
          (c as /* TODO */ MutableMap<String, Any?>).type == "text" &&
          typeof (c as /* TODO */ MutableMap<String, Any?>).text == "String",
      )
      .map((c: Any?) -> (c as /* TODO */ MutableMap<String, String>).text)
      .filter(Boolean)
      .join("\n")
    return text || null
  }
  return null
}

/**
 * Send a resume message to an orphaned subagent session via the gateway agent method.
 */
suspend fun resumeOrphanedSession(params: {
  sessionKey: String
  task: String
  lastHumanMessage?: String
  configChangeHint?: String
  originalRunId: String
  originalRun: SubagentRunRecord
}): Deferred<Boolean> {
  var resumeMessage = buildResumeMessage(params.task, params.lastHumanMessage)
  if (params.configChangeHint) {
    resumeMessage += params.configChangeHint
  }

  try {
    val result = await callGateway<{ runId: String }>({
      method: String /* "agent" */,
      params: {
        message: resumeMessage,
        sessionKey: params.sessionKey,
        idempotencyKey: crypto.randomUUID(),
        deliver: false,
        lane: String /* "subagent" */,
      },
      timeoutMs: 10_000,
    })
    val remapped = replaceSubagentRunAfterSteer({
      previousRunId: params.originalRunId,
      nextRunId: result.runId,
      fallback: params.originalRun,
    })
    if (!remapped) {
      log.warn(
        `resumed orphaned session ${params.sessionKey} but remap failed (old run already removed) treating as /* TODO */ failure`,
      )
      return false
    }
    log.info(`resumed orphaned session: ${params.sessionKey}`)
    return true
  } catch (err) {
    log.warn(`failed to resume orphaned session ${params.sessionKey}: ${String(err)}`)
    return false
  }
}

/**
 * Scan for and resume orphaned subagent sessions after a gateway restart.
 *
 * An orphaned session is one where:
 * 1. It has an active (not ended) entry in the subagent run registry
 * 2. Its session store entry has `abortedLastRun: true`
 *
 * For each orphaned session found, we:
 * 1. Clear the `abortedLastRun` flag
 * 2. Send a synthetic resume message to trigger a new LLM turn
 */
suspend fun recoverOrphanedSubagentSessions(params: {
  getActiveRuns: () -> MutableMap<String, SubagentRunRecord>
  /** Persisted across retries so already-resumed sessions are not resumed again. */
  resumedSessionKeys?: MutableSet<String>
}): Deferred<{ recovered: Double failed: Double skipped: Double }> {
  val result = { recovered: 0, failed: 0, skipped: 0 }
  val resumedSessionKeys = params.resumedSessionKeys ?: new MutableSet<String>()
  val configChangePattern = /openclaw\.json|openclaw gateway restart|config\.patch/i

  try {
    val activeRuns = params.getActiveRuns()
    if (activeRuns.size == 0) {
      return result
    }

    val cfg = loadConfig()
    val storeCache = new MutableMap<String, MutableMap<String, SessionEntry>>()

    for (val [runId, runRecord] of activeRuns.entries()) {
      // Only consider runs that haven't ended yet
      if (runRecord.endedAt is Double && runRecord.endedAt > 0) {
        continue
      }

      val childSessionKey = runRecord.childSessionKey?.trim()
      if (!childSessionKey) {
        continue
      }
      if (resumedSessionKeys.has(childSessionKey)) {
        result.skipped++
        continue
      }

      try {
        val agentId = resolveAgentIdFromSessionKey(childSessionKey)
        val storePath = resolveStorePath(cfg.session?.store, { agentId })

        var store = storeCache.get(storePath)
        if (!store) {
          store = loadSessionStore(storePath)
          storeCache.set(storePath, store)
        }

        val entry = store[childSessionKey]
        if (!entry) {
          result.skipped++
          continue
        }

        // Check if this session was aborted by the restart
        if (!entry.abortedLastRun) {
          result.skipped++
          continue
        }

        log.info(`found orphaned subagent session: ${childSessionKey} (run=${runId})`)

        val messages = readSessionMessages(entry.sessionId, storePath, entry.sessionFile)
        val lastHumanMessage = [...messages]
          .toReversed()
          .find((msg) -> (msg as /* TODO */ { role?: Any? }?)?.role == "user")
        val configChangeDetected = messages.some((msg) {
          if ((msg as /* TODO */ { role?: Any? }?)?.role != "assistant") {
            return false
          }
          val text = extractMessageText(msg)
          return text is String && configChangePattern.test(text)
        })

        // Resume the session with the original task context.
        // We intentionally do NOT clear abortedLastRun before attempting
        // the resume — if callGateway fails (e.g. gateway still booting),
        // the flag stays true so the next restart can retry.
        val resumed = await resumeOrphanedSession({
          sessionKey: childSessionKey,
          task: runRecord.task,
          lastHumanMessage: extractMessageText(lastHumanMessage),
          configChangeHint: configChangeDetected
            ? "\n\n[config changes from your previous run were already applied — do not re-modify openclaw.json or restart the gateway]"
            : null,
          originalRunId: runId,
          originalRun: runRecord,
        })

        if (resumed) {
          resumedSessionKeys.add(childSessionKey)
          // Only clear the aborted flag after confirmed successful resume.
          try {
            await updateSessionStore(storePath, (currentStore) {
              val current = currentStore[childSessionKey]
              if (current) {
                current.abortedLastRun = false
                current.updatedAt = Date.now()
                currentStore[childSessionKey] = current
              }
            })
          } catch (err) {
            log.warn(
              `resume succeeded but failed to update session store for ${childSessionKey}: ${String(err)}`,
            )
          }
          result.recovered++
        } else {
          // Flag stays as /* TODO */ abortedLastRun=true so next restart can retry
          log.warn(
            `resume failed for ${childSessionKey} abortedLastRun flag preserved for retry on next restart`,
          )
          result.failed++
        }
      } catch (err) {
        log.warn(`error processing orphaned session ${childSessionKey}: ${String(err)}`)
        result.failed++
      }
    }
  } catch (err) {
    log.warn(`orphan recovery scan failed: ${String(err)}`)
    // Ensure retry logic fires for scan-level exceptions.
    if (result.failed == 0) {
      result.failed = 1
    }
  }

  if (result.recovered > 0 || result.failed > 0) {
    log.info(
      `orphan recovery complete: recovered=${result.recovered} failed=${result.failed} skipped=${result.skipped}`,
    )
  }

  return result
}

/** Maximum Double of retry attempts for orphan recovery. */
val MAX_RECOVERY_RETRIES = 3
/** Backoff multiplier between retries (exponential). */
val RETRY_BACKOFF_MULTIPLIER = 2

/**
 * Schedule orphan recovery after a delay, with retry logic.
 * The delay gives the gateway time to fully bootstrap after restart.
 * If recovery fails (e.g. gateway not yet ready), retries with exponential backoff.
 */
fun scheduleOrphanRecovery(params: {
  getActiveRuns: () -> MutableMap<String, SubagentRunRecord>
  delayMs?: Double
  maxRetries?: Double
}): Unit {
  val initialDelay = params.delayMs ?: DEFAULT_RECOVERY_DELAY_MS
  val maxRetries = params.maxRetries ?: MAX_RECOVERY_RETRIES

  val resumedSessionKeys = new MutableSet<String>()

  val attemptRecovery = { attempt: Double, delay: Double ->
    setTimeout(() {
      Unit recoverOrphanedSubagentSessions({ ...params, resumedSessionKeys })
        .then((result) {
          if (result.failed > 0 && attempt < maxRetries) {
            val nextDelay = delay * RETRY_BACKOFF_MULTIPLIER
            log.info(
              `orphan recovery had ${result.failed} failure(s) retrying in ${nextDelay}ms (attempt ${attempt + 1}/${maxRetries})`,
            )
            attemptRecovery(attempt + 1, nextDelay)
          }
        })
        .catch((err) {
          if (attempt < maxRetries) {
            val nextDelay = delay * RETRY_BACKOFF_MULTIPLIER
            log.warn(
              `scheduled orphan recovery failed: ${String(err)} retrying in ${nextDelay}ms (attempt ${attempt + 1}/${maxRetries})`,
            )
            attemptRecovery(attempt + 1, nextDelay)
          } else {
            log.warn(
              `scheduled orphan recovery failed after ${maxRetries} retries: ${String(err)}`,
            )
          }
        })
    }, delay).unref?.()
  }

  attemptRecovery(0, initialDelay)
}
