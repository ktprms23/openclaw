@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/tool-name-allowlist.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun collectAllowedToolNames(/* parameters preserved from TypeScript source */): Set<string> {
    TODO("Port runtime function collectAllowedToolNames from src/agents/pi-embedded-runner/tool-name-allowlist.ts")
}

/*
Original imports retained for mapping context:
import type { AgentTool } from "@mariozechner/pi-agent-core";
import type { ClientToolDefinition } from "./run/params.js";
*/

/*
Original TypeScript reference:
import type { AgentTool } from "@mariozechner/pi-agent-core";
import type { ClientToolDefinition } from "./run/params.js";

function addName(names: Set<string>, value: unknown): void {
  if (typeof value !== "string") {
    return;
  }
  const trimmed = value.trim();
  if (trimmed) {
    names.add(trimmed);
  }
}

export function collectAllowedToolNames(params: {
  tools: AgentTool[];
  clientTools?: ClientToolDefinition[];
}): Set<string> {
  const names = new Set<string>();
  for (const tool of params.tools) {
    addName(names, tool.name);
  }
  for (const tool of params.clientTools ?? []) {
    addName(names, tool.function?.name);
  }
  return names;
}

*/
