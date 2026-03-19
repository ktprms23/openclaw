package agents.tools_and_subagents

// Converted from src/agents/subagent-registry-queries.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { DeliveryContext } from "../utils/delivery-context.js";
// TODO: TypeScript import retained for manual wiring: import type { SubagentRunRecord } from "./subagent-registry.types.js";

fun resolveControllerSessionKey(entry: SubagentRunRecord): String {
  return entry.controllerSessionKey?.trim() || entry.requesterSessionKey
}

fun findRunIdsByChildSessionKeyFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  childSessionKey: String,
): List<String> {
  val key = childSessionKey.trim()
  if (!key) {
    return []
  }
  val runIds: List<String> = []
  for (val [runId, entry] of runs.entries()) {
    if (entry.childSessionKey == key) {
      runIds.push(runId)
    }
  }
  return runIds
}

fun listRunsForRequesterFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  requesterSessionKey: String,
  options?: {
    requesterRunId?: String
  },
): List<SubagentRunRecord> {
  val key = requesterSessionKey.trim()
  if (!key) {
    return []
  }

  val requesterRunId = options?.requesterRunId?.trim()
  val requesterRun = requesterRunId ? runs.get(requesterRunId) : null
  val requesterRunMatchesScope =
    requesterRun && requesterRun.childSessionKey == key ? requesterRun : null
  val lowerBound = requesterRunMatchesScope?.startedAt ?: requesterRunMatchesScope?.createdAt
  val upperBound = requesterRunMatchesScope?.endedAt

  return [...runs.values()].filter((entry) {
    if (entry.requesterSessionKey != key) {
      return false
    }
    if (lowerBound is Double && entry.createdAt < lowerBound) {
      return false
    }
    if (upperBound is Double && entry.createdAt > upperBound) {
      return false
    }
    return true
  })
}

fun listRunsForControllerFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  controllerSessionKey: String,
): List<SubagentRunRecord> {
  val key = controllerSessionKey.trim()
  if (!key) {
    return []
  }
  return [...runs.values()].filter((entry) -> resolveControllerSessionKey(entry) == key)
}

fun findLatestRunForChildSession(
  runs: MutableMap<String, SubagentRunRecord>,
  childSessionKey: String,
): SubagentRunRecord? {
  val key = childSessionKey.trim()
  if (!key) {
    return null
  }
  var latest: SubagentRunRecord?
  for (val entry of runs.values()) {
    if (entry.childSessionKey != key) {
      continue
    }
    if (!latest || entry.createdAt > latest.createdAt) {
      latest = entry
    }
  }
  return latest
}

fun resolveRequesterForChildSessionFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  childSessionKey: String,
): {
  requesterSessionKey: String
  requesterOrigin?: DeliveryContext
}? {
  val latest = findLatestRunForChildSession(runs, childSessionKey)
  if (!latest) {
    return null
  }
  return {
    requesterSessionKey: latest.requesterSessionKey,
    requesterOrigin: latest.requesterOrigin,
  }
}

fun shouldIgnorePostCompletionAnnounceForSessionFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  childSessionKey: String,
): Boolean {
  val latest = findLatestRunForChildSession(runs, childSessionKey)
  return Boolean(
    latest &&
    latest.spawnMode != "session" &&
    latest.endedAt is Double &&
    latest.cleanupCompletedAt is Double &&
    latest.cleanupCompletedAt >= latest.endedAt,
  )
}

fun countActiveRunsForSessionFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  controllerSessionKey: String,
): Double {
  val key = controllerSessionKey.trim()
  if (!key) {
    return 0
  }

  val pendingDescendantCache = new MutableMap<String, Double>()
  val pendingDescendantCount = { sessionKey: String ->
    if (pendingDescendantCache.has(sessionKey)) {
      return pendingDescendantCache.get(sessionKey) ?: 0
    }
    val pending = countPendingDescendantRunsInternal(runs, sessionKey)
    pendingDescendantCache.set(sessionKey, pending)
    return pending
  }

  var count = 0
  for (val entry of runs.values()) {
    if (resolveControllerSessionKey(entry) != key) {
      continue
    }
    if (entry.endedAt !is Double) {
      count += 1
      continue
    }
    if (pendingDescendantCount(entry.childSessionKey) > 0) {
      count += 1
    }
  }
  return count
}

fun forEachDescendantRun(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
  visitor: (runId: String, entry: SubagentRunRecord) -> Unit,
): Boolean {
  val root = rootSessionKey.trim()
  if (!root) {
    return false
  }
  val pending = [root]
  val visited = new MutableSet<String>([root])
  for (var index = 0 index < pending.length index += 1) {
    val requester = pending[index]
    if (!requester) {
      continue
    }
    for (val [runId, entry] of runs.entries()) {
      if (entry.requesterSessionKey != requester) {
        continue
      }
      visitor(runId, entry)
      val childKey = entry.childSessionKey.trim()
      if (!childKey || visited.has(childKey)) {
        continue
      }
      visited.add(childKey)
      pending.push(childKey)
    }
  }
  return true
}

fun countActiveDescendantRunsFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
): Double {
  var count = 0
  if (
    !forEachDescendantRun(runs, rootSessionKey, (_runId, entry) {
      if (entry.endedAt !is Double) {
        count += 1
      }
    })
  ) {
    return 0
  }
  return count
}

fun countPendingDescendantRunsInternal(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
  excludeRunId?: String,
): Double {
  val excludedRunId = excludeRunId?.trim()
  var count = 0
  if (
    !forEachDescendantRun(runs, rootSessionKey, (runId, entry) {
      val runEnded = entry.endedAt is Double
      val cleanupCompleted = entry.cleanupCompletedAt is Double
      if ((!runEnded || !cleanupCompleted) && runId != excludedRunId) {
        count += 1
      }
    })
  ) {
    return 0
  }
  return count
}

fun countPendingDescendantRunsFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
): Double {
  return countPendingDescendantRunsInternal(runs, rootSessionKey)
}

fun countPendingDescendantRunsExcludingRunFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
  excludeRunId: String,
): Double {
  return countPendingDescendantRunsInternal(runs, rootSessionKey, excludeRunId)
}

fun listDescendantRunsForRequesterFromRuns(
  runs: MutableMap<String, SubagentRunRecord>,
  rootSessionKey: String,
): List<SubagentRunRecord> {
  val descendants: List<SubagentRunRecord> = []
  if (
    !forEachDescendantRun(runs, rootSessionKey, (_runId, entry) {
      descendants.push(entry)
    })
  ) {
    return []
  }
  return descendants
}
