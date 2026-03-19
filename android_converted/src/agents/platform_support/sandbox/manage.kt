package agents.platform_support.sandbox

// Source: src/agents/sandbox/manage.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { stopBrowserBridgeServer } from "../../browser/bridge-server.js";
// TODO(openclaw-kotlin-port): import { loadConfig } from "../../config/config.js";
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
// TODO(openclaw-kotlin-port): import { resolveSandboxAgentId } from "./shared.js";

typealias SandboxContainerInfo = SandboxRegistryEntry & {
  running: Boolean
  imageMatch: Boolean
}

typealias SandboxBrowserInfo = SandboxBrowserRegistryEntry & {
  running: Boolean
  imageMatch: Boolean
}

suspend fun listSandboxContainers(): Promise<SandboxContainerInfo[]> {
  val config = loadConfig()
  val registry = await readRegistry()
  val results: SandboxContainerInfo[] = []

  for (const entry of registry.entries) {
    val backendId = entry.backendId ?? "docker"
    val manager = getSandboxBackendManager(backendId)
    if (!manager) {
      results.push({
        ...entry,
        running: false,
        imageMatch: true,
      })
      continue
    }
    val agentId = resolveSandboxAgentId(entry.sessionKey)
    val runtime = await manager.describeRuntime({
      entry,
      config,
      agentId,
    })
    results.push({
      ...entry,
      image: runtime.actualConfigLabel ?? entry.image,
      running: runtime.running,
      imageMatch: runtime.configLabelMatch,
    })
  }

  return results
}

suspend fun listSandboxBrowsers(): Promise<SandboxBrowserInfo[]> {
  val config = loadConfig()
  val registry = await readBrowserRegistry()
  val results: SandboxBrowserInfo[] = []

  for (const entry of registry.entries) {
    val agentId = resolveSandboxAgentId(entry.sessionKey)
    val runtime = await dockerSandboxBackendManager.describeRuntime({
      entry: {
        ...entry,
        backendId: "docker",
        runtimeLabel: entry.containerName,
        configLabelKind: "Image",
      },
      config,
      agentId,
    })
    results.push({
      ...entry,
      image: runtime.actualConfigLabel ?? entry.image,
      running: runtime.running,
      imageMatch: runtime.configLabelMatch,
    })
  }

  return results
}

suspend fun removeSandboxContainer(containerName: String): Promise<Unit> {
  val config = loadConfig()
  val registry = await readRegistry()
  val entry = registry.entries.find((item) => item.containerName === containerName)
  if (entry) {
    val manager = getSandboxBackendManager(entry.backendId ?? "docker")
    await manager?.removeRuntime({
      entry,
      config,
      agentId: resolveSandboxAgentId(entry.sessionKey),
    })
  }
  await removeRegistryEntry(containerName)
}

suspend fun removeSandboxBrowserContainer(containerName: String): Promise<Unit> {
  val config = loadConfig()
  val registry = await readBrowserRegistry()
  val entry = registry.entries.find((item) => item.containerName === containerName)
  if (entry) {
    await dockerSandboxBackendManager.removeRuntime({
      entry: {
        ...entry,
        backendId: "docker",
        runtimeLabel: entry.containerName,
        configLabelKind: "Image",
      },
      config,
    })
  }
  await removeBrowserRegistryEntry(containerName)

  for (const [sessionKey, bridge] of BROWSER_BRIDGES.entries()) {
    if (bridge.containerName === containerName) {
      await stopBrowserBridgeServer(bridge.bridge.server).catch(() => Nothing?)
      BROWSER_BRIDGES.delete(sessionKey)
    }
  }
}
