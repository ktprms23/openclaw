package agents.platform_support.sandbox

// Source: src/agents/sandbox/backend.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import type { SandboxFsBridge } from "./fs-bridge.js";
// TODO(openclaw-kotlin-port): import type { SandboxRegistryEntry } from "./registry.js";
// TODO(openclaw-kotlin-port): import type { SandboxConfig, SandboxContext } from "./types.js";

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SandboxBackendId.
typealias SandboxBackendId = String

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendExecSpec.
typealias SandboxBackendExecSpec = Any?
/*
export type SandboxBackendExecSpec = {
  argv: string[];
  env: NodeJS.ProcessEnv;
  stdinMode: "pipe-open" | "pipe-closed";
  finalizeToken?: unknown;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendCommandParams.
typealias SandboxBackendCommandParams = Any?
/*
export type SandboxBackendCommandParams = {
  script: string;
  args?: string[];
  stdin?: Buffer | string;
  allowFailure?: boolean;
  signal?: AbortSignal;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendCommandResult.
typealias SandboxBackendCommandResult = Any?
/*
export type SandboxBackendCommandResult = {
  stdout: Buffer;
  stderr: Buffer;
  code: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendHandle.
typealias SandboxBackendHandle = Any?
/*
export type SandboxBackendHandle = {
  id: SandboxBackendId;
  runtimeId: string;
  runtimeLabel: string;
  workdir: string;
  env?: Record<string, string>;
  configLabel?: string;
  configLabelKind?: string;
  capabilities?: {
    browser?: boolean;
  };
  buildExecSpec(params: {
    command: string;
    workdir?: string;
    env: Record<string, string>;
    usePty: boolean;
  }): Promise<SandboxBackendExecSpec>;
  finalizeExec?: (params: {
    status: "completed" | "failed";
    exitCode: number | null;
    timedOut: boolean;
    token?: unknown;
  }) => Promise<void>;
  runShellCommand(params: SandboxBackendCommandParams): Promise<SandboxBackendCommandResult>;
  createFsBridge?: (params: { sandbox: SandboxContext }) => SandboxFsBridge;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendRuntimeInfo.
typealias SandboxBackendRuntimeInfo = Any?
/*
export type SandboxBackendRuntimeInfo = {
  running: boolean;
  actualConfigLabel?: string;
  configLabelMatch: boolean;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBackendManager.
typealias SandboxBackendManager = Any?
/*
export type SandboxBackendManager = {
  describeRuntime(params: {
    entry: SandboxRegistryEntry;
    config: OpenClawConfig;
    agentId?: string;
  }): Promise<SandboxBackendRuntimeInfo>;
  removeRuntime(params: {
    entry: SandboxRegistryEntry;
    config: OpenClawConfig;
    agentId?: string;
  }): Promise<void>;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for CreateSandboxBackendParams.
typealias CreateSandboxBackendParams = Any?
/*
export type CreateSandboxBackendParams = {
  sessionKey: string;
  scopeKey: string;
  workspaceDir: string;
  agentWorkspaceDir: string;
  cfg: SandboxConfig;
};
*/

typealias SandboxBackendFactory = (
  params: CreateSandboxBackendParams,
) => Promise<SandboxBackendHandle>

typealias SandboxBackendRegistration =
  | SandboxBackendFactory
  | {
      factory: SandboxBackendFactory
      manager?: SandboxBackendManager
    }

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RegisteredSandboxBackend.
typealias RegisteredSandboxBackend = Any?
/*
type RegisteredSandboxBackend = {
  factory: SandboxBackendFactory;
  manager?: SandboxBackendManager;
};
*/

val SANDBOX_BACKEND_FACTORIES = mutableMapOf<SandboxBackendId, RegisteredSandboxBackend>()

fun normalizeSandboxBackendId(id: String): SandboxBackendId {
  val normalized = id.trim().toLowerCase()
  if (!normalized) {
    throw error("Sandbox backend id must not be empty.")
  }
  return normalized
}

fun registerSandboxBackend(
  id: String,
  registration: SandboxBackendRegistration,
): () => Unit {
  val normalizedId = normalizeSandboxBackendId(id)
  val resolved = typeof registration === "function" ? { factory: registration } : registration
  val previous = SANDBOX_BACKEND_FACTORIES.get(normalizedId)
  SANDBOX_BACKEND_FACTORIES.set(normalizedId, resolved)
  return () => {
    if (previous) {
      SANDBOX_BACKEND_FACTORIES.set(normalizedId, previous)
      return
    }
    SANDBOX_BACKEND_FACTORIES.delete(normalizedId)
  }
}

fun getSandboxBackendFactory(id: String): SandboxBackendFactory | Nothing? {
  return SANDBOX_BACKEND_FACTORIES.get(normalizeSandboxBackendId(id))?.factory ?? Nothing?
}

fun getSandboxBackendManager(id: String): SandboxBackendManager | Nothing? {
  return SANDBOX_BACKEND_FACTORIES.get(normalizeSandboxBackendId(id))?.manager ?? Nothing?
}

fun requireSandboxBackendFactory(id: String): SandboxBackendFactory {
  val factory = getSandboxBackendFactory(id)
  if (factory) {
    return factory
  }
  throw error(
    [
      `Sandbox backend "${id}" is not registered.`,
      "Load the plugin that provides it, or set agents.defaults.sandbox.backend=docker.",
    ].join("\n"),
  )
}

// TODO(openclaw-kotlin-port): import { createDockerSandboxBackend, dockerSandboxBackendManager } from "./docker-backend.js";
// TODO(openclaw-kotlin-port): import { createSshSandboxBackend, sshSandboxBackendManager } from "./ssh-backend.js";

registerSandboxBackend("docker", {
  factory: createDockerSandboxBackend,
  manager: dockerSandboxBackendManager,
})

registerSandboxBackend("ssh", {
  factory: createSshSandboxBackend,
  manager: sshSandboxBackendManager,
})
