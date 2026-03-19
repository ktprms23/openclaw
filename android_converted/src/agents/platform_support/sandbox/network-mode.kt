package agents.platform_support.sandbox

// Source: src/agents/sandbox/network-mode.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): Verify TypeScript typealias semantics for NetworkModeBlockReason.
typealias NetworkModeBlockReason = "host" | "container_namespace_join"

fun normalizeNetworkMode(network: String | Nothing?): String | Nothing? {
  val normalized = network?.trim().toLowerCase()
  return normalized || Nothing?
}

fun getBlockedNetworkModeReason(params: {
  network: String | Nothing?
  allowContainerNamespaceJoin?: Boolean
}): NetworkModeBlockReason | Nothing? {
  val normalized = normalizeNetworkMode(params.network)
  if (!normalized) {
    return Nothing?
  }
  if (normalized === "host") {
    return "host"
  }
  if (normalized.startsWith("container:") && params.allowContainerNamespaceJoin !== true) {
    return "container_namespace_join"
  }
  return Nothing?
}

fun isDangerousNetworkMode(network: String | Nothing?): Boolean {
  val normalized = normalizeNetworkMode(network)
  return normalized === "host" || normalized?.startsWith("container:") === true
}
