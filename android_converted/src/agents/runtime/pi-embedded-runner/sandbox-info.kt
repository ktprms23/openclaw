@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/sandbox-info.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun buildEmbeddedSandboxInfo(/* parameters preserved from TypeScript source */): Any /* EmbeddedSandboxInfo | undefined */ {
    TODO("Port runtime function buildEmbeddedSandboxInfo from src/agents/pi-embedded-runner/sandbox-info.ts")
}

/*
Original imports retained for mapping context:
import type { ExecElevatedDefaults } from "../bash-tools.js";
import type { resolveSandboxContext } from "../sandbox.js";
import type { EmbeddedSandboxInfo } from "./types.js";
*/

/*
Original TypeScript reference:
import type { ExecElevatedDefaults } from "../bash-tools.js";
import type { resolveSandboxContext } from "../sandbox.js";
import type { EmbeddedSandboxInfo } from "./types.js";

export function buildEmbeddedSandboxInfo(
  sandbox?: Awaited<ReturnType<typeof resolveSandboxContext>>,
  execElevated?: ExecElevatedDefaults,
): EmbeddedSandboxInfo | undefined {
  if (!sandbox?.enabled) {
    return undefined;
  }
  const elevatedAllowed = Boolean(execElevated?.enabled && execElevated.allowed);
  return {
    enabled: true,
    workspaceDir: sandbox.workspaceDir,
    containerWorkspaceDir: sandbox.containerWorkdir,
    workspaceAccess: sandbox.workspaceAccess,
    agentWorkspaceMount: sandbox.workspaceAccess === "ro" ? "/agent" : undefined,
    browserBridgeUrl: sandbox.browser?.bridgeUrl,
    browserNoVncUrl: sandbox.browser?.noVncUrl,
    hostBrowserAllowed: sandbox.browserAllowHostControl,
    ...(elevatedAllowed
      ? {
          elevated: {
            allowed: true,
            defaultLevel: execElevated?.defaultLevel ?? "off",
          },
        }
      : {}),
  };
}

*/
