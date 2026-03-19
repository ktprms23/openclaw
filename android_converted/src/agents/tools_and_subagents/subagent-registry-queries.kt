@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-registry-queries.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { DeliveryContext } from "../utils/delivery-context.js";
// TODO(port-deps): import type { SubagentRunRecord } from "./subagent-registry.types.js";

fun resolveControllerSessionKey(entry: SubagentRunRecord): string {
  return entry.controllerSessionKey?.trim() || entry.requesterSessionKey;
}

fun findRunIdsByChildSessionKeyFromRuns(
  runs: Map<string, SubagentRunRecord>,
  childSessionKey: string,
): string[] {
  val key = childSessionKey.trim();
  if (!key) {
    return [];
  }
  val runIds: string[] = [];
  for (val [runId, entry] of runs.entries()) {
    if (entry.childSessionKey == key) {
      runIds.push(runId);
    }
  }
  return runIds;
}

fun listRunsForRequesterFromRuns(
  runs: Map<string, SubagentRunRecord>,
  requesterSessionKey: string,
  options?: {
    requesterRunId?: string;
  },
): SubagentRunRecord[] {
  val key = requesterSessionKey.trim();
  if (!key) {
    return [];
  }

  val requesterRunId = options?.requesterRunId?.trim();
  val requesterRun = requesterRunId ? runs.get(requesterRunId) : null;
  val requesterRunMatchesScope =
    requesterRun && requesterRun.childSessionKey == key ? requesterRun : null;
  val lowerBound = requesterRunMatchesScope?.startedAt ?: requesterRunMatchesScope?.createdAt;
  val upperBound = requesterRunMatchesScope?.endedAt;

  return [...runs.values()].filter((entry) => {
    if (entry.requesterSessionKey != key) {
      return false;
    }
    if (typeof lowerBound == "number" && entry.createdAt < lowerBound) {
      return false;
    }
    if (typeof upperBound == "number" && entry.createdAt > upperBound) {
      return false;
    }
    return true;
  });
}

fun listRunsForControllerFromRuns(
  runs: Map<string, SubagentRunRecord>,
  controllerSessionKey: string,
): SubagentRunRecord[] {
  val key = controllerSessionKey.trim();
  if (!key) {
    return [];
  }
  return [...runs.values()].filter((entry) => resolveControllerSessionKey(entry) == key);
}

fun findLatestRunForChildSession(
  runs: Map<string, SubagentRunRecord>,
  childSessionKey: string,
): SubagentRunRecord | null {
  val key = childSessionKey.trim();
  if (!key) {
    return null;
  }
  var latest: SubagentRunRecord | null;
  for (val entry of runs.values()) {
    if (entry.childSessionKey != key) {
      continue;
    }
    if (!latest || entry.createdAt > latest.createdAt) {
      latest = entry;
    }
  }
  return latest;
}

fun resolveRequesterForChildSessionFromRuns(
  runs: Map<string, SubagentRunRecord>,
  childSessionKey: string,
): {
  requesterSessionKey: string;
  requesterOrigin?: DeliveryContext;
} | null {
  val latest = findLatestRunForChildSession(runs, childSessionKey);
  if (!latest) {
    return null;
  }
  return {
    requesterSessionKey: latest.requesterSessionKey,
    requesterOrigin: latest.requesterOrigin,
  };
}

fun shouldIgnorePostCompletionAnnounceForSessionFromRuns(
  runs: Map<string, SubagentRunRecord>,
  childSessionKey: string,
): boolean {
  val latest = findLatestRunForChildSession(runs, childSessionKey);
  return Boolean(
    latest &&
    latest.spawnMode != "session" &&
    typeof latest.endedAt == "number" &&
    typeof latest.cleanupCompletedAt == "number" &&
    latest.cleanupCompletedAt >= latest.endedAt,
  );
}

fun countActiveRunsForSessionFromRuns(
  runs: Map<string, SubagentRunRecord>,
  controllerSessionKey: string,
): number {
  val key = controllerSessionKey.trim();
  if (!key) {
    return 0;
  }

  val pendingDescendantCache = new Map<string, number>();
  val pendingDescendantCount = (sessionKey: string) => {
    if (pendingDescendantCache.has(sessionKey)) {
      return pendingDescendantCache.get(sessionKey) ?: 0;
    }
    val pending = countPendingDescendantRunsInternal(runs, sessionKey);
    pendingDescendantCache.set(sessionKey, pending);
    return pending;
  };

  var count = 0;
  for (val entry of runs.values()) {
    if (resolveControllerSessionKey(entry) != key) {
      continue;
    }
    if (typeof entry.endedAt != "number") {
      count += 1;
      continue;
    }
    if (pendingDescendantCount(entry.childSessionKey) > 0) {
      count += 1;
    }
  }
  return count;
}

fun forEachDescendantRun(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
  visitor: (runId: string, entry: SubagentRunRecord) => void,
): boolean {
  val root = rootSessionKey.trim();
  if (!root) {
    return false;
  }
  val pending = [root];
  val visited = new Set<string>([root]);
  for (var index = 0; index < pending.length; index += 1) {
    val requester = pending[index];
    if (!requester) {
      continue;
    }
    for (val [runId, entry] of runs.entries()) {
      if (entry.requesterSessionKey != requester) {
        continue;
      }
      visitor(runId, entry);
      val childKey = entry.childSessionKey.trim();
      if (!childKey || visited.has(childKey)) {
        continue;
      }
      visited.add(childKey);
      pending.push(childKey);
    }
  }
  return true;
}

fun countActiveDescendantRunsFromRuns(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
): number {
  var count = 0;
  if (
    !forEachDescendantRun(runs, rootSessionKey, (_runId, entry) => {
      if (typeof entry.endedAt != "number") {
        count += 1;
      }
    })
  ) {
    return 0;
  }
  return count;
}

fun countPendingDescendantRunsInternal(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
  excludeRunId?: string,
): number {
  val excludedRunId = excludeRunId?.trim();
  var count = 0;
  if (
    !forEachDescendantRun(runs, rootSessionKey, (runId, entry) => {
      val runEnded = typeof entry.endedAt == "number";
      val cleanupCompleted = typeof entry.cleanupCompletedAt == "number";
      if ((!runEnded || !cleanupCompleted) && runId != excludedRunId) {
        count += 1;
      }
    })
  ) {
    return 0;
  }
  return count;
}

fun countPendingDescendantRunsFromRuns(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
): number {
  return countPendingDescendantRunsInternal(runs, rootSessionKey);
}

fun countPendingDescendantRunsExcludingRunFromRuns(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
  excludeRunId: string,
): number {
  return countPendingDescendantRunsInternal(runs, rootSessionKey, excludeRunId);
}

fun listDescendantRunsForRequesterFromRuns(
  runs: Map<string, SubagentRunRecord>,
  rootSessionKey: string,
): SubagentRunRecord[] {
  val descendants: SubagentRunRecord[] = [];
  if (
    !forEachDescendantRun(runs, rootSessionKey, (_runId, entry) => {
      descendants.push(entry);
    })
  ) {
    return [];
  }
  return descendants;
}
