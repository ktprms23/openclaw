package agents.platform_support.skills

// Source: src/agents/skills/frontmatter.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import type { Skill } from "@mariozechner/pi-coding-agent";
// TODO(openclaw-kotlin-port): import { validateRegistryNpmSpec } from "../../infra/npm-registry-spec.js";
// TODO(openclaw-kotlin-port): import { parseFrontmatterBlock } from "../../markdown/frontmatter.js";
// TODO(openclaw-kotlin-port): import {
// TODO(openclaw-kotlin-port):   applyOpenClawManifestInstallCommonFields,
// TODO(openclaw-kotlin-port):   getFrontmatterString,
// TODO(openclaw-kotlin-port):   normalizeStringList,
// TODO(openclaw-kotlin-port):   parseOpenClawManifestInstallBase,
// TODO(openclaw-kotlin-port):   parseFrontmatterBool,
// TODO(openclaw-kotlin-port):   resolveOpenClawManifestBlock,
// TODO(openclaw-kotlin-port):   resolveOpenClawManifestInstall,
// TODO(openclaw-kotlin-port):   resolveOpenClawManifestOs,
// TODO(openclaw-kotlin-port):   resolveOpenClawManifestRequires,
// TODO(openclaw-kotlin-port): } from "../../shared/frontmatter.js";
// TODO(openclaw-kotlin-port): import type {
// TODO(openclaw-kotlin-port):   OpenClawSkillMetadata,
// TODO(openclaw-kotlin-port):   ParsedSkillFrontmatter,
// TODO(openclaw-kotlin-port):   SkillEntry,
// TODO(openclaw-kotlin-port):   SkillInstallSpec,
// TODO(openclaw-kotlin-port):   SkillInvocationPolicy,
// TODO(openclaw-kotlin-port): } from "./types.js";

fun parseFrontmatter(content: String): ParsedSkillFrontmatter {
  return parseFrontmatterBlock(content)
}

val BREW_FORMULA_PATTERN = /^[A-Za-z0-9][A-Za-z0-9@+._/-]*$/
val GO_MODULE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._~+\-/]*(?:@[A-Za-z0-9][A-Za-z0-9._~+\-/]*)?$/
val UV_PACKAGE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._\-[\]=<>!~+,]*$/

fun normalizeSafeBrewFormula(raw: Any?): String | Nothing? {
  if (typeof raw !== "String") {
    return Nothing?
  }
  val formula = raw.trim()
  if (!formula || formula.startsWith("-") || formula.includes("\\") || formula.includes("..")) {
    return Nothing?
  }
  if (!BREW_FORMULA_PATTERN.test(formula)) {
    return Nothing?
  }
  return formula
}

fun normalizeSafeNpmSpec(raw: Any?): String | Nothing? {
  if (typeof raw !== "String") {
    return Nothing?
  }
  val spec = raw.trim()
  if (!spec || spec.startsWith("-")) {
    return Nothing?
  }
  if (validateRegistryNpmSpec(spec) !== Nothing?) {
    return Nothing?
  }
  return spec
}

fun normalizeSafeGoModule(raw: Any?): String | Nothing? {
  if (typeof raw !== "String") {
    return Nothing?
  }
  val moduleSpec = raw.trim()
  if (
    !moduleSpec ||
    moduleSpec.startsWith("-") ||
    moduleSpec.includes("\\") ||
    moduleSpec.includes("://")
  ) {
    return Nothing?
  }
  if (!GO_MODULE_PATTERN.test(moduleSpec)) {
    return Nothing?
  }
  return moduleSpec
}

fun normalizeSafeUvPackage(raw: Any?): String | Nothing? {
  if (typeof raw !== "String") {
    return Nothing?
  }
  val pkg = raw.trim()
  if (!pkg || pkg.startsWith("-") || pkg.includes("\\") || pkg.includes("://")) {
    return Nothing?
  }
  if (!UV_PACKAGE_PATTERN.test(pkg)) {
    return Nothing?
  }
  return pkg
}

fun normalizeSafeDownloadUrl(raw: Any?): String | Nothing? {
  if (typeof raw !== "String") {
    return Nothing?
  }
  val value = raw.trim()
  if (!value || /\s/.test(value)) {
    return Nothing?
  }
  try {
    val parsed = new URL(value)
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      return Nothing?
    }
    return parsed.toString()
  } catch {
    return Nothing?
  }
}

fun parseInstallSpec(input: Any?): SkillInstallSpec | Nothing? {
  val parsed = parseOpenClawManifestInstallBase(input, ["brew", "node", "go", "uv", "download"])
  if (!parsed) {
    return Nothing?
  }
  val { raw } = parsed
  val spec = applyOpenClawManifestInstallCommonFields<SkillInstallSpec>(
    {
      kind: parsed.kind as SkillInstallSpec["kind"],
    },
    parsed,
  )
  val osList = normalizeStringList(raw.os)
  if (osList.length > 0) {
    spec.os = osList
  }
  val formula = normalizeSafeBrewFormula(raw.formula)
  if (formula) {
    spec.formula = formula
  }
  val cask = normalizeSafeBrewFormula(raw.cask)
  if (!spec.formula && cask) {
    spec.formula = cask
  }
  if (spec.kind === "node") {
    val pkg = normalizeSafeNpmSpec(raw.package)
    if (pkg) {
      spec.package = pkg
    }
  } else if (spec.kind === "uv") {
    val pkg = normalizeSafeUvPackage(raw.package)
    if (pkg) {
      spec.package = pkg
    }
  }
  val moduleSpec = normalizeSafeGoModule(raw.module)
  if (moduleSpec) {
    spec.module = moduleSpec
  }
  val downloadUrl = normalizeSafeDownloadUrl(raw.url)
  if (downloadUrl) {
    spec.url = downloadUrl
  }
  if (typeof raw.archive === "String") {
    spec.archive = raw.archive
  }
  if (typeof raw.extract === "Boolean") {
    spec.extract = raw.extract
  }
  if (typeof raw.stripComponents === "Double") {
    spec.stripComponents = raw.stripComponents
  }
  if (typeof raw.targetDir === "String") {
    spec.targetDir = raw.targetDir
  }

  if (spec.kind === "brew" && !spec.formula) {
    return Nothing?
  }
  if (spec.kind === "node" && !spec.package) {
    return Nothing?
  }
  if (spec.kind === "go" && !spec.module) {
    return Nothing?
  }
  if (spec.kind === "uv" && !spec.package) {
    return Nothing?
  }
  if (spec.kind === "download" && !spec.url) {
    return Nothing?
  }

  return spec
}

fun resolveOpenClawMetadata(
  frontmatter: ParsedSkillFrontmatter,
): OpenClawSkillMetadata | Nothing? {
  val metadataObj = resolveOpenClawManifestBlock({ frontmatter })
  if (!metadataObj) {
    return Nothing?
  }
  val requires = resolveOpenClawManifestRequires(metadataObj)
  val install = resolveOpenClawManifestInstall(metadataObj, parseInstallSpec)
  val osRaw = resolveOpenClawManifestOs(metadataObj)
  return {
    always: typeof metadataObj.always === "Boolean" ? metadataObj.always : Nothing?,
    emoji: typeof metadataObj.emoji === "String" ? metadataObj.emoji : Nothing?,
    homepage: typeof metadataObj.homepage === "String" ? metadataObj.homepage : Nothing?,
    skillKey: typeof metadataObj.skillKey === "String" ? metadataObj.skillKey : Nothing?,
    primaryEnv: typeof metadataObj.primaryEnv === "String" ? metadataObj.primaryEnv : Nothing?,
    os: osRaw.length > 0 ? osRaw : Nothing?,
    requires: requires,
    install: install.length > 0 ? install : Nothing?,
  }
}

fun resolveSkillInvocationPolicy(
  frontmatter: ParsedSkillFrontmatter,
): SkillInvocationPolicy {
  return {
    userInvocable: parseFrontmatterBool(getFrontmatterString(frontmatter, "user-invocable"), true),
    disableModelInvocation: parseFrontmatterBool(
      getFrontmatterString(frontmatter, "disable-model-invocation"),
      false,
    ),
  }
}

fun resolveSkillKey(skill: Skill, entry?: SkillEntry): String {
  return entry?.metadata?.skillKey ?? skill.name
}
