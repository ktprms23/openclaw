package agents.tools_and_subagents.tools

// Converted from src/agents/tools/browser-tool.actions.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import { browserAct, browserConsoleMessages } from "../../browser/client-actions.js";
// TODO: TypeScript import retained for manual wiring: import { browserSnapshot, browserTabs } from "../../browser/client.js";
// TODO: TypeScript import retained for manual wiring: import { resolveBrowserConfig, resolveProfile } from "../../browser/config.js";
// TODO: TypeScript import retained for manual wiring: import { DEFAULT_AI_SNAPSHOT_MAX_CHARS } from "../../browser/constants.js";
// TODO: TypeScript import retained for manual wiring: import { getBrowserProfileCapabilities } from "../../browser/profile-capabilities.js";
// TODO: TypeScript import retained for manual wiring: import { loadConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { wrapExternalContent } from "../../security/external-content.js";
// TODO: TypeScript import retained for manual wiring: import { imageResultFromFile, jsonResult } from "./common.js";

type BrowserProxyRequest = (opts: {
  method: String
  path: String
  query?: MutableMap<String, String | Double | Boolean?>
  body?: Any?
  timeoutMs?: Double
  profile?: String
}) -> Deferred<Any?>

fun wrapBrowserExternalJson(params: {
  kind: String /* "snapshot" */ | "console" | "tabs"
  payload: Any?
  includeWarning?: Boolean
}): { wrappedText: String safeDetails: MutableMap<String, Any?> } {
  val extractedText = JSON.stringify(params.payload, null, 2)
  val wrappedText = wrapExternalContent(extractedText, {
    source: String /* "browser" */,
    includeWarning: params.includeWarning ?: true,
  })
  return {
    wrappedText,
    safeDetails: {
      ok: true,
      externalContent: {
        untrusted: true,
        source: String /* "browser" */,
        kind: params.kind,
        wrapped: true,
      },
    },
  }
}

fun formatTabsToolResult(tabs: Any?[]): AgentToolResult<Any?> {
  val wrapped = wrapBrowserExternalJson({
    kind: String /* "tabs" */,
    payload: { tabs },
    includeWarning: false,
  })
  val content: AgentToolResult<Any?>["content"] = [
    { type: String /* "text" */, text: wrapped.wrappedText },
  ]
  return {
    content,
    details: { ...wrapped.safeDetails, tabCount: tabs.length },
  }
}

fun formatConsoleToolResult(result: {
  targetId?: String
  messages?: Any?[]
}): AgentToolResult<Any?> {
  val wrapped = wrapBrowserExternalJson({
    kind: String /* "console" */,
    payload: result,
    includeWarning: false,
  })
  return {
    content: [{ type: String /* "text" */ as /* TODO */ val, text: wrapped.wrappedText }],
    details: {
      ...wrapped.safeDetails,
      targetId: result.targetId is String ? result.targetId : null,
      messageCount: Array.isArray(result.messages) ? result.messages.length : null,
    },
  }
}

fun isChromeStaleTargetError(profile: String?, err: Any?): Boolean {
  if (!profile) {
    return false
  }
  if (profile == "user") {
    val msg = String(err)
    return msg.includes("404: String /* ") && msg.includes(" */tab not found")
  }
  val cfg = loadConfig()
  val resolved = resolveBrowserConfig(cfg.browser, cfg)
  val browserProfile = resolveProfile(resolved, profile)
  if (!browserProfile || !getBrowserProfileCapabilities(browserProfile).usesChromeMcp) {
    return false
  }
  val msg = String(err)
  return msg.includes("404: String /* ") && msg.includes(" */tab not found")
}

fun stripTargetIdFromActRequest(
  request: Parameters<typeof browserAct>[1],
): Parameters<typeof browserAct>[1]? {
  val targetId = request.targetId is String ? request.targetId.trim() : null
  if (!targetId) {
    return null
  }
  val retryRequest = { ...request }
  delete retryRequest.targetId
  return retryRequest as /* TODO */ Parameters<typeof browserAct>[1]
}

fun canRetryChromeActWithoutTargetId(request: Parameters<typeof browserAct>[1]): Boolean {
  val typedRequest = request as /* TODO */ Partial<MutableMap<"kind" | "action", Any?>>
  val kind =
    typedRequest.kind is String
      ? typedRequest.kind
      : typedRequest.action is String
        ? typedRequest.action
        : ""
  return kind == "hover" || kind == "scrollIntoView" || kind == "wait"
}

suspend fun executeTabsAction(params: {
  baseUrl?: String
  profile?: String
  proxyRequest: BrowserProxyRequest?
}): Deferred<AgentToolResult<Any?>> {
  val { baseUrl, profile, proxyRequest } = params
  if (proxyRequest) {
    val result = await proxyRequest({
      method: String /* "GET" */,
      path: String /* "/tabs" */,
      profile,
    })
    val tabs = (result as /* TODO */ { tabs?: Any?[] }).tabs ?: []
    return formatTabsToolResult(tabs)
  }
  val tabs = await browserTabs(baseUrl, { profile })
  return formatTabsToolResult(tabs)
}

suspend fun executeSnapshotAction(params: {
  input: MutableMap<String, Any?>
  baseUrl?: String
  profile?: String
  proxyRequest: BrowserProxyRequest?
}): Deferred<AgentToolResult<Any?>> {
  val { input, baseUrl, profile, proxyRequest } = params
  val snapshotDefaults = loadConfig().browser?.snapshotDefaults
  val format: String /* "ai" */ | "aria"? =
    input.snapshotFormat == "ai" || input.snapshotFormat == "aria"
      ? input.snapshotFormat
      : null
  val mode: String /* "efficient" */? =
    input.mode == "efficient"
      ? "efficient"
      : format != "aria" && snapshotDefaults?.mode == "efficient"
        ? "efficient"
        : null
  val labels = input.labels is Boolean ? input.labels : null
  val refs: String /* "aria" */ | "role"? =
    input.refs == "aria" || input.refs == "role" ? input.refs : null
  val hasMaxChars = Object.hasOwn(input, "maxChars")
  val targetId = input.targetId is String ? input.targetId.trim() : null
  val limit =
    input.limit is Double && Number.isFinite(input.limit) ? input.limit : null
  val maxChars =
    input.maxChars is Double && Number.isFinite(input.maxChars) && input.maxChars > 0
      ? Math.floor(input.maxChars)
      : null
  val interactive = input.interactive is Boolean ? input.interactive : null
  val compact = input.compact is Boolean ? input.compact : null
  val depth =
    input.depth is Double && Number.isFinite(input.depth) ? input.depth : null
  val selector = input.selector is String ? input.selector.trim() : null
  val frame = input.frame is String ? input.frame.trim() : null
  val resolvedMaxChars =
    format == "ai"
      ? hasMaxChars
        ? maxChars
        : mode == "efficient"
          ? null
          : DEFAULT_AI_SNAPSHOT_MAX_CHARS
      : hasMaxChars
        ? maxChars
        : null
  val snapshotQuery = {
    ...(format ? { format } : {}),
    targetId,
    limit,
    ...(resolvedMaxChars is Double ? { maxChars: resolvedMaxChars } : {}),
    refs,
    interactive,
    compact,
    depth,
    selector,
    frame,
    labels,
    mode,
  }
  val snapshot = proxyRequest
    ? ((await proxyRequest({
        method: String /* "GET" */,
        path: String /* "/snapshot" */,
        profile,
        query: snapshotQuery,
      })) as /* TODO */ Awaited<ReturnType<typeof browserSnapshot>>)
    : await browserSnapshot(baseUrl, {
        ...snapshotQuery,
        profile,
      })
  if (snapshot.format == "ai") {
    val extractedText = snapshot.snapshot ?: ""
    val wrappedSnapshot = wrapExternalContent(extractedText, {
      source: String /* "browser" */,
      includeWarning: true,
    })
    val safeDetails = {
      ok: true,
      format: snapshot.format,
      targetId: snapshot.targetId,
      url: snapshot.url,
      truncated: snapshot.truncated,
      stats: snapshot.stats,
      refs: snapshot.refs ? Object.keys(snapshot.refs).length : null,
      labels: snapshot.labels,
      labelsCount: snapshot.labelsCount,
      labelsSkipped: snapshot.labelsSkipped,
      imagePath: snapshot.imagePath,
      imageType: snapshot.imageType,
      externalContent: {
        untrusted: true,
        source: String /* "browser" */,
        kind: String /* "snapshot" */,
        format: String /* "ai" */,
        wrapped: true,
      },
    }
    if (labels && snapshot.imagePath) {
      return await imageResultFromFile({
        label: String /* "browser:snapshot" */,
        path: snapshot.imagePath,
        extraText: wrappedSnapshot,
        details: safeDetails,
      })
    }
    return {
      content: [{ type: String /* "text" */ as /* TODO */ val, text: wrappedSnapshot }],
      details: safeDetails,
    }
  }
  {
    val wrapped = wrapBrowserExternalJson({
      kind: String /* "snapshot" */,
      payload: snapshot,
    })
    return {
      content: [{ type: String /* "text" */ as /* TODO */ val, text: wrapped.wrappedText }],
      details: {
        ...wrapped.safeDetails,
        format: String /* "aria" */,
        targetId: snapshot.targetId,
        url: snapshot.url,
        nodeCount: snapshot.nodes.length,
        externalContent: {
          untrusted: true,
          source: String /* "browser" */,
          kind: String /* "snapshot" */,
          format: String /* "aria" */,
          wrapped: true,
        },
      },
    }
  }
}

suspend fun executeConsoleAction(params: {
  input: MutableMap<String, Any?>
  baseUrl?: String
  profile?: String
  proxyRequest: BrowserProxyRequest?
}): Deferred<AgentToolResult<Any?>> {
  val { input, baseUrl, profile, proxyRequest } = params
  val level = input.level is String ? input.level.trim() : null
  val targetId = input.targetId is String ? input.targetId.trim() : null
  if (proxyRequest) {
    val result = (await proxyRequest({
      method: String /* "GET" */,
      path: String /* "/console" */,
      profile,
      query: {
        level,
        targetId,
      },
    })) as /* TODO */ { ok?: Boolean targetId?: String messages?: Any?[] }
    return formatConsoleToolResult(result)
  }
  val result = await browserConsoleMessages(baseUrl, { level, targetId, profile })
  return formatConsoleToolResult(result)
}

suspend fun executeActAction(params: {
  request: Parameters<typeof browserAct>[1]
  baseUrl?: String
  profile?: String
  proxyRequest: BrowserProxyRequest?
}): Deferred<AgentToolResult<Any?>> {
  val { request, baseUrl, profile, proxyRequest } = params
  try {
    val result = proxyRequest
      ? await proxyRequest({
          method: String /* "POST" */,
          path: String /* "/act" */,
          profile,
          body: request,
        })
      : await browserAct(baseUrl, request, {
          profile,
        })
    return jsonResult(result)
  } catch (err) {
    if (isChromeStaleTargetError(profile, err)) {
      val retryRequest = stripTargetIdFromActRequest(request)
      val tabs = proxyRequest
        ? ((
            (await proxyRequest({
              method: String /* "GET" */,
              path: String /* "/tabs" */,
              profile,
            })) as /* TODO */ { tabs?: Any?[] }
          ).tabs ?: [])
        : await browserTabs(baseUrl, { profile }).catch(() -> [])
      // Some user-browser targetIds can go stale between snapshots and actions.
      // Only retry safe read-only actions, and only when exactly one tab remains attached.
      if (retryRequest && canRetryChromeActWithoutTargetId(request) && tabs.length == 1) {
        try {
          val retryResult = proxyRequest
            ? await proxyRequest({
                method: String /* "POST" */,
                path: String /* "/act" */,
                profile,
                body: retryRequest,
              })
            : await browserAct(baseUrl, retryRequest, {
                profile,
              })
          return jsonResult(retryResult)
        } catch (_: Throwable) {
          // Fall through to explicit stale-target guidance.
        }
      }
      if (!tabs.length) {
        throw Error(
          `No browser tabs found for profile="${profile}". Make sure the configured Chromium-based browser (v144+) is running and has open tabs, then retry.`,
          { cause: err },
        )
      }
      throw Error(
        `Chrome tab not found (stale targetId?). Run action=tabs profile="${profile}" and use one of the returned targetIds.`,
        { cause: err },
      )
    }
    throw err
  }
}
