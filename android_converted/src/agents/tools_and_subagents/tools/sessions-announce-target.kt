package agents.tools_and_subagents.tools

// Converted from src/agents/tools/sessions-announce-target.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { getChannelPlugin, normalizeChannelId } from "../../channels/plugins/index.js";
// TODO: TypeScript import retained for manual wiring: import { callGateway } from "../../gateway/call.js";
// TODO: TypeScript import retained for manual wiring: import { SessionListRow } from "./sessions-helpers.js";
// TODO: TypeScript import retained for manual wiring: import type { AnnounceTarget } from "./sessions-send-helpers.js";
// TODO: TypeScript import retained for manual wiring: import { resolveAnnounceTargetFromKey } from "./sessions-send-helpers.js";

suspend fun resolveAnnounceTarget(params: {
  sessionKey: String
  displayKey: String
}): Deferred<AnnounceTarget?> {
  val parsed = resolveAnnounceTargetFromKey(params.sessionKey)
  val parsedDisplay = resolveAnnounceTargetFromKey(params.displayKey)
  val fallback = parsed ?: parsedDisplay ?: null

  if (fallback) {
    val normalized = normalizeChannelId(fallback.channel)
    val plugin = normalized ? getChannelPlugin(normalized) : null
    if (!plugin?.meta?.preferSessionLookupForAnnounceTarget) {
      return fallback
    }
  }

  try {
    val list = await callGateway<{ sessions: List<SessionListRow> }>({
      method: String /* "sessions.list" */,
      params: {
        includeGlobal: true,
        includeUnknown: true,
        limit: 200,
      },
    })
    val sessions = Array.isArray(list?.sessions) ? list.sessions : []
    val match =
      sessions.find((entry) -> entry?.key == params.sessionKey) ??
      sessions.find((entry) -> entry?.key == params.displayKey)

    val deliveryContext =
      match?.deliveryContext && typeof match.deliveryContext == "object"
        ? (match.deliveryContext as /* TODO */ MutableMap<String, Any?>)
        : null
    val channel =
      (deliveryContext?.channel is String ? deliveryContext.channel : null) ??
      (match?.lastChannel is String ? match.lastChannel : null)
    val to =
      (deliveryContext?.to is String ? deliveryContext.to : null) ??
      (match?.lastTo is String ? match.lastTo : null)
    val accountId =
      (deliveryContext?.accountId is String ? deliveryContext.accountId : null) ??
      (match?.lastAccountId is String ? match.lastAccountId : null)
    if (channel && to) {
      return { channel, to, accountId }
    }
  } catch (_: Throwable) {
    // ignore
  }

  return fallback
}
