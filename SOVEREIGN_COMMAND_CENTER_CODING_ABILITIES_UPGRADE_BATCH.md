# Sovereign Command Center: Coding Abilities Upgrade Batch

## Objectives
Fix the two core failure modes identified in executive testing:
1. **Framework-Neutral Capability**: The agent must natively inspect, build, test, and repair unfamiliar Android (Kotlin/Gradle), Rust, Java, Python, Go, backend, infrastructure, and mixed-stack monorepo projects rather than asking the CEO to convert them.
2. **Evidence-Complete Activity Logs**: No activity card may display as "completed" without captured execution output (stdout/stderr), exit status, timing, working directory, and cryptographic digest (or an explicit "completed with no output" record).

## Implementation Architecture

### 1. Two-Plane Architecture & Typed Broker
- **Command Center Plane**: Intent, governance, approvals, policy, verification, and audit.
- **Coding Agent Plane**: Inspection, workspace edits, builds, diagnostics, and repairs.
- **Broker Lease Protocol**: Validates owner, session, branch, environment, and sequence. Redacts secrets from execution telemetry.

### 2. Isolated Storage Namespaces
- `cc:*`: Command Center governance and executive state.
- `agent:*`: Coding agent execution state and session scratchpad.
- `broker:*`: Lease tokens, cross-plane requests, and verified evidence payloads.
- Explicit regression tests preventing cross-namespace read/write violations.

### 3. Framework-Neutral Project Intelligence & Adapters
- Detection of project types:
  - Android / Kotlin / Java (Gradle, Maven, gradlew)
  - Rust (Cargo)
  - Python (Poetry, Pipenv, requirements.txt, pyproject.toml)
  - Go (go.mod)
  - Node/TypeScript (pnpm, yarn, npm, bun)
- Standardized lifecycle operations: `detect`, `inspect`, `build`, `test`, `lint`, `repair`.

### 4. Autonomous Diagnostic & Recovery Pipeline
- Structured parse -> root cause classify -> generate patch -> test verify -> complete/escalate.

### 5. Evidence-Complete Activity Tracking
- Run cards must store exit status, duration, stdout/stderr, cwd, and sha256 output digest.
- Prevents empty cards or unverified status claims.
