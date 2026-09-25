package com.sovereign.commandcenter.data.models

data class DurableSession(
    val id: String,
    val ownerId: String,
    val title: String,
    val updatedAt: Long,
    val activeRunId: String? = null
)

data class SessionTranscriptMessage(
    val id: String,
    val sessionId: String,
    val runId: String? = null,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isDraft: Boolean = false,
    val pacedSteps: List<DurablePacedStep> = emptyList()
)

data class DurablePacedStep(
    val stepId: String,
    val title: String,
    val state: String,
    val diagnosis: String? = null,
    val safeSummary: String? = null
)
