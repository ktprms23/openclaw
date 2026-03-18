@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-helpers.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

object PiEmbeddedHelpersFacade {
    val exportedMembers: List<String> = listOf(
        "buildBootstrapContextFiles, DEFAULT_BOOTSTRAP_MAX_CHARS, DEFAULT_BOOTSTRAP_PROMPT_TRUNCATION_WARNING_MODE, DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS, ensureSessionHeader, resolveBootstrapMaxChars, resolveBootstrapPromptTruncationWarningMode, resolveBootstrapTotalMaxChars, stripThoughtSignatures,  <- ./pi-embedded-helpers/bootstrap.js",
        "BILLING_ERROR_USER_MESSAGE, formatBillingErrorMessage, classifyFailoverReason, classifyFailoverReasonFromHttpStatus, formatRawAssistantErrorForUi, formatAssistantErrorText, getApiErrorPayloadFingerprint, isAuthAssistantError, isAuthErrorMessage, isAuthPermanentErrorMessage, isModelNotFoundErrorMessage, isBillingAssistantError, extractObservedOverflowTokenCount, parseApiErrorInfo, sanitizeUserFacingText, isBillingErrorMessage, isCloudflareOrHtmlErrorPage, isCloudCodeAssistFormatError, isCompactionFailureError, isContextOverflowError, isLikelyContextOverflowError, isFailoverAssistantError, isFailoverErrorMessage, isImageDimensionErrorMessage, isImageSizeError, isOverloadedErrorMessage, isRawApiErrorPayload, isRateLimitAssistantError, isRateLimitErrorMessage, isTransientHttpError, isTimeoutErrorMessage, parseImageDimensionError, parseImageSizeError,  <- ./pi-embedded-helpers/errors.js",
        "isGoogleModelApi, sanitizeGoogleTurnOrdering <- ./pi-embedded-helpers/google.js",
        "downgradeOpenAIFunctionCallReasoningPairs, downgradeOpenAIReasoningBlocks,  <- ./pi-embedded-helpers/openai.js",
        "isEmptyAssistantMessageContent, sanitizeSessionMessagesImages,  <- ./pi-embedded-helpers/images.js",
        "isMessagingToolDuplicate, isMessagingToolDuplicateNormalized, normalizeTextForComparison,  <- ./pi-embedded-helpers/messaging-dedupe.js",
        "pickFallbackThinkingLevel <- ./pi-embedded-helpers/thinking.js",
        "mergeConsecutiveUserTurns, validateAnthropicTurns, validateGeminiTurns,  <- ./pi-embedded-helpers/turns.js",
        "EmbeddedContextFile, FailoverReason <- ./pi-embedded-helpers/types.js",
        "ToolCallIdMode <- ./tool-call-id.js",
        "isValidCloudCodeAssistToolId, sanitizeToolCallId <- ./tool-call-id.js",
    )
}

/*
Original TypeScript reference:
export {
  buildBootstrapContextFiles,
  DEFAULT_BOOTSTRAP_MAX_CHARS,
  DEFAULT_BOOTSTRAP_PROMPT_TRUNCATION_WARNING_MODE,
  DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS,
  ensureSessionHeader,
  resolveBootstrapMaxChars,
  resolveBootstrapPromptTruncationWarningMode,
  resolveBootstrapTotalMaxChars,
  stripThoughtSignatures,
} from "./pi-embedded-helpers/bootstrap.js";
export {
  BILLING_ERROR_USER_MESSAGE,
  formatBillingErrorMessage,
  classifyFailoverReason,
  classifyFailoverReasonFromHttpStatus,
  formatRawAssistantErrorForUi,
  formatAssistantErrorText,
  getApiErrorPayloadFingerprint,
  isAuthAssistantError,
  isAuthErrorMessage,
  isAuthPermanentErrorMessage,
  isModelNotFoundErrorMessage,
  isBillingAssistantError,
  extractObservedOverflowTokenCount,
  parseApiErrorInfo,
  sanitizeUserFacingText,
  isBillingErrorMessage,
  isCloudflareOrHtmlErrorPage,
  isCloudCodeAssistFormatError,
  isCompactionFailureError,
  isContextOverflowError,
  isLikelyContextOverflowError,
  isFailoverAssistantError,
  isFailoverErrorMessage,
  isImageDimensionErrorMessage,
  isImageSizeError,
  isOverloadedErrorMessage,
  isRawApiErrorPayload,
  isRateLimitAssistantError,
  isRateLimitErrorMessage,
  isTransientHttpError,
  isTimeoutErrorMessage,
  parseImageDimensionError,
  parseImageSizeError,
} from "./pi-embedded-helpers/errors.js";
export { isGoogleModelApi, sanitizeGoogleTurnOrdering } from "./pi-embedded-helpers/google.js";

export {
  downgradeOpenAIFunctionCallReasoningPairs,
  downgradeOpenAIReasoningBlocks,
} from "./pi-embedded-helpers/openai.js";
export {
  isEmptyAssistantMessageContent,
  sanitizeSessionMessagesImages,
} from "./pi-embedded-helpers/images.js";
export {
  isMessagingToolDuplicate,
  isMessagingToolDuplicateNormalized,
  normalizeTextForComparison,
} from "./pi-embedded-helpers/messaging-dedupe.js";

export { pickFallbackThinkingLevel } from "./pi-embedded-helpers/thinking.js";

export {
  mergeConsecutiveUserTurns,
  validateAnthropicTurns,
  validateGeminiTurns,
} from "./pi-embedded-helpers/turns.js";
export type { EmbeddedContextFile, FailoverReason } from "./pi-embedded-helpers/types.js";

export type { ToolCallIdMode } from "./tool-call-id.js";
export { isValidCloudCodeAssistToolId, sanitizeToolCallId } from "./tool-call-id.js";

*/
