@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

/**
 * Phase 2 Kotlin port for `src/agents/tools/nodes-utils.ts`.
 * Structural translation only; external dependencies remain as placeholders where needed.
 */

// TODO(port-deps): import { parseNodeList, parsePairingList } from "../../shared/node-list-parse.js";
// TODO(port-deps): import type { NodeListNode } from "../../shared/node-list-types.js";
// TODO(port-deps): import { resolveNodeFromNodeList, resolveNodeIdFromNodeList } from "../../shared/node-resolve.js";
// TODO(port-deps): import { callGatewayTool, type GatewayCallOptions } from "./gateway.js";

// export type { NodeListNode };

typealias DefaultNodeFallback = Any /* TODO: translate TypeScript alias */

typealias DefaultNodeSelectionOptions = Any /* TODO: translate TypeScript alias */

fun messageFromError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error == "string") {
    return error;
  }
  if (
    typeof error == "object" &&
    error != null &&
    "message" in error &&
    typeof (error as { message?: unknown }).message == "string"
  ) {
    return (error as { message: string }).message;
  }
  if (typeof error == "object" && error != null) {
    try {
      return JSON.stringify(error);
    } catch {
      return "";
    }
  }
  return "";
}

fun shouldFallbackToPairList(error: unknown): boolean {
  val message = messageFromError(error).toLowerCase();
  if (!message.includes("node.list")) {
    return false;
  }
  return (
    message.includes("unknown method") ||
    message.includes("method not found") ||
    message.includes("not implemented") ||
    message.includes("unsupported")
  );
}

suspend fun loadNodes(opts: GatewayCallOptions): Promise<NodeListNode[]> {
  try {
    val res = await callGatewayTool("node.list", opts, {});
    return parseNodeList(res);
  } catch (error) {
    if (!shouldFallbackToPairList(error)) {
      throw error;
    }
    val res = await callGatewayTool("node.pair.list", opts, {});
    val { paired } = parsePairingList(res);
    return paired.map((n) => ({
      nodeId: n.nodeId,
      displayName: n.displayName,
      platform: n.platform,
      remoteIp: n.remoteIp,
    }));
  }
}

fun isLocalMacNode(node: NodeListNode): boolean {
  return (
    node.platform?.toLowerCase().startsWith("mac") == true &&
    typeof node.nodeId == "string" &&
    node.nodeId.startsWith("mac-")
  );
}

fun compareDefaultNodeOrder(a: NodeListNode, b: NodeListNode): number {
  val aConnectedAt = Number.isFinite(a.connectedAtMs) ? (a.connectedAtMs ?: 0) : -1;
  val bConnectedAt = Number.isFinite(b.connectedAtMs) ? (b.connectedAtMs ?: 0) : -1;
  if (aConnectedAt != bConnectedAt) {
    return bConnectedAt - aConnectedAt;
  }
  return a.nodeId.localeCompare(b.nodeId);
}

fun selectDefaultNodeFromList(
  nodes: NodeListNode[],
  options: DefaultNodeSelectionOptions = {},
): NodeListNode | null {
  val capability = options.capability?.trim();
  val withCapability = capability
    ? nodes.filter((n) => (Array.isArray(n.caps) ? n.caps.includes(capability) : true))
    : nodes;
  if (withCapability.length == 0) {
    return null;
  }

  val connected = withCapability.filter((n) => n.connected);
  val candidates = connected.length > 0 ? connected : withCapability;
  if (candidates.length == 1) {
    return candidates[0];
  }

  val preferLocalMac = options.preferLocalMac ?: true;
  if (preferLocalMac) {
    val local = candidates.filter(isLocalMacNode);
    if (local.length == 1) {
      return local[0];
    }
  }

  val fallback = options.fallback ?: "none";
  if (fallback == "none") {
    return null;
  }

  val ordered = [...candidates].toSorted(compareDefaultNodeOrder);
  // Multiple candidates — pick the first connected canvas-capable node.
  // For A2UI and other canvas operations, any node works since multi-node
  // setups broadcast surfaces across devices.
  return ordered[0] ?: null;
}

fun pickDefaultNode(nodes: NodeListNode[]): NodeListNode | null {
  return selectDefaultNodeFromList(nodes, {
    capability: "canvas",
    fallback: "first",
    preferLocalMac: true,
  });
}

suspend fun listNodes(opts: GatewayCallOptions): Promise<NodeListNode[]> {
  return loadNodes(opts);
}

fun resolveNodeIdFromList(
  nodes: NodeListNode[],
  query?: string,
  allowDefault = false,
): string {
  return resolveNodeIdFromNodeList(nodes, query, {
    allowDefault,
    pickDefaultNode: pickDefaultNode,
  });
}

suspend fun resolveNodeId(
  opts: GatewayCallOptions,
  query?: string,
  allowDefault = false,
) {
  return (await resolveNode(opts, query, allowDefault)).nodeId;
}

suspend fun resolveNode(
  opts: GatewayCallOptions,
  query?: string,
  allowDefault = false,
): Promise<NodeListNode> {
  val nodes = await loadNodes(opts);
  return resolveNodeFromNodeList(nodes, query, {
    allowDefault,
    pickDefaultNode: pickDefaultNode,
  });
}
