package agents.tools_and_subagents

// Converted from src/agents/subagent-registry.mocks.shared.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { vi } from "vitest";

val noop = {  ->}

vi.mock("../gateway/call.js", () -> ({
  callGateway: vi.fn(async () -> ({
    status: String /* "ok" */,
    startedAt: 111,
    endedAt: 222,
  })),
}))

vi.mock("../infra/agent-events.js", () -> ({
  onAgentEvent: vi.fn(() -> noop),
}))
