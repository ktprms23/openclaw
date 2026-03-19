package agents.platform_support.sandbox

// Source: src/agents/sandbox/registry.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import fs from "node:fs/promises";
// TODO(openclaw-kotlin-port): import { writeJsonAtomic } from "../../infra/json-files.js";
// TODO(openclaw-kotlin-port): import { acquireSessionWriteLock } from "../session-write-lock.js";
// TODO(openclaw-kotlin-port): import { SANDBOX_BROWSER_REGISTRY_PATH, SANDBOX_REGISTRY_PATH } from "./constants.js";

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxRegistryEntry.
typealias SandboxRegistryEntry = Any?
/*
export type SandboxRegistryEntry = {
  containerName: string;
  backendId?: string;
  runtimeLabel?: string;
  sessionKey: string;
  createdAtMs: number;
  lastUsedAtMs: number;
  image: string;
  configLabelKind?: string;
  configHash?: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxRegistry.
typealias SandboxRegistry = Any?
/*
type SandboxRegistry = {
  entries: SandboxRegistryEntry[];
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBrowserRegistryEntry.
typealias SandboxBrowserRegistryEntry = Any?
/*
export type SandboxBrowserRegistryEntry = {
  containerName: string;
  sessionKey: string;
  createdAtMs: number;
  lastUsedAtMs: number;
  image: string;
  configHash?: string;
  cdpPort: number;
  noVncPort?: number;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for SandboxBrowserRegistry.
typealias SandboxBrowserRegistry = Any?
/*
type SandboxBrowserRegistry = {
  entries: SandboxBrowserRegistryEntry[];
};
*/

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for RegistryReadMode.
typealias RegistryReadMode = "strict" | "fallback"

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RegistryEntry.
typealias RegistryEntry = Any?
/*
type RegistryEntry = {
  containerName: string;
};
*/

// TODO(openclaw-kotlin-port): Original TypeScript object type preserved below for RegistryFile.
typealias RegistryFile<T extends RegistryEntry> = Any?
/*
type RegistryFile<T extends RegistryEntry> = {
  entries: T[];
};
*/

typealias UpsertEntry = RegistryEntry & {
  backendId?: String
  runtimeLabel?: String
  createdAtMs: Double
  image: String
  configLabelKind?: String
  configHash?: String
}

fun isRecord(value: Any?): value is Map<String, Any?> {
  return Boolean(value) && typeof value === "object"
}

fun isRegistryEntry(value: Any?): value is RegistryEntry {
  return isRecord(value) && typeof value.containerName === "String"
}

fun normalizeSandboxRegistryEntry(entry: SandboxRegistryEntry): SandboxRegistryEntry {
  return {
    ...entry,
    backendId: entry.backendId?.trim() || "docker",
    runtimeLabel: entry.runtimeLabel?.trim() || entry.containerName,
    configLabelKind: entry.configLabelKind?.trim() || "Image",
  }
}

fun isRegistryFile<T extends RegistryEntry>(value: Any?): value is RegistryFile<T> {
  if (!isRecord(value)) {
    return false
  }

  val maybeEntries = value.entries
  return Array.isArray(maybeEntries) && maybeEntries.every(isRegistryEntry)
}

suspend fun withRegistryLock<T>(registryPath: String, fn: () => Promise<T>): Promise<T> {
  val lock = await acquireSessionWriteLock({ sessionFile: registryPath, allowReentrant: false })
  try {
    return await fn()
  } finally {
    await lock.release()
  }
}

suspend fun readRegistryFromFile<T extends RegistryEntry>(
  registryPath: String,
  mode: RegistryReadMode,
): Promise<RegistryFile<T>> {
  try {
    val raw = await fs.readFile(registryPath, "utf-8")
    val parsed = JSON.parse(raw) as Any?
    if (isRegistryFile<T>(parsed)) {
      return parsed
    }
    if (mode === "fallback") {
      return { entries: [] }
    }
    throw error(`Invalid sandbox registry format: ${registryPath}`)
  } catch (error) {
    val code = (error as { code?: String } | Nothing?)?.code
    if (code === "ENOENT") {
      return { entries: [] }
    }
    if (mode === "fallback") {
      return { entries: [] }
    }
    if (error instanceof Error) {
      throw error
    }
    throw error(`Failed to read sandbox registry file: ${registryPath}`, { cause: error })
  }
}

suspend fun writeRegistryFile<T extends RegistryEntry>(
  registryPath: String,
  registry: RegistryFile<T>,
): Promise<Unit> {
  await writeJsonAtomic(registryPath, registry, { trailingNewline: true })
}

suspend fun readRegistry(): Promise<SandboxRegistry> {
  val registry = await readRegistryFromFile<SandboxRegistryEntry>(
    SANDBOX_REGISTRY_PATH,
    "fallback",
  )
  return {
    entries: registry.entries.map((entry) => normalizeSandboxRegistryEntry(entry)),
  }
}

fun upsertEntry<T extends UpsertEntry>(entries: T[], entry: T): T[] {
  val existing = entries.find((item) => item.containerName === entry.containerName)
  val next = entries.filter((item) => item.containerName !== entry.containerName)
  next.push({
    ...entry,
    backendId: entry.backendId ?? existing?.backendId,
    runtimeLabel: entry.runtimeLabel ?? existing?.runtimeLabel,
    createdAtMs: existing?.createdAtMs ?? entry.createdAtMs,
    image: existing?.image ?? entry.image,
    configLabelKind: entry.configLabelKind ?? existing?.configLabelKind,
    configHash: entry.configHash ?? existing?.configHash,
  })
  return next
}

fun removeEntry<T extends RegistryEntry>(entries: T[], containerName: String): T[] {
  return entries.filter((entry) => entry.containerName !== containerName)
}

suspend fun withRegistryMutation<T extends RegistryEntry>(
  registryPath: String,
  mutate: (entries: T[]) => T[] | Nothing?,
): Promise<Unit> {
  await withRegistryLock(registryPath, async () => {
    val registry = await readRegistryFromFile<T>(registryPath, "strict")
    val next = mutate(registry.entries)
    if (next === Nothing?) {
      return
    }
    await writeRegistryFile(registryPath, { entries: next })
  })
}

suspend fun updateRegistry(entry: SandboxRegistryEntry) {
  await withRegistryMutation<SandboxRegistryEntry>(SANDBOX_REGISTRY_PATH, (entries) =>
    upsertEntry(entries, entry),
  )
}

suspend fun removeRegistryEntry(containerName: String) {
  await withRegistryMutation<SandboxRegistryEntry>(SANDBOX_REGISTRY_PATH, (entries) => {
    val next = removeEntry(entries, containerName)
    if (next.length === entries.length) {
      return Nothing?
    }
    return next
  })
}

suspend fun readBrowserRegistry(): Promise<SandboxBrowserRegistry> {
  return await readRegistryFromFile<SandboxBrowserRegistryEntry>(
    SANDBOX_BROWSER_REGISTRY_PATH,
    "fallback",
  )
}

suspend fun updateBrowserRegistry(entry: SandboxBrowserRegistryEntry) {
  await withRegistryMutation<SandboxBrowserRegistryEntry>(
    SANDBOX_BROWSER_REGISTRY_PATH,
    (entries) => upsertEntry(entries, entry),
  )
}

suspend fun removeBrowserRegistryEntry(containerName: String) {
  await withRegistryMutation<SandboxBrowserRegistryEntry>(
    SANDBOX_BROWSER_REGISTRY_PATH,
    (entries) => {
      val next = removeEntry(entries, containerName)
      if (next.length === entries.length) {
        return Nothing?
      }
      return next
    },
  )
}
