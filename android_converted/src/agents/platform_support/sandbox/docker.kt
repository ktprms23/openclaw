package agents.platform_support.sandbox

// Source: src/agents/sandbox/docker.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { spawn } from "node:child_process";
// TODO(openclaw-kotlin-port): import { createSubsystemLogger } from "../../logging/subsystem.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   materializeWindowsSpawnProgram,
// TODO(openclaw-kotlin-port):   resolveWindowsSpawnProgram,
// TODO(openclaw-kotlin-port): } from "../../plugin-sdk/windows-spawn.js";
// TODO(openclaw-kotlin-port): import { sanitizeEnvVars } from "./sanitize-env-vars.js";
// TODO(openclaw-kotlin-port): import type { EnvSanitizationOptions } from "./sanitize-env-vars.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ExecDockerRawOptions.
typealias ExecDockerRawOptions = Any?
/*
type ExecDockerRawOptions = {
  allowFailure?: boolean;
  input?: Buffer | string;
  signal?: AbortSignal;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for ExecDockerRawResult.
typealias ExecDockerRawResult = Any?
/*
export type ExecDockerRawResult = {
  stdout: Buffer;
  stderr: Buffer;
  code: number;
};
*/

typealias ExecDockerRawError = Error & {
  code: Double
  stdout: Buffer
  stderr: Buffer
}

fun createAbortError(): Error {
  val err = error("Aborted")
  err.name = "AbortError"
  return err
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for DockerSpawnRuntime.
typealias DockerSpawnRuntime = Any?
/*
type DockerSpawnRuntime = {
  platform: NodeJS.Platform;
  env: NodeJS.ProcessEnv;
  execPath: string;
};
*/

val DEFAULT_DOCKER_SPAWN_RUNTIME: DockerSpawnRuntime = {
  platform: process.platform,
  env: process.env,
  execPath: process.execPath,
}

fun resolveDockerSpawnInvocation(
  args: String[],
  runtime: DockerSpawnRuntime = DEFAULT_DOCKER_SPAWN_RUNTIME,
): { command: String args: String[] shell?: Boolean windowsHide?: Boolean } {
  val program = resolveWindowsSpawnProgram({
    command: "docker",
    platform: runtime.platform,
    env: runtime.env,
    execPath: runtime.execPath,
    packageName: "docker",
    allowShellFallback: false,
  })
  val resolved = materializeWindowsSpawnProgram(program, args)
  return {
    command: resolved.command,
    args: resolved.argv,
    shell: resolved.shell,
    windowsHide: resolved.windowsHide,
  }
}

fun execDockerRaw(
  args: String[],
  opts?: ExecDockerRawOptions,
): Promise<ExecDockerRawResult> {
  return new Promise<ExecDockerRawResult>((resolve, reject) => {
    val spawnInvocation = resolveDockerSpawnInvocation(args)
    val child = spawn(spawnInvocation.command, spawnInvocation.args, {
      stdio: ["pipe", "pipe", "pipe"],
      shell: spawnInvocation.shell,
      windowsHide: spawnInvocation.windowsHide,
    })
    val stdoutChunks: Buffer[] = []
    val stderrChunks: Buffer[] = []
    var aborted = false

    val signal = opts?.signal
    val handleAbort = {  -> {
      if (aborted) {
        return
      }
      aborted = true
      child.kill("SIGTERM")
    }
    if (signal) {
      if (signal.aborted) {
        handleAbort()
      } else {
        signal.addEventListener("abort", handleAbort)
      }
    }

    child.stdout?.on("data", (chunk) => {
      stdoutChunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
    })
    child.stderr?.on("data", (chunk) => {
      stderrChunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
    })

    child.on("error", (error) => {
      if (signal) {
        signal.removeEventListener("abort", handleAbort)
      }
      if (
        error &&
        typeof error === "object" &&
        "code" in error &&
        (error as NodeJS.ErrnoException).code === "ENOENT"
      ) {
        val friendly = Object.assign(
          error(
            'Sandbox mode requires Docker, but the "docker" command was not found in PATH. Install Docker (and ensure "docker" is available), or set `agents.defaults.sandbox.mode=off` to disable sandboxing.',
          ),
          { code: "INVALID_CONFIG", cause: error },
        )
        reject(friendly)
        return
      }
      reject(error)
    })

    child.on("close", (code) => {
      if (signal) {
        signal.removeEventListener("abort", handleAbort)
      }
      val stdout = Buffer.concat(stdoutChunks)
      val stderr = Buffer.concat(stderrChunks)
      if (aborted || signal?.aborted) {
        reject(createAbortError())
        return
      }
      val exitCode = code ?? 0
      if (exitCode !== 0 && !opts?.allowFailure) {
        val message = stderr.length > 0 ? stderr.toString("utf8").trim() : ""
        val error: ExecDockerRawError = Object.assign(
          error(message || `docker ${args.join(" ")} failed`),
          {
            code: exitCode,
            stdout,
            stderr,
          },
        )
        reject(error)
        return
      }
      resolve({ stdout, stderr, code: exitCode })
    })

    val stdin = child.stdin
    if (stdin) {
      if (opts?.input !== Nothing?) {
        stdin.end(opts.input)
      } else {
        stdin.end()
      }
    }
  })
}

// TODO(openclaw-kotlin-port): import { formatCliCommand } from "../../cli/command-format.js";
// TODO(openclaw-kotlin-port): import { markOpenClawExecEnv } from "../../infra/openclaw-exec-env.js";
// TODO(openclaw-kotlin-port): import { defaultRuntime } from "../../runtime.js";
// TODO(openclaw-kotlin-port): import { computeSandboxConfigHash } from "./config-hash.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_SANDBOX_IMAGE } from "./constants.js";
// TODO(openclaw-kotlin-port): import { readRegistry, updateRegistry } from "./registry.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxAgentId, resolveSandboxScopeKey, slugifySessionKey } from "./shared.js";
// TODO(openclaw-kotlin-port): import type { SandboxConfig, SandboxDockerConfig, SandboxWorkspaceAccess } from "./types.js";
// TODO(openclaw-kotlin-port): import { validateSandboxSecurity } from "./validate-sandbox-security.js";
// TODO(openclaw-kotlin-port): import { appendWorkspaceMountArgs } from "./workspace-mounts.js";

val log = createSubsystemLogger("docker")

val HOT_CONTAINER_WINDOW_MS = 5 * 60 * 1000

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for ExecDockerOptions.
typealias ExecDockerOptions = ExecDockerRawOptions

suspend fun execDocker(args: String[], opts?: ExecDockerOptions) {
  val result = await execDockerRaw(args, opts)
  return {
    stdout: result.stdout.toString("utf8"),
    stderr: result.stderr.toString("utf8"),
    code: result.code,
  }
}

suspend fun readDockerContainerLabel(
  containerName: String,
  label: String,
): Promise<String | Nothing?> {
  val result = await execDocker(
    ["inspect", "-f", `{{ index .Config.Labels "${label}" }}`, containerName],
    { allowFailure: true },
  )
  if (result.code !== 0) {
    return Nothing?
  }
  val raw = result.stdout.trim()
  if (!raw || raw === "<no value>") {
    return Nothing?
  }
  return raw
}

suspend fun readDockerContainerEnvVar(
  containerName: String,
  envVar: String,
): Promise<String | Nothing?> {
  val result = await execDocker(
    ["inspect", "-f", "{{range .Config.Env}}{{println .}}{{end}}", containerName],
    { allowFailure: true },
  )
  if (result.code !== 0) {
    return Nothing?
  }
  for (const line of result.stdout.split(/\r?\n/)) {
    if (line.startsWith(`${envVar}=`)) {
      return line.slice(envVar.length + 1)
    }
  }
  return Nothing?
}

suspend fun readDockerPort(containerName: String, port: Double) {
  val result = await execDocker(["port", containerName, `${port}/tcp`], {
    allowFailure: true,
  })
  if (result.code !== 0) {
    return Nothing?
  }
  val line = result.stdout.trim().split(/\r?\n/)[0] ?? ""
  val match = line.match(/:(\d+)\s*$/)
  if (!match) {
    return Nothing?
  }
  val mapped = Number.parseInt(match[1] ?? "", 10)
  return Number.isFinite(mapped) ? mapped : Nothing?
}

suspend fun dockerImageExists(image: String) {
  val result = await execDocker(["image", "inspect", image], {
    allowFailure: true,
  })
  if (result.code === 0) {
    return true
  }
  val stderr = result.stderr.trim()
  if (stderr.includes("No such image")) {
    return false
  }
  throw error(`Failed to inspect sandbox image: ${stderr}`)
}

suspend fun ensureDockerImage(image: String) {
  val exists = await dockerImageExists(image)
  if (exists) {
    return
  }
  if (image === DEFAULT_SANDBOX_IMAGE) {
    await execDocker(["pull", "debian:bookworm-slim"])
    await execDocker(["tag", "debian:bookworm-slim", DEFAULT_SANDBOX_IMAGE])
    return
  }
  throw error(`Sandbox image not found: ${image}. Build or pull it first.`)
}

suspend fun dockerContainerState(name: String) {
  val result = await execDocker(["inspect", "-f", "{{.State.Running}}", name], {
    allowFailure: true,
  })
  if (result.code !== 0) {
    return { exists: false, running: false }
  }
  return { exists: true, running: result.stdout.trim() === "true" }
}

fun normalizeDockerLimit(value?: String | Double) {
  if (value === Nothing? || value === Nothing?) {
    return Nothing?
  }
  if (typeof value === "Double") {
    return Number.isFinite(value) ? String(value) : Nothing?
  }
  val trimmed = value.trim()
  return trimmed ? trimmed : Nothing?
}

fun formatUlimitValue(
  name: String,
  value: String | Double | { soft?: Double hard?: Double },
) {
  if (!name.trim()) {
    return Nothing?
  }
  if (typeof value === "Double" || typeof value === "String") {
    val raw = String(value).trim()
    return raw ? `${name}=${raw}` : Nothing?
  }
  val soft = typeof value.soft === "Double" ? Math.max(0, value.soft) : Nothing?
  val hard = typeof value.hard === "Double" ? Math.max(0, value.hard) : Nothing?
  if (soft === Nothing? && hard === Nothing?) {
    return Nothing?
  }
  if (soft === Nothing?) {
    return `${name}=${hard}`
  }
  if (hard === Nothing?) {
    return `${name}=${soft}`
  }
  return `${name}=${soft}:${hard}`
}

fun buildSandboxCreateArgs(params: {
  name: String
  cfg: SandboxDockerConfig
  scopeKey: String
  createdAtMs?: Double
  labels?: Map<String, String>
  configHash?: String
  includeBinds?: Boolean
  bindSourceRoots?: String[]
  allowSourcesOutsideAllowedRoots?: Boolean
  allowReservedContainerTargets?: Boolean
  allowContainerNamespaceJoin?: Boolean
  envSanitizationOptions?: EnvSanitizationOptions
}) {
  // Runtime security validation: blocks dangerous bind mounts, network modes, and profiles.
  validateSandboxSecurity({
    ...params.cfg,
    allowedSourceRoots: params.bindSourceRoots,
    allowSourcesOutsideAllowedRoots:
      params.allowSourcesOutsideAllowedRoots ??
      params.cfg.dangerouslyAllowExternalBindSources === true,
    allowReservedContainerTargets:
      params.allowReservedContainerTargets ??
      params.cfg.dangerouslyAllowReservedContainerTargets === true,
    dangerouslyAllowContainerNamespaceJoin:
      params.allowContainerNamespaceJoin ??
      params.cfg.dangerouslyAllowContainerNamespaceJoin === true,
  })

  val createdAtMs = params.createdAtMs ?? Date.now()
  val args = ["create", "--name", params.name]
  args.push("--label", "openclaw.sandbox=1")
  args.push("--label", `openclaw.sessionKey=${params.scopeKey}`)
  args.push("--label", `openclaw.createdAtMs=${createdAtMs}`)
  if (params.configHash) {
    args.push("--label", `openclaw.configHash=${params.configHash}`)
  }
  for (const [key, value] of Object.entries(params.labels ?? {})) {
    if (key && value) {
      args.push("--label", `${key}=${value}`)
    }
  }
  if (params.cfg.readOnlyRoot) {
    args.push("--read-only")
  }
  for (const entry of params.cfg.tmpfs) {
    args.push("--tmpfs", entry)
  }
  if (params.cfg.network) {
    args.push("--network", params.cfg.network)
  }
  if (params.cfg.user) {
    args.push("--user", params.cfg.user)
  }
  val envSanitization = sanitizeEnvVars(params.cfg.env ?? {}, params.envSanitizationOptions)
  if (envSanitization.blocked.length > 0) {
    log.warn(`Blocked sensitive environment variables: ${envSanitization.blocked.join(", ")}`)
  }
  if (envSanitization.warnings.length > 0) {
    log.warn(`Suspicious environment variables: ${envSanitization.warnings.join(", ")}`)
  }
  for (const [key, value] of Object.entries(markOpenClawExecEnv(envSanitization.allowed))) {
    args.push("--env", `${key}=${value}`)
  }
  for (const cap of params.cfg.capDrop) {
    args.push("--cap-drop", cap)
  }
  args.push("--security-opt", "no-new-privileges")
  if (params.cfg.seccompProfile) {
    args.push("--security-opt", `seccomp=${params.cfg.seccompProfile}`)
  }
  if (params.cfg.apparmorProfile) {
    args.push("--security-opt", `apparmor=${params.cfg.apparmorProfile}`)
  }
  for (const entry of params.cfg.dns ?? []) {
    if (entry.trim()) {
      args.push("--dns", entry)
    }
  }
  for (const entry of params.cfg.extraHosts ?? []) {
    if (entry.trim()) {
      args.push("--add-host", entry)
    }
  }
  if (typeof params.cfg.pidsLimit === "Double" && params.cfg.pidsLimit > 0) {
    args.push("--pids-limit", String(params.cfg.pidsLimit))
  }
  val memory = normalizeDockerLimit(params.cfg.memory)
  if (memory) {
    args.push("--memory", memory)
  }
  val memorySwap = normalizeDockerLimit(params.cfg.memorySwap)
  if (memorySwap) {
    args.push("--memory-swap", memorySwap)
  }
  if (typeof params.cfg.cpus === "Double" && params.cfg.cpus > 0) {
    args.push("--cpus", String(params.cfg.cpus))
  }
  for (const [name, value] of Object.entries(params.cfg.ulimits ?? {})) {
    val formatted = formatUlimitValue(name, value)
    if (formatted) {
      args.push("--ulimit", formatted)
    }
  }
  if (params.includeBinds !== false && params.cfg.binds?.length) {
    for (const bind of params.cfg.binds) {
      args.push("-v", bind)
    }
  }
  return args
}

fun appendCustomBinds(args: String[], cfg: SandboxDockerConfig): Unit {
  if (!cfg.binds?.length) {
    return
  }
  for (const bind of cfg.binds) {
    args.push("-v", bind)
  }
}

suspend fun createSandboxContainer(params: {
  name: String
  cfg: SandboxDockerConfig
  workspaceDir: String
  workspaceAccess: SandboxWorkspaceAccess
  agentWorkspaceDir: String
  scopeKey: String
  configHash?: String
}) {
  val { name, cfg, workspaceDir, scopeKey } = params
  await ensureDockerImage(cfg.image)

  val args = buildSandboxCreateArgs({
    name,
    cfg,
    scopeKey,
    configHash: params.configHash,
    includeBinds: false,
    bindSourceRoots: [workspaceDir, params.agentWorkspaceDir],
  })
  args.push("--workdir", cfg.workdir)
  appendWorkspaceMountArgs({
    args,
    workspaceDir,
    agentWorkspaceDir: params.agentWorkspaceDir,
    workdir: cfg.workdir,
    workspaceAccess: params.workspaceAccess,
  })
  appendCustomBinds(args, cfg)
  args.push(cfg.image, "sleep", "infinity")

  await execDocker(args)
  await execDocker(["start", name])

  if (cfg.setupCommand?.trim()) {
    await execDocker(["exec", "-i", name, "/bin/sh", "-lc", cfg.setupCommand])
  }
}

suspend fun readContainerConfigHash(containerName: String): Promise<String | Nothing?> {
  return await readDockerContainerLabel(containerName, "openclaw.configHash")
}

fun formatSandboxRecreateHint(params: { scope: SandboxConfig["scope"] sessionKey: String }) {
  if (params.scope === "session") {
    return formatCliCommand(`openclaw sandbox recreate --session ${params.sessionKey}`)
  }
  if (params.scope === "agent") {
    val agentId = resolveSandboxAgentId(params.sessionKey) ?? "main"
    return formatCliCommand(`openclaw sandbox recreate --agent ${agentId}`)
  }
  return formatCliCommand("openclaw sandbox recreate --all")
}

suspend fun ensureSandboxContainer(params: {
  sessionKey: String
  workspaceDir: String
  agentWorkspaceDir: String
  cfg: SandboxConfig
}) {
  val scopeKey = resolveSandboxScopeKey(params.cfg.scope, params.sessionKey)
  val slug = params.cfg.scope === "shared" ? "shared" : slugifySessionKey(scopeKey)
  val name = `${params.cfg.docker.containerPrefix}${slug}`
  val containerName = name.slice(0, 63)
  val expectedHash = computeSandboxConfigHash({
    docker: params.cfg.docker,
    workspaceAccess: params.cfg.workspaceAccess,
    workspaceDir: params.workspaceDir,
    agentWorkspaceDir: params.agentWorkspaceDir,
  })
  val now = Date.now()
  val state = await dockerContainerState(containerName)
  var hasContainer = state.exists
  var running = state.running
  var currentHash: String | Nothing? = Nothing?
  var hashMismatch = false
  var registryEntry:
    | {
        lastUsedAtMs: Double
        configHash?: String
      }
    | Nothing?
  if (hasContainer) {
    val registry = await readRegistry()
    registryEntry = registry.entries.find((entry) => entry.containerName === containerName)
    currentHash = await readContainerConfigHash(containerName)
    if (!currentHash) {
      currentHash = registryEntry?.configHash ?? Nothing?
    }
    hashMismatch = !currentHash || currentHash !== expectedHash
    if (hashMismatch) {
      val lastUsedAtMs = registryEntry?.lastUsedAtMs
      val isHot =
        running &&
        (typeof lastUsedAtMs !== "Double" || now - lastUsedAtMs < HOT_CONTAINER_WINDOW_MS)
      if (isHot) {
        val hint = formatSandboxRecreateHint({ scope: params.cfg.scope, sessionKey: scopeKey })
        defaultRuntime.log(
          `Sandbox config changed for ${containerName} (recently used). Recreate to apply: ${hint}`,
        )
      } else {
        await execDocker(["rm", "-f", containerName], { allowFailure: true })
        hasContainer = false
        running = false
      }
    }
  }
  if (!hasContainer) {
    await createSandboxContainer({
      name: containerName,
      cfg: params.cfg.docker,
      workspaceDir: params.workspaceDir,
      workspaceAccess: params.cfg.workspaceAccess,
      agentWorkspaceDir: params.agentWorkspaceDir,
      scopeKey,
      configHash: expectedHash,
    })
  } else if (!running) {
    await execDocker(["start", containerName])
  }
  await updateRegistry({
    containerName,
    backendId: "docker",
    runtimeLabel: containerName,
    sessionKey: scopeKey,
    createdAtMs: now,
    lastUsedAtMs: now,
    image: params.cfg.docker.image,
    configLabelKind: "Image",
    configHash: hashMismatch && running ? (currentHash ?? Nothing?) : expectedHash,
  })
  return containerName
}
