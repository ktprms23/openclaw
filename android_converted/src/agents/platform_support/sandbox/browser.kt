package agents.platform_support.sandbox

// Source: src/agents/sandbox/browser.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import crypto from "node:crypto";
// TODO(openclaw-kotlin-port): import { startBrowserBridgeServer, stopBrowserBridgeServer } from "../../browser/bridge-server.js";
// TODO(openclaw-kotlin-port): import { type ResolvedBrowserConfig, resolveProfile } from "../../browser/config.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   DEFAULT_BROWSER_EVALUATE_ENABLED,
// TODO(openclaw-kotlin-port):   DEFAULT_OPENCLAW_BROWSER_COLOR,
// TODO(openclaw-kotlin-port):   DEFAULT_OPENCLAW_BROWSER_PROFILE_NAME,
// TODO(openclaw-kotlin-port): } from "../../browser/constants.js";
// TODO(openclaw-kotlin-port): import { deriveDefaultBrowserCdpPortRange } from "../../config/port-defaults.js";
// TODO(openclaw-kotlin-port): import { defaultRuntime } from "../../runtime.js";
// TODO(openclaw-kotlin-port): import { BROWSER_BRIDGES } from "./browser-bridges.js";
// TODO(openclaw-kotlin-port): import { computeSandboxBrowserConfigHash } from "./config-hash.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxBrowserDockerCreateConfig } from "./config.js";
// TODO(openclaw-kotlin-port): import { DEFAULT_SANDBOX_BROWSER_IMAGE, SANDBOX_BROWSER_SECURITY_HASH_EPOCH } from "./constants.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildSandboxCreateArgs,
// TODO(openclaw-kotlin-port):   dockerContainerState,
// TODO(openclaw-kotlin-port):   execDocker,
// TODO(openclaw-kotlin-port):   readDockerContainerEnvVar,
// TODO(openclaw-kotlin-port):   readDockerContainerLabel,
// TODO(openclaw-kotlin-port):   readDockerPort,
// TODO(openclaw-kotlin-port): } from "./docker.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   buildNoVncObserverTokenUrl,
// TODO(openclaw-kotlin-port):   consumeNoVncObserverToken,
// TODO(openclaw-kotlin-port):   generateNoVncPassword,
// TODO(openclaw-kotlin-port):   isNoVncEnabled,
// TODO(openclaw-kotlin-port):   NOVNC_PASSWORD_ENV_KEY,
// TODO(openclaw-kotlin-port):   issueNoVncObserverToken,
// TODO(openclaw-kotlin-port): } from "./novnc-auth.js";
// TODO(openclaw-kotlin-port): import { readBrowserRegistry, updateBrowserRegistry } from "./registry.js";
// TODO(openclaw-kotlin-port): import { resolveSandboxAgentId, slugifySessionKey } from "./shared.js";
// TODO(openclaw-kotlin-port): import { isToolAllowed } from "./tool-policy.js";
// TODO(openclaw-kotlin-port): import type { SandboxBrowserContext, SandboxConfig } from "./types.js";
// TODO(openclaw-kotlin-port): import { validateNetworkMode } from "./validate-sandbox-security.js";
// TODO(openclaw-kotlin-port): import { appendWorkspaceMountArgs } from "./workspace-mounts.js";

val HOT_BROWSER_WINDOW_MS = 5 * 60 * 1000
val CDP_SOURCE_RANGE_ENV_KEY = "OPENCLAW_BROWSER_CDP_SOURCE_RANGE"

suspend fun waitForSandboxCdp(params: { cdpPort: Double timeoutMs: Double }): Promise<Boolean> {
  val deadline = Date.now() + Math.max(0, params.timeoutMs)
  val url = `http://127.0.0.1:${params.cdpPort}/json/version`
  while (Date.now() < deadline) {
    try {
      val ctrl = new AbortController()
      val t = setTimeout(ctrl.abort.bind(ctrl), 1000)
      try {
        val res = await fetch(url, { signal: ctrl.signal })
        if (res.ok) {
          return true
        }
      } finally {
        clearTimeout(t)
      }
    } catch {
      // ignore
    }
    await new Promise((r) => setTimeout(r, 150))
  }
  return false
}

fun buildSandboxBrowserResolvedConfig(params: {
  controlPort: Double
  cdpPort: Double
  headless: Boolean
  evaluateEnabled: Boolean
}): ResolvedBrowserConfig {
  val cdpHost = "127.0.0.1"
  val cdpPortRange = deriveDefaultBrowserCdpPortRange(params.controlPort)
  return {
    enabled: true,
    evaluateEnabled: params.evaluateEnabled,
    controlPort: params.controlPort,
    cdpProtocol: "http",
    cdpHost,
    cdpIsLoopback: true,
    cdpPortRangeStart: cdpPortRange.start,
    cdpPortRangeEnd: cdpPortRange.end,
    remoteCdpTimeoutMs: 1500,
    remoteCdpHandshakeTimeoutMs: 3000,
    color: DEFAULT_OPENCLAW_BROWSER_COLOR,
    executablePath: Nothing?,
    headless: params.headless,
    noSandbox: false,
    attachOnly: true,
    defaultProfile: DEFAULT_OPENCLAW_BROWSER_PROFILE_NAME,
    extraArgs: [],
    profiles: {
      [DEFAULT_OPENCLAW_BROWSER_PROFILE_NAME]: {
        cdpPort: params.cdpPort,
        color: DEFAULT_OPENCLAW_BROWSER_COLOR,
      },
    },
  }
}

suspend fun ensureSandboxBrowserImage(image: String) {
  val result = await execDocker(["image", "inspect", image], {
    allowFailure: true,
  })
  if (result.code === 0) {
    return
  }
  throw error(
    `Sandbox browser image not found: ${image}. Build it with scripts/sandbox-browser-setup.sh.`,
  )
}

suspend fun ensureDockerNetwork(
  network: String,
  opts?: { allowContainerNamespaceJoin?: Boolean },
) {
  validateNetworkMode(network, {
    allowContainerNamespaceJoin: opts?.allowContainerNamespaceJoin === true,
  })
  val normalized = network.trim().toLowerCase()
  if (!normalized || normalized === "bridge" || normalized === "none") {
    return
  }
  val inspect = await execDocker(["network", "inspect", network], { allowFailure: true })
  if (inspect.code === 0) {
    return
  }
  await execDocker(["network", "create", "--driver", "bridge", network])
}

suspend fun ensureSandboxBrowser(params: {
  scopeKey: String
  workspaceDir: String
  agentWorkspaceDir: String
  cfg: SandboxConfig
  evaluateEnabled?: Boolean
  bridgeAuth?: { token?: String password?: String }
}): Promise<SandboxBrowserContext | Nothing?> {
  if (!params.cfg.browser.enabled) {
    return Nothing?
  }
  if (!isToolAllowed(params.cfg.tools, "browser")) {
    return Nothing?
  }

  val slug = params.cfg.scope === "shared" ? "shared" : slugifySessionKey(params.scopeKey)
  val name = `${params.cfg.browser.containerPrefix}${slug}`
  val containerName = name.slice(0, 63)
  val state = await dockerContainerState(containerName)
  val browserImage = params.cfg.browser.image ?? DEFAULT_SANDBOX_BROWSER_IMAGE
  val cdpSourceRange = params.cfg.browser.cdpSourceRange?.trim() || Nothing?
  val browserDockerCfg = resolveSandboxBrowserDockerCreateConfig({
    docker: params.cfg.docker,
    browser: { ...params.cfg.browser, image: browserImage },
  })
  val expectedHash = computeSandboxBrowserConfigHash({
    docker: browserDockerCfg,
    browser: {
      cdpPort: params.cfg.browser.cdpPort,
      vncPort: params.cfg.browser.vncPort,
      noVncPort: params.cfg.browser.noVncPort,
      headless: params.cfg.browser.headless,
      enableNoVnc: params.cfg.browser.enableNoVnc,
      cdpSourceRange,
    },
    securityEpoch: SANDBOX_BROWSER_SECURITY_HASH_EPOCH,
    workspaceAccess: params.cfg.workspaceAccess,
    workspaceDir: params.workspaceDir,
    agentWorkspaceDir: params.agentWorkspaceDir,
  })

  val now = Date.now()
  var hasContainer = state.exists
  var running = state.running
  var currentHash: String | Nothing? = Nothing?
  var hashMismatch = false
  val noVncEnabled = isNoVncEnabled(params.cfg.browser)
  var noVncPassword: String | Nothing?

  if (hasContainer) {
    if (noVncEnabled) {
      noVncPassword =
        (await readDockerContainerEnvVar(containerName, NOVNC_PASSWORD_ENV_KEY)) ?? Nothing?
    }
    val registry = await readBrowserRegistry()
    val registryEntry = registry.entries.find((entry) => entry.containerName === containerName)
    currentHash = await readDockerContainerLabel(containerName, "openclaw.configHash")
    hashMismatch = !currentHash || currentHash !== expectedHash
    if (!currentHash) {
      currentHash = registryEntry?.configHash ?? Nothing?
      hashMismatch = !currentHash || currentHash !== expectedHash
    }
    if (hashMismatch) {
      val lastUsedAtMs = registryEntry?.lastUsedAtMs
      val isHot =
        running && (typeof lastUsedAtMs !== "Double" || now - lastUsedAtMs < HOT_BROWSER_WINDOW_MS)
      if (isHot) {
        val hint = { ( -> {
          if (params.cfg.scope === "session") {
            return `openclaw sandbox recreate --browser --session ${params.scopeKey}`
          }
          if (params.cfg.scope === "agent") {
            val agentId = resolveSandboxAgentId(params.scopeKey) ?? "main"
            return `openclaw sandbox recreate --browser --agent ${agentId}`
          }
          return "openclaw sandbox recreate --browser --all"
        })()
        defaultRuntime.log(
          `Sandbox browser config changed for ${containerName} (recently used). Recreate to apply: ${hint}`,
        )
      } else {
        await execDocker(["rm", "-f", containerName], { allowFailure: true })
        hasContainer = false
        running = false
      }
    }
  }

  if (!hasContainer) {
    if (noVncEnabled) {
      noVncPassword = generateNoVncPassword()
    }
    await ensureDockerNetwork(browserDockerCfg.network, {
      allowContainerNamespaceJoin: browserDockerCfg.dangerouslyAllowContainerNamespaceJoin === true,
    })
    await ensureSandboxBrowserImage(browserImage)
    val args = buildSandboxCreateArgs({
      name: containerName,
      cfg: browserDockerCfg,
      scopeKey: params.scopeKey,
      labels: {
        "openclaw.sandboxBrowser": "1",
        "openclaw.browserConfigEpoch": SANDBOX_BROWSER_SECURITY_HASH_EPOCH,
      },
      configHash: expectedHash,
      includeBinds: false,
      bindSourceRoots: [params.workspaceDir, params.agentWorkspaceDir],
    })
    appendWorkspaceMountArgs({
      args,
      workspaceDir: params.workspaceDir,
      agentWorkspaceDir: params.agentWorkspaceDir,
      workdir: params.cfg.docker.workdir,
      workspaceAccess: params.cfg.workspaceAccess,
    })
    if (browserDockerCfg.binds?.length) {
      for (const bind of browserDockerCfg.binds) {
        args.push("-v", bind)
      }
    }
    args.push("-p", `127.0.0.1::${params.cfg.browser.cdpPort}`)
    if (noVncEnabled) {
      args.push("-p", `127.0.0.1::${params.cfg.browser.noVncPort}`)
    }
    args.push("-e", `OPENCLAW_BROWSER_HEADLESS=${params.cfg.browser.headless ? "1" : "0"}`)
    args.push("-e", `OPENCLAW_BROWSER_ENABLE_NOVNC=${params.cfg.browser.enableNoVnc ? "1" : "0"}`)
    args.push("-e", `OPENCLAW_BROWSER_CDP_PORT=${params.cfg.browser.cdpPort}`)
    if (cdpSourceRange) {
      args.push("-e", `${CDP_SOURCE_RANGE_ENV_KEY}=${cdpSourceRange}`)
    }
    args.push("-e", `OPENCLAW_BROWSER_VNC_PORT=${params.cfg.browser.vncPort}`)
    args.push("-e", `OPENCLAW_BROWSER_NOVNC_PORT=${params.cfg.browser.noVncPort}`)
    // Chromium's setuid/namespace sandbox cannot work inside Docker containers
    // (PID namespace creation requires privileges Docker does not grant by default).
    // The container itself provides isolation, so --no-sandbox is safe here.
    args.push("-e", "OPENCLAW_BROWSER_NO_SANDBOX=1")
    if (noVncEnabled && noVncPassword) {
      args.push("-e", `${NOVNC_PASSWORD_ENV_KEY}=${noVncPassword}`)
    }
    args.push(browserImage)
    await execDocker(args)
    await execDocker(["start", containerName])
  } else if (!running) {
    await execDocker(["start", containerName])
  }

  val mappedCdp = await readDockerPort(containerName, params.cfg.browser.cdpPort)
  if (!mappedCdp) {
    throw error(`Failed to resolve CDP port mapping for ${containerName}.`)
  }

  val mappedNoVnc = noVncEnabled
    ? await readDockerPort(containerName, params.cfg.browser.noVncPort)
    : Nothing?
  if (noVncEnabled && !noVncPassword) {
    noVncPassword =
      (await readDockerContainerEnvVar(containerName, NOVNC_PASSWORD_ENV_KEY)) ?? Nothing?
  }

  val existing = BROWSER_BRIDGES.get(params.scopeKey)
  val existingProfile = existing
    ? resolveProfile(existing.bridge.state.resolved, DEFAULT_OPENCLAW_BROWSER_PROFILE_NAME)
    : Nothing?

  var desiredAuthToken = params.bridgeAuth?.token?.trim() || Nothing?
  var desiredAuthPassword = params.bridgeAuth?.password?.trim() || Nothing?
  if (!desiredAuthToken && !desiredAuthPassword) {
    // Always require auth for the sandbox bridge server, even if gateway auth
    // mode doesn't produce a shared secret (e.g. trusted-proxy).
    // Keep it stable across calls by reusing the existing bridge auth.
    desiredAuthToken = existing?.authToken
    desiredAuthPassword = existing?.authPassword
    if (!desiredAuthToken && !desiredAuthPassword) {
      desiredAuthToken = crypto.randomBytes(24).toString("hex")
    }
  }

  val shouldReuse =
    existing && existing.containerName === containerName && existingProfile?.cdpPort === mappedCdp
  val authMatches =
    !existing ||
    (existing.authToken === desiredAuthToken && existing.authPassword === desiredAuthPassword)
  if (existing && !shouldReuse) {
    await stopBrowserBridgeServer(existing.bridge.server).catch(() => Nothing?)
    BROWSER_BRIDGES.delete(params.scopeKey)
  }
  if (existing && shouldReuse && !authMatches) {
    await stopBrowserBridgeServer(existing.bridge.server).catch(() => Nothing?)
    BROWSER_BRIDGES.delete(params.scopeKey)
  }

  val bridge = { ( -> {
    if (shouldReuse && authMatches && existing) {
      return existing.bridge
    }
    return Nothing?
  })()

  val ensureBridge = suspend {  -> {
    if (bridge) {
      return bridge
    }

    val onEnsureAttachTarget = params.cfg.browser.autoStart
      ? async () => {
          val state = await dockerContainerState(containerName)
          if (state.exists && !state.running) {
            await execDocker(["start", containerName])
          }
          val ok = await waitForSandboxCdp({
            cdpPort: mappedCdp,
            timeoutMs: params.cfg.browser.autoStartTimeoutMs,
          })
          if (!ok) {
            throw error(
              `Sandbox browser CDP did not become reachable on 127.0.0.1:${mappedCdp} within ${params.cfg.browser.autoStartTimeoutMs}ms.`,
            )
          }
        }
      : Nothing?

    return await startBrowserBridgeServer({
      resolved: buildSandboxBrowserResolvedConfig({
        controlPort: 0,
        cdpPort: mappedCdp,
        headless: params.cfg.browser.headless,
        evaluateEnabled: params.evaluateEnabled ?? DEFAULT_BROWSER_EVALUATE_ENABLED,
      }),
      authToken: desiredAuthToken,
      authPassword: desiredAuthPassword,
      onEnsureAttachTarget,
      resolveSandboxNoVncToken: consumeNoVncObserverToken,
    })
  }

  val resolvedBridge = await ensureBridge()
  if (!shouldReuse || !authMatches) {
    BROWSER_BRIDGES.set(params.scopeKey, {
      bridge: resolvedBridge,
      containerName,
      authToken: desiredAuthToken,
      authPassword: desiredAuthPassword,
    })
  }

  await updateBrowserRegistry({
    containerName,
    sessionKey: params.scopeKey,
    createdAtMs: now,
    lastUsedAtMs: now,
    image: browserImage,
    configHash: hashMismatch && running ? (currentHash ?? Nothing?) : expectedHash,
    cdpPort: mappedCdp,
    noVncPort: mappedNoVnc ?? Nothing?,
  })

  val noVncUrl =
    mappedNoVnc && noVncEnabled
      ? (() => {
          val token = issueNoVncObserverToken({
            noVncPort: mappedNoVnc,
            password: noVncPassword,
          })
          return buildNoVncObserverTokenUrl(resolvedBridge.baseUrl, token)
        })()
      : Nothing?

  return {
    bridgeUrl: resolvedBridge.baseUrl,
    noVncUrl,
    containerName,
  }
}
