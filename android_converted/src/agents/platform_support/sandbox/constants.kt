package agents.platform_support.sandbox

// Source: src/agents/sandbox/constants.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { CHANNEL_IDS } from "../../channels/ids.js";
// TODO(openclaw-kotlin-port): import { STATE_DIR } from "../../config/paths.js";

val DEFAULT_SANDBOX_WORKSPACE_ROOT = path.join(STATE_DIR, "sandboxes")

val DEFAULT_SANDBOX_IMAGE = "openclaw-sandbox:bookworm-slim"
val DEFAULT_SANDBOX_CONTAINER_PREFIX = "openclaw-sbx-"
val DEFAULT_SANDBOX_WORKDIR = "/workspace"
val DEFAULT_SANDBOX_IDLE_HOURS = 24
val DEFAULT_SANDBOX_MAX_AGE_DAYS = 7

val DEFAULT_TOOL_ALLOW = [
  "exec",
  "process",
  "read",
  "write",
  "edit",
  "apply_patch",
  "image",
  "sessions_list",
  "sessions_history",
  "sessions_send",
  "sessions_spawn",
  "sessions_yield",
  "subagents",
  "session_status",
] /* as const */

// Provider docking: keep sandbox policy aligned with provider tool names.
val DEFAULT_TOOL_DENY = [
  "browser",
  "canvas",
  "nodes",
  "cron",
  "gateway",
  ...CHANNEL_IDS,
] /* as const */

val DEFAULT_SANDBOX_BROWSER_IMAGE = "openclaw-sandbox-browser:bookworm-slim"
val DEFAULT_SANDBOX_COMMON_IMAGE = "openclaw-sandbox-common:bookworm-slim"
val SANDBOX_BROWSER_SECURITY_HASH_EPOCH = "2026-02-28-no-sandbox-env"

val DEFAULT_SANDBOX_BROWSER_PREFIX = "openclaw-sbx-browser-"
val DEFAULT_SANDBOX_BROWSER_NETWORK = "openclaw-sandbox-browser"
val DEFAULT_SANDBOX_BROWSER_CDP_PORT = 9222
val DEFAULT_SANDBOX_BROWSER_VNC_PORT = 5900
val DEFAULT_SANDBOX_BROWSER_NOVNC_PORT = 6080
val DEFAULT_SANDBOX_BROWSER_AUTOSTART_TIMEOUT_MS = 12_000

val SANDBOX_AGENT_WORKSPACE_MOUNT = "/agent"

val SANDBOX_STATE_DIR = path.join(STATE_DIR, "sandbox")
val SANDBOX_REGISTRY_PATH = path.join(SANDBOX_STATE_DIR, "containers.json")
val SANDBOX_BROWSER_REGISTRY_PATH = path.join(SANDBOX_STATE_DIR, "browsers.json")
