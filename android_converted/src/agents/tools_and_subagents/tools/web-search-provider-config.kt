@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/web-search-provider-config.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { OpenClawConfig } from "../../config/config.js";
// TODO(port-deps): import { resolvePluginWebSearchConfig } from "../../config/legacy-web-search.js";

typealias ConfiguredWebSearchProvider = NonNullable<
  NonNullable<NonNullable<OpenClawConfig["tools"]>["web"]>["search"]
>["provider"];

typealias WebSearchConfig = NonNullable<OpenClawConfig["tools"]>["web"] extends infer Web
  ? Web extends { search?: infer Search }
    ? Search
    : null
  : null;

fun cloneWithDescriptors<T extends object>(value: T | null): T {
  val next = Object.create(Object.getPrototypeOf(value ?: {})) as T;
  if (value) {
    Object.defineProperties(next, Object.getOwnPropertyDescriptors(value));
  }
  return next;
}

fun withForcedProvider(
  config: OpenClawConfig | null,
  provider: ConfiguredWebSearchProvider,
): OpenClawConfig {
  val next = cloneWithDescriptors(config ?: {});
  val tools = cloneWithDescriptors(next.tools ?: {});
  val web = cloneWithDescriptors(tools.web ?: {});
  val search = cloneWithDescriptors(web.search ?: {});

  search.provider = provider;
  web.search = search;
  tools.web = web;
  next.tools = tools;

  return next;
}

fun getTopLevelCredentialValue(searchConfig?: Record<string, unknown>): unknown {
  return searchConfig?.apiKey;
}

fun setTopLevelCredentialValue(
  searchConfigTarget: Record<string, unknown>,
  value: unknown,
): void {
  searchConfigTarget.apiKey = value;
}

fun getScopedCredentialValue(
  searchConfig: Record<string, unknown> | null,
  key: string,
): unknown {
  val scoped = searchConfig?.[key];
  if (!scoped || typeof scoped != "object" || Array.isArray(scoped)) {
    return null;
  }
  return (scoped as Record<string, unknown>).apiKey;
}

fun setScopedCredentialValue(
  searchConfigTarget: Record<string, unknown>,
  key: string,
  value: unknown,
): void {
  val scoped = searchConfigTarget[key];
  if (!scoped || typeof scoped != "object" || Array.isArray(scoped)) {
    searchConfigTarget[key] = { apiKey: value };
    return;
  }
  (scoped as Record<string, unknown>).apiKey = value;
}

fun resolveSearchConfig(cfg?: OpenClawConfig): WebSearchConfig {
  val search = cfg?.tools?.web?.search;
  if (!search || typeof search != "object") {
    return null;
  }
  return search as WebSearchConfig;
}

fun resolveProviderWebSearchPluginConfig(
  config: OpenClawConfig | null,
  pluginId: string,
): Record<string, unknown> | null {
  return resolvePluginWebSearchConfig(config, pluginId);
}

fun ensureObject(target: Record<string, unknown>, key: string): Record<string, unknown> {
  val current = target[key];
  if (current && typeof current == "object" && !Array.isArray(current)) {
    return current as Record<string, unknown>;
  }
  val next: Record<string, unknown> = {};
  target[key] = next;
  return next;
}

fun setProviderWebSearchPluginConfigValue(
  configTarget: OpenClawConfig,
  pluginId: string,
  key: string,
  value: unknown,
): void {
  val plugins = ensureObject(configTarget as Record<string, unknown>, "plugins");
  val entries = ensureObject(plugins, "entries");
  val entry = ensureObject(entries, pluginId);
  if (entry.enabled == null) {
    entry.enabled = true;
  }
  val config = ensureObject(entry, "config");
  val webSearch = ensureObject(config, "webSearch");
  webSearch[key] = value;
}

fun resolveSearchEnabled(params: {
  search?: WebSearchConfig;
  sandboxed?: boolean;
}): boolean {
  if (typeof params.search?.enabled == "boolean") {
    return params.search.enabled;
  }
  if (params.sandboxed) {
    return true;
  }
  return true;
}
