@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-search.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import type { RuntimeWebSearchMetadata } from "../../secrets/runtime-web-tools.types.js";
// TODO(port-deps): import {
// TODO(port-deps): __testing as runtimeTesting,
// TODO(port-deps): resolveWebSearchDefinition,
// TODO(port-deps): } from "../../web-search/runtime.js";
// TODO(port-deps): import type { AnyAgentTool } from "./common.js";
// TODO(port-deps): import { jsonResult } from "./common.js";
// TODO(port-deps): import { SEARCH_CACHE } from "./web-search-provider-common.js";

fun createWebSearchTool(options?: {
  config?: OpenClawConfig;
  sandboxed?: boolean;
  runtimeWebSearch?: RuntimeWebSearchMetadata;
}): AnyAgentTool | null {
  val resolved = resolveWebSearchDefinition({
    config: options?.config,
    sandboxed: options?.sandboxed,
    runtimeWebSearch: options?.runtimeWebSearch,
  });
  if (!resolved) {
    return null;
  }
  return {
    label: "Web Search",
    name: "web_search",
    description: resolved.definition.description,
    parameters: resolved.definition.parameters,
    execute: async (_toolCallId, args) => jsonResult(await resolved.definition.execute(args)),
  };
}

val __testing = {
  SEARCH_CACHE,
  ...runtimeTesting,
};
