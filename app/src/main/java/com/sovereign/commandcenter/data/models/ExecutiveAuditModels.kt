package com.sovereign.commandcenter.data.models

enum class AuditSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class AuditReport(
    val id: String,
    val title: String,
    val timestamp: String,
    val severity: AuditSeverity,
    val summary: String,
    val technicalDetails: String,
    val affectedScope: String
)

object ExecutiveAuditDigestProvider {
    val dailyReports: List<AuditReport> = listOf(
        AuditReport(
            id = "AUDIT-2026-09-25-01",
            title = "Zero-Leak Credential Verification",
            timestamp = "Sep 25, 2026 • 06:00 UTC",
            severity = AuditSeverity.INFO,
            summary = "Server-authoritative token brokerage verified with 0 tokens leaked to client plane.",
            technicalDetails = "Scanned all APK manifests, bytecode, and client logs. No occurrences of ghp_, sk-, or PEM private keys found. Control plane isolation verified.",
            affectedScope = "Control Plane / Token Broker"
        ),
        AuditReport(
            id = "AUDIT-2026-09-25-02",
            title = "3-Way Workspace Boundary Isolation",
            timestamp = "Sep 25, 2026 • 04:30 UTC",
            severity = AuditSeverity.INFO,
            summary = "Greenfield, External, and Org sandboxes confirmed isolated in backend plane.",
            technicalDetails = "Greenfield workspaces mounted read-only upstream. External repos restricted to anonymous HTTPS clone with stripped credentials. Governed Org workspace enforced by ephemeral installation tokens.",
            affectedScope = "Execution Engine / Sandboxing"
        ),
        AuditReport(
            id = "AUDIT-2026-09-25-03",
            title = "VM Dignity & Storage Threshold Guard",
            timestamp = "Sep 25, 2026 • 02:15 UTC",
            severity = AuditSeverity.WARNING,
            summary = "Host disk capacity operating at 91% (430 MB available). gradlew clean locked.",
            technicalDetails = "Enforced single-worker bounded compilation (-Xmx1536m) and prohibited destructive cache clearing. .bak recovery files protected from deletion.",
            affectedScope = "Cloud Shell Infrastructure"
        )
    )
}
