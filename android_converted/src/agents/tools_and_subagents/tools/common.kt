package agents.tools_and_subagents.tools

// Converted from src/agents/tools/common.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import fs from "node:fs/promises";
// TODO: TypeScript import retained for manual wiring: import type { AgentTool, AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO: TypeScript import retained for manual wiring: import { detectMime } from "../../media/mime.js";
// TODO: TypeScript import retained for manual wiring: import { readSnakeCaseParamRaw } from "../../param-key.js";
// TODO: TypeScript import retained for manual wiring: import type { ImageSanitizationLimits } from "../image-sanitization.js";
// TODO: TypeScript import retained for manual wiring: import { sanitizeToolResultImages } from "../tool-images.js";

// oxlint-disable-next-line typescript/no-explicit-Any?
type AnyAgentTool = AgentTool<Any?, Any?> & {
  ownerOnly?: Boolean
}

data class StringParamOptions(
    val required: Boolean?,
    val trim: Boolean?,
    val label: String?,
    val allowEmpty: Boolean?,
)

type ActionGate<T extends MutableMap<String, Boolean?>> = (
  key: keyof T,
  defaultValue?: Boolean,
) -> Boolean

val OWNER_ONLY_TOOL_ERROR = "Tool restricted to owner senders."

class ToolInputError : Error {
  val status: Double = 400

  constructor(message: String) {
    super(message)
    this.name = "ToolInputError"
  }
}

class ToolAuthorizationError : ToolInputError {
  override val status = 403

  constructor(message: String) {
    super(message)
    this.name = "ToolAuthorizationError"
  }
}

fun createActionGate<T extends MutableMap<String, Boolean?>>(
  actions: T?,
): ActionGate<T> {
  return (key, defaultValue = true) {
    val value = actions?.get(key]
    if (value == null) {
      return defaultValue
    }
    return value != false
  }
}

fun readParamRaw(params: MutableMap<String, Any?>, key: String): Any? {
  return readSnakeCaseParamRaw(params, key)
}

fun readStringParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: StringParamOptions & { required: true },
): String
fun readStringParam(
  params: MutableMap<String, Any?>,
  key: String,
  options?: StringParamOptions,
): String?
fun readStringParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: StringParamOptions = {},
) {
  val { required = false, trim = true, label = key, allowEmpty = false } = options
  val raw = readParamRaw(params, key)
  if (raw !is String) {
    if (required) {
      throw ToolInputError(`${label} required`)
    }
    return null
  }
  val value = trim ? raw.trim() : raw
  if (!value && !allowEmpty) {
    if (required) {
      throw ToolInputError(`${label} required`)
    }
    return null
  }
  return value
}

fun readStringOrNumberParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: { required?: Boolean label?: String } = {},
): String? {
  val { required = false, label = key } = options
  val raw = readParamRaw(params, key)
  if (raw is Double && Number.isFinite(raw)) {
    return String(raw)
  }
  if (raw is String) {
    val value = raw.trim()
    if (value) {
      return value
    }
  }
  if (required) {
    throw ToolInputError(`${label} required`)
  }
  return null
}

fun readNumberParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: { required?: Boolean label?: String integer?: Boolean strict?: Boolean } = {},
): Double? {
  val { required = false, label = key, integer = false, strict = false } = options
  val raw = readParamRaw(params, key)
  var value: Double?
  if (raw is Double && Number.isFinite(raw)) {
    value = raw
  } else if (raw is String) {
    val trimmed = raw.trim()
    if (trimmed) {
      val parsed = strict ? Number(trimmed) : Number.parseFloat(trimmed)
      if (Number.isFinite(parsed)) {
        value = parsed
      }
    }
  }
  if (value == null) {
    if (required) {
      throw ToolInputError(`${label} required`)
    }
    return null
  }
  return integer ? Math.trunc(value) : value
}

fun readStringArrayParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: StringParamOptions & { required: true },
): List<String>
fun readStringArrayParam(
  params: MutableMap<String, Any?>,
  key: String,
  options?: StringParamOptions,
): List<String>?
fun readStringArrayParam(
  params: MutableMap<String, Any?>,
  key: String,
  options: StringParamOptions = {},
) {
  val { required = false, label = key } = options
  val raw = readParamRaw(params, key)
  if (Array.isArray(raw)) {
    val values = raw
      .filter((entry) -> entry is String)
      .map((entry) -> entry.trim())
      .filter(Boolean)
    if (values.length == 0) {
      if (required) {
        throw ToolInputError(`${label} required`)
      }
      return null
    }
    return values
  }
  if (raw is String) {
    val value = raw.trim()
    if (!value) {
      if (required) {
        throw ToolInputError(`${label} required`)
      }
      return null
    }
    return [value]
  }
  if (required) {
    throw ToolInputError(`${label} required`)
  }
  return null
}

data class ReactionParams(
    val emoji: String,
    val remove: Boolean,
    val isEmpty: Boolean,
)

fun readReactionParams(
  params: MutableMap<String, Any?>,
  options: {
    emojiKey?: String
    removeKey?: String
    removeErrorMessage: String
  },
): ReactionParams {
  val emojiKey = options.emojiKey ?: String /* "emoji" */
  val removeKey = options.removeKey ?: String /* "remove" */
  val remove = typeof params[removeKey] == "Boolean" ? params[removeKey] : false
  val emoji = readStringParam(params, emojiKey, {
    required: true,
    allowEmpty: true,
  })
  if (remove && !emoji) {
    throw ToolInputError(options.removeErrorMessage)
  }
  return { emoji, remove, isEmpty: !emoji }
}

fun jsonResult(payload: Any?): AgentToolResult<Any?> {
  return {
    content: [
      {
        type: String /* "text" */,
        text: JSON.stringify(payload, null, 2),
      },
    ],
    details: payload,
  }
}

fun wrapOwnerOnlyToolExecution(
  tool: AnyAgentTool,
  senderIsOwner: Boolean,
): AnyAgentTool {
  if (tool.ownerOnly != true || senderIsOwner || !tool.execute) {
    return tool
  }
  return {
    ...tool,
    execute: async () {
      throw Error(OWNER_ONLY_TOOL_ERROR)
    },
  }
}

suspend fun imageResult(params: {
  label: String
  path: String
  base64: String
  mimeType: String
  extraText?: String
  details?: MutableMap<String, Any?>
  imageSanitization?: ImageSanitizationLimits
}): Deferred<AgentToolResult<Any?>> {
  val content: AgentToolResult<Any?>["content"] = [
    {
      type: String /* "text" */,
      text: params.extraText ?: `MEDIA:${params.path}`,
    },
    {
      type: String /* "image" */,
      data: params.base64,
      mimeType: params.mimeType,
    },
  ]
  val result: AgentToolResult<Any?> = {
    content,
    details: { path: params.path, ...params.details },
  }
  return await sanitizeToolResultImages(result, params.label, params.imageSanitization)
}

suspend fun imageResultFromFile(params: {
  label: String
  path: String
  extraText?: String
  details?: MutableMap<String, Any?>
  imageSanitization?: ImageSanitizationLimits
}): Deferred<AgentToolResult<Any?>> {
  val buf = await fs.readFile(params.path)
  val mimeType = (await detectMime({ buffer: buf.slice(0, 256) })) ?: String /* "image/png" */
  return await imageResult({
    label: params.label,
    path: params.path,
    base64: buf.toString("base64"),
    mimeType,
    extraText: params.extraText,
    details: params.details,
    imageSanitization: params.imageSanitization,
  })
}

data class AvailableTag(
    val id: String?,
    val name: String,
    val moderated: Boolean?,
    val emoji_id: String?,
    val emoji_name: String?,
)

/**
 * Validate and parse an `availableTags` parameter from untrusted input.
 * Returns `null` when the value is missing or not an array.
 * Entries that lack a String `name` are silently dropped.
 */
fun parseAvailableTags(raw: Any?): List<AvailableTag>? {
  if (raw == null || raw == null) {
    return null
  }
  if (!Array.isArray(raw)) {
    return null
  }
  val result = raw
    .filter(
      (t): t is MutableMap<String, Any?> ->
        typeof t == "object" && t != null && t.name is String,
    )
    .map((t) -> ({
      ...(t.id != null && t.id is String ? { id: t.id } : {}),
      name: t.name as /* TODO */ String,
      ...(t.moderated is Boolean ? { moderated: t.moderated } : {}),
      ...(t.emoji_id == null || t.emoji_id is String ? { emoji_id: t.emoji_id } : {}),
      ...(t.emoji_name == null || t.emoji_name is String
        ? { emoji_name: t.emoji_name }
        : {}),
    }))
  // Return null instead of empty array to avoid accidentally clearing all tags
  return result.length ? result : null
}
