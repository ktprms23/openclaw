package agents.platform_support

// Source: src/agents/models-config.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   getRuntimeConfigSourceSnapshot,
// TODO(openclaw-kotlin-port):   projectConfigOntoRuntimeSourceSnapshot,
// TODO(openclaw-kotlin-port):   type OpenClawConfig,
// TODO(openclaw-kotlin-port):   loadConfig,
// TODO(openclaw-kotlin-port): } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { createConfigRuntimeEnv } from "../config/env-vars.js";
// TODO(openclaw-kotlin-port): import { resolveOpenClawAgentDir } from "./agent-paths.js";
// TODO(openclaw-kotlin-port): import { planOpenClawModelsJson } from "./models-config.plan.js";

val MODELS_JSON_WRITE_LOCKS = new Map<String, Promise<Unit>>()

suspend fun readExistingModelsFile(pathname: String): Promise<{
  raw: String
  parsed: Any?
}> {
  try {
    val raw = await fs.readFile(pathname, "utf8")
    return {
      raw,
      parsed: JSON.parse(raw) as Any?,
    }
  } catch {
    return {
      raw: "",
      parsed: Nothing?,
    }
  }
}

suspend fun ensureModelsFileMode(pathname: String): Promise<Unit> {
  await fs.chmod(pathname, 0o600).catch(() => {
    // best-effort
  })
}

suspend fun writeModelsFileAtomic(targetPath: String, contents: String): Promise<Unit> {
  val tempPath = `${targetPath}.${process.pid}.${Date.now()}.tmp`
  await fs.writeFile(tempPath, contents, { mode: 0o600 })
  await fs.rename(tempPath, targetPath)
}

fun resolveModelsConfigInput(config?: OpenClawConfig): {
  config: OpenClawConfig
  sourceConfigForSecrets: OpenClawConfig
} {
  val runtimeSource = getRuntimeConfigSourceSnapshot()
  if (!config) {
    val loaded = loadConfig()
    return {
      config: runtimeSource ?? loaded,
      sourceConfigForSecrets: runtimeSource ?? loaded,
    }
  }
  if (!runtimeSource) {
    return {
      config,
      sourceConfigForSecrets: config,
    }
  }
  val projected = projectConfigOntoRuntimeSourceSnapshot(config)
  return {
    config: projected,
    // If projection is skipped (for example incompatible top-level shape),
    // keep managed secret persistence anchored to the active source snapshot.
    sourceConfigForSecrets: projected === config ? runtimeSource : projected,
  }
}

suspend fun withModelsJsonWriteLock<T>(targetPath: String, run: () => Promise<T>): Promise<T> {
  val prior = MODELS_JSON_WRITE_LOCKS.get(targetPath) ?? Promise.resolve()
  var release: () => Unit = {  -> {}
  val gate = new Promise<Unit>((resolve) => {
    release = resolve
  })
  val pending = prior.then(() => gate)
  MODELS_JSON_WRITE_LOCKS.set(targetPath, pending)
  try {
    await prior
    return await run()
  } finally {
    release()
    if (MODELS_JSON_WRITE_LOCKS.get(targetPath) === pending) {
      MODELS_JSON_WRITE_LOCKS.delete(targetPath)
    }
  }
}

suspend fun ensureOpenClawModelsJson(
  config?: OpenClawConfig,
  agentDirOverride?: String,
): Promise<{ agentDir: String wrote: Boolean }> {
  val resolved = resolveModelsConfigInput(config)
  val cfg = resolved.config
  val agentDir = agentDirOverride?.trim() ? agentDirOverride.trim() : resolveOpenClawAgentDir()
  val targetPath = path.join(agentDir, "models.json")

  return await withModelsJsonWriteLock(targetPath, async () => {
    // Ensure config env vars (e.g. AWS_PROFILE, AWS_ACCESS_KEY_ID) are
    // are available to provider discovery without mutating process.env.
    val env = createConfigRuntimeEnv(cfg)
    val existingModelsFile = await readExistingModelsFile(targetPath)
    val plan = await planOpenClawModelsJson({
      cfg,
      sourceConfigForSecrets: resolved.sourceConfigForSecrets,
      agentDir,
      env,
      existingRaw: existingModelsFile.raw,
      existingParsed: existingModelsFile.parsed,
    })

    if (plan.action === "skip") {
      return { agentDir, wrote: false }
    }

    if (plan.action === "noop") {
      await ensureModelsFileMode(targetPath)
      return { agentDir, wrote: false }
    }

    await fs.mkdir(agentDir, { recursive: true, mode: 0o700 })
    await writeModelsFileAtomic(targetPath, plan.contents)
    await ensureModelsFileMode(targetPath)
    return { agentDir, wrote: true }
  })
}
