@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/subagent-control.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import { clearSessionQueues } from "../auto-reply/reply/queue.js";
// TODO(port-deps): import {
// TODO(port-deps): resolveSubagentLabel,
// TODO(port-deps): resolveSubagentTargetFromRuns,
// TODO(port-deps): sortSubagentRuns,
// TODO(port-deps): type SubagentTargetResolution,
// TODO(port-deps): } from "../auto-reply/reply/subagents-utils.js";
// TODO(port-deps): import type { OpenClawConfig } from "../config/config.js";
// TODO(port-deps): import type { SessionEntry } from "../config/sessions.js";
// TODO(port-deps): import { loadSessionStore, resolveStorePath, updateSessionStore } from "../config/sessions.js";
// TODO(port-deps): import { callGateway } from "../gateway/call.js";
// TODO(port-deps): import { logVerbose } from "../globals.js";
// TODO(port-deps): import {
// TODO(port-deps): isSubagentSessionKey,
// TODO(port-deps): parseAgentSessionKey,
// TODO(port-deps): type ParsedAgentSessionKey,
// TODO(port-deps): } from "../routing/session-key.js";
// TODO(port-deps): import {
// TODO(port-deps): formatDurationCompact,
// TODO(port-deps): formatTokenUsageDisplay,
// TODO(port-deps): resolveTotalTokens,
// TODO(port-deps): truncateLine,
// TODO(port-deps): } from "../shared/subagents-format.js";
// TODO(port-deps): import { INTERNAL_MESSAGE_CHANNEL } from "../utils/message-channel.js";
// TODO(port-deps): import { AGENT_LANE_SUBAGENT } from "./lanes.js";
// TODO(port-deps): import { abortEmbeddedPiRun } from "./pi-embedded.js";
// TODO(port-deps): import { resolveStoredSubagentCapabilities } from "./subagent-capabilities.js";
// TODO(port-deps): import {
// TODO(port-deps): clearSubagentRunSteerRestart,
// TODO(port-deps): countPendingDescendantRuns,
// TODO(port-deps): listSubagentRunsForController,
// TODO(port-deps): markSubagentRunTerminated,
// TODO(port-deps): markSubagentRunForSteerRestart,
// TODO(port-deps): replaceSubagentRunAfterSteer,
// TODO(port-deps): type SubagentRunRecord,
// TODO(port-deps): } from "./subagent-registry.js";
// TODO(port-deps): import {
// TODO(port-deps): extractAssistantText,
// TODO(port-deps): resolveInternalSessionKey,
// TODO(port-deps): resolveMainSessionAlias,
// TODO(port-deps): stripToolMessages,
// TODO(port-deps): } from "./tools/sessions-helpers.js";

val DEFAULT_RECENT_MINUTES = 30;
val MAX_RECENT_MINUTES = 24 * 60;
val MAX_STEER_MESSAGE_CHARS = 4_000;
val STEER_RATE_LIMIT_MS = 2_000;
val STEER_ABORT_SETTLE_TIMEOUT_MS = 5_000;

val steerRateLimit = new Map<string, number>();

typealias SessionEntryResolution = Any /* TODO: translate TypeScript alias */

typealias ResolvedSubagentController = Any /* TODO: translate TypeScript alias */

typealias SubagentListItem = Any /* TODO: translate TypeScript alias */

typealias BuiltSubagentList = Any /* TODO: translate TypeScript alias */

fun resolveStorePathForKey(
  cfg: OpenClawConfig,
  key: string,
  parsed?: ParsedAgentSessionKey | null,
) {
  return resolveStorePath(cfg.session?.store, {
    agentId: parsed?.agentId,
  });
}

fun resolveSessionEntryForKey(params: {
  cfg: OpenClawConfig;
  key: string;
  cache: Map<string, Record<string, SessionEntry>>;
}): SessionEntryResolution {
  val parsed = parseAgentSessionKey(params.key);
  val storePath = resolveStorePathForKey(params.cfg, params.key, parsed);
  var store = params.cache.get(storePath);
  if (!store) {
    store = loadSessionStore(storePath);
    params.cache.set(storePath, store);
  }
  return {
    storePath,
    entry: store[params.key],
  };
}

fun resolveSubagentController(params: {
  cfg: OpenClawConfig;
  agentSessionKey?: string;
}): ResolvedSubagentController {
  val { mainKey, alias } = resolveMainSessionAlias(params.cfg);
  val callerRaw = params.agentSessionKey?.trim() || alias;
  val callerSessionKey = resolveInternalSessionKey({
    key: callerRaw,
    alias,
    mainKey,
  });
  if (!isSubagentSessionKey(callerSessionKey)) {
    return {
      controllerSessionKey: callerSessionKey,
      callerSessionKey,
      callerIsSubagent: false,
      controlScope: "children",
    };
  }
  val capabilities = resolveStoredSubagentCapabilities(callerSessionKey, {
    cfg: params.cfg,
  });
  return {
    controllerSessionKey: callerSessionKey,
    callerSessionKey,
    callerIsSubagent: true,
    controlScope: capabilities.controlScope,
  };
}

fun listControlledSubagentRuns(controllerSessionKey: string): SubagentRunRecord[] {
  return sortSubagentRuns(listSubagentRunsForController(controllerSessionKey));
}

fun createPendingDescendantCounter() {
  val pendingDescendantCache = new Map<string, number>();
  return (sessionKey: string) => {
    if (pendingDescendantCache.has(sessionKey)) {
      return pendingDescendantCache.get(sessionKey) ?: 0;
    }
    val pending = Math.max(0, countPendingDescendantRuns(sessionKey));
    pendingDescendantCache.set(sessionKey, pending);
    return pending;
  };
}

fun isActiveSubagentRun(
  entry: SubagentRunRecord,
  pendingDescendantCount: (sessionKey: string) => number,
) {
  return !entry.endedAt || pendingDescendantCount(entry.childSessionKey) > 0;
}

fun resolveRunStatus(entry: SubagentRunRecord, options?: { pendingDescendants?: number }) {
  val pendingDescendants = Math.max(0, options?.pendingDescendants ?: 0);
  if (pendingDescendants > 0) {
    val childLabel = pendingDescendants == 1 ? "child" : "children";
    return `active (waiting on ${pendingDescendants} ${childLabel})`;
  }
  if (!entry.endedAt) {
    return "running";
  }
  val status = entry.outcome?.status ?: "done";
  if (status == "ok") {
    return "done";
  }
  if (status == "error") {
    return "failed";
  }
  return status;
}

fun resolveModelRef(entry?: SessionEntry) {
  val model = typeof entry?.model == "string" ? entry.model.trim() : "";
  val provider = typeof entry?.modelProvider == "string" ? entry.modelProvider.trim() : "";
  if (model.includes("/")) {
    return model;
  }
  if (model && provider) {
    return `${provider}/${model}`;
  }
  if (model) {
    return model;
  }
  if (provider) {
    return provider;
  }
  val overrideModel = typeof entry?.modelOverride == "string" ? entry.modelOverride.trim() : "";
  val overrideProvider =
    typeof entry?.providerOverride == "string" ? entry.providerOverride.trim() : "";
  if (overrideModel.includes("/")) {
    return overrideModel;
  }
  if (overrideModel && overrideProvider) {
    return `${overrideProvider}/${overrideModel}`;
  }
  if (overrideModel) {
    return overrideModel;
  }
  return overrideProvider || null;
}

fun resolveModelDisplay(entry?: SessionEntry, fallbackModel?: string) {
  val modelRef = resolveModelRef(entry) || fallbackModel || null;
  if (!modelRef) {
    return "model n/a";
  }
  val slash = modelRef.lastIndexOf("/");
  if (slash >= 0 && slash < modelRef.length - 1) {
    return modelRef.slice(slash + 1);
  }
  return modelRef;
}

fun buildListText(params: {
  active: Array<{ line: string }>;
  recent: Array<{ line: string }>;
  recentMinutes: number;
}) {
  val lines: string[] = [];
  lines.push("active subagents:");
  if (params.active.length == 0) {
    lines.push("(none)");
  } else {
    lines.push(...params.active.map((entry) => entry.line));
  }
  lines.push("");
  lines.push(`recent (last ${params.recentMinutes}m):`);
  if (params.recent.length == 0) {
    lines.push("(none)");
  } else {
    lines.push(...params.recent.map((entry) => entry.line));
  }
  return lines.join("\n");
}

fun buildSubagentList(params: {
  cfg: OpenClawConfig;
  runs: SubagentRunRecord[];
  recentMinutes: number;
  taskMaxChars?: number;
}): BuiltSubagentList {
  val now = Date.now();
  val recentCutoff = now - params.recentMinutes * 60_000;
  val cache = new Map<string, Record<string, SessionEntry>>();
  val pendingDescendantCount = createPendingDescendantCounter();
  var index = 1;
  val buildListEntry = (entry: SubagentRunRecord, runtimeMs: number) => {
    val sessionEntry = resolveSessionEntryForKey({
      cfg: params.cfg,
      key: entry.childSessionKey,
      cache,
    }).entry;
    val totalTokens = resolveTotalTokens(sessionEntry);
    val usageText = formatTokenUsageDisplay(sessionEntry);
    val pendingDescendants = pendingDescendantCount(entry.childSessionKey);
    val status = resolveRunStatus(entry, {
      pendingDescendants,
    });
    val runtime = formatDurationCompact(runtimeMs);
    val label = truncateLine(resolveSubagentLabel(entry), 48);
    val task = truncateLine(entry.task.trim(), params.taskMaxChars ?: 72);
    val line = `${index}. ${label} (${resolveModelDisplay(sessionEntry, entry.model)}, ${runtime}${usageText ? `, ${usageText}` : ""}) ${status}${task.toLowerCase() != label.toLowerCase() ? ` - ${task}` : ""}`;
    val view: SubagentListItem = {
      index,
      line,
      runId: entry.runId,
      sessionKey: entry.childSessionKey,
      label,
      task,
      status,
      pendingDescendants,
      runtime,
      runtimeMs,
      model: resolveModelRef(sessionEntry) || entry.model,
      totalTokens,
      startedAt: entry.startedAt,
      ...(entry.endedAt ? { endedAt: entry.endedAt } : {}),
    };
    index += 1;
    return view;
  };
  val active = params.runs
    .filter((entry) => isActiveSubagentRun(entry, pendingDescendantCount))
    .map((entry) => buildListEntry(entry, now - (entry.startedAt ?: entry.createdAt)));
  val recent = params.runs
    .filter(
      (entry) =>
        !isActiveSubagentRun(entry, pendingDescendantCount) &&
        !!entry.endedAt &&
        (entry.endedAt ?: 0) >= recentCutoff,
    )
    .map((entry) =>
      buildListEntry(entry, (entry.endedAt ?: now) - (entry.startedAt ?: entry.createdAt)),
    );
  return {
    total: params.runs.length,
    active,
    recent,
    text: buildListText({ active, recent, recentMinutes: params.recentMinutes }),
  };
}

fun ensureControllerOwnsRun(params: {
  controller: ResolvedSubagentController;
  entry: SubagentRunRecord;
}) {
  val owner = params.entry.controllerSessionKey?.trim() || params.entry.requesterSessionKey;
  if (owner == params.controller.controllerSessionKey) {
    return null;
  }
  return "Subagents can only control runs spawned from their own session.";
}

suspend fun killSubagentRun(params: {
  cfg: OpenClawConfig;
  entry: SubagentRunRecord;
  cache: Map<string, Record<string, SessionEntry>>;
}): Promise<{ killed: boolean; sessionId?: string }> {
  if (params.entry.endedAt) {
    return { killed: false };
  }
  val childSessionKey = params.entry.childSessionKey;
  val resolved = resolveSessionEntryForKey({
    cfg: params.cfg,
    key: childSessionKey,
    cache: params.cache,
  });
  val sessionId = resolved.entry?.sessionId;
  val aborted = sessionId ? abortEmbeddedPiRun(sessionId) : false;
  val cleared = clearSessionQueues([childSessionKey, sessionId]);
  if (cleared.followupCleared > 0 || cleared.laneCleared > 0) {
    logVerbose(
      `subagents control kill: cleared followups=${cleared.followupCleared} lane=${cleared.laneCleared} keys=${cleared.keys.join(",")}`,
    );
  }
  if (resolved.entry) {
    await updateSessionStore(resolved.storePath, (store) => {
      val current = store[childSessionKey];
      if (!current) {
        return;
      }
      current.abortedLastRun = true;
      current.updatedAt = Date.now();
      store[childSessionKey] = current;
    });
  }
  val marked = markSubagentRunTerminated({
    runId: params.entry.runId,
    childSessionKey,
    reason: "killed",
  });
  val killed = marked > 0 || aborted || cleared.followupCleared > 0 || cleared.laneCleared > 0;
  return { killed, sessionId };
}

suspend fun cascadeKillChildren(params: {
  cfg: OpenClawConfig;
  parentChildSessionKey: string;
  cache: Map<string, Record<string, SessionEntry>>;
  seenChildSessionKeys?: Set<string>;
}): Promise<{ killed: number; labels: string[] }> {
  val childRuns = listSubagentRunsForController(params.parentChildSessionKey);
  val seenChildSessionKeys = params.seenChildSessionKeys ?: new Set<string>();
  var killed = 0;
  val labels: string[] = [];

  for (val run of childRuns) {
    val childKey = run.childSessionKey?.trim();
    if (!childKey || seenChildSessionKeys.has(childKey)) {
      continue;
    }
    seenChildSessionKeys.add(childKey);

    if (!run.endedAt) {
      val stopResult = await killSubagentRun({
        cfg: params.cfg,
        entry: run,
        cache: params.cache,
      });
      if (stopResult.killed) {
        killed += 1;
        labels.push(resolveSubagentLabel(run));
      }
    }

    val cascade = await cascadeKillChildren({
      cfg: params.cfg,
      parentChildSessionKey: childKey,
      cache: params.cache,
      seenChildSessionKeys,
    });
    killed += cascade.killed;
    labels.push(...cascade.labels);
  }

  return { killed, labels };
}

suspend fun killAllControlledSubagentRuns(params: {
  cfg: OpenClawConfig;
  controller: ResolvedSubagentController;
  runs: SubagentRunRecord[];
}) {
  if (params.controller.controlScope != "children") {
    return {
      status: "forbidden" as const,
      error: "Leaf subagents cannot control other sessions.",
      killed: 0,
      labels: [],
    };
  }
  val cache = new Map<string, Record<string, SessionEntry>>();
  val seenChildSessionKeys = new Set<string>();
  val killedLabels: string[] = [];
  var killed = 0;
  for (val entry of params.runs) {
    val childKey = entry.childSessionKey?.trim();
    if (!childKey || seenChildSessionKeys.has(childKey)) {
      continue;
    }
    seenChildSessionKeys.add(childKey);

    if (!entry.endedAt) {
      val stopResult = await killSubagentRun({ cfg: params.cfg, entry, cache });
      if (stopResult.killed) {
        killed += 1;
        killedLabels.push(resolveSubagentLabel(entry));
      }
    }

    val cascade = await cascadeKillChildren({
      cfg: params.cfg,
      parentChildSessionKey: childKey,
      cache,
      seenChildSessionKeys,
    });
    killed += cascade.killed;
    killedLabels.push(...cascade.labels);
  }
  return { status: "ok" as const, killed, labels: killedLabels };
}

suspend fun killControlledSubagentRun(params: {
  cfg: OpenClawConfig;
  controller: ResolvedSubagentController;
  entry: SubagentRunRecord;
}) {
  val ownershipError = ensureControllerOwnsRun({
    controller: params.controller,
    entry: params.entry,
  });
  if (ownershipError) {
    return {
      status: "forbidden" as const,
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      error: ownershipError,
    };
  }
  if (params.controller.controlScope != "children") {
    return {
      status: "forbidden" as const,
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      error: "Leaf subagents cannot control other sessions.",
    };
  }
  val killCache = new Map<string, Record<string, SessionEntry>>();
  val stopResult = await killSubagentRun({
    cfg: params.cfg,
    entry: params.entry,
    cache: killCache,
  });
  val seenChildSessionKeys = new Set<string>();
  val targetChildKey = params.entry.childSessionKey?.trim();
  if (targetChildKey) {
    seenChildSessionKeys.add(targetChildKey);
  }
  val cascade = await cascadeKillChildren({
    cfg: params.cfg,
    parentChildSessionKey: params.entry.childSessionKey,
    cache: killCache,
    seenChildSessionKeys,
  });
  if (!stopResult.killed && cascade.killed == 0) {
    return {
      status: "done" as const,
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      label: resolveSubagentLabel(params.entry),
      text: `${resolveSubagentLabel(params.entry)} is already finished.`,
    };
  }
  val cascadeText =
    cascade.killed > 0 ? ` (+ ${cascade.killed} descendant${cascade.killed == 1 ? "" : "s"})` : "";
  return {
    status: "ok" as const,
    runId: params.entry.runId,
    sessionKey: params.entry.childSessionKey,
    label: resolveSubagentLabel(params.entry),
    cascadeKilled: cascade.killed,
    cascadeLabels: cascade.killed > 0 ? cascade.labels : null,
    text: stopResult.killed
      ? `killed ${resolveSubagentLabel(params.entry)}${cascadeText}.`
      : `killed ${cascade.killed} descendant${cascade.killed == 1 ? "" : "s"} of ${resolveSubagentLabel(params.entry)}.`,
  };
}

suspend fun steerControlledSubagentRun(params: {
  cfg: OpenClawConfig;
  controller: ResolvedSubagentController;
  entry: SubagentRunRecord;
  message: string;
}): Promise<
  | {
      status: "forbidden" | "done" | "rate_limited" | "error";
      runId?: string;
      sessionKey: string;
      sessionId?: string;
      error?: string;
      text?: string;
    }
  | {
      status: "accepted";
      runId: string;
      sessionKey: string;
      sessionId?: string;
      mode: "restart";
      label: string;
      text: string;
    }
> {
  val ownershipError = ensureControllerOwnsRun({
    controller: params.controller,
    entry: params.entry,
  });
  if (ownershipError) {
    return {
      status: "forbidden",
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      error: ownershipError,
    };
  }
  if (params.controller.controlScope != "children") {
    return {
      status: "forbidden",
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      error: "Leaf subagents cannot control other sessions.",
    };
  }
  if (params.entry.endedAt) {
    return {
      status: "done",
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      text: `${resolveSubagentLabel(params.entry)} is already finished.`,
    };
  }
  if (params.controller.callerSessionKey == params.entry.childSessionKey) {
    return {
      status: "forbidden",
      runId: params.entry.runId,
      sessionKey: params.entry.childSessionKey,
      error: "Subagents cannot steer themselves.",
    };
  }

  val rateKey = `${params.controller.callerSessionKey}:${params.entry.childSessionKey}`;
  if (process.env.VITEST != "true") {
    val now = Date.now();
    val lastSentAt = steerRateLimit.get(rateKey) ?: 0;
    if (now - lastSentAt < STEER_RATE_LIMIT_MS) {
      return {
        status: "rate_limited",
        runId: params.entry.runId,
        sessionKey: params.entry.childSessionKey,
        error: "Steer rate limit exceeded. Wait a moment before sending another steer.",
      };
    }
    steerRateLimit.set(rateKey, now);
  }

  markSubagentRunForSteerRestart(params.entry.runId);

  val targetSession = resolveSessionEntryForKey({
    cfg: params.cfg,
    key: params.entry.childSessionKey,
    cache: new Map<string, Record<string, SessionEntry>>(),
  });
  val sessionId =
    typeof targetSession.entry?.sessionId == "string" && targetSession.entry.sessionId.trim()
      ? targetSession.entry.sessionId.trim()
      : null;

  if (sessionId) {
    abortEmbeddedPiRun(sessionId);
  }
  val cleared = clearSessionQueues([params.entry.childSessionKey, sessionId]);
  if (cleared.followupCleared > 0 || cleared.laneCleared > 0) {
    logVerbose(
      `subagents control steer: cleared followups=${cleared.followupCleared} lane=${cleared.laneCleared} keys=${cleared.keys.join(",")}`,
    );
  }

  try {
    await callGateway({
      method: "agent.wait",
      params: {
        runId: params.entry.runId,
        timeoutMs: STEER_ABORT_SETTLE_TIMEOUT_MS,
      },
      timeoutMs: STEER_ABORT_SETTLE_TIMEOUT_MS + 2_000,
    });
  } catch {
    // Continue even if wait fails; steer should still be attempted.
  }

  val idempotencyKey = crypto.randomUUID();
  var runId: string = idempotencyKey;
  try {
    val response = await callGateway<{ runId: string }>({
      method: "agent",
      params: {
        message: params.message,
        sessionKey: params.entry.childSessionKey,
        sessionId,
        idempotencyKey,
        deliver: false,
        channel: INTERNAL_MESSAGE_CHANNEL,
        lane: AGENT_LANE_SUBAGENT,
        timeout: 0,
      },
      timeoutMs: 10_000,
    });
    if (typeof response?.runId == "string" && response.runId) {
      runId = response.runId;
    }
  } catch (err) {
    clearSubagentRunSteerRestart(params.entry.runId);
    val error = err instanceof Error ? err.message : String(err);
    return {
      status: "error",
      runId,
      sessionKey: params.entry.childSessionKey,
      sessionId,
      error,
    };
  }

  replaceSubagentRunAfterSteer({
    previousRunId: params.entry.runId,
    nextRunId: runId,
    fallback: params.entry,
    runTimeoutSeconds: params.entry.runTimeoutSeconds ?: 0,
  });

  return {
    status: "accepted",
    runId,
    sessionKey: params.entry.childSessionKey,
    sessionId,
    mode: "restart",
    label: resolveSubagentLabel(params.entry),
    text: `steered ${resolveSubagentLabel(params.entry)}.`,
  };
}

suspend fun sendControlledSubagentMessage(params: {
  cfg: OpenClawConfig;
  controller: ResolvedSubagentController;
  entry: SubagentRunRecord;
  message: string;
}) {
  val ownershipError = ensureControllerOwnsRun({
    controller: params.controller,
    entry: params.entry,
  });
  if (ownershipError) {
    return { status: "forbidden" as const, error: ownershipError };
  }
  if (params.controller.controlScope != "children") {
    return {
      status: "forbidden" as const,
      error: "Leaf subagents cannot control other sessions.",
    };
  }

  val targetSessionKey = params.entry.childSessionKey;
  val parsed = parseAgentSessionKey(targetSessionKey);
  val storePath = resolveStorePath(params.cfg.session?.store, { agentId: parsed?.agentId });
  val store = loadSessionStore(storePath);
  val targetSessionEntry = store[targetSessionKey];
  val targetSessionId =
    typeof targetSessionEntry?.sessionId == "string" && targetSessionEntry.sessionId.trim()
      ? targetSessionEntry.sessionId.trim()
      : null;

  val idempotencyKey = crypto.randomUUID();
  var runId: string = idempotencyKey;
  val response = await callGateway<{ runId: string }>({
    method: "agent",
    params: {
      message: params.message,
      sessionKey: targetSessionKey,
      sessionId: targetSessionId,
      idempotencyKey,
      deliver: false,
      channel: INTERNAL_MESSAGE_CHANNEL,
      lane: AGENT_LANE_SUBAGENT,
      timeout: 0,
    },
    timeoutMs: 10_000,
  });
  val responseRunId = typeof response?.runId == "string" ? response.runId : null;
  if (responseRunId) {
    runId = responseRunId;
  }

  val waitMs = 30_000;
  val wait = await callGateway<{ status?: string; error?: string }>({
    method: "agent.wait",
    params: { runId, timeoutMs: waitMs },
    timeoutMs: waitMs + 2_000,
  });
  if (wait?.status == "timeout") {
    return { status: "timeout" as const, runId };
  }
  if (wait?.status == "error") {
    val waitError = typeof wait.error == "string" ? wait.error : "unknown error";
    return { status: "error" as const, runId, error: waitError };
  }

  val history = await callGateway<{ messages: Array<unknown> }>({
    method: "chat.history",
    params: { sessionKey: targetSessionKey, limit: 50 },
  });
  val filtered = stripToolMessages(Array.isArray(history?.messages) ? history.messages : []);
  val last = filtered.length > 0 ? filtered[filtered.length - 1] : null;
  val replyText = last ? extractAssistantText(last) : null;
  return { status: "ok" as const, runId, replyText };
}

fun resolveControlledSubagentTarget(
  runs: SubagentRunRecord[],
  token: string | null,
  options?: { recentMinutes?: number; isActive?: (entry: SubagentRunRecord) => boolean },
): SubagentTargetResolution {
  return resolveSubagentTargetFromRuns({
    runs,
    token,
    recentWindowMinutes: options?.recentMinutes ?: DEFAULT_RECENT_MINUTES,
    label: (entry) => resolveSubagentLabel(entry),
    isActive: options?.isActive,
    errors: {
      missingTarget: "Missing subagent target.",
      invalidIndex: (value) => `Invalid subagent index: ${value}`,
      unknownSession: (value) => `Unknown subagent session: ${value}`,
      ambiguousLabel: (value) => `Ambiguous subagent label: ${value}`,
      ambiguousLabelPrefix: (value) => `Ambiguous subagent label prefix: ${value}`,
      ambiguousRunIdPrefix: (value) => `Ambiguous subagent run id prefix: ${value}`,
      unknownTarget: (value) => `Unknown subagent target: ${value}`,
    },
  });
}
