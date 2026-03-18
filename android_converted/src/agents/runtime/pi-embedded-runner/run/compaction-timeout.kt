@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/run/compaction-timeout.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

data class CompactionTimeoutSignal(
    val isTimeout: Boolean = TODO("Port default"),
    val isCompactionPendingOrRetrying: Boolean = TODO("Port default"),
    val isCompactionInFlight: Boolean = TODO("Port default")
)

data class SnapshotSelectionParams(
    val timedOutDuringCompaction: Boolean = TODO("Port default"),
    val preCompactionSnapshot: Any /* AgentMessage[] | null */ = TODO("Port default"),
    val preCompactionSessionId: String = TODO("Port default"),
    val currentSnapshot: List<AgentMessage> = TODO("Port default"),
    val currentSessionId: String = TODO("Port default")
)

data class SnapshotSelection(
    val messagesSnapshot: List<AgentMessage> = TODO("Port default"),
    val sessionIdUsed: String = TODO("Port default"),
    val source: Any /* "pre-compaction" | "current" */ = TODO("Port default")
)

fun shouldFlagCompactionTimeout(/* parameters preserved from TypeScript source */): Boolean {
    TODO("Port runtime function shouldFlagCompactionTimeout from src/agents/pi-embedded-runner/run/compaction-timeout.ts")
}

fun resolveRunTimeoutDuringCompaction(/* parameters preserved from TypeScript source */): Any /* "extend" | "abort" */ {
    TODO("Port runtime function resolveRunTimeoutDuringCompaction from src/agents/pi-embedded-runner/run/compaction-timeout.ts")
}

fun resolveRunTimeoutWithCompactionGraceMs(/* parameters preserved from TypeScript source */): Double {
    TODO("Port runtime function resolveRunTimeoutWithCompactionGraceMs from src/agents/pi-embedded-runner/run/compaction-timeout.ts")
}

fun selectCompactionTimeoutSnapshot(/* parameters preserved from TypeScript source */): SnapshotSelection {
    TODO("Port runtime function selectCompactionTimeoutSnapshot from src/agents/pi-embedded-runner/run/compaction-timeout.ts")
}

/*
Original imports retained for mapping context:
import type { AgentMessage } from "@mariozechner/pi-agent-core";
*/

/*
Original TypeScript reference:
import type { AgentMessage } from "@mariozechner/pi-agent-core";

export type CompactionTimeoutSignal = {
  isTimeout: boolean;
  isCompactionPendingOrRetrying: boolean;
  isCompactionInFlight: boolean;
};

export function shouldFlagCompactionTimeout(signal: CompactionTimeoutSignal): boolean {
  if (!signal.isTimeout) {
    return false;
  }
  return signal.isCompactionPendingOrRetrying || signal.isCompactionInFlight;
}

export function resolveRunTimeoutDuringCompaction(params: {
  isCompactionPendingOrRetrying: boolean;
  isCompactionInFlight: boolean;
  graceAlreadyUsed: boolean;
}): "extend" | "abort" {
  if (!params.isCompactionPendingOrRetrying && !params.isCompactionInFlight) {
    return "abort";
  }
  return params.graceAlreadyUsed ? "abort" : "extend";
}

export function resolveRunTimeoutWithCompactionGraceMs(params: {
  runTimeoutMs: number;
  compactionTimeoutMs: number;
}): number {
  return params.runTimeoutMs + params.compactionTimeoutMs;
}

export type SnapshotSelectionParams = {
  timedOutDuringCompaction: boolean;
  preCompactionSnapshot: AgentMessage[] | null;
  preCompactionSessionId: string;
  currentSnapshot: AgentMessage[];
  currentSessionId: string;
};

export type SnapshotSelection = {
  messagesSnapshot: AgentMessage[];
  sessionIdUsed: string;
  source: "pre-compaction" | "current";
};

export function selectCompactionTimeoutSnapshot(
  params: SnapshotSelectionParams,
): SnapshotSelection {
  if (!params.timedOutDuringCompaction) {
    return {
      messagesSnapshot: params.currentSnapshot,
      sessionIdUsed: params.currentSessionId,
      source: "current",
    };
  }

  if (params.preCompactionSnapshot) {
    return {
      messagesSnapshot: params.preCompactionSnapshot,
      sessionIdUsed: params.preCompactionSessionId,
      source: "pre-compaction",
    };
  }

  return {
    messagesSnapshot: params.currentSnapshot,
    sessionIdUsed: params.currentSessionId,
    source: "current",
  };
}

*/
