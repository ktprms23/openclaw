package agents.platform_support.sandbox

// Source: src/agents/sandbox/workspace.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import syncFs from "node:fs";
// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { openBoundaryFile } from "../../infra/boundary-file-read.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   DEFAULT_AGENTS_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_BOOTSTRAP_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_HEARTBEAT_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_IDENTITY_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_SOUL_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_TOOLS_FILENAME,
// TODO(openclaw-kotlin-port):   DEFAULT_USER_FILENAME,
// TODO(openclaw-kotlin-port):   ensureAgentWorkspace,
// TODO(openclaw-kotlin-port): } from "../workspace.js";

suspend fun ensureSandboxWorkspace(
  workspaceDir: String,
  seedFrom?: String,
  skipBootstrap?: Boolean,
) {
  await fs.mkdir(workspaceDir, { recursive: true })
  if (seedFrom) {
    val seed = resolveUserPath(seedFrom)
    val files = [
      DEFAULT_AGENTS_FILENAME,
      DEFAULT_SOUL_FILENAME,
      DEFAULT_TOOLS_FILENAME,
      DEFAULT_IDENTITY_FILENAME,
      DEFAULT_USER_FILENAME,
      DEFAULT_BOOTSTRAP_FILENAME,
      DEFAULT_HEARTBEAT_FILENAME,
    ]
    for (const name of files) {
      val src = path.join(seed, name)
      val dest = path.join(workspaceDir, name)
      try {
        await fs.access(dest)
      } catch {
        try {
          val opened = await openBoundaryFile({
            absolutePath: src,
            rootPath: seed,
            boundaryLabel: "sandbox seed workspace",
          })
          if (!opened.ok) {
            continue
          }
          try {
            val content = syncFs.readFileSync(opened.fd, "utf-8")
            await fs.writeFile(dest, content, { encoding: "utf-8", flag: "wx" })
          } finally {
            syncFs.closeSync(opened.fd)
          }
        } catch {
          // ignore missing seed file
        }
      }
    }
  }
  await ensureAgentWorkspace({
    dir: workspaceDir,
    ensureBootstrapFiles: !skipBootstrap,
  })
}
