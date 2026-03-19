package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-search.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import type { RuntimeWebSearchMetadata } from "../../secrets/runtime-web-tools.types.js";
// TODO: TypeScript import retained for manual wiring: import {
  __testing as /* TODO */ runtimeTesting,
  resolveWebSearchDefinition,
} from "../../web-search/runtime.js"
// TODO: TypeScript import retained for manual wiring: import type { AnyAgentTool } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { jsonResult } from "./common.js";
// TODO: TypeScript import retained for manual wiring: import { SEARCH_CACHE } from "./web-search-provider-common.js";

fun createWebSearchTool(options?: {
  config?: OpenClawConfig
  sandboxed?: Boolean
  runtimeWebSearch?: RuntimeWebSearchMetadata
}): AnyAgentTool? {
  val resolved = resolveWebSearchDefinition({
    config: options?.config,
    sandboxed: options?.sandboxed,
    runtimeWebSearch: options?.runtimeWebSearch,
  })
  if (!resolved) {
    return null
  }
  return {
    label: String /* "Web Search" */,
    name: String /* "web_search" */,
    description: resolved.definition.description,
    parameters: resolved.definition.parameters,
    execute: async (_toolCallId, args) -> jsonResult(await resolved.definition.execute(args)),
  }
}

val __testing = {
  SEARCH_CACHE,
  ...runtimeTesting,
}
