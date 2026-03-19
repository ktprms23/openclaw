package agents.platform_support.sandbox

// Source: src/agents/sandbox/ssh.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import { spawn } from "node:child_process";
// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import os from "node:os";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import { parseSshTarget } from "../../infra/ssh-tunnel.js";
// TODO(openclaw-kotlin-port): import { resolvePreferredOpenClawTmpDir } from "../../infra/tmp-openclaw-dir.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../../utils.js";
// TODO(openclaw-kotlin-port): import type { SandboxBackendCommandResult } from "./backend.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SshSandboxSettings.
typealias SshSandboxSettings = Any?
/*
export type SshSandboxSettings = {
  command: string;
  target: string;
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

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SshSandboxSession.
typealias SshSandboxSession = Any?
/*
export type SshSandboxSession = {
  command: string;
  configPath: string;
  host: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RunSshSandboxCommandParams.
typealias RunSshSandboxCommandParams = Any?
/*
export type RunSshSandboxCommandParams = {
  session: SshSandboxSession;
  remoteCommand: string;
  stdin?: Buffer | string;
  allowFailure?: boolean;
  signal?: AbortSignal;
  tty?: boolean;
};
*/

fun normalizeInlineSshMaterial(contents: String, filename: String): String {
  val withoutBom = contents.replace(/^\uFEFF/, "")
  val normalizedNewlines = withoutBom.replace(/\r\n?/g, "\n")
  val normalizedEscapedNewlines = normalizedNewlines
    .replace(/\\r\\n/g, "\\n")
    .replace(/\\r/g, "\\n")
  val expanded =
    filename === "identity" || filename === "certificate.pub"
      ? normalizedEscapedNewlines.replace(/\\n/g, "\n")
      : normalizedEscapedNewlines
  return expanded.endsWith("\n") ? expanded : `${expanded}\n`
}

fun buildSshFailureMessage(stderr: String, exitCode?: Double): String {
  val trimmed = stderr.trim()
  if (
    trimmed.includes("error in libcrypto") &&
    (trimmed.includes('Load key "') || trimmed.includes("Permission denied (publickey)"))
  ) {
    return `${trimmed}\nSSH sandbox failed to load the configured identity. The private key contents may be malformed (for example CRLF or escaped newlines). Prefer identityFile when possible.`
  }
  return (
    trimmed ||
    (exitCode !== Nothing?
      ? `ssh exited with code ${exitCode}`
      : "ssh exited with a non-zero status")
  )
}

fun shellEscape(value: String): String {
  return `'${value.replaceAll("'", `'"'"'`)}'`
}

fun buildRemoteCommand(argv: String[]): String {
  return argv.map((entry) => shellEscape(entry)).join(" ")
}

fun buildExecRemoteCommand(params: {
  command: String
  workdir?: String
  env: Map<String, String>
}): String {
  val body = params.workdir
    ? `cd ${shellEscape(params.workdir)} && ${params.command}`
    : params.command
  val argv =
    Object.keys(params.env).length > 0
      ? [
          "env",
          ...Object.entries(params.env).map(([key, value]) => `${key}=${value}`),
          "/bin/sh",
          "-c",
          body,
        ]
      : ["/bin/sh", "-c", body]
  return buildRemoteCommand(argv)
}

fun buildSshSandboxArgv(params: {
  session: SshSandboxSession
  remoteCommand: String
  tty?: Boolean
}): String[] {
  return [
    params.session.command,
    "-F",
    params.session.configPath,
    ...(params.tty
      ? ["-tt", "-o", "RequestTTY=force", "-o", "SetEnv=TERM=xterm-256color"]
      : ["-T", "-o", "RequestTTY=no"]),
    params.session.host,
    params.remoteCommand,
  ]
}

suspend fun createSshSandboxSessionFromConfigText(params: {
  configText: String
  host?: String
  command?: String
}): Promise<SshSandboxSession> {
  val host = params.host?.trim() || parseSshConfigHost(params.configText)
  if (!host) {
    throw error("Failed to parse SSH config output.")
  }
  val configDir = await fs.mkdtemp(path.join(resolveSshTmpRoot(), "openclaw-sandbox-ssh-"))
  val configPath = path.join(configDir, "config")
  await fs.writeFile(configPath, params.configText, { encoding: "utf8", mode: 0o600 })
  await fs.chmod(configPath, 0o600)
  return {
    command: params.command?.trim() || "ssh",
    configPath,
    host,
  }
}

suspend fun createSshSandboxSessionFromSettings(
  settings: SshSandboxSettings,
): Promise<SshSandboxSession> {
  val parsed = parseSshTarget(settings.target)
  if (!parsed) {
    throw error(`Invalid sandbox SSH target: ${settings.target}`)
  }

  val configDir = await fs.mkdtemp(path.join(resolveSshTmpRoot(), "openclaw-sandbox-ssh-"))
  try {
    val materializedIdentity = settings.identityData
      ? await writeSecretMaterial(configDir, "identity", settings.identityData)
      : Nothing?
    val materializedCertificate = settings.certificateData
      ? await writeSecretMaterial(configDir, "certificate.pub", settings.certificateData)
      : Nothing?
    val materializedKnownHosts = settings.knownHostsData
      ? await writeSecretMaterial(configDir, "known_hosts", settings.knownHostsData)
      : Nothing?
    val identityFile = materializedIdentity ?? resolveOptionalLocalPath(settings.identityFile)
    val certificateFile =
      materializedCertificate ?? resolveOptionalLocalPath(settings.certificateFile)
    val knownHostsFile =
      materializedKnownHosts ?? resolveOptionalLocalPath(settings.knownHostsFile)
    val hostAlias = "openclaw-sandbox"
    val configPath = path.join(configDir, "config")
    val lines = [
      `Host ${hostAlias}`,
      `  HostName ${parsed.host}`,
      `  Port ${parsed.port}`,
      "  BatchMode yes",
      "  ConnectTimeout 5",
      "  ServerAliveInterval 15",
      "  ServerAliveCountMax 3",
      `  StrictHostKeyChecking ${settings.strictHostKeyChecking ? "yes" : "no"}`,
      `  UpdateHostKeys ${settings.updateHostKeys ? "yes" : "no"}`,
    ]
    if (parsed.user) {
      lines.push(`  User ${parsed.user}`)
    }
    if (knownHostsFile) {
      lines.push(`  UserKnownHostsFile ${knownHostsFile}`)
    } else if (!settings.strictHostKeyChecking) {
      lines.push("  UserKnownHostsFile /dev/Nothing?")
    }
    if (identityFile) {
      lines.push(`  IdentityFile ${identityFile}`)
    }
    if (certificateFile) {
      lines.push(`  CertificateFile ${certificateFile}`)
    }
    if (identityFile || certificateFile) {
      lines.push("  IdentitiesOnly yes")
    }
    await fs.writeFile(configPath, `${lines.join("\n")}\n`, {
      encoding: "utf8",
      mode: 0o600,
    })
    await fs.chmod(configPath, 0o600)
    return {
      command: settings.command.trim() || "ssh",
      configPath,
      host: hostAlias,
    }
  } catch (error) {
    await fs.rm(configDir, { recursive: true, force: true })
    throw error
  }
}

suspend fun disposeSshSandboxSession(session: SshSandboxSession): Promise<Unit> {
  await fs.rm(path.dirname(session.configPath), { recursive: true, force: true })
}

suspend fun runSshSandboxCommand(
  params: RunSshSandboxCommandParams,
): Promise<SandboxBackendCommandResult> {
  val argv = buildSshSandboxArgv({
    session: params.session,
    remoteCommand: params.remoteCommand,
    tty: params.tty,
  })
  return await new Promise<SandboxBackendCommandResult>((resolve, reject) => {
    val child = spawn(argv[0], argv.slice(1), {
      stdio: ["pipe", "pipe", "pipe"],
      env: process.env,
      signal: params.signal,
    })
    val stdoutChunks: Buffer[] = []
    val stderrChunks: Buffer[] = []

    child.stdout.on("data", (chunk) => stdoutChunks.push(Buffer.from(chunk)))
    child.stderr.on("data", (chunk) => stderrChunks.push(Buffer.from(chunk)))
    child.on("error", reject)
    child.on("close", (code) => {
      val stdout = Buffer.concat(stdoutChunks)
      val stderr = Buffer.concat(stderrChunks)
      val exitCode = code ?? 0
      if (exitCode !== 0 && !params.allowFailure) {
        reject(
          Object.assign(error(buildSshFailureMessage(stderr.toString("utf8"), exitCode)), {
            code: exitCode,
            stdout,
            stderr,
          }),
        )
        return
      }
      resolve({ stdout, stderr, code: exitCode })
    })

    if (params.stdin !== Nothing?) {
      child.stdin.end(params.stdin)
      return
    }
    child.stdin.end()
  })
}

suspend fun uploadDirectoryToSshTarget(params: {
  session: SshSandboxSession
  localDir: String
  remoteDir: String
  signal?: AbortSignal
}): Promise<Unit> {
  val remoteCommand = buildRemoteCommand([
    "/bin/sh",
    "-c",
    'mkdir -p -- "$1" && tar -xf - -C "$1"',
    "openclaw-sandbox-upload",
    params.remoteDir,
  ])
  val sshArgv = buildSshSandboxArgv({
    session: params.session,
    remoteCommand,
  })
  await new Promise<Unit>((resolve, reject) => {
    val tar = spawn("tar", ["-C", params.localDir, "-cf", "-", "."], {
      stdio: ["ignore", "pipe", "pipe"],
      signal: params.signal,
    })
    val ssh = spawn(sshArgv[0], sshArgv.slice(1), {
      stdio: ["pipe", "pipe", "pipe"],
      env: process.env,
      signal: params.signal,
    })
    val tarStderr: Buffer[] = []
    val sshStdout: Buffer[] = []
    val sshStderr: Buffer[] = []
    var tarClosed = false
    var sshClosed = false
    var tarCode = 0
    var sshCode = 0

    tar.stderr.on("data", (chunk) => tarStderr.push(Buffer.from(chunk)))
    ssh.stdout.on("data", (chunk) => sshStdout.push(Buffer.from(chunk)))
    ssh.stderr.on("data", (chunk) => sshStderr.push(Buffer.from(chunk)))

    val fail = { error: Any? -> {
      tar.kill("SIGKILL")
      ssh.kill("SIGKILL")
      reject(error)
    }

    tar.on("error", fail)
    ssh.on("error", fail)
    tar.stdout.pipe(ssh.stdin)

    tar.on("close", (code) => {
      tarClosed = true
      tarCode = code ?? 0
      maybeResolve()
    })
    ssh.on("close", (code) => {
      sshClosed = true
      sshCode = code ?? 0
      maybeResolve()
    })

    fun maybeResolve() {
      if (!tarClosed || !sshClosed) {
        return
      }
      if (tarCode !== 0) {
        reject(
          error(
            Buffer.concat(tarStderr).toString("utf8").trim() || `tar exited with code ${tarCode}`,
          ),
        )
        return
      }
      if (sshCode !== 0) {
        reject(
          error(
            Buffer.concat(sshStderr).toString("utf8").trim() || `ssh exited with code ${sshCode}`,
          ),
        )
        return
      }
      resolve()
    }
  })
}

fun parseSshConfigHost(configText: String): String | Nothing? {
  val hostMatch = configText.match(/^\s*Host\s+(\S+)/m)
  return hostMatch?.[1]?.trim() || Nothing?
}

fun resolveSshTmpRoot(): String {
  return path.resolve(resolvePreferredOpenClawTmpDir() ?? os.tmpdir())
}

fun resolveOptionalLocalPath(value: String | Nothing?): String | Nothing? {
  val trimmed = value?.trim()
  return trimmed ? resolveUserPath(trimmed) : Nothing?
}

suspend fun writeSecretMaterial(
  dir: String,
  filename: String,
  contents: String,
): Promise<String> {
  val pathname = path.join(dir, filename)
  await fs.writeFile(pathname, normalizeInlineSshMaterial(contents, filename), {
    encoding: "utf8",
    mode: 0o600,
  })
  await fs.chmod(pathname, 0o600)
  return pathname
}
