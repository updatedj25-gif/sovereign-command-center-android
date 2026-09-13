# Command Center <-> Coding Agent Broker Boundary

## Context & Architecture
The system enforces a strict two-plane separation between executive governance and coding execution:

### Command Center Plane ("Brain")
- Handles intent, governance, HITL approvals, security policies, verification, release gating, and operational memory.
- Storage namespace: `cc:*`.
- Never directly executes shell commands, inspects raw workspace files, or mutates source trees.

### Coding Agent Plane ("Hands")
- Handles isolated workspace inspection, code generation, diff/patch applications, builds, tests, previews, and autonomous repairs.
- Storage namespace: `agent:*`.
- Never modifies executive policies, overrides approvals, or mutates release manifests without broker leases.

### Typed Broker Boundary
- Storage namespace: `broker:*` (temporary, lease-bound execution requests and evidence records).
- All interactions between the Command Center and the Coding Agent pass through typed, schema-validated broker leases.
- Enforces context, owner, session, branch, environment, and sequence validation.
- Emits redacted execution events and captures evidence-complete execution logs.
- Strictly prohibits cross-plane storage reads (Command Center cannot read `agent:*` raw keys, Coding Agent cannot read `cc:*` governance keys).

## Key Enforcement Invariants
1. **Namespace Isolation**: `cc:*` strictly isolated from `agent:*`.
2. **Evidence Requirement**: An activity card cannot transition to completed without exit code, stdout/stderr, execution duration, working directory, and output digest.
3. **Zero Cross-plane Pollution**: Ephemeral execution state does not contaminate durable CEO memory.
