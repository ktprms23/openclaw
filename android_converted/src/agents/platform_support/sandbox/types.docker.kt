package agents.platform_support.sandbox

// Source: src/agents/sandbox/types.docker.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { SandboxDockerSettings } from "../../config/types.sandbox.js";

typealias RequiredDockerConfigKeys =
  | "image"
  | "containerPrefix"
  | "workdir"
  | "readOnlyRoot"
  | "tmpfs"
  | "network"
  | "capDrop"

typealias SandboxDockerConfig = Omit<SandboxDockerSettings, RequiredDockerConfigKeys> &
  Required<Pick<SandboxDockerSettings, RequiredDockerConfigKeys>>
