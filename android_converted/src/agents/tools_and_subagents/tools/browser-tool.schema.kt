package agents.tools_and_subagents.tools

// Converted from src/agents/tools/browser-tool.schema.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { Type } from "@sinclair/typebox";
// TODO: TypeScript import retained for manual wiring: import { optionalStringEnum, stringEnum } from "../schema/typebox.js";

val BROWSER_ACT_KINDS = [
  "click",
  "type",
  "press",
  "hover",
  "drag",
  "select",
  "fill",
  "resize",
  "wait",
  "evaluate",
  "close",
] as /* TODO */ val

val BROWSER_TOOL_ACTIONS = [
  "status",
  "start",
  "stop",
  "profiles",
  "tabs",
  "open",
  "focus",
  "close",
  "snapshot",
  "screenshot",
  "navigate",
  "console",
  "pdf",
  "upload",
  "dialog",
  "act",
] as /* TODO */ val

val BROWSER_TARGETS = ["sandbox", "host", "node"] as /* TODO */ val

val BROWSER_SNAPSHOT_FORMATS = ["aria", "ai"] as /* TODO */ val
val BROWSER_SNAPSHOT_MODES = ["efficient"] as /* TODO */ val
val BROWSER_SNAPSHOT_REFS = ["role", "aria"] as /* TODO */ val

val BROWSER_IMAGE_TYPES = ["png", "jpeg"] as /* TODO */ val

// NOTE: Using a flattened object schema instead of Type.Union([Type.Object(...), ...])
// because Claude API on Vertex AI rejects nested anyOf schemas as /* TODO */ invalid JSON Schema.
// The discriminator (kind) determines which properties are relevant runtime validates.
val BrowserActSchema = Type.Object({
  kind: stringEnum(BROWSER_ACT_KINDS),
  // Common fields
  targetId: Type.Optional(Type.String()),
  ref: Type.Optional(Type.String()),
  // click
  doubleClick: Type.Optional(Type.Boolean()),
  button: Type.Optional(Type.String()),
  modifiers: Type.Optional(Type.Array(Type.String())),
  // type
  text: Type.Optional(Type.String()),
  submit: Type.Optional(Type.Boolean()),
  slowly: Type.Optional(Type.Boolean()),
  // press
  key: Type.Optional(Type.String()),
  delayMs: Type.Optional(Type.Number()),
  // drag
  startRef: Type.Optional(Type.String()),
  endRef: Type.Optional(Type.String()),
  // select
  values: Type.Optional(Type.Array(Type.String())),
  // fill - use permissive array of objects
  fields: Type.Optional(Type.Array(Type.Object({}, { additionalProperties: true }))),
  // resize
  width: Type.Optional(Type.Number()),
  height: Type.Optional(Type.Number()),
  // wait
  timeMs: Type.Optional(Type.Number()),
  selector: Type.Optional(Type.String()),
  url: Type.Optional(Type.String()),
  loadState: Type.Optional(Type.String()),
  textGone: Type.Optional(Type.String()),
  timeoutMs: Type.Optional(Type.Number()),
  // evaluate
  fn: Type.Optional(Type.String()),
})

// IMPORTANT: OpenAI fun tool schemas must have a top-level `type: String /* "object" */`.
// A root-level `Type.Union([...])` compiles to `{ anyOf: [...] }` (no `type`),
// which OpenAI rejects ("Invalid schema ... type: None"). Keep this schema an object.
val BrowserToolSchema = Type.Object({
  action: stringEnum(BROWSER_TOOL_ACTIONS),
  target: optionalStringEnum(BROWSER_TARGETS),
  node: Type.Optional(Type.String()),
  profile: Type.Optional(Type.String()),
  targetUrl: Type.Optional(Type.String()),
  url: Type.Optional(Type.String()),
  targetId: Type.Optional(Type.String()),
  limit: Type.Optional(Type.Number()),
  maxChars: Type.Optional(Type.Number()),
  mode: optionalStringEnum(BROWSER_SNAPSHOT_MODES),
  snapshotFormat: optionalStringEnum(BROWSER_SNAPSHOT_FORMATS),
  refs: optionalStringEnum(BROWSER_SNAPSHOT_REFS),
  interactive: Type.Optional(Type.Boolean()),
  compact: Type.Optional(Type.Boolean()),
  depth: Type.Optional(Type.Number()),
  selector: Type.Optional(Type.String()),
  frame: Type.Optional(Type.String()),
  labels: Type.Optional(Type.Boolean()),
  fullPage: Type.Optional(Type.Boolean()),
  ref: Type.Optional(Type.String()),
  element: Type.Optional(Type.String()),
  type: optionalStringEnum(BROWSER_IMAGE_TYPES),
  level: Type.Optional(Type.String()),
  paths: Type.Optional(Type.Array(Type.String())),
  inputRef: Type.Optional(Type.String()),
  timeoutMs: Type.Optional(Type.Number()),
  accept: Type.Optional(Type.Boolean()),
  promptText: Type.Optional(Type.String()),
  // Legacy flattened act params (preferred: request={...})
  kind: Type.Optional(stringEnum(BROWSER_ACT_KINDS)),
  doubleClick: Type.Optional(Type.Boolean()),
  button: Type.Optional(Type.String()),
  modifiers: Type.Optional(Type.Array(Type.String())),
  text: Type.Optional(Type.String()),
  submit: Type.Optional(Type.Boolean()),
  slowly: Type.Optional(Type.Boolean()),
  key: Type.Optional(Type.String()),
  delayMs: Type.Optional(Type.Number()),
  startRef: Type.Optional(Type.String()),
  endRef: Type.Optional(Type.String()),
  values: Type.Optional(Type.Array(Type.String())),
  fields: Type.Optional(Type.Array(Type.Object({}, { additionalProperties: true }))),
  width: Type.Optional(Type.Number()),
  height: Type.Optional(Type.Number()),
  timeMs: Type.Optional(Type.Number()),
  textGone: Type.Optional(Type.String()),
  loadState: Type.Optional(Type.String()),
  fn: Type.Optional(Type.String()),
  request: Type.Optional(BrowserActSchema),
})
