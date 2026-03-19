package agents.tools_and_subagents.tools

// Converted from src/agents/tools/nodes-utils.ts for the Kotlin Phase 2 tools/subagents port.
// This is a structural source-to-source translation and is not expected to compile yet.
// TODO: Replace placeholder imports and runtime dependencies with Android/Kotlin equivalents.
// TODO: TypeScript import retained for manual wiring: import { parseNodeList, parsePairingList } from "../../shared/node-list-parse.js";
// TODO: TypeScript import retained for manual wiring: import type { NodeListNode } from "../../shared/node-list-types.js";
// TODO: TypeScript import retained for manual wiring: import { resolveNodeFromNodeList, resolveNodeIdFromNodeList } from "../../shared/node-resolve.js";
// TODO: TypeScript import retained for manual wiring: import { callGatewayTool, type GatewayCallOptions } from "./gateway.js";

type { NodeListNode }

typealias DefaultNodeFallback = "none" | "first"

data class DefaultNodeSelectionOptions(
    val capability: String?,
    val fallback: DefaultNodeFallback?,
    val preferLocalMac: Boolean?,
)

fun messageFromError(error: Any?): String {
  if (error instanceof Error) {
    return error.message
  }
  if (error is String) {
    return error
  }
  if (
    typeof error == "object" &&
    error != null &&
    "message" in error &&
    typeof (error as /* TODO */ { message?: Any? }).message == "String"
  ) {
    return (error as /* TODO */ { message: String }).message
  }
  if (typeof error == "object" && error != null) {
    try {
      return JSON.stringify(error)
    } catch (_: Throwable) {
      return ""
    }
  }
  return ""
}

fun shouldFallbackToPairList(error: Any?): Boolean {
  val message = messageFromError(error).toLowerCase()
  if (!message.includes("node.list")) {
    return false
  }
  return (
    message.includes("Any? method") ||
    message.includes("method not found") ||
    message.includes("not implemented") ||
    message.includes("unsupported")
  )
}

suspend fun loadNodes(opts: GatewayCallOptions): Deferred<List<NodeListNode>> {
  try {
    val res = await callGatewayTool("node.list", opts, {})
    return parseNodeList(res)
  } catch (error) {
    if (!shouldFallbackToPairList(error)) {
      throw error
    }
    val res = await callGatewayTool("node.pair.list", opts, {})
    val { paired } = parsePairingList(res)
    return paired.map((n) -> ({
      nodeId: n.nodeId,
      displayName: n.displayName,
      platform: n.platform,
      remoteIp: n.remoteIp,
    }))
  }
}

fun isLocalMacNode(node: NodeListNode): Boolean {
  return (
    node.platform?.toLowerCase().startsWith("mac") == true &&
    node.nodeId is String &&
    node.nodeId.startsWith("mac-")
  )
}

fun compareDefaultNodeOrder(a: NodeListNode, b: NodeListNode): Double {
  val aConnectedAt = Number.isFinite(a.connectedAtMs) ? (a.connectedAtMs ?: 0) : -1
  val bConnectedAt = Number.isFinite(b.connectedAtMs) ? (b.connectedAtMs ?: 0) : -1
  if (aConnectedAt != bConnectedAt) {
    return bConnectedAt - aConnectedAt
  }
  return a.nodeId.localeCompare(b.nodeId)
}

fun selectDefaultNodeFromList(
  nodes: List<NodeListNode>,
  options: DefaultNodeSelectionOptions = {},
): NodeListNode? {
  val capability = options.capability?.trim()
  val withCapability = capability
    ? nodes.filter((n) -> (Array.isArray(n.caps) ? n.caps.includes(capability) : true))
    : nodes
  if (withCapability.length == 0) {
    return null
  }

  val connected = withCapability.filter((n) -> n.connected)
  val candidates = connected.length > 0 ? connected : withCapability
  if (candidates.length == 1) {
    return candidates[0]
  }

  val preferLocalMac = options.preferLocalMac ?: true
  if (preferLocalMac) {
    val local = candidates.filter(isLocalMacNode)
    if (local.length == 1) {
      return local[0]
    }
  }

  val fallback = options.fallback ?: String /* "none" */
  if (fallback == "none") {
    return null
  }

  val ordered = [...candidates].toSorted(compareDefaultNodeOrder)
  // Multiple candidates — pick the first connected canvas-capable node.
  // For A2UI and other canvas operations, Any? node works since multi-node
  // setups broadcast surfaces across devices.
  return ordered[0] ?: null
}

fun pickDefaultNode(nodes: List<NodeListNode>): NodeListNode? {
  return selectDefaultNodeFromList(nodes, {
    capability: String /* "canvas" */,
    fallback: String /* "first" */,
    preferLocalMac: true,
  })
}

suspend fun listNodes(opts: GatewayCallOptions): Deferred<List<NodeListNode>> {
  return loadNodes(opts)
}

fun resolveNodeIdFromList(
  nodes: List<NodeListNode>,
  query?: String,
  allowDefault = false,
): String {
  return resolveNodeIdFromNodeList(nodes, query, {
    allowDefault,
    pickDefaultNode: pickDefaultNode,
  })
}

suspend fun resolveNodeId(
  opts: GatewayCallOptions,
  query?: String,
  allowDefault = false,
) {
  return (await resolveNode(opts, query, allowDefault)).nodeId
}

suspend fun resolveNode(
  opts: GatewayCallOptions,
  query?: String,
  allowDefault = false,
): Deferred<NodeListNode> {
  val nodes = await loadNodes(opts)
  return resolveNodeFromNodeList(nodes, query, {
    allowDefault,
    pickDefaultNode: pickDefaultNode,
  })
}
