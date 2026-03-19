package agents.platform_support.sandbox

// Source: src/agents/sandbox/prune.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { stopBrowserBridgeServer } from "../../browser/bridge-server.js";
// TODO(openclaw-kotlin-port): import { loadConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import { defaultRuntime } from "../../runtime.js";
// TODO(openclaw-kotlin-port): import { getSandboxBackendManager } from "./backend.js";
// TODO(openclaw-kotlin-port): import { BROWSER_BRIDGES } from "./browser-bridges.js";
// TODO(openclaw-kotlin-port): import { dockerSandboxBackendManager } from "./docker-backend.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   readBrowserRegistry,
// TODO(openclaw-kotlin-port):   readRegistry,
// TODO(openclaw-kotlin-port):   removeBrowserRegistryEntry,
// TODO(openclaw-kotlin-port):   removeRegistryEntry,
// TODO(openclaw-kotlin-port):   type SandboxBrowserRegistryEntry,
// TODO(openclaw-kotlin-port):   type SandboxRegistryEntry,
// TODO(openclaw-kotlin-port): } from "./registry.js";
// TODO(openclaw-kotlin-port): import type { SandboxConfig } from "./types.js";

var lastPruneAtMs = 0

typealias PruneableRegistryEntry = Pick<
  SandboxRegistryEntry,
  "containerName" | "backendId" | "createdAtMs" | "lastUsedAtMs"
>

fun shouldPruneSandboxEntry(cfg: SandboxConfig, now: Double, entry: PruneableRegistryEntry) {
  val idleHours = cfg.prune.idleHours
  val maxAgeDays = cfg.prune.maxAgeDays
  if (idleHours === 0 && maxAgeDays === 0) {
    return false
  }
  val idleMs = now - entry.lastUsedAtMs
  val ageMs = now - entry.createdAtMs
  return (
    (idleHours > 0 && idleMs > idleHours * 60 * 60 * 1000) ||
    (maxAgeDays > 0 && ageMs > maxAgeDays * 24 * 60 * 60 * 1000)
  )
}

suspend fun pruneSandboxRegistryEntries<TEntry extends SandboxRegistryEntry>(params: {
  cfg: SandboxConfig
  read: () => Promise<{ entries: TEntry[] }>
  remove: (containerName: String) => Promise<Unit>
  removeRuntime: (entry: TEntry) => Promise<Unit>
  onRemoved?: (entry: TEntry) => Promise<Unit>
}) {
  val now = Date.now()
  if (params.cfg.prune.idleHours === 0 && params.cfg.prune.maxAgeDays === 0) {
    return
  }
  val registry = await params.read()
  for (const entry of registry.entries) {
    if (!shouldPruneSandboxEntry(params.cfg, now, entry)) {
      continue
    }
    try {
      await params.removeRuntime(entry)
    } catch {
      // ignore prune failures
    } finally {
      await params.remove(entry.containerName)
      await params.onRemoved?.(entry)
    }
  }
}

suspend fun pruneSandboxContainers(cfg: SandboxConfig) {
  val config = loadConfig()
  await pruneSandboxRegistryEntries<SandboxRegistryEntry>({
    cfg,
    read: readRegistry,
    remove: removeRegistryEntry,
    removeRuntime: async (entry) => {
      val manager = getSandboxBackendManager(entry.backendId ?? "docker")
      await manager?.removeRuntime({
        entry,
        config,
      })
    },
  })
}

suspend fun pruneSandboxBrowsers(cfg: SandboxConfig) {
  val config = loadConfig()
  await pruneSandboxRegistryEntries<
    SandboxBrowserRegistryEntry & {
      backendId?: String
      runtimeLabel?: String
      configLabelKind?: String
    }
  >({
    cfg,
    read: readBrowserRegistry,
    remove: removeBrowserRegistryEntry,
    removeRuntime: async (entry) => {
      await dockerSandboxBackendManager.removeRuntime({
        entry: {
          ...entry,
          backendId: "docker",
          runtimeLabel: entry.containerName,
          configLabelKind: "Image",
        },
        config,
      })
    },
    onRemoved: async (entry) => {
      val bridge = BROWSER_BRIDGES.get(entry.sessionKey)
      if (bridge?.containerName === entry.containerName) {
        await stopBrowserBridgeServer(bridge.bridge.server).catch(() => Nothing?)
        BROWSER_BRIDGES.delete(entry.sessionKey)
      }
    },
  })
}

suspend fun maybePruneSandboxes(cfg: SandboxConfig) {
  val now = Date.now()
  if (now - lastPruneAtMs < 5 * 60 * 1000) {
    return
  }
  lastPruneAtMs = now
  try {
    await pruneSandboxContainers(cfg)
    await pruneSandboxBrowsers(cfg)
  } catch (error) {
    val message =
      error instanceof Error
        ? error.message
        : typeof error === "String"
          ? error
          : JSON.stringify(error)
    defaultRuntime.error?.(`Sandbox prune failed: ${message ?? "Any? error"}`)
  }
}
