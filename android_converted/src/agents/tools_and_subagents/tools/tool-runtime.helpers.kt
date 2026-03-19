@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/tool-runtime.helpers.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

object ToolRuntimeHelpersFacade {
    val exports = listOf(
        "getApiKeyForModel, requireApiKey <- ../model-auth.js",
        "runWithImageModelFallback <- ../model-fallback.js",
        "ensureOpenClawModelsJson <- ../models-config.js",
        "discoverAuthStorage, discoverModels <- ../pi-model-discovery.js",
        "createSandboxBridgeReadFile, resolveSandboxedBridgeMediaPath, type SandboxedBridgeMediaPathConfig, <- ../sandbox-media-paths.js",
        "SandboxFsBridge <- ../sandbox/fs-bridge.js",
        "ToolFsPolicy <- ../tool-fs-policy.js",
        "normalizeWorkspaceDir <- ../workspace-dir.js",
        "AnyAgentTool <- ./common.js",
    )
}
