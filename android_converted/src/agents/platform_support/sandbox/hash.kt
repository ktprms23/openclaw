package agents.platform_support.sandbox

// Source: src/agents/sandbox/hash.ts
// TODO(openclaw-kotlin-port): Mechanical Phase 3 source-to-source translation.
// The goal here is structural fidelity, not compile-ready Kotlin.
// TODO: Replace placeholder imports/runtime shims during platform integration.

// TODO(openclaw-kotlin-port): import crypto from "node:crypto";

fun hashTextSha256(value: String): String {
  return crypto.createHash("sha256").update(value).digest("hex")
}
