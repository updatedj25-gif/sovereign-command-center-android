package com.sovereign.commandcenter.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream

sealed class ResilientSseEvent {
    data class ContentChunk(val text: String) : ResilientSseEvent()
    data class StepUpdate(
        val stepId: String,
        val title: String,
        val state: String, // queued, running, recovering, completed, failed, cancelled
        val diagnosis: String? = null,
        val safeSummary: String? = null
    ) : ResilientSseEvent()
    data class Terminal(val runId: String, val status: String, val reason: String) : ResilientSseEvent()
    data class RecoverableStreamError(val message: String, val isMutatingAction: Boolean = false) : ResilientSseEvent()
}

class ResilientSseParser {

    private val lineBuffer = StringBuilder()

    fun parseStream(
        inputStream: InputStream,
        expectedRunId: String?,
        onEvent: (ResilientSseEvent) -> Unit
    ) {
        val reader = BufferedReader(inputStream.reader())
        var line: String?

        try {
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.isBlank()) {
                    // Frame boundary reached (\n\n); dispatch buffered lines
                    if (lineBuffer.isNotEmpty()) {
                        processBufferedFrame(lineBuffer.toString(), expectedRunId, onEvent)
                        lineBuffer.clear()
                    }
                } else {
                    lineBuffer.append(currentLine).append("\n")
                }
            }
            // Flush any residual content on clean stream termination
            if (lineBuffer.isNotEmpty()) {
                processBufferedFrame(lineBuffer.toString(), expectedRunId, onEvent)
                lineBuffer.clear()
            }
        } catch (e: Exception) {
            onEvent(ResilientSseEvent.RecoverableStreamError("Safe stream disconnect: ${e.localizedMessage}"))
        }
    }

    private fun processBufferedFrame(
        rawBlock: String,
        expectedRunId: String?,
        onEvent: (ResilientSseEvent) -> Unit
    ) {
        val lines = rawBlock.lines()
        var eventType = "message"
        val dataBuffer = StringBuilder()

        for (l in lines) {
            when {
                l.startsWith("event:") -> eventType = l.removePrefix("event:").trim()
                l.startsWith("data:") -> dataBuffer.append(l.removePrefix("data:").trim()).append(" ")
            }
        }

        val rawData = dataBuffer.toString().trim()
        if (rawData.isEmpty() || rawData == "[DONE]") return

        try {
            val json = JSONObject(rawData)

            // Authoritative terminal event verification
            if (eventType == "terminal" || json.optString("type") == "terminal") {
                val runId = json.optString("runId")
                if (expectedRunId == null || expectedRunId == runId || runId.isEmpty()) {
                    onEvent(
                        ResilientSseEvent.Terminal(
                            runId = runId,
                            status = json.optString("status", "stopped"),
                            reason = json.optString("terminalReason", "Execution completed by operator")
                        )
                    )
                }
                return
            }

            // PacedStep sequential accordion event
            if (eventType == "step" || json.has("stepId")) {
                onEvent(
                    ResilientSseEvent.StepUpdate(
                        stepId = json.optString("stepId"),
                        title = json.optString("title", "Active Task Step"),
                        state = json.optString("state", "running"),
                        diagnosis = json.optString("diagnosis").takeIf { it.isNotEmpty() },
                        safeSummary = json.optString("safeSummary").takeIf { it.isNotEmpty() }
                    )
                )
                return
            }

            // Text token chunk
            if (json.has("text")) {
                onEvent(ResilientSseEvent.ContentChunk(json.getString("text")))
            } else if (json.has("content")) {
                onEvent(ResilientSseEvent.ContentChunk(json.getString("content")))
            }
        } catch (e: Exception) {
            // Malformed JSON safety: Keep conversation intact, emit non-fatal diagnostic
            onEvent(ResilientSseEvent.RecoverableStreamError("Safely recovered from malformed SSE chunk: ${e.message}"))
        }
    }
}
