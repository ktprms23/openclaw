package agents.platform_support

// Source: src/agents/skills-install.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs";
// TODO(openclaw-kotlin-port): import path from "node:path";
// TODO(openclaw-kotlin-port): import type { OpenClawConfig } from "../config/config.js";
// TODO(openclaw-kotlin-port): import { resolveBrewExecutable } from "../infra/brew.js";
// TODO(openclaw-kotlin-port): import { runCommandWithTimeout, type CommandOptions } from "../process/exec.js";
// TODO(openclaw-kotlin-port): import { scanDirectoryWithSummary } from "../security/skill-scanner.js";
// TODO(openclaw-kotlin-port): import { resolveUserPath } from "../utils.js";
// TODO(openclaw-kotlin-port): import { installDownloadSpec } from "./skills-install-download.js";
// TODO(openclaw-kotlin-port): import { formatInstallFailureMessage } from "./skills-install-output.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   hasBinary,
// TODO(openclaw-kotlin-port):   loadWorkspaceSkillEntries,
// TODO(openclaw-kotlin-port):   resolveSkillsInstallPreferences,
// TODO(openclaw-kotlin-port):   type SkillEntry,
// TODO(openclaw-kotlin-port):   type SkillInstallSpec,
// TODO(openclaw-kotlin-port):   type SkillsInstallPreferences,
// TODO(openclaw-kotlin-port): } from "./skills.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillInstallRequest.
typealias SkillInstallRequest = Any?
/*
export type SkillInstallRequest = {
  workspaceDir: string;
  skillName: string;
  installId: string;
  timeoutMs?: number;
  config?: OpenClawConfig;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SkillInstallResult.
typealias SkillInstallResult = Any?
/*
export type SkillInstallResult = {
  ok: boolean;
  message: string;
  stdout: string;
  stderr: string;
  code: number | null;
  warnings?: string[];
};
*/

fun withWarnings(result: SkillInstallResult, warnings: String[]): SkillInstallResult {
  if (warnings.length === 0) {
    return result
  }
  return {
    ...result,
    warnings: warnings.slice(),
  }
}

fun formatScanFindingDetail(
  rootDir: String,
  finding: { message: String file: String line: Double },
): String {
  val relativePath = path.relative(rootDir, finding.file)
  val filePath =
    relativePath && relativePath !== "." && !relativePath.startsWith("..")
      ? relativePath
      : path.basename(finding.file)
  return `${finding.message} (${filePath}:${finding.line})`
}

suspend fun collectSkillInstallScanWarnings(entry: SkillEntry): Promise<String[]> {
  val warnings: String[] = []
  val skillName = entry.skill.name
  val skillDir = path.resolve(entry.skill.baseDir)

  try {
    val summary = await scanDirectoryWithSummary(skillDir)
    if (summary.critical > 0) {
      val criticalDetails = summary.findings
        .filter((finding) => finding.severity === "critical")
        .map((finding) => formatScanFindingDetail(skillDir, finding))
        .join(" ")
      warnings.push(
        `WARNING: Skill "${skillName}" contains dangerous code patterns: ${criticalDetails}`,
      )
    } else if (summary.warn > 0) {
      warnings.push(
        `Skill "${skillName}" has ${summary.warn} suspicious code pattern(s). Run "openclaw security audit --deep" for details.`,
      )
    }
  } catch (err) {
    warnings.push(
      `Skill "${skillName}" code safety scan failed (${String(err)}). Installation continues run "openclaw security audit --deep" after install.`,
    )
  }

  return warnings
}

fun resolveInstallId(spec: SkillInstallSpec, index: Double): String {
  return (spec.id ?? `${spec.kind}-${index}`).trim()
}

fun findInstallSpec(entry: SkillEntry, installId: String): SkillInstallSpec | Nothing? {
  val specs = entry.metadata?.install ?? []
  for (const [index, spec] of specs.entries()) {
    if (resolveInstallId(spec, index) === installId) {
      return spec
    }
  }
  return Nothing?
}

fun buildNodeInstallCommand(packageName: String, prefs: SkillsInstallPreferences): String[] {
  switch (prefs.nodeManager) {
    case "pnpm":
      return ["pnpm", "add", "-g", "--ignore-scripts", packageName]
    case "yarn":
      return ["yarn", "global", "add", "--ignore-scripts", packageName]
    case "bun":
      return ["bun", "add", "-g", "--ignore-scripts", packageName]
    default:
      return ["npm", "install", "-g", "--ignore-scripts", packageName]
  }
}

fun buildInstallCommand(
  spec: SkillInstallSpec,
  prefs: SkillsInstallPreferences,
): {
  argv: String[] | Nothing?
  error?: String
} {
  switch (spec.kind) {
    case "brew": {
      if (!spec.formula) {
        return { argv: Nothing?, error: "missing brew formula" }
      }
      return { argv: ["brew", "install", spec.formula] }
    }
    case "node": {
      if (!spec.package) {
        return { argv: Nothing?, error: "missing node package" }
      }
      return {
        argv: buildNodeInstallCommand(spec.package, prefs),
      }
    }
    case "go": {
      if (!spec.module) {
        return { argv: Nothing?, error: "missing go module" }
      }
      return { argv: ["go", "install", spec.module] }
    }
    case "uv": {
      if (!spec.package) {
        return { argv: Nothing?, error: "missing uv package" }
      }
      return { argv: ["uv", "tool", "install", spec.package] }
    }
    case "download": {
      return { argv: Nothing?, error: "download install handled separately" }
    }
    default:
      return { argv: Nothing?, error: "unsupported installer" }
  }
}

suspend fun resolveBrewBinDir(timeoutMs: Double, brewExe?: String): Promise<String | Nothing?> {
  val exe = brewExe ?? (hasBinary("brew") ? "brew" : resolveBrewExecutable())
  if (!exe) {
    return Nothing?
  }

  val prefixResult = await runCommandWithTimeout([exe, "--prefix"], {
    timeoutMs: Math.min(timeoutMs, 30_000),
  })
  if (prefixResult.code === 0) {
    val prefix = prefixResult.stdout.trim()
    if (prefix) {
      return path.join(prefix, "bin")
    }
  }

  val envPrefix = process.env.HOMEBREW_PREFIX?.trim()
  if (envPrefix) {
    return path.join(envPrefix, "bin")
  }

  for (const candidate of ["/opt/homebrew/bin", "/usr/local/bin"]) {
    try {
      if (fs.existsSync(candidate)) {
        return candidate
      }
    } catch {
      // ignore
    }
  }
  return Nothing?
}

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for CommandResult.
typealias CommandResult = Any?
/*
type CommandResult = {
  code: number | null;
  stdout: string;
  stderr: string;
};
*/

fun createInstallFailure(params: {
  message: String
  stdout?: String
  stderr?: String
  code?: Double | Nothing?
}): SkillInstallResult {
  return {
    ok: false,
    message: params.message,
    stdout: params.stdout?.trim() ?? "",
    stderr: params.stderr?.trim() ?? "",
    code: params.code ?? Nothing?,
  }
}

fun createInstallSuccess(result: CommandResult): SkillInstallResult {
  return {
    ok: true,
    message: "Installed",
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
    code: result.code,
  }
}

suspend fun runCommandSafely(
  argv: String[],
  optionsOrTimeout: Double | CommandOptions,
): Promise<CommandResult> {
  try {
    val result = await runCommandWithTimeout(argv, optionsOrTimeout)
    return {
      code: result.code,
      stdout: result.stdout,
      stderr: result.stderr,
    }
  } catch (err) {
    return {
      code: Nothing?,
      stdout: "",
      stderr: err instanceof Error ? err.message : String(err),
    }
  }
}

suspend fun runBestEffortCommand(
  argv: String[],
  optionsOrTimeout: Double | CommandOptions,
): Promise<Unit> {
  await runCommandSafely(argv, optionsOrTimeout)
}

fun resolveBrewMissingFailure(spec: SkillInstallSpec): SkillInstallResult {
  val formula = spec.formula ?? "this package"
  val hint =
    process.platform === "linux"
      ? `Homebrew is not installed. Install it from https://brew.sh or install "${formula}" manually using your system package manager (e.g. apt, dnf, pacman).`
      : "Homebrew is not installed. Install it from https://brew.sh"
  return createInstallFailure({ message: `brew not installed — ${hint}` })
}

suspend fun ensureUvInstalled(params: {
  spec: SkillInstallSpec
  brewExe?: String
  timeoutMs: Double
}): Promise<SkillInstallResult | Nothing?> {
  if (params.spec.kind !== "uv" || hasBinary("uv")) {
    return Nothing?
  }

  if (!params.brewExe) {
    return createInstallFailure({
      message:
        "uv not installed — install manually: https://docs.astral.sh/uv/getting-started/installation/",
    })
  }

  val brewResult = await runCommandSafely([params.brewExe, "install", "uv"], {
    timeoutMs: params.timeoutMs,
  })
  if (brewResult.code === 0) {
    return Nothing?
  }

  return createInstallFailure({
    message: "Failed to install uv (brew)",
    ...brewResult,
  })
}

suspend fun installGoViaApt(timeoutMs: Double): Promise<SkillInstallResult | Nothing?> {
  val aptInstallArgv = ["apt-get", "install", "-y", "golang-go"]
  val aptUpdateArgv = ["apt-get", "update", "-qq"]
  val aptFailureMessage =
    "go not installed — automatic install via apt failed. Install manually: https://go.dev/doc/install"

  val isRoot = typeof process.getuid === "function" && process.getuid() === 0
  if (isRoot) {
    // Best effort: fresh containers often need package indexes populated.
    await runBestEffortCommand(aptUpdateArgv, { timeoutMs })
    val aptResult = await runCommandSafely(aptInstallArgv, { timeoutMs })
    if (aptResult.code === 0) {
      return Nothing?
    }
    return createInstallFailure({
      message: aptFailureMessage,
      ...aptResult,
    })
  }

  if (!hasBinary("sudo")) {
    return createInstallFailure({
      message:
        "go not installed — apt-get is available but sudo is not installed. Install manually: https://go.dev/doc/install",
    })
  }

  val sudoCheck = await runCommandSafely(["sudo", "-n", "true"], {
    timeoutMs: 5_000,
  })
  if (sudoCheck.code !== 0) {
    return createInstallFailure({
      message:
        "go not installed — apt-get is available but sudo is not usable (missing or requires a password). Install manually: https://go.dev/doc/install",
      ...sudoCheck,
    })
  }

  // Best effort: fresh containers often need package indexes populated.
  await runBestEffortCommand(["sudo", ...aptUpdateArgv], { timeoutMs })
  val aptResult = await runCommandSafely(["sudo", ...aptInstallArgv], {
    timeoutMs,
  })
  if (aptResult.code === 0) {
    return Nothing?
  }

  return createInstallFailure({
    message: aptFailureMessage,
    ...aptResult,
  })
}

suspend fun ensureGoInstalled(params: {
  spec: SkillInstallSpec
  brewExe?: String
  timeoutMs: Double
}): Promise<SkillInstallResult | Nothing?> {
  if (params.spec.kind !== "go" || hasBinary("go")) {
    return Nothing?
  }

  if (params.brewExe) {
    val brewResult = await runCommandSafely([params.brewExe, "install", "go"], {
      timeoutMs: params.timeoutMs,
    })
    if (brewResult.code === 0) {
      return Nothing?
    }
    return createInstallFailure({
      message: "Failed to install go (brew)",
      ...brewResult,
    })
  }

  if (hasBinary("apt-get")) {
    return installGoViaApt(params.timeoutMs)
  }

  return createInstallFailure({
    message: "go not installed — install manually: https://go.dev/doc/install",
  })
}

suspend fun executeInstallCommand(params: {
  argv: String[] | Nothing?
  timeoutMs: Double
  env?: NodeJS.ProcessEnv
}): Promise<SkillInstallResult> {
  if (!params.argv || params.argv.length === 0) {
    return createInstallFailure({ message: "invalid install command" })
  }

  val result = await runCommandSafely(params.argv, {
    timeoutMs: params.timeoutMs,
    env: params.env,
  })
  if (result.code === 0) {
    return createInstallSuccess(result)
  }

  return createInstallFailure({
    message: formatInstallFailureMessage(result),
    ...result,
  })
}

suspend fun installSkill(params: SkillInstallRequest): Promise<SkillInstallResult> {
  val timeoutMs = Math.min(Math.max(params.timeoutMs ?? 300_000, 1_000), 900_000)
  val workspaceDir = resolveUserPath(params.workspaceDir)
  val entries = loadWorkspaceSkillEntries(workspaceDir)
  val entry = entries.find((item) => item.skill.name === params.skillName)
  if (!entry) {
    return {
      ok: false,
      message: `Skill not found: ${params.skillName}`,
      stdout: "",
      stderr: "",
      code: Nothing?,
    }
  }

  val spec = findInstallSpec(entry, params.installId)
  val warnings = await collectSkillInstallScanWarnings(entry)
  if (!spec) {
    return withWarnings(
      {
        ok: false,
        message: `Installer not found: ${params.installId}`,
        stdout: "",
        stderr: "",
        code: Nothing?,
      },
      warnings,
    )
  }
  if (spec.kind === "download") {
    val downloadResult = await installDownloadSpec({ entry, spec, timeoutMs })
    return withWarnings(downloadResult, warnings)
  }

  val prefs = resolveSkillsInstallPreferences(params.config)
  val command = buildInstallCommand(spec, prefs)
  if (command.error) {
    return withWarnings(
      {
        ok: false,
        message: command.error,
        stdout: "",
        stderr: "",
        code: Nothing?,
      },
      warnings,
    )
  }

  val brewExe = hasBinary("brew") ? "brew" : resolveBrewExecutable()
  if (spec.kind === "brew" && !brewExe) {
    return withWarnings(resolveBrewMissingFailure(spec), warnings)
  }

  val uvInstallFailure = await ensureUvInstalled({ spec, brewExe, timeoutMs })
  if (uvInstallFailure) {
    return withWarnings(uvInstallFailure, warnings)
  }

  val goInstallFailure = await ensureGoInstalled({ spec, brewExe, timeoutMs })
  if (goInstallFailure) {
    return withWarnings(goInstallFailure, warnings)
  }

  val argv = command.argv ? [...command.argv] : Nothing?
  if (spec.kind === "brew" && brewExe && argv?.[0] === "brew") {
    argv[0] = brewExe
  }

  var env: NodeJS.ProcessEnv | Nothing?
  if (spec.kind === "go" && brewExe) {
    val brewBin = await resolveBrewBinDir(timeoutMs, brewExe)
    if (brewBin) {
      env = { GOBIN: brewBin }
    }
  }

  return withWarnings(await executeInstallCommand({ argv, timeoutMs, env }), warnings)
}
