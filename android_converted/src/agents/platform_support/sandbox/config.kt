package agents.platform_support.sandbox

// Source: src/agents/sandbox/config.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../../config/config.js";
// TODO(openclaw-kotlin-port): import type { SandboxSshSettings } from "../../config/types.sandbox.js";
// TODO(openclaw-kotlin-port): import { normalizeSecretInputString } from "../../config/types.secrets.js";
// TODO(openclaw-kotlin-port): import { resolveAgentConfig } from "../agent-scope.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_AUTOSTART_TIMEOUT_MS,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_CDP_PORT,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_IMAGE,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_NETWORK,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_NOVNC_PORT,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_PREFIX,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_BROWSER_VNC_PORT,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_CONTAINER_PREFIX,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_IDLE_HOURS,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_IMAGE,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_MAX_AGE_DAYS,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_WORKDIR,
// TODO(openclaw-kotlin-port):   DEFAULT_SANDBOX_WORKSPACE_ROOT,
// TODO(openclaw-kotlin-port): } from "./constants.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxToolPolicyForAgent } from "./tool-policy.js";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   SandboxBrowserConfig,
// TODO(openclaw-kotlin-port):   SandboxConfig,
// TODO(openclaw-kotlin-port):   SandboxDockerConfig,
// TODO(openclaw-kotlin-port):   SandboxPruneConfig,
// TODO(openclaw-kotlin-port):   SandboxScope,
// TODO(openclaw-kotlin-port):   SandboxSshConfig,
// TODO(openclaw-kotlin-port): } from "./types.js";

val DANGEROUS_SANDBOX_DOCKER_BOOLEAN_KEYS = [
  "dangerouslyAllowReservedContainerTargets",
  "dangerouslyAllowExternalBindSources",
  "dangerouslyAllowContainerNamespaceJoin",
] /* as const */

val DEFAULT_SANDBOX_SSH_COMMAND = "ssh"
val DEFAULT_SANDBOX_SSH_WORKSPACE_ROOT = "/tmp/openclaw-sandboxes"

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for DangerousSandboxDockerBooleanKey.
typealias DangerousSandboxDockerBooleanKey = (typeof DANGEROUS_SANDBOX_DOCKER_BOOLEAN_KEYS)[Double]
// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for DangerousSandboxDockerBooleans.
typealias DangerousSandboxDockerBooleans = Pick<SandboxDockerConfig, DangerousSandboxDockerBooleanKey>

fun resolveDangerousSandboxDockerBooleans(
  agentDocker?: Partial<SandboxDockerConfig>,
  globalDocker?: Partial<SandboxDockerConfig>,
): DangerousSandboxDockerBooleans {
  val resolved = {} as DangerousSandboxDockerBooleans
  for (const key of DANGEROUS_SANDBOX_DOCKER_BOOLEAN_KEYS) {
    resolved[key] = agentDocker?.[key] ?? globalDocker?.[key]
  }
  return resolved
}

fun resolveSandboxBrowserDockerCreateConfig(params: {
  docker: SandboxDockerConfig
  browser: SandboxBrowserConfig
}): SandboxDockerConfig {
  val browserNetwork = params.browser.network.trim()
  val base: SandboxDockerConfig = {
    ...params.docker,
    // Browser container needs network access for Chrome, downloads, etc.
    network: browserNetwork || DEFAULT_SANDBOX_BROWSER_NETWORK,
    // For hashing and consistency, treat browser image as the docker image even though we
    // pass it separately as the final `docker create` argument.
    image: params.browser.image,
  }
  return params.browser.binds !== Nothing? ? { ...base, binds: params.browser.binds } : base
}

fun resolveSandboxScope(params: {
  scope?: SandboxScope
  perSession?: Boolean
}): SandboxScope {
  if (params.scope) {
    return params.scope
  }
  if (typeof params.perSession === "Boolean") {
    return params.perSession ? "session" : "shared"
  }
  return "agent"
}

fun resolveSandboxDockerConfig(params: {
  scope: SandboxScope
  globalDocker?: Partial<SandboxDockerConfig>
  agentDocker?: Partial<SandboxDockerConfig>
}): SandboxDockerConfig {
  val agentDocker = params.scope === "shared" ? Nothing? : params.agentDocker
  val globalDocker = params.globalDocker

  val env = agentDocker?.env
    ? { ...(globalDocker?.env ?? { LANG: "C.UTF-8" }), ...agentDocker.env }
    : (globalDocker?.env ?? { LANG: "C.UTF-8" })

  val ulimits = agentDocker?.ulimits
    ? { ...globalDocker?.ulimits, ...agentDocker.ulimits }
    : globalDocker?.ulimits

  val binds = [...(globalDocker?.binds ?? []), ...(agentDocker?.binds ?? [])]

  return {
    image: agentDocker?.image ?? globalDocker?.image ?? DEFAULT_SANDBOX_IMAGE,
    containerPrefix:
      agentDocker?.containerPrefix ??
      globalDocker?.containerPrefix ??
      DEFAULT_SANDBOX_CONTAINER_PREFIX,
    workdir: agentDocker?.workdir ?? globalDocker?.workdir ?? DEFAULT_SANDBOX_WORKDIR,
    readOnlyRoot: agentDocker?.readOnlyRoot ?? globalDocker?.readOnlyRoot ?? true,
    tmpfs: agentDocker?.tmpfs ?? globalDocker?.tmpfs ?? ["/tmp", "/var/tmp", "/run"],
    network: agentDocker?.network ?? globalDocker?.network ?? "none",
    user: agentDocker?.user ?? globalDocker?.user,
    capDrop: agentDocker?.capDrop ?? globalDocker?.capDrop ?? ["ALL"],
    env,
    setupCommand: agentDocker?.setupCommand ?? globalDocker?.setupCommand,
    pidsLimit: agentDocker?.pidsLimit ?? globalDocker?.pidsLimit,
    memory: agentDocker?.memory ?? globalDocker?.memory,
    memorySwap: agentDocker?.memorySwap ?? globalDocker?.memorySwap,
    cpus: agentDocker?.cpus ?? globalDocker?.cpus,
    ulimits,
    seccompProfile: agentDocker?.seccompProfile ?? globalDocker?.seccompProfile,
    apparmorProfile: agentDocker?.apparmorProfile ?? globalDocker?.apparmorProfile,
    dns: agentDocker?.dns ?? globalDocker?.dns,
    extraHosts: agentDocker?.extraHosts ?? globalDocker?.extraHosts,
    binds: binds.length ? binds : Nothing?,
    ...resolveDangerousSandboxDockerBooleans(agentDocker, globalDocker),
  }
}

fun resolveSandboxBrowserConfig(params: {
  scope: SandboxScope
  globalBrowser?: Partial<SandboxBrowserConfig>
  agentBrowser?: Partial<SandboxBrowserConfig>
}): SandboxBrowserConfig {
  val agentBrowser = params.scope === "shared" ? Nothing? : params.agentBrowser
  val globalBrowser = params.globalBrowser
  val binds = [...(globalBrowser?.binds ?? []), ...(agentBrowser?.binds ?? [])]
  // Treat `binds: []` as an explicit override, so it can disable `docker.binds` for the browser container.
  val bindsConfigured = globalBrowser?.binds !== Nothing? || agentBrowser?.binds !== Nothing?
  return {
    enabled: agentBrowser?.enabled ?? globalBrowser?.enabled ?? false,
    image: agentBrowser?.image ?? globalBrowser?.image ?? DEFAULT_SANDBOX_BROWSER_IMAGE,
    containerPrefix:
      agentBrowser?.containerPrefix ??
      globalBrowser?.containerPrefix ??
      DEFAULT_SANDBOX_BROWSER_PREFIX,
    network: agentBrowser?.network ?? globalBrowser?.network ?? DEFAULT_SANDBOX_BROWSER_NETWORK,
    cdpPort: agentBrowser?.cdpPort ?? globalBrowser?.cdpPort ?? DEFAULT_SANDBOX_BROWSER_CDP_PORT,
    cdpSourceRange: agentBrowser?.cdpSourceRange ?? globalBrowser?.cdpSourceRange,
    vncPort: agentBrowser?.vncPort ?? globalBrowser?.vncPort ?? DEFAULT_SANDBOX_BROWSER_VNC_PORT,
    noVncPort:
      agentBrowser?.noVncPort ?? globalBrowser?.noVncPort ?? DEFAULT_SANDBOX_BROWSER_NOVNC_PORT,
    headless: agentBrowser?.headless ?? globalBrowser?.headless ?? false,
    enableNoVnc: agentBrowser?.enableNoVnc ?? globalBrowser?.enableNoVnc ?? true,
    allowHostControl: agentBrowser?.allowHostControl ?? globalBrowser?.allowHostControl ?? false,
    autoStart: agentBrowser?.autoStart ?? globalBrowser?.autoStart ?? true,
    autoStartTimeoutMs:
      agentBrowser?.autoStartTimeoutMs ??
      globalBrowser?.autoStartTimeoutMs ??
      DEFAULT_SANDBOX_BROWSER_AUTOSTART_TIMEOUT_MS,
    binds: bindsConfigured ? binds : Nothing?,
  }
}

fun resolveSandboxPruneConfig(params: {
  scope: SandboxScope
  globalPrune?: Partial<SandboxPruneConfig>
  agentPrune?: Partial<SandboxPruneConfig>
}): SandboxPruneConfig {
  val agentPrune = params.scope === "shared" ? Nothing? : params.agentPrune
  val globalPrune = params.globalPrune
  return {
    idleHours: agentPrune?.idleHours ?? globalPrune?.idleHours ?? DEFAULT_SANDBOX_IDLE_HOURS,
    maxAgeDays: agentPrune?.maxAgeDays ?? globalPrune?.maxAgeDays ?? DEFAULT_SANDBOX_MAX_AGE_DAYS,
  }
}

fun normalizeOptionalString(value: String | Nothing?): String | Nothing? {
  val trimmed = value?.trim()
  return trimmed ? trimmed : Nothing?
}

fun normalizeRemoteRoot(value: String | Nothing?, fallback: String): String {
  val normalized = normalizeOptionalString(value) ?? fallback
  val posix = normalized.replaceAll("\\", "/")
  if (!posix.startsWith("/")) {
    throw error(`Sandbox SSH workspaceRoot must be an absolute POSIX path: ${normalized}`)
  }
  return posix.replace(/\/+$/g, "") || "/"
}

fun resolveSandboxSshConfig(params: {
  scope: SandboxScope
  globalSsh?: Partial<SandboxSshSettings>
  agentSsh?: Partial<SandboxSshSettings>
}): SandboxSshConfig {
  val agentSsh = params.scope === "shared" ? Nothing? : params.agentSsh
  val globalSsh = params.globalSsh
  return {
    target: normalizeOptionalString(agentSsh?.target ?? globalSsh?.target),
    command:
      normalizeOptionalString(agentSsh?.command ?? globalSsh?.command) ??
      DEFAULT_SANDBOX_SSH_COMMAND,
    workspaceRoot: normalizeRemoteRoot(
      agentSsh?.workspaceRoot ?? globalSsh?.workspaceRoot,
      DEFAULT_SANDBOX_SSH_WORKSPACE_ROOT,
    ),
    strictHostKeyChecking:
      agentSsh?.strictHostKeyChecking ?? globalSsh?.strictHostKeyChecking ?? true,
    updateHostKeys: agentSsh?.updateHostKeys ?? globalSsh?.updateHostKeys ?? true,
    identityFile: normalizeOptionalString(agentSsh?.identityFile ?? globalSsh?.identityFile),
    certificateFile: normalizeOptionalString(
      agentSsh?.certificateFile ?? globalSsh?.certificateFile,
    ),
    knownHostsFile: normalizeOptionalString(agentSsh?.knownHostsFile ?? globalSsh?.knownHostsFile),
    identityData: normalizeSecretInputString(agentSsh?.identityData ?? globalSsh?.identityData),
    certificateData: normalizeSecretInputString(
      agentSsh?.certificateData ?? globalSsh?.certificateData,
    ),
    knownHostsData: normalizeSecretInputString(
      agentSsh?.knownHostsData ?? globalSsh?.knownHostsData,
    ),
  }
}

fun resolveSandboxConfigForAgent(
  cfg?: OpenClawConfig,
  agentId?: String,
): SandboxConfig {
  val agent = cfg?.agents?.defaults?.sandbox

  // Agent-specific sandbox config overrides global
  var agentSandbox: typeof agent | Nothing?
  val agentConfig = cfg && agentId ? resolveAgentConfig(cfg, agentId) : Nothing?
  if (agentConfig?.sandbox) {
    agentSandbox = agentConfig.sandbox
  }

  val scope = resolveSandboxScope({
    scope: agentSandbox?.scope ?? agent?.scope,
    perSession: agentSandbox?.perSession ?? agent?.perSession,
  })

  val toolPolicy = resolveSandboxToolPolicyForAgent(cfg, agentId)

  return {
    mode: agentSandbox?.mode ?? agent?.mode ?? "off",
    backend: agentSandbox?.backend?.trim() || agent?.backend?.trim() || "docker",
    scope,
    workspaceAccess: agentSandbox?.workspaceAccess ?? agent?.workspaceAccess ?? "none",
    workspaceRoot:
      agentSandbox?.workspaceRoot ?? agent?.workspaceRoot ?? DEFAULT_SANDBOX_WORKSPACE_ROOT,
    docker: resolveSandboxDockerConfig({
      scope,
      globalDocker: agent?.docker,
      agentDocker: agentSandbox?.docker,
    }),
    ssh: resolveSandboxSshConfig({
      scope,
      globalSsh: agent?.ssh,
      agentSsh: agentSandbox?.ssh,
    }),
    browser: resolveSandboxBrowserConfig({
      scope,
      globalBrowser: agent?.browser,
      agentBrowser: agentSandbox?.browser,
    }),
    tools: {
      allow: toolPolicy.allow,
      deny: toolPolicy.deny,
    },
    prune: resolveSandboxPruneConfig({
      scope,
      globalPrune: agent?.prune,
      agentPrune: agentSandbox?.prune,
    }),
  }
}
