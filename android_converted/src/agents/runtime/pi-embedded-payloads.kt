@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-payloads.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

data class BlockReplyPayload(
    val text: String? = TODO("Port default"),
    val mediaUrls: List<String>? = TODO("Port default"),
    val audioAsVoice: Boolean? = TODO("Port default"),
    val isReasoning: Boolean? = TODO("Port default"),
    val replyToId: String? = TODO("Port default"),
    val replyToTag: Boolean? = TODO("Port default"),
    val replyToCurrent: Boolean? = TODO("Port default")
)

/*
Original TypeScript reference:
export type BlockReplyPayload = {
  text?: string;
  mediaUrls?: string[];
  audioAsVoice?: boolean;
  isReasoning?: boolean;
  replyToId?: string;
  replyToTag?: boolean;
  replyToCurrent?: boolean;
};

*/
