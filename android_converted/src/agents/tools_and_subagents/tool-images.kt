@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tool-images.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import type { AgentToolResult } from "@mariozechner/pi-agent-core";
// TODO(port-deps): import type { ImageContent } from "@mariozechner/pi-ai";
// TODO(port-deps): import { createSubsystemLogger } from "../logging/subsystem.js";
// TODO(port-deps): import { canonicalizeBase64 } from "../media/base64.js";
// TODO(port-deps): import {
// TODO(port-deps): buildImageResizeSideGrid,
// TODO(port-deps): getImageMetadata,
// TODO(port-deps): IMAGE_REDUCE_QUALITY_STEPS,
// TODO(port-deps): resizeToJpeg,
// TODO(port-deps): } from "../media/image-ops.js";
// TODO(port-deps): import {
// TODO(port-deps): DEFAULT_IMAGE_MAX_BYTES,
// TODO(port-deps): DEFAULT_IMAGE_MAX_DIMENSION_PX,
// TODO(port-deps): type ImageSanitizationLimits,
// TODO(port-deps): } from "./image-sanitization.js";

typealias ToolContentBlock = AgentToolResult<unknown>["content"][number]
typealias ImageContentBlock = Any /* TODO: translate TypeScript alias */
typealias TextContentBlock = Any /* TODO: translate TypeScript alias */
