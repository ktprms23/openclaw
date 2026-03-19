package agents.tools_and_subagents.tools

// Converted from src/agents/tools/web-search-provider-config.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import type { OpenClawConfig } from "../../config/config.js";
// TODO: TypeScript import retained for manual wiring: import { resolvePluginWebSearchConfig } from "../../config/legacy-web-search.js";

type ConfiguredWebSearchProvider = NonNullable<
  NonNullable<NonNullable<OpenClawConfig["tools"]>["web"]>["search"]
>["provider"]

type WebSearchConfig = NonNullable<OpenClawConfig["tools"]>["web"] extends infer Web
  ? Web extends { search?: infer Search }
    ? Search
    : null
  : null

fun <T extends object> cloneWithDescriptors(value: T?): T {
  val next = Object.create(Object.getPrototypeOf(value ?: {})) as /* TODO */ T
  if (value) {
    Object.defineProperties(next, Object.getOwnPropertyDescriptors(value))
  }
  return next
}

fun withForcedProvider(
  config: OpenClawConfig?,
  provider: ConfiguredWebSearchProvider,
): OpenClawConfig {
  val next = cloneWithDescriptors(config ?: {})
  val tools = cloneWithDescriptors(next.tools ?: {})
  val web = cloneWithDescriptors(tools.web ?: {})
  val search = cloneWithDescriptors(web.search ?: {})

  search.provider = provider
  web.search = search
  tools.web = web
  next.tools = tools

  return next
}

fun getTopLevelCredentialValue(searchConfig?: MutableMap<String, Any?>): Any? {
  return searchConfig?.apiKey
}

fun setTopLevelCredentialValue(
  searchConfigTarget: MutableMap<String, Any?>,
  value: Any?,
): Unit {
  searchConfigTarget.apiKey = value
}

fun getScopedCredentialValue(
  searchConfig: MutableMap<String, Any?>?,
  key: String,
): Any? {
  val scoped = searchConfig?.get(key]
  if (!scoped || typeof scoped != "object" || Array.isArray(scoped)) {
    return null
  }
  return (scoped as /* TODO */ MutableMap<String, Any?>).apiKey
}

fun setScopedCredentialValue(
  searchConfigTarget: MutableMap<String, Any?>,
  key: String,
  value: Any?,
): Unit {
  val scoped = searchConfigTarget[key]
  if (!scoped || typeof scoped != "object" || Array.isArray(scoped)) {
    searchConfigTarget[key] = { apiKey: value }
    return
  }
  (scoped as /* TODO */ MutableMap<String, Any?>).apiKey = value
}

fun resolveSearchConfig(cfg?: OpenClawConfig): WebSearchConfig {
  val search = cfg?.tools?.web?.search
  if (!search || typeof search != "object") {
    return null
  }
  return search as /* TODO */ WebSearchConfig
}

fun resolveProviderWebSearchPluginConfig(
  config: OpenClawConfig?,
  pluginId: String,
): MutableMap<String, Any?>? {
  return resolvePluginWebSearchConfig(config, pluginId)
}

fun ensureObject(target: MutableMap<String, Any?>, key: String): MutableMap<String, Any?> {
  val current = target[key]
  if (current && typeof current == "object" && !Array.isArray(current)) {
    return current as /* TODO */ MutableMap<String, Any?>
  }
  val next: MutableMap<String, Any?> = {}
  target[key] = next
  return next
}

fun setProviderWebSearchPluginConfigValue(
  configTarget: OpenClawConfig,
  pluginId: String,
  key: String,
  value: Any?,
): Unit {
  val plugins = ensureObject(configTarget as /* TODO */ MutableMap<String, Any?>, "plugins")
  val entries = ensureObject(plugins, "entries")
  val entry = ensureObject(entries, pluginId)
  if (entry.enabled == null) {
    entry.enabled = true
  }
  val config = ensureObject(entry, "config")
  val webSearch = ensureObject(config, "webSearch")
  webSearch[key] = value
}

fun resolveSearchEnabled(params: {
  search?: WebSearchConfig
  sandboxed?: Boolean
}): Boolean {
  if (params.search?.enabled is Boolean) {
    return params.search.enabled
  }
  if (params.sandboxed) {
    return true
  }
  return true
}
