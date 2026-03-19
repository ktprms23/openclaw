@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/sessions-announce-target.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { getChannelPlugin, normalizeChannelId } from "../../channels/plugins/index.js";
// TODO(port-deps): import { callGateway } from "../../gateway/call.js";
// TODO(port-deps): import { SessionListRow } from "./sessions-helpers.js";
// TODO(port-deps): import type { AnnounceTarget } from "./sessions-send-helpers.js";
// TODO(port-deps): import { resolveAnnounceTargetFromKey } from "./sessions-send-helpers.js";

suspend fun resolveAnnounceTarget(params: {
  sessionKey: string;
  displayKey: string;
}): Promise<AnnounceTarget | null> {
  val parsed = resolveAnnounceTargetFromKey(params.sessionKey);
  val parsedDisplay = resolveAnnounceTargetFromKey(params.displayKey);
  val fallback = parsed ?: parsedDisplay ?: null;

  if (fallback) {
    val normalized = normalizeChannelId(fallback.channel);
    val plugin = normalized ? getChannelPlugin(normalized) : null;
    if (!plugin?.meta?.preferSessionLookupForAnnounceTarget) {
      return fallback;
    }
  }

  try {
    val list = await callGateway<{ sessions: Array<SessionListRow> }>({
      method: "sessions.list",
      params: {
        includeGlobal: true,
        includeUnknown: true,
        limit: 200,
      },
    });
    val sessions = Array.isArray(list?.sessions) ? list.sessions : [];
    val match =
      sessions.find((entry) => entry?.key == params.sessionKey) ??
      sessions.find((entry) => entry?.key == params.displayKey);

    val deliveryContext =
      match?.deliveryContext && typeof match.deliveryContext == "object"
        ? (match.deliveryContext as Record<string, unknown>)
        : null;
    val channel =
      (typeof deliveryContext?.channel == "string" ? deliveryContext.channel : null) ??
      (typeof match?.lastChannel == "string" ? match.lastChannel : null);
    val to =
      (typeof deliveryContext?.to == "string" ? deliveryContext.to : null) ??
      (typeof match?.lastTo == "string" ? match.lastTo : null);
    val accountId =
      (typeof deliveryContext?.accountId == "string" ? deliveryContext.accountId : null) ??
      (typeof match?.lastAccountId == "string" ? match.lastAccountId : null);
    if (channel && to) {
      return { channel, to, accountId };
    }
  } catch {
    // ignore
  }

  return fallback;
}
