@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/common.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import fs from "node:fs/promises";
// TODO(port-deps): import type { AgentTool, AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO(port-deps): import { detectMime } from "../../media/mime.js";
// TODO(port-deps): import { readSnakeCaseParamRaw } from "../../param-key.js";
// TODO(port-deps): import type { ImageSanitizationLimits } from "../image-sanitization.js";
// TODO(port-deps): import { sanitizeToolResultImages } from "../tool-images.js";

// oxlint-disable-next-line typescript/no-explicit-any
typealias AnyAgentTool = Any /* TODO: translate TypeScript alias */

typealias StringParamOptions = Any /* TODO: translate TypeScript alias */

type ActionGate<T extends Record<string, boolean | null>> = (
  key: keyof T,
  defaultValue?: boolean,
) => boolean;

val OWNER_ONLY_TOOL_ERROR = "Tool restricted to owner senders.";

class ToolInputError extends Error {
  status: number = 400;

  constructor(message: string) {
    super(message);
    this.name = "ToolInputError";
  }
}

class ToolAuthorizationError extends ToolInputError {
  override status = 403;

  constructor(message: string) {
    super(message);
    this.name = "ToolAuthorizationError";
  }
}

fun createActionGate<T extends Record<string, boolean | null>>(
  actions: T | null,
): ActionGate<T> {
  return (key, defaultValue = true) => {
    val value = actions?.[key];
    if (value == null) {
      return defaultValue;
    }
    return value != false;
  };
}

fun readParamRaw(params: Record<string, unknown>, key: string): unknown {
  return readSnakeCaseParamRaw(params, key);
}

fun readStringParam(
  params: Record<string, unknown>,
  key: string,
  options: StringParamOptions & { required: true },
): string;
fun readStringParam(
  params: Record<string, unknown>,
  key: string,
  options?: StringParamOptions,
): string | null;
fun readStringParam(
  params: Record<string, unknown>,
  key: string,
  options: StringParamOptions = {},
) {
  val { required = false, trim = true, label = key, allowEmpty = false } = options;
  val raw = readParamRaw(params, key);
  if (typeof raw != "string") {
    if (required) {
      throw new ToolInputError(`${label} required`);
    }
    return null;
  }
  val value = trim ? raw.trim() : raw;
  if (!value && !allowEmpty) {
    if (required) {
      throw new ToolInputError(`${label} required`);
    }
    return null;
  }
  return value;
}

fun readStringOrNumberParam(
  params: Record<string, unknown>,
  key: string,
  options: { required?: boolean; label?: string } = {},
): string | null {
  val { required = false, label = key } = options;
  val raw = readParamRaw(params, key);
  if (typeof raw == "number" && Number.isFinite(raw)) {
    return String(raw);
  }
  if (typeof raw == "string") {
    val value = raw.trim();
    if (value) {
      return value;
    }
  }
  if (required) {
    throw new ToolInputError(`${label} required`);
  }
  return null;
}

fun readNumberParam(
  params: Record<string, unknown>,
  key: string,
  options: { required?: boolean; label?: string; integer?: boolean; strict?: boolean } = {},
): number | null {
  val { required = false, label = key, integer = false, strict = false } = options;
  val raw = readParamRaw(params, key);
  var value: number | null;
  if (typeof raw == "number" && Number.isFinite(raw)) {
    value = raw;
  } else if (typeof raw == "string") {
    val trimmed = raw.trim();
    if (trimmed) {
      val parsed = strict ? Number(trimmed) : Number.parseFloat(trimmed);
      if (Number.isFinite(parsed)) {
        value = parsed;
      }
    }
  }
  if (value == null) {
    if (required) {
      throw new ToolInputError(`${label} required`);
    }
    return null;
  }
  return integer ? Math.trunc(value) : value;
}

fun readStringArrayParam(
  params: Record<string, unknown>,
  key: string,
  options: StringParamOptions & { required: true },
): string[];
fun readStringArrayParam(
  params: Record<string, unknown>,
  key: string,
  options?: StringParamOptions,
): string[] | null;
fun readStringArrayParam(
  params: Record<string, unknown>,
  key: string,
  options: StringParamOptions = {},
) {
  val { required = false, label = key } = options;
  val raw = readParamRaw(params, key);
  if (Array.isArray(raw)) {
    val values = raw
      .filter((entry) => typeof entry == "string")
      .map((entry) => entry.trim())
      .filter(Boolean);
    if (values.length == 0) {
      if (required) {
        throw new ToolInputError(`${label} required`);
      }
      return null;
    }
    return values;
  }
  if (typeof raw == "string") {
    val value = raw.trim();
    if (!value) {
      if (required) {
        throw new ToolInputError(`${label} required`);
      }
      return null;
    }
    return [value];
  }
  if (required) {
    throw new ToolInputError(`${label} required`);
  }
  return null;
}

typealias ReactionParams = Any /* TODO: translate TypeScript alias */

fun readReactionParams(
  params: Record<string, unknown>,
  options: {
    emojiKey?: string;
    removeKey?: string;
    removeErrorMessage: string;
  },
): ReactionParams {
  val emojiKey = options.emojiKey ?: "emoji";
  val removeKey = options.removeKey ?: "remove";
  val remove = typeof params[removeKey] == "boolean" ? params[removeKey] : false;
  val emoji = readStringParam(params, emojiKey, {
    required: true,
    allowEmpty: true,
  });
  if (remove && !emoji) {
    throw new ToolInputError(options.removeErrorMessage);
  }
  return { emoji, remove, isEmpty: !emoji };
}

fun jsonResult(payload: unknown): AgentToolResult<unknown> {
  return {
    content: [
      {
        type: "text",
        text: JSON.stringify(payload, null, 2),
      },
    ],
    details: payload,
  };
}

fun wrapOwnerOnlyToolExecution(
  tool: AnyAgentTool,
  senderIsOwner: boolean,
): AnyAgentTool {
  if (tool.ownerOnly != true || senderIsOwner || !tool.execute) {
    return tool;
  }
  return {
    ...tool,
    execute: async () => {
      throw Error(OWNER_ONLY_TOOL_ERROR);
    },
  };
}

suspend fun imageResult(params: {
  label: string;
  path: string;
  base64: string;
  mimeType: string;
  extraText?: string;
  details?: Record<string, unknown>;
  imageSanitization?: ImageSanitizationLimits;
}): Promise<AgentToolResult<unknown>> {
  val content: AgentToolResult<unknown>["content"] = [
    {
      type: "text",
      text: params.extraText ?: `MEDIA:${params.path}`,
    },
    {
      type: "image",
      data: params.base64,
      mimeType: params.mimeType,
    },
  ];
  val result: AgentToolResult<unknown> = {
    content,
    details: { path: params.path, ...params.details },
  };
  return await sanitizeToolResultImages(result, params.label, params.imageSanitization);
}

suspend fun imageResultFromFile(params: {
  label: string;
  path: string;
  extraText?: string;
  details?: Record<string, unknown>;
  imageSanitization?: ImageSanitizationLimits;
}): Promise<AgentToolResult<unknown>> {
  val buf = await fs.readFile(params.path);
  val mimeType = (await detectMime({ buffer: buf.slice(0, 256) })) ?: "image/png";
  return await imageResult({
    label: params.label,
    path: params.path,
    base64: buf.toString("base64"),
    mimeType,
    extraText: params.extraText,
    details: params.details,
    imageSanitization: params.imageSanitization,
  });
}

typealias AvailableTag = Any /* TODO: translate TypeScript alias */

/**
 * Validate and parse an `availableTags` parameter from untrusted input.
 * Returns `null` when the value is missing or not an array.
 * Entries that lack a string `name` are silently dropped.
 */
fun parseAvailableTags(raw: unknown): AvailableTag[] | null {
  if (raw == null || raw == null) {
    return null;
  }
  if (!Array.isArray(raw)) {
    return null;
  }
  val result = raw
    .filter(
      (t): t is Record<string, unknown> =>
        typeof t == "object" && t != null && typeof t.name == "string",
    )
    .map((t) => ({
      ...(t.id != null && typeof t.id == "string" ? { id: t.id } : {}),
      name: t.name as string,
      ...(typeof t.moderated == "boolean" ? { moderated: t.moderated } : {}),
      ...(t.emoji_id == null || typeof t.emoji_id == "string" ? { emoji_id: t.emoji_id } : {}),
      ...(t.emoji_name == null || typeof t.emoji_name == "string"
        ? { emoji_name: t.emoji_name }
        : {}),
    }));
  // Return null instead of empty array to avoid accidentally clearing all tags
  return result.length ? result : null;
}
