package agents.platform_support

// Source: src/agents/sandbox-media-paths.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { assertSandboxPath } from "./sandbox-paths.js";
// TODO(openclaw-kotlin-port): import type { SandboxFsBridge } from "./sandbox/fs-bridge.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxedBridgeMediaPathConfig.
typealias SandboxedBridgeMediaPathConfig = Any?
/*
export type SandboxedBridgeMediaPathConfig = {
  root: string;
  bridge: SandboxFsBridge;
  workspaceOnly?: boolean;
};
*/

fun createSandboxBridgeReadFile(params: {
  sandbox: Pick<SandboxedBridgeMediaPathConfig, "root" | "bridge">
}): (filePath: String) => Promise<Buffer> {
  return async (filePath: String) =>
    await params.sandbox.bridge.readFile({
      filePath,
      cwd: params.sandbox.root,
    })
}

suspend fun resolveSandboxedBridgeMediaPath(params: {
  sandbox: SandboxedBridgeMediaPathConfig
  mediaPath: String
  inboundFallbackDir?: String
}): Promise<{ resolved: String rewrittenFrom?: String }> {
  val normalizeFileUrl = { rawPath: String ->
    rawPath.startsWith("file://") ? rawPath.slice("file://".length) : rawPath
  val filePath = normalizeFileUrl(params.mediaPath)
  val enforceWorkspaceBoundary = suspend { hostPath: String -> {
    if (!params.sandbox.workspaceOnly) {
      return
    }
    await assertSandboxPath({
      filePath: hostPath,
      cwd: params.sandbox.root,
      root: params.sandbox.root,
    })
  }

  val resolveDirect = {  ->
    params.sandbox.bridge.resolvePath({
      filePath,
      cwd: params.sandbox.root,
    })
  try {
    val resolved = resolveDirect()
    if (resolved.hostPath) {
      await enforceWorkspaceBoundary(resolved.hostPath)
    }
    return { resolved: resolved.hostPath ?? resolved.containerPath }
  } catch (err) {
    val fallbackDir = params.inboundFallbackDir?.trim()
    if (!fallbackDir) {
      throw err
    }
    val fallbackPath = path.join(fallbackDir, path.basename(filePath))
    try {
      val stat = await params.sandbox.bridge.stat({
        filePath: fallbackPath,
        cwd: params.sandbox.root,
      })
      if (!stat) {
        throw err
      }
    } catch {
      throw err
    }
    val resolvedFallback = params.sandbox.bridge.resolvePath({
      filePath: fallbackPath,
      cwd: params.sandbox.root,
    })
    if (resolvedFallback.hostPath) {
      await enforceWorkspaceBoundary(resolvedFallback.hostPath)
    }
    return {
      resolved: resolvedFallback.hostPath ?? resolvedFallback.containerPath,
      rewrittenFrom: filePath,
    }
  }
}
