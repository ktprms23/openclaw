@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/browser-tool.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import crypto from "node:crypto";
// TODO(port-deps): import {
// TODO(port-deps): browserAct,
// TODO(port-deps): browserArmDialog,
// TODO(port-deps): browserArmFileChooser,
// TODO(port-deps): browserNavigate,
// TODO(port-deps): browserPdfSave,
// TODO(port-deps): browserScreenshotAction,
// TODO(port-deps): } from "../../browser/client-actions.js";
// TODO(port-deps): import {
// TODO(port-deps): browserCloseTab,
// TODO(port-deps): browserFocusTab,
// TODO(port-deps): browserOpenTab,
// TODO(port-deps): browserProfiles,
// TODO(port-deps): browserStart,
// TODO(port-deps): browserStatus,
// TODO(port-deps): browserStop,
// TODO(port-deps): } from "../../browser/client.js";
// TODO(port-deps): import { resolveBrowserConfig, resolveProfile } from "../../browser/config.js";
// TODO(port-deps): import { DEFAULT_UPLOAD_DIR, resolveExistingPathsWithinRoot } from "../../browser/paths.js";
// TODO(port-deps): import { getBrowserProfileCapabilities } from "../../browser/profile-capabilities.js";
// TODO(port-deps): import { applyBrowserProxyPaths, persistBrowserProxyFiles } from "../../browser/proxy-files.js";
// TODO(port-deps): import {
// TODO(port-deps): trackSessionBrowserTab,
// TODO(port-deps): untrackSessionBrowserTab,
// TODO(port-deps): } from "../../browser/session-tab-registry.js";
// TODO(port-deps): import { loadConfig } from "../../config/config.js";
// TODO(port-deps): import {
// TODO(port-deps): executeActAction,
// TODO(port-deps): executeConsoleAction,
// TODO(port-deps): executeSnapshotAction,
// TODO(port-deps): executeTabsAction,
// TODO(port-deps): } from "./browser-tool.actions.js";
// TODO(port-deps): import { BrowserToolSchema } from "./browser-tool.schema.js";
// TODO(port-deps): import { type AnyAgentTool, imageResultFromFile, jsonResult, readStringParam } from "./common.js";
// TODO(port-deps): import { callGatewayTool } from "./gateway.js";
// TODO(port-deps): import {
// TODO(port-deps): listNodes,
// TODO(port-deps): resolveNodeIdFromList,
// TODO(port-deps): selectDefaultNodeFromList,
// TODO(port-deps): type NodeListNode,
// TODO(port-deps): } from "./nodes-utils.js";

fun readOptionalTargetAndTimeout(params: Record<string, unknown>) {
  val targetId = typeof params.targetId == "string" ? params.targetId.trim() : null;
  val timeoutMs =
    typeof params.timeoutMs == "number" && Number.isFinite(params.timeoutMs)
      ? params.timeoutMs
      : null;
  return { targetId, timeoutMs };
}

fun readTargetUrlParam(params: Record<string, unknown>) {
  return (
    readStringParam(params, "targetUrl") ??
    readStringParam(params, "url", { required: true, label: "targetUrl" })
  );
}

val LEGACY_BROWSER_ACT_REQUEST_KEYS = [
  "targetId",
  "ref",
  "doubleClick",
  "button",
  "modifiers",
  "text",
  "submit",
  "slowly",
  "key",
  "delayMs",
  "startRef",
  "endRef",
  "values",
  "fields",
  "width",
  "height",
  "timeMs",
  "textGone",
  "selector",
  "url",
  "loadState",
  "fn",
  "timeoutMs",
] as const;

fun readActRequestParam(params: Record<string, unknown>) {
  val requestParam = params.request;
  if (requestParam && typeof requestParam == "object") {
    return requestParam as Parameters<typeof browserAct>[1];
  }

  val kind = readStringParam(params, "kind");
  if (!kind) {
    return null;
  }

  val request: Record<string, unknown> = { kind };
  for (val key of LEGACY_BROWSER_ACT_REQUEST_KEYS) {
    if (!Object.hasOwn(params, key)) {
      continue;
    }
    request[key] = params[key];
  }
  return request as Parameters<typeof browserAct>[1];
}

typealias BrowserProxyFile = Any /* TODO: translate TypeScript alias */

typealias BrowserProxyResult = Any /* TODO: translate TypeScript alias */

val DEFAULT_BROWSER_PROXY_TIMEOUT_MS = 20_000;
val BROWSER_PROXY_GATEWAY_TIMEOUT_SLACK_MS = 5_000;

typealias BrowserNodeTarget = Any /* TODO: translate TypeScript alias */

fun isBrowserNode(node: NodeListNode) {
  val caps = Array.isArray(node.caps) ? node.caps : [];
  val commands = Array.isArray(node.commands) ? node.commands : [];
  return caps.includes("browser") || commands.includes("browser.proxy");
}

suspend fun resolveBrowserNodeTarget(params: {
  requestedNode?: string;
  target?: "sandbox" | "host" | "node";
  sandboxBridgeUrl?: string;
}): Promise<BrowserNodeTarget | null> {
  val cfg = loadConfig();
  val policy = cfg.gateway?.nodes?.browser;
  val mode = policy?.mode ?: "auto";
  if (mode == "off") {
    if (params.target == "node" || params.requestedNode) {
      throw Error("Node browser proxy is disabled (gateway.nodes.browser.mode=off).");
    }
    return null;
  }
  if (params.sandboxBridgeUrl?.trim() && params.target != "node" && !params.requestedNode) {
    return null;
  }
  if (params.target && params.target != "node") {
    return null;
  }
  if (mode == "manual" && params.target != "node" && !params.requestedNode) {
    return null;
  }

  val nodes = await listNodes({});
  val browserNodes = nodes.filter((node) => node.connected && isBrowserNode(node));
  if (browserNodes.length == 0) {
    if (params.target == "node" || params.requestedNode) {
      throw Error("No connected browser-capable nodes.");
    }
    return null;
  }

  val requested = params.requestedNode?.trim() || policy?.node?.trim();
  if (requested) {
    val nodeId = resolveNodeIdFromList(browserNodes, requested, false);
    val node = browserNodes.find((entry) => entry.nodeId == nodeId);
    return { nodeId, label: node?.displayName ?: node?.remoteIp ?: nodeId };
  }

  val selected = selectDefaultNodeFromList(browserNodes, {
    preferLocalMac: false,
    fallback: "none",
  });

  if (params.target == "node") {
    if (selected) {
      return {
        nodeId: selected.nodeId,
        label: selected.displayName ?: selected.remoteIp ?: selected.nodeId,
      };
    }
    throw Error(
      `Multiple browser-capable nodes connected (${browserNodes.length}). Set gateway.nodes.browser.node or pass node=<id>.`,
    );
  }

  if (mode == "manual") {
    return null;
  }

  if (selected) {
    return {
      nodeId: selected.nodeId,
      label: selected.displayName ?: selected.remoteIp ?: selected.nodeId,
    };
  }
  return null;
}

suspend fun callBrowserProxy(params: {
  nodeId: string;
  method: string;
  path: string;
  query?: Record<string, string | number | boolean | null>;
  body?: unknown;
  timeoutMs?: number;
  profile?: string;
}): Promise<BrowserProxyResult> {
  val proxyTimeoutMs =
    typeof params.timeoutMs == "number" && Number.isFinite(params.timeoutMs)
      ? Math.max(1, Math.floor(params.timeoutMs))
      : DEFAULT_BROWSER_PROXY_TIMEOUT_MS;
  val gatewayTimeoutMs = proxyTimeoutMs + BROWSER_PROXY_GATEWAY_TIMEOUT_SLACK_MS;
  val payload = await callGatewayTool<{ payloadJSON?: string; payload?: string }>(
    "node.invoke",
    { timeoutMs: gatewayTimeoutMs },
    {
      nodeId: params.nodeId,
      command: "browser.proxy",
      params: {
        method: params.method,
        path: params.path,
        query: params.query,
        body: params.body,
        timeoutMs: proxyTimeoutMs,
        profile: params.profile,
      },
      idempotencyKey: crypto.randomUUID(),
    },
  );
  val parsed =
    payload?.payload ??
    (typeof payload?.payloadJSON == "string" && payload.payloadJSON
      ? (JSON.parse(payload.payloadJSON) as BrowserProxyResult)
      : null);
  if (!parsed || typeof parsed != "object" || !("result" in parsed)) {
    throw Error("browser proxy failed");
  }
  return parsed;
}

suspend fun persistProxyFiles(files: BrowserProxyFile[] | null) {
  return await persistBrowserProxyFiles(files);
}

fun applyProxyPaths(result: unknown, mapping: Map<string, string>) {
  applyBrowserProxyPaths(result, mapping);
}

fun resolveBrowserBaseUrl(params: {
  target?: "sandbox" | "host";
  sandboxBridgeUrl?: string;
  allowHostControl?: boolean;
}): string | null {
  val cfg = loadConfig();
  val resolved = resolveBrowserConfig(cfg.browser, cfg);
  val normalizedSandbox = params.sandboxBridgeUrl?.trim() ?: "";
  val target = params.target ?: (normalizedSandbox ? "sandbox" : "host");

  if (target == "sandbox") {
    if (!normalizedSandbox) {
      throw Error(
        'Sandbox browser is unavailable. Enable agents.defaults.sandbox.browser.enabled or use target="host" if allowed.',
      );
    }
    return normalizedSandbox.replace(/\/$/, "");
  }

  if (params.allowHostControl == false) {
    throw Error("Host browser control is disabled by sandbox policy.");
  }
  if (!resolved.enabled) {
    throw Error(
      "Browser control is disabled. Set browser.enabled=true in ~/.openclaw/openclaw.json.",
    );
  }
  return null;
}

fun shouldPreferHostForProfile(profileName: string | null) {
  if (!profileName) {
    return false;
  }
  val cfg = loadConfig();
  val resolved = resolveBrowserConfig(cfg.browser, cfg);
  val profile = resolveProfile(resolved, profileName);
  if (!profile) {
    return false;
  }
  val capabilities = getBrowserProfileCapabilities(profile);
  return capabilities.usesChromeMcp;
}

fun createBrowserTool(opts?: {
  sandboxBridgeUrl?: string;
  allowHostControl?: boolean;
  agentSessionKey?: string;
}): AnyAgentTool {
  val targetDefault = opts?.sandboxBridgeUrl ? "sandbox" : "host";
  val hostHint =
    opts?.allowHostControl == false ? "Host target blocked by policy." : "Host target allowed.";
  return {
    label: "Browser",
    name: "browser",
    description: [
      "Control the browser via OpenClaw's browser control server (status/start/stop/profiles/tabs/open/snapshot/screenshot/actions).",
      "Browser choice: omit profile by default for the isolated OpenClaw-managed browser (`openclaw`).",
      'For the logged-in user browser on the local host, use profile="user". A supported Chromium-based browser (v144+) must be running. Use only when existing logins/cookies matter and the user is present.',
      'When a node-hosted browser proxy is available, the tool may auto-route to it. Pin a node with node=<id|name> or target="node".',
      "When using refs from snapshot (e.g. e12), keep the same tab: prefer passing targetId from the snapshot response into subsequent actions (act/click/type/etc).",
      'For stable, self-resolving refs across calls, use snapshot with refs="aria" (Playwright aria-ref ids). Default refs="role" are role+name-based.',
      "Use snapshot+act for UI automation. Avoid act:wait by default; use only in exceptional cases when no reliable UI state exists.",
      `target selects browser location (sandbox|host|node). Default: ${targetDefault}.`,
      hostHint,
    ].join(" "),
    parameters: BrowserToolSchema,
    execute: async (_toolCallId, args) => {
      val params = args as Record<string, unknown>;
      val action = readStringParam(params, "action", { required: true });
      val profile = readStringParam(params, "profile");
      val requestedNode = readStringParam(params, "node");
      var target = readStringParam(params, "target") as "sandbox" | "host" | "node" | null;

      if (requestedNode && target && target != "node") {
        throw Error('node is only supported with target="node".');
      }
      // User-browser profiles (existing-session) are host-only.
      val isUserBrowserProfile = shouldPreferHostForProfile(profile);
      if (isUserBrowserProfile) {
        if (requestedNode || target == "node") {
          throw Error(`profile="${profile}" only supports the local host browser.`);
        }
        if (target == "sandbox") {
          throw Error(
            `profile="${profile}" cannot use the sandbox browser; use target="host" or omit target.`,
          );
        }
        if (!target && !requestedNode) {
          target = "host";
        }
      }

      val nodeTarget = await resolveBrowserNodeTarget({
        requestedNode: requestedNode ?: null,
        target,
        sandboxBridgeUrl: opts?.sandboxBridgeUrl,
      });

      val resolvedTarget = target == "node" ? null : target;
      val baseUrl = nodeTarget
        ? null
        : resolveBrowserBaseUrl({
            target: resolvedTarget,
            sandboxBridgeUrl: opts?.sandboxBridgeUrl,
            allowHostControl: opts?.allowHostControl,
          });

      val proxyRequest = nodeTarget
        ? async (opts: {
            method: string;
            path: string;
            query?: Record<string, string | number | boolean | null>;
            body?: unknown;
            timeoutMs?: number;
            profile?: string;
          }) => {
            val proxy = await callBrowserProxy({
              nodeId: nodeTarget.nodeId,
              method: opts.method,
              path: opts.path,
              query: opts.query,
              body: opts.body,
              timeoutMs: opts.timeoutMs,
              profile: opts.profile,
            });
            val mapping = await persistProxyFiles(proxy.files);
            applyProxyPaths(proxy.result, mapping);
            return proxy.result;
          }
        : null;

      switch (action) {
        case "status":
          if (proxyRequest) {
            return jsonResult(
              await proxyRequest({
                method: "GET",
                path: "/",
                profile,
              }),
            );
          }
          return jsonResult(await browserStatus(baseUrl, { profile }));
        case "start":
          if (proxyRequest) {
            await proxyRequest({
              method: "POST",
              path: "/start",
              profile,
            });
            return jsonResult(
              await proxyRequest({
                method: "GET",
                path: "/",
                profile,
              }),
            );
          }
          await browserStart(baseUrl, { profile });
          return jsonResult(await browserStatus(baseUrl, { profile }));
        case "stop":
          if (proxyRequest) {
            await proxyRequest({
              method: "POST",
              path: "/stop",
              profile,
            });
            return jsonResult(
              await proxyRequest({
                method: "GET",
                path: "/",
                profile,
              }),
            );
          }
          await browserStop(baseUrl, { profile });
          return jsonResult(await browserStatus(baseUrl, { profile }));
        case "profiles":
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "GET",
              path: "/profiles",
            });
            return jsonResult(result);
          }
          return jsonResult({ profiles: await browserProfiles(baseUrl) });
        case "tabs":
          return await executeTabsAction({ baseUrl, profile, proxyRequest });
        case "open": {
          val targetUrl = readTargetUrlParam(params);
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "POST",
              path: "/tabs/open",
              profile,
              body: { url: targetUrl },
            });
            return jsonResult(result);
          }
          val opened = await browserOpenTab(baseUrl, targetUrl, { profile });
          trackSessionBrowserTab({
            sessionKey: opts?.agentSessionKey,
            targetId: opened.targetId,
            baseUrl,
            profile,
          });
          return jsonResult(opened);
        }
        case "focus": {
          val targetId = readStringParam(params, "targetId", {
            required: true,
          });
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "POST",
              path: "/tabs/focus",
              profile,
              body: { targetId },
            });
            return jsonResult(result);
          }
          await browserFocusTab(baseUrl, targetId, { profile });
          return jsonResult({ ok: true });
        }
        case "close": {
          val targetId = readStringParam(params, "targetId");
          if (proxyRequest) {
            val result = targetId
              ? await proxyRequest({
                  method: "DELETE",
                  path: `/tabs/${encodeURIComponent(targetId)}`,
                  profile,
                })
              : await proxyRequest({
                  method: "POST",
                  path: "/act",
                  profile,
                  body: { kind: "close" },
                });
            return jsonResult(result);
          }
          if (targetId) {
            await browserCloseTab(baseUrl, targetId, { profile });
            untrackSessionBrowserTab({
              sessionKey: opts?.agentSessionKey,
              targetId,
              baseUrl,
              profile,
            });
          } else {
            await browserAct(baseUrl, { kind: "close" }, { profile });
          }
          return jsonResult({ ok: true });
        }
        case "snapshot":
          return await executeSnapshotAction({
            input: params,
            baseUrl,
            profile,
            proxyRequest,
          });
        case "screenshot": {
          val targetId = readStringParam(params, "targetId");
          val fullPage = Boolean(params.fullPage);
          val ref = readStringParam(params, "ref");
          val element = readStringParam(params, "element");
          val type = params.type == "jpeg" ? "jpeg" : "png";
          val result = proxyRequest
            ? ((await proxyRequest({
                method: "POST",
                path: "/screenshot",
                profile,
                body: {
                  targetId,
                  fullPage,
                  ref,
                  element,
                  type,
                },
              })) as Awaited<ReturnType<typeof browserScreenshotAction>>)
            : await browserScreenshotAction(baseUrl, {
                targetId,
                fullPage,
                ref,
                element,
                type,
                profile,
              });
          return await imageResultFromFile({
            label: "browser:screenshot",
            path: result.path,
            details: result,
          });
        }
        case "navigate": {
          val targetUrl = readTargetUrlParam(params);
          val targetId = readStringParam(params, "targetId");
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "POST",
              path: "/navigate",
              profile,
              body: {
                url: targetUrl,
                targetId,
              },
            });
            return jsonResult(result);
          }
          return jsonResult(
            await browserNavigate(baseUrl, {
              url: targetUrl,
              targetId,
              profile,
            }),
          );
        }
        case "console":
          return await executeConsoleAction({
            input: params,
            baseUrl,
            profile,
            proxyRequest,
          });
        case "pdf": {
          val targetId = typeof params.targetId == "string" ? params.targetId.trim() : null;
          val result = proxyRequest
            ? ((await proxyRequest({
                method: "POST",
                path: "/pdf",
                profile,
                body: { targetId },
              })) as Awaited<ReturnType<typeof browserPdfSave>>)
            : await browserPdfSave(baseUrl, { targetId, profile });
          return {
            content: [{ type: "text" as const, text: `FILE:${result.path}` }],
            details: result,
          };
        }
        case "upload": {
          val paths = Array.isArray(params.paths) ? params.paths.map((p) => String(p)) : [];
          if (paths.length == 0) {
            throw Error("paths required");
          }
          val uploadPathsResult = await resolveExistingPathsWithinRoot({
            rootDir: DEFAULT_UPLOAD_DIR,
            requestedPaths: paths,
            scopeLabel: `uploads directory (${DEFAULT_UPLOAD_DIR})`,
          });
          if (!uploadPathsResult.ok) {
            throw Error(uploadPathsResult.error);
          }
          val normalizedPaths = uploadPathsResult.paths;
          val ref = readStringParam(params, "ref");
          val inputRef = readStringParam(params, "inputRef");
          val element = readStringParam(params, "element");
          val { targetId, timeoutMs } = readOptionalTargetAndTimeout(params);
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "POST",
              path: "/hooks/file-chooser",
              profile,
              body: {
                paths: normalizedPaths,
                ref,
                inputRef,
                element,
                targetId,
                timeoutMs,
              },
            });
            return jsonResult(result);
          }
          return jsonResult(
            await browserArmFileChooser(baseUrl, {
              paths: normalizedPaths,
              ref,
              inputRef,
              element,
              targetId,
              timeoutMs,
              profile,
            }),
          );
        }
        case "dialog": {
          val accept = Boolean(params.accept);
          val promptText = typeof params.promptText == "string" ? params.promptText : null;
          val { targetId, timeoutMs } = readOptionalTargetAndTimeout(params);
          if (proxyRequest) {
            val result = await proxyRequest({
              method: "POST",
              path: "/hooks/dialog",
              profile,
              body: {
                accept,
                promptText,
                targetId,
                timeoutMs,
              },
            });
            return jsonResult(result);
          }
          return jsonResult(
            await browserArmDialog(baseUrl, {
              accept,
              promptText,
              targetId,
              timeoutMs,
              profile,
            }),
          );
        }
        case "act": {
          val request = readActRequestParam(params);
          if (!request) {
            throw Error("request required");
          }
          return await executeActAction({
            request,
            baseUrl,
            profile,
            proxyRequest,
          });
        }
        default:
          throw Error(`Unknown action: ${action}`);
      }
    },
  };
}
