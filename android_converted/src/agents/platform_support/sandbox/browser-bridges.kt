package agents.platform_support.sandbox

// Source: src/agents/sandbox/browser-bridges.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { BrowserBridge } from "../../browser/bridge-server.js";

val BROWSER_BRIDGES = new Map<
  String,
  {
    bridge: BrowserBridge
    containerName: String
    authToken?: String
    authPassword?: String
  }
>()
