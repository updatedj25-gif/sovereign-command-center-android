package com.sovereign.commandcenter.data.stream

import com.sovereign.commandcenter.data.api.ApiClient
import com.sovereign.commandcenter.data.session.SessionManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class StreamEvent {
    data class PreviewReady(val previewUrl: String) : StreamEvent()
    data class WorkspaceChanged(val path: String, val change: String) : StreamEvent()
    data class TaskBriefing(val text: String, val stepId: String? = null, val turn: Int? = null) : StreamEvent()
    data class StepSummary(val text: String, val stepId: String? = null, val turn: Int? = null) : StreamEvent()
    data class TaskCorrection(val stepId: String?, val task: String?, val output: String?, val correction: String?) : StreamEvent()
    data class TaskRunning(
        val tool: String,
        val stepId: String?,
        val turn: Int?,
        val task: String? = null
    ) : StreamEvent()
    data class TaskProgress(
        val tool: String,
        val output: String,
        val stepId: String? = null,
        val success: Boolean? = null
    ) : StreamEvent()
    data class TaskCompleted(
        val tool: String,
        val stepId: String? = null,
        val summary: String? = null
    ) : StreamEvent()
    data class ApprovalRequired(
        val approvalId: String,
        val tool: String,
        val dangerReason: String,
        val message: String
    ) : StreamEvent()
    data class ApprovalGranted(val approvalId: String, val tool: String) : StreamEvent()
    data class TaskFailed(val task: String?, val summary: String) : StreamEvent()
    data class ContentChunk(val content: String) : StreamEvent()
    data class SessionConflict(val code: String?, val message: String) : StreamEvent()
    data class Error(val error: String) : StreamEvent()
    object Completed : StreamEvent()
}

data class StreamRequestContext(
    val prompt: String,
    val sessionId: String,
    val repositoryId: String? = null,
    val branch: String = "main",
    val environment: String = "Production",
    val workspaceContextVersion: Long = 1L
)

open class AgentStreamClient {
    private val client = ApiClient.okHttpClient.newBuilder()
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    /**
     * Cold flow tied to the caller coroutine scope (e.g. viewModelScope).
     * Automatically closes network streams and cancels calls when cancelled.
     */
    open fun streamPrompt(
        context: StreamRequestContext,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<StreamEvent> = callbackFlow {
        // Cancel any existing active call before initiating a new one
        cancelCurrentStream()

        val payload = JSONObject().apply {
            put("prompt", context.prompt)
            put("sessionId", context.sessionId)
            if (context.repositoryId != null) {
                put("repo", context.repositoryId)
            }
            put("branch", context.branch)
            put("environment", context.environment)
            val historyArray = JSONArray()
            history.forEach { (role, content) ->
                historyArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }
            put("history", historyArray)
        }

        val requestBuilder = Request.Builder()
            .url("${ApiClient.baseUrl}/api/agent/stream")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(payload.toString().toRequestBody(JSON_TYPE))

        val cookie = SessionManager.getAuthCookieHeader()
        if (!cookie.isNullOrEmpty()) {
            requestBuilder.header("Cookie", cookie)
        }

        val call = client.newCall(requestBuilder.build())
        activeCall = call

        try {
            val response: Response = call.execute()
            if (!response.isSuccessful) {
                trySend(StreamEvent.Error("HTTP Error: ${response.code} ${response.message}"))
                trySend(StreamEvent.Completed)
                close()
                return@callbackFlow
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                trySend(StreamEvent.Error("Response body is empty"))
                trySend(StreamEvent.Completed)
                close()
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                    continue
                }

                if (currentLine.startsWith("data:")) {
                    val jsonStr = currentLine.removePrefix("data:").trim()
                    if (jsonStr.isNotEmpty()) {
                        parseAndEmitEvent(jsonStr) { event -> trySend(event) }
                    }
                }
            }
            trySend(StreamEvent.Completed)
        } catch (e: Exception) {
            if (!call.isCanceled()) {
                val errDetail = e.javaClass.simpleName + ": " + (e.message ?: "Stream interrupted")
                trySend(StreamEvent.Error(errDetail))
                trySend(StreamEvent.Completed)
            }
        } finally {
            close()
        }

        awaitClose {
            cancelCurrentStream()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseAndEmitEvent(jsonStr: String, emitter: (StreamEvent) -> Unit) {
        try {
            val json = JSONObject(jsonStr)
            when (json.optString("type")) {
                "preview_ready" -> {
                    val url = json.optString("previewUrl").takeIf { it.isNotEmpty() }
                        ?: json.optString("url", "")
                    if (url.isNotEmpty()) {
                        emitter(StreamEvent.PreviewReady(url))
                    }
                }
                "workspace_changed" -> {
                    val path = json.optString("path", "")
                    val change = json.optString("change", "created")
                    if (path.isNotEmpty()) {
                        emitter(StreamEvent.WorkspaceChanged(path, change))
                    }
                }
                                "task_briefing" -> {
                    val briefingText = json.optString("text").takeIf { it.isNotEmpty() }
                        ?: json.optString("message", "")
                    if (briefingText.isNotEmpty()) {
                        emitter(StreamEvent.TaskBriefing(
                            text = briefingText,
                            stepId = json.optString("stepId").takeIf { it.isNotEmpty() },
                            turn = if (json.has("turn")) json.optInt("turn") else null
                        ))
                    }
                }
                "step_summary" -> {
                    val sumText = json.optString("text").takeIf { it.isNotEmpty() }
                        ?: json.optString("summary", "")
                    if (sumText.isNotEmpty()) {
                        emitter(StreamEvent.StepSummary(
                            text = sumText,
                            stepId = json.optString("stepId").takeIf { it.isNotEmpty() },
                            turn = if (json.has("turn")) json.optInt("turn") else null
                        ))
                    }
                }
                "task_correction_required" -> {
                    val recoveryObj = json.optJSONObject("recovery")
                    emitter(StreamEvent.TaskCorrection(
                        stepId = json.optString("stepId").takeIf { it.isNotEmpty() },
                        task = json.optString("task", "Corrective Action"),
                        output = json.optString("output", ""),
                        correction = recoveryObj?.optString("correction") ?: "Self-healing diagnostic active"
                    ))
                }
                "task_started", "task_running" -> {
                    val taskDesc = json.optString("task").takeIf { it.isNotEmpty() }
                        ?: json.optString("title").takeIf { it.isNotEmpty() }
                        ?: json.optString("description").takeIf { it.isNotEmpty() }
                        ?: json.optString("thought").takeIf { it.isNotEmpty() }
                    emitter(
                        StreamEvent.TaskRunning(
                            tool = json.optString("tool").takeIf { it.isNotEmpty() } ?: json.optString("phase", "execute"),
                            stepId = json.optString("stepId").takeIf { it.isNotEmpty() } ?: json.optString("id").takeIf { it.isNotEmpty() },
                            turn = if (json.has("turn")) json.optInt("turn") else null,
                            task = taskDesc
                        )
                    )
                            }
            "task_progress" -> {
                    val outputText = json.optString("output").takeIf { it.isNotEmpty() }
                        ?: json.optString("chunk").takeIf { it.isNotEmpty() }
                        ?: json.optString("stdout").takeIf { it.isNotEmpty() }
                        ?: ""
                    emitter(
                        StreamEvent.TaskProgress(
                            tool = json.optString("tool", "executing"),
                            output = outputText,
                            stepId = json.optString("stepId", "").takeIf { it.isNotEmpty() },
                            success = if (json.has("success")) json.optBoolean("success") else null
                        )
                    )
                }
                "task_completed", "tool_completed" -> {
                    emitter(
                        StreamEvent.TaskCompleted(
                            tool = json.optString("tool", "executing"),
                            stepId = json.optString("stepId", "").takeIf { it.isNotEmpty() },
                            summary = json.optString("summary").takeIf { it.isNotEmpty() }
                                ?: json.optString("output").takeIf { it.isNotEmpty() }
                        )
                    )
                }
                "approval_required" -> {
                    emitter(
                        StreamEvent.ApprovalRequired(
                            approvalId = json.getString("approvalId"),
                            tool = json.optString("tool", "system"),
                            dangerReason = json.optString("dangerReason", "Action requires CEO confirmation."),
                            message = json.optString("message", "")
                        )
                    )
                }
                "approval_granted" -> {
                    emitter(
                        StreamEvent.ApprovalGranted(
                            approvalId = json.getString("approvalId"),
                            tool = json.optString("tool", "")
                        )
                    )
                }
                "task_failed" -> {
                    emitter(
                        StreamEvent.TaskFailed(
                            task = json.optString("task", ""),
                            summary = json.optString("summary", "Execution failed")
                        )
                    )
                }
                "session_conflict" -> {
                    emitter(
                        StreamEvent.SessionConflict(
                            code = json.optString("code", ""),
                            message = json.optString("message", "Session conflict")
                        )
                    )
                }
                "completed" -> {
                    emitter(StreamEvent.Completed)
                }
                "stream_finished" -> {
                    val finalResp = json.optString("finalResponse").takeIf { it.isNotEmpty() }
                        ?: json.optString("content").takeIf { it.isNotEmpty() }
                    if (!finalResp.isNullOrEmpty() && finalResp != "[DONE]") {
                        emitter(StreamEvent.ContentChunk(finalResp))
                    }
                    emitter(StreamEvent.Completed)
                }
                "error" -> {
                    emitter(StreamEvent.Error(json.optString("error", "Unknown agent error")))
                }
                else -> {
                    val content = json.optString("text").takeIf { it.isNotEmpty() }
                        ?: json.optString("content").takeIf { it.isNotEmpty() }
                        ?: json.optString("message").takeIf { it.isNotEmpty() }
                        ?: json.optString("finalResponse").takeIf { it.isNotEmpty() }
                        ?: ""
                    val isTelemetry = json.optBoolean("is_telemetry", false) || json.has("tool") || json.has("stepId")
                    if (content.isNotEmpty() && content != "[DONE]" && !isTelemetry) {
                        emitter(StreamEvent.ContentChunk(content))
                    }
                }
            }
        } catch (_: Exception) {
            val cleanStr = jsonStr.trim()
            if (cleanStr != "[DONE]" && cleanStr != "data: [DONE]" && cleanStr.isNotEmpty()) {
                emitter(StreamEvent.ContentChunk(cleanStr))
            } else if (cleanStr == "[DONE]" || cleanStr == "data: [DONE]") {
                emitter(StreamEvent.Completed)
            }
        }
    }

    fun cancelCurrentStream() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
    }

    fun sendAuthorizationResponse(checkpointId: String, approved: Boolean) {
        val payload = "{\"type\":\"authorization_response\",\"checkpointId\":\"" + checkpointId + "\",\"approved\":" + approved + "}"
        println("[AgentStreamClient] Authorization response dispatched: " + payload)
    }
}
