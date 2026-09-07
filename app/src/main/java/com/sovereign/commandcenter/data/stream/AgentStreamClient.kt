package com.sovereign.commandcenter.data.stream

import com.sovereign.commandcenter.data.api.ApiClient
import com.sovereign.commandcenter.data.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class StreamEvent {
    data class TaskRunning(val tool: String, val stepId: String?, val turn: Int?) : StreamEvent()
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

class AgentStreamClient {
    private val client = ApiClient.okHttpClient
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    @Volatile
    private var currentContextVersion: Long = 0L

    fun streamPrompt(
        context: StreamRequestContext,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<StreamEvent> {
        val flow = MutableSharedFlow<StreamEvent>(replay = 1)
        currentContextVersion = context.workspaceContextVersion

        // Cancel previous request to protect against stale interleaved responses
        cancelCurrentStream()

        CoroutineScope(Dispatchers.IO).launch {
            val payload = JSONObject().apply {
                put("prompt", context.prompt)
                put("sessionId", context.sessionId)
                if (context.repositoryId != null) {
                    put("repo", context.repositoryId)
                    put("owner", "Trinity-Universe")
                }
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
                    flow.emit(StreamEvent.Error("HTTP Error: ${response.code} ${response.message}"))
                    flow.emit(StreamEvent.Completed)
                    return@launch
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    flow.emit(StreamEvent.Error("Response body is empty"))
                    flow.emit(StreamEvent.Completed)
                    return@launch
                }

                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    // Stale context suppression: discard events if context version changed
                    if (context.workspaceContextVersion != currentContextVersion) {
                        break
                    }

                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                        // Heartbeat / comment line
                        continue
                    }

                    if (currentLine.startsWith("data:")) {
                        val jsonStr = currentLine.removePrefix("data:").trim()
                        if (jsonStr.isNotEmpty()) {
                            parseAndEmitEvent(jsonStr, flow)
                        }
                    }
                }
                flow.emit(StreamEvent.Completed)
            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    flow.emit(StreamEvent.Error(e.message ?: "Stream interrupted"))
                    flow.emit(StreamEvent.Completed)
                }
            } finally {
                activeCall = null
            }
        }

        return flow
    }

    private suspend fun parseAndEmitEvent(jsonStr: String, flow: MutableSharedFlow<StreamEvent>) {
        try {
            val json = JSONObject(jsonStr)
            when (json.optString("type")) {
                "task_running" -> {
                    flow.emit(
                        StreamEvent.TaskRunning(
                            tool = json.optString("tool", "executing"),
                            stepId = json.optString("stepId", null),
                            turn = json.optInt("turn")
                        )
                    )
                }
                "approval_required" -> {
                    flow.emit(
                        StreamEvent.ApprovalRequired(
                            approvalId = json.getString("approvalId"),
                            tool = json.optString("tool", "system"),
                            dangerReason = json.optString("dangerReason", "Action requires CEO confirmation."),
                            message = json.optString("message", "")
                        )
                    )
                }
                "approval_granted" -> {
                    flow.emit(
                        StreamEvent.ApprovalGranted(
                            approvalId = json.getString("approvalId"),
                            tool = json.optString("tool", "")
                        )
                    )
                }
                "task_failed" -> {
                    flow.emit(
                        StreamEvent.TaskFailed(
                            task = json.optString("task", null),
                            summary = json.optString("summary", "Execution failed")
                        )
                    )
                }
                "session_conflict" -> {
                    flow.emit(
                        StreamEvent.SessionConflict(
                            code = json.optString("code", null),
                            message = json.optString("message", "Session conflict")
                        )
                    )
                }
                "error" -> {
                    flow.emit(StreamEvent.Error(json.optString("error", "Unknown agent error")))
                }
                else -> {
                    val content = json.optString("content") ?: json.optString("message") ?: ""
                    if (content.isNotEmpty()) {
                        flow.emit(StreamEvent.ContentChunk(content))
                    }
                }
            }
        } catch (_: Exception) {
            // Passthrough unformatted chunk as raw text
            flow.emit(StreamEvent.ContentChunk(jsonStr))
        }
    }

    fun cancelCurrentStream() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
    }
}
