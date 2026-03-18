@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

/**
 * Phase 1 Kotlin port scaffold for `src/agents/pi-embedded-runner/skills-runtime.ts`.
 *
 * Scope: embedded runtime execution and streaming only.
 * This file preserves the runtime structure and exported surface of the
 * TypeScript source while deferring compile-perfect Kotlinization to later phases.
 */

fun resolveEmbeddedRunSkillEntries(/* parameters preserved from TypeScript source */):  {
    TODO("Port runtime function resolveEmbeddedRunSkillEntries from src/agents/pi-embedded-runner/skills-runtime.ts")
}

/*
Original imports retained for mapping context:
import type { OpenClawConfig } from "../../config/config.js";
import { loadWorkspaceSkillEntries, type SkillEntry, type SkillSnapshot } from "../skills.js";
*/

/*
Original TypeScript reference:
import type { OpenClawConfig } from "../../config/config.js";
import { loadWorkspaceSkillEntries, type SkillEntry, type SkillSnapshot } from "../skills.js";

export function resolveEmbeddedRunSkillEntries(params: {
  workspaceDir: string;
  config?: OpenClawConfig;
  skillsSnapshot?: SkillSnapshot;
}): {
  shouldLoadSkillEntries: boolean;
  skillEntries: SkillEntry[];
} {
  const shouldLoadSkillEntries = !params.skillsSnapshot || !params.skillsSnapshot.resolvedSkills;
  return {
    shouldLoadSkillEntries,
    skillEntries: shouldLoadSkillEntries
      ? loadWorkspaceSkillEntries(params.workspaceDir, { config: params.config })
      : [],
  };
}

*/
