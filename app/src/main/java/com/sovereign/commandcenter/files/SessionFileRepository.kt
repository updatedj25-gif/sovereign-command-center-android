package com.sovereign.commandcenter.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SessionFileRepository {

    private val _sessionFiles = MutableStateFlow<Map<String, List<SessionFileEntry>>>(emptyMap())
    val sessionFiles: StateFlow<Map<String, List<SessionFileEntry>>> = _sessionFiles.asStateFlow()

    fun recordAgentFileWrite(
        sessionId: String,
        runId: String?,
        path: String,
        changeType: FileChangeType,
        rawDiff: String?,
        sizeBytes: Long
    ) {
        val safeDiff = rawDiff?.let { RedactionUtility.sanitizeDiff(it) }
        val entry = SessionFileEntry(
            sessionId = sessionId,
            runId = runId,
            path = path,
            changeType = changeType,
            sizeBytes = sizeBytes,
            diffContent = safeDiff,
            isRedacted = true
        )

        val currentMap = _sessionFiles.value.toMutableMap()
        val currentList = currentMap[sessionId]?.toMutableList() ?: mutableListOf()
        
        // Upsert by path to maintain clean single manifest per session
        val existingIndex = currentList.indexOfFirst { it.path == path }
        if (existingIndex >= 0) {
            currentList[existingIndex] = entry
        } else {
            currentList.add(entry)
        }
        currentMap[sessionId] = currentList
        _sessionFiles.value = currentMap
    }

    fun getFilesForSession(sessionId: String): List<SessionFileEntry> {
        return _sessionFiles.value[sessionId] ?: emptyList()
    }

    fun parseManifestJson(sessionId: String, runId: String?, manifestJson: String) {
        try {
            val root = JSONObject(manifestJson)
            val filesArray = root.optJSONArray("files") ?: JSONArray()
            for (i in 0 until filesArray.length()) {
                val f = filesArray.getJSONObject(i)
                val type = when (f.optString("type", "MODIFIED").uppercase()) {
                    "CREATED" -> FileChangeType.CREATED
                    "DELETED" -> FileChangeType.DELETED
                    else -> FileChangeType.MODIFIED
                }
                recordAgentFileWrite(
                    sessionId = sessionId,
                    runId = runId,
                    path = f.optString("path"),
                    changeType = type,
                    rawDiff = f.optString("diff"),
                    sizeBytes = f.optLong("size", 0L)
                )
            }
        } catch (_: Exception) {}
    }

    fun clearSessionFiles(sessionId: String) {
        val currentMap = _sessionFiles.value.toMutableMap()
        currentMap.remove(sessionId)
        _uiClear(currentMap)
    }

    private fun _uiClear(newMap: Map<String, List<SessionFileEntry>>) {
        _sessionFiles.value = newMap
    }
}
