@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/compaction-runtime-context.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

data class EmbeddedCompactionRuntimeContext(
    val sessionKey: String? = TODO("Port default"),
    val messageChannel: String? = TODO("Port default"),
    val messageProvider: String? = TODO("Port default"),
    val agentAccountId: String? = TODO("Port default"),
    val currentChannelId: String? = TODO("Port default"),
    val currentThreadTs: String? = TODO("Port default"),
    val currentMessageId: Any /* string | number */? = TODO("Port default"),
    val authProfileId: String? = TODO("Port default"),
    val workspaceDir: String = TODO("Port default"),
    val agentDir: String = TODO("Port default"),
    val config: OpenClawConfig? = TODO("Port default"),
    val skillsSnapshot: SkillSnapshot? = TODO("Port default"),
    val senderIsOwner: Boolean? = TODO("Port default"),
    val senderId: String? = TODO("Port default"),
    val provider: String? = TODO("Port default"),
    val model: String? = TODO("Port default"),
    val thinkLevel: ThinkLevel? = TODO("Port default"),
    val reasoningLevel: ReasoningLevel? = TODO("Port default"),
    val bashElevated: ExecElevatedDefaults? = TODO("Port default"),
    val extraSystemPrompt: String? = TODO("Port default"),
    val ownerNumbers: List<String>? = TODO("Port default")
)

fun buildEmbeddedCompactionRuntimeContext(/* parameters preserved from TypeScript source */): EmbeddedCompactionRuntimeContext {
    TODO("Port runtime function buildEmbeddedCompactionRuntimeContext from src/agents/pi-embedded-runner/compaction-runtime-context.ts")
}

/*
Original imports retained for mapping context:
import type { ReasoningLevel, ThinkLevel } from "../../auto-reply/thinking.js";
import type { OpenClawConfig } from "../../config/config.js";
import type { ExecElevatedDefaults } from "../bash-tools.js";
import type { SkillSnapshot } from "../skills.js";
*/

/*
Original TypeScript reference:
import type { ReasoningLevel, ThinkLevel } from "../../auto-reply/thinking.js";
import type { OpenClawConfig } from "../../config/config.js";
import type { ExecElevatedDefaults } from "../bash-tools.js";
import type { SkillSnapshot } from "../skills.js";

export type EmbeddedCompactionRuntimeContext = {
  sessionKey?: string;
  messageChannel?: string;
  messageProvider?: string;
  agentAccountId?: string;
  currentChannelId?: string;
  currentThreadTs?: string;
  currentMessageId?: string | number;
  authProfileId?: string;
  workspaceDir: string;
  agentDir: string;
  config?: OpenClawConfig;
  skillsSnapshot?: SkillSnapshot;
  senderIsOwner?: boolean;
  senderId?: string;
  provider?: string;
  model?: string;
  thinkLevel?: ThinkLevel;
  reasoningLevel?: ReasoningLevel;
  bashElevated?: ExecElevatedDefaults;
  extraSystemPrompt?: string;
  ownerNumbers?: string[];
};

export function buildEmbeddedCompactionRuntimeContext(params: {
  sessionKey?: string | null;
  messageChannel?: string | null;
  messageProvider?: string | null;
  agentAccountId?: string | null;
  currentChannelId?: string | null;
  currentThreadTs?: string | null;
  currentMessageId?: string | number | null;
  authProfileId?: string | null;
  workspaceDir: string;
  agentDir: string;
  config?: OpenClawConfig;
  skillsSnapshot?: SkillSnapshot;
  senderIsOwner?: boolean;
  senderId?: string | null;
  provider?: string | null;
  modelId?: string | null;
  thinkLevel?: ThinkLevel;
  reasoningLevel?: ReasoningLevel;
  bashElevated?: ExecElevatedDefaults;
  extraSystemPrompt?: string;
  ownerNumbers?: string[];
}): EmbeddedCompactionRuntimeContext {
  return {
    sessionKey: params.sessionKey ?? undefined,
    messageChannel: params.messageChannel ?? undefined,
    messageProvider: params.messageProvider ?? undefined,
    agentAccountId: params.agentAccountId ?? undefined,
    currentChannelId: params.currentChannelId ?? undefined,
    currentThreadTs: params.currentThreadTs ?? undefined,
    currentMessageId: params.currentMessageId ?? undefined,
    authProfileId: params.authProfileId ?? undefined,
    workspaceDir: params.workspaceDir,
    agentDir: params.agentDir,
    config: params.config,
    skillsSnapshot: params.skillsSnapshot,
    senderIsOwner: params.senderIsOwner,
    senderId: params.senderId ?? undefined,
    provider: params.provider ?? undefined,
    model: params.modelId ?? undefined,
    thinkLevel: params.thinkLevel,
    reasoningLevel: params.reasoningLevel,
    bashElevated: params.bashElevated,
    extraSystemPrompt: params.extraSystemPrompt,
    ownerNumbers: params.ownerNumbers,
  };
}

*/
