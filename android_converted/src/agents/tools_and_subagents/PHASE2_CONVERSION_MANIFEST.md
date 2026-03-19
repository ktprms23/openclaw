# Phase 2 Kotlin tools/subagents conversion manifest

## Branch

- Requested branch: `kotlin-port/agents-phase2-tools-subagents`.
- Network access to fetch `main` was unavailable in this workspace, so the branch was created from the provided workspace HEAD as the closest available base.

## Converted files

- `src/agents/command/delivery.ts`
- `src/agents/command/run-context.ts`
- `src/agents/command/session-store.ts`
- `src/agents/command/session.ts`
- `src/agents/command/types.ts`
- `src/agents/openclaw-tools.ts`
- `src/agents/pi-tools.ts`
- `src/agents/session-dirs.ts`
- `src/agents/session-file-repair.ts`
- `src/agents/session-slug.ts`
- `src/agents/session-tool-result-guard-wrapper.ts`
- `src/agents/session-tool-result-guard.ts`
- `src/agents/session-tool-result-state.ts`
- `src/agents/session-transcript-repair.ts`
- `src/agents/session-write-lock.ts`
- `src/agents/subagent-announce-dispatch.ts`
- `src/agents/subagent-announce-queue.ts`
- `src/agents/subagent-announce.ts`
- `src/agents/subagent-attachments.ts`
- `src/agents/subagent-capabilities.ts`
- `src/agents/subagent-control.ts`
- `src/agents/subagent-depth.ts`
- `src/agents/subagent-lifecycle-events.ts`
- `src/agents/subagent-orphan-recovery.ts`
- `src/agents/subagent-registry-cleanup.ts`
- `src/agents/subagent-registry-completion.ts`
- `src/agents/subagent-registry-queries.ts`
- `src/agents/subagent-registry-runtime.ts`
- `src/agents/subagent-registry-state.ts`
- `src/agents/subagent-registry.mocks.shared.ts`
- `src/agents/subagent-registry.store.ts`
- `src/agents/subagent-registry.ts`
- `src/agents/subagent-registry.types.ts`
- `src/agents/subagent-spawn.ts`
- `src/agents/timeout.ts`
- `src/agents/tool-call-id.ts`
- `src/agents/tool-catalog.ts`
- `src/agents/tool-display-common.ts`
- `src/agents/tool-display.ts`
- `src/agents/tool-fs-policy.ts`
- `src/agents/tool-images.ts`
- `src/agents/tool-loop-detection.ts`
- `src/agents/tool-mutation.ts`
- `src/agents/tool-policy-match.ts`
- `src/agents/tool-policy-pipeline.ts`
- `src/agents/tool-policy-shared.ts`
- `src/agents/tool-policy.conformance.ts`
- `src/agents/tool-policy.ts`
- `src/agents/tool-summaries.ts`
- `src/agents/tools/agent-step.ts`
- `src/agents/tools/agents-list-tool.ts`
- `src/agents/tools/browser-tool.actions.ts`
- `src/agents/tools/browser-tool.schema.ts`
- `src/agents/tools/browser-tool.ts`
- `src/agents/tools/canvas-tool.ts`
- `src/agents/tools/common.ts`
- `src/agents/tools/cron-tool.ts`
- `src/agents/tools/gateway-tool.ts`
- `src/agents/tools/gateway.ts`
- `src/agents/tools/image-generate-tool.ts`
- `src/agents/tools/image-tool.helpers.ts`
- `src/agents/tools/image-tool.ts`
- `src/agents/tools/media-tool-shared.ts`
- `src/agents/tools/memory-tool.ts`
- `src/agents/tools/message-tool.ts`
- `src/agents/tools/nodes-tool.ts`
- `src/agents/tools/nodes-utils.ts`
- `src/agents/tools/pdf-native-providers.ts`
- `src/agents/tools/pdf-tool.helpers.ts`
- `src/agents/tools/pdf-tool.ts`
- `src/agents/tools/session-status-tool.ts`
- `src/agents/tools/sessions-access.ts`
- `src/agents/tools/sessions-announce-target.ts`
- `src/agents/tools/sessions-helpers.ts`
- `src/agents/tools/sessions-history-tool.ts`
- `src/agents/tools/sessions-list-tool.ts`
- `src/agents/tools/sessions-resolution.ts`
- `src/agents/tools/sessions-send-helpers.ts`
- `src/agents/tools/sessions-send-tool.a2a.ts`
- `src/agents/tools/sessions-send-tool.ts`
- `src/agents/tools/sessions-spawn-tool.ts`
- `src/agents/tools/sessions-yield-tool.ts`
- `src/agents/tools/subagents-tool.ts`
- `src/agents/tools/tool-runtime.helpers.ts`
- `src/agents/tools/tts-tool.ts`
- `src/agents/tools/web-fetch-utils.ts`
- `src/agents/tools/web-fetch-visibility.ts`
- `src/agents/tools/web-fetch.ts`
- `src/agents/tools/web-guarded-fetch.ts`
- `src/agents/tools/web-search-citation-redirect.ts`
- `src/agents/tools/web-search-provider-common.ts`
- `src/agents/tools/web-search-provider-config.ts`
- `src/agents/tools/web-search-provider-credentials.ts`
- `src/agents/tools/web-search.ts`
- `src/agents/tools/web-shared.ts`
- `src/agents/tools/web-tools.ts`

## Skipped files

- `src/agents/pi-embedded-runner.ts`
- `src/agents/pi-embedded-runner/*`
- `src/agents/pi-embedded-subscribe.ts`
- `src/agents/pi-embedded-subscribe.*`
- `src/agents/pi-embedded-helpers.ts`
- `src/agents/pi-embedded-helpers/*`
- `src/agents/pi-embedded-utils.ts`
- `src/agents/pi-embedded-messaging.ts`
- `src/agents/pi-embedded-payloads.ts`
- `src/agents/pi-embedded-error-observation.ts`
- `src/agents/pi-embedded-block-chunker.ts`
- `src/agents/sandbox.ts`
- `src/agents/sandbox/*`
- `src/agents/models-config*`
- `src/agents/model-*`
- `src/agents/auth-profiles*`
- `src/agents/skills*`
- `src/agents/schema/*`
- `src/agents/**/__tests__/*`
- `src/agents/**/*.test.ts`
- `src/agents/tools/web-fetch.test-harness.ts`
- `src/agents/tools/web-fetch.test-mocks.ts`

## Major unresolved mappings

- TypeScript-only runtime/library types (for example `AgentTool`, `ClientToolDefinition`, `OpenClawConfig`, `AbortSignal`, and PI runtime helpers) remain as placeholder references or TODO comments in the Kotlin files.
- Complex TypeScript object literals, utility types, indexed-access types, unions, `as const`, `satisfies`, and callback-heavy APIs were preserved structurally with Kotlin-like syntax where possible, but many declarations still need hand-finished Kotlin typing.
- Async orchestration logic was translated toward `suspend fun` where the shape was obvious, but non-trivial promise/callback/event flows still require manual Kotlin coroutine mapping.

## Assumptions and risky translations

- Structural fidelity was prioritized over compilability or idiomatic Kotlin.
- Placeholder package names mirror the requested output tree rather than any final Android package namespace.
- Some literal bodies still contain TypeScript expressions or comments when a safe Kotlin equivalent was not obvious without refactoring.

## Scope confirmation

- Generated outputs live only under `android_converted/src/agents/tools_and_subagents`.
- Original TypeScript sources were not modified.
- The intended PR scope is Phase 2 only.
