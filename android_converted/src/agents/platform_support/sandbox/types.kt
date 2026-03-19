package agents.platform_support.sandbox

// Source: src/agents/sandbox/types.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { SandboxBackendHandle, SandboxBackendId } from "./backend.js";
// TODO(openclaw-kotlin-port): import type { SandboxFsBridge } from "./fs-bridge.js";
// TODO(openclaw-kotlin-port): import type { SandboxDockerConfig } from "./types.docker.js";

// export type { SandboxDockerConfig } from "./types.docker.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxToolPolicy.
typealias SandboxToolPolicy = Any?
/*
export type SandboxToolPolicy = {
  allow?: string[];
  deny?: string[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxToolPolicySource.
typealias SandboxToolPolicySource = Any?
/*
export type SandboxToolPolicySource = {
  source: "agent" | "global" | "default";
  /**
   * Config key path hint for humans.
   * (Arrays use `agents.list[].…` form.)
   */
  key: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxToolPolicyResolved.
typealias SandboxToolPolicyResolved = Any?
/*
export type SandboxToolPolicyResolved = {
  allow: string[];
  deny: string[];
  sources: {
    allow: SandboxToolPolicySource;
    deny: SandboxToolPolicySource;
  };
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SandboxWorkspaceAccess.
typealias SandboxWorkspaceAccess = "none" | "ro" | "rw"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBrowserConfig.
typealias SandboxBrowserConfig = Any?
/*
export type SandboxBrowserConfig = {
  enabled: boolean;
  image: string;
  containerPrefix: string;
  network: string;
  cdpPort: number;
  cdpSourceRange?: string;
  vncPort: number;
  noVncPort: number;
  headless: boolean;
  enableNoVnc: boolean;
  allowHostControl: boolean;
  autoStart: boolean;
  autoStartTimeoutMs: number;
  binds?: string[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxPruneConfig.
typealias SandboxPruneConfig = Any?
/*
export type SandboxPruneConfig = {
  idleHours: number;
  maxAgeDays: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxSshConfig.
typealias SandboxSshConfig = Any?
/*
export type SandboxSshConfig = {
  target?: string;
  command: string;
  workspaceRoot: string;
  strictHostKeyChecking: boolean;
  updateHostKeys: boolean;
  identityFile?: string;
  certificateFile?: string;
  knownHostsFile?: string;
  identityData?: string;
  certificateData?: string;
  knownHostsData?: string;
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for SandboxScope.
typealias SandboxScope = "session" | "agent" | "shared"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxConfig.
typealias SandboxConfig = Any?
/*
export type SandboxConfig = {
  mode: "off" | "non-main" | "all";
  backend: SandboxBackendId;
  scope: SandboxScope;
  workspaceAccess: SandboxWorkspaceAccess;
  workspaceRoot: string;
  docker: SandboxDockerConfig;
  ssh: SandboxSshConfig;
  browser: SandboxBrowserConfig;
  tools: SandboxToolPolicy;
  prune: SandboxPruneConfig;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBrowserContext.
typealias SandboxBrowserContext = Any?
/*
export type SandboxBrowserContext = {
  bridgeUrl: string;
  noVncUrl?: string;
  containerName: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxContext.
typealias SandboxContext = Any?
/*
export type SandboxContext = {
  enabled: boolean;
  backendId: SandboxBackendId;
  sessionKey: string;
  workspaceDir: string;
  agentWorkspaceDir: string;
  workspaceAccess: SandboxWorkspaceAccess;
  runtimeId: string;
  runtimeLabel: string;
  containerName: string;
  containerWorkdir: string;
  docker: SandboxDockerConfig;
  tools: SandboxToolPolicy;
  browserAllowHostControl: boolean;
  browser?: SandboxBrowserContext;
  fsBridge?: SandboxFsBridge;
  backend?: SandboxBackendHandle;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxWorkspaceInfo.
typealias SandboxWorkspaceInfo = Any?
/*
export type SandboxWorkspaceInfo = {
  workspaceDir: string;
  containerWorkdir: string;
};
*/
