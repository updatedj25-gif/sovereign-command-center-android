package com.sovereign.commandcenter.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovereign.commandcenter.data.api.*
import com.sovereign.commandcenter.data.session.OwnerSession
import com.sovereign.commandcenter.data.session.SessionManager
import com.sovereign.commandcenter.data.stream.AgentStreamClient
import com.sovereign.commandcenter.data.stream.StreamEvent
import com.sovereign.commandcenter.data.stream.StreamRequestContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val repositoryContext: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingApprovalData(
    val approvalId: String,
    val tool: String,
    val dangerReason: String,
    val repository: String?
)

data class CommandCenterUiState(
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val session: OwnerSession? = null,
    val selectedRepository: String? = null,
    val selectedBranch: String = "main",
    val selectedEnvironment: String = "Production",
    val contextVersion: Long = 1L,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val pendingApproval: PendingApprovalData? = null,
    val orgHealth: OrgHealth? = null,
    val ceoMessages: List<CeoMessage> = emptyList(),
    val errorMessage: String? = null
)

class CommandCenterViewModel(
    private val streamClient: AgentStreamClient = AgentStreamClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandCenterUiState())
    val uiState: StateFlow<CommandCenterUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null

    fun checkExistingSession(context: Context) {
        val existing = SessionManager.loadSession(context)
        if (existing != null && SessionManager.isSessionValid(existing)) {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                session = existing
            )
            refreshHealthAndMessages()
        }
    }

    fun login(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _uiState.value = _uiState.value.copy(isAuthenticating = true, errorMessage = null)
        viewModelScope.launch {
            val challengeResult = ApiClient.getPasskeyChallenge()
            if (challengeResult.isFailure) {
                val err = challengeResult.exceptionOrNull()?.message ?: "Network failure"
                _uiState.value = _uiState.value.copy(isAuthenticating = false, errorMessage = err)
                onError(err)
                return@launch
            }

            val challenge = challengeResult.getOrThrow()
            val verifyResult = ApiClient.verifyPasskey(challenge.challengeId)
            _uiState.value = _uiState.value.copy(isAuthenticating = false)

            if (verifyResult.isSuccess) {
                val verified = verifyResult.getOrThrow()
                val session = OwnerSession(
                    token = verified.token,
                    ownerId = verified.ownerId,
                    expiresAtMillis = System.currentTimeMillis() + 86_400_000,
                    displayName = verified.displayName
                )
                SessionManager.saveSession(context, session)
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    session = session
                )
                refreshHealthAndMessages()
                onSuccess()
            } else {
                val err = verifyResult.exceptionOrNull()?.message ?: "Passkey authentication rejected"
                _uiState.value = _uiState.value.copy(errorMessage = err)
                onError(err)
            }
        }
    }

    fun logout(context: Context) {
        cancelActiveStream()
        SessionManager.clearSession(context)
        _uiState.value = CommandCenterUiState()
    }

    fun selectRepository(repo: String?) {
        if (_uiState.value.selectedRepository == repo) return
        cancelActiveStream()
        val newVersion = _uiState.value.contextVersion + 1
        _uiState.value = _uiState.value.copy(
            selectedRepository = repo,
            contextVersion = newVersion,
            isStreaming = false
        )
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        cancelActiveStream()


        val state = _uiState.value
        val userMsg = ChatMessage(
            role = "user",
            content = trimmed,
            repositoryContext = state.selectedRepository
        )
        val assistantPlaceholderId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantPlaceholderId,
            role = "assistant",
            content = "",
            repositoryContext = state.selectedRepository
        )

        _uiState.value = state.copy(
            chatMessages = state.chatMessages + userMsg + initialAssistantMsg,
            isStreaming = true,
            errorMessage = null
        )

        val context = StreamRequestContext(
            prompt = trimmed,
            sessionId = state.session?.ownerId ?: "ceo-command-session",
            repositoryId = state.selectedRepository,
            branch = state.selectedBranch,
            environment = state.selectedEnvironment,
            workspaceContextVersion = state.contextVersion
        )

        val historyPairs = state.chatMessages.map { it.role to it.content }

        activeStreamJob = viewModelScope.launch {
            streamClient.streamPrompt(context, historyPairs).collect { event ->
                if (_uiState.value.contextVersion != context.workspaceContextVersion) {
                    return@collect
                }

                when (event) {
                    is StreamEvent.ContentChunk -> {
                        appendAssistantContent(assistantPlaceholderId, event.content)
                    }
                    is StreamEvent.ApprovalRequired -> {
                        _uiState.value = _uiState.value.copy(
                            pendingApproval = PendingApprovalData(
                                approvalId = event.approvalId,
                                tool = event.tool,
                                dangerReason = event.dangerReason,
                                repository = state.selectedRepository
                            )
                        )
                    }
                    is StreamEvent.ApprovalGranted -> {
                        appendAssistantContent(assistantPlaceholderId, "\n\n[✓ Action authorized by CEO: ${event.tool}]")
                        _uiState.value = _uiState.value.copy(pendingApproval = null)
                    }
                    is StreamEvent.TaskFailed -> {
                        appendAssistantContent(assistantPlaceholderId, "\n\n[⚠ Task Failed: ${event.summary}]")
                    }
                    is StreamEvent.SessionConflict -> {
                        appendAssistantContent(assistantPlaceholderId, "\n\n[⚠ Session Conflict: ${event.message}]")
                    }
                    is StreamEvent.Error -> {
                        appendAssistantContent(assistantPlaceholderId, "\n\n[Error: ${event.error}]")
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                    is StreamEvent.Completed -> {
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                    is StreamEvent.TaskRunning -> {
                    }
                }
            }
        }
    }

    private fun appendAssistantContent(messageId: String, textToAppend: String) {
        val updated = _uiState.value.chatMessages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(content = msg.content + textToAppend)
            } else {
                msg
            }
        }
        _uiState.value = _uiState.value.copy(chatMessages = updated)
    }

    fun submitApproval(approved: Boolean, reason: String? = null) {
        val approval = _uiState.value.pendingApproval ?: return
        viewModelScope.launch {
            val result = ApiClient.submitApproval(
                approvalId = approval.approvalId,
                approved = approved,
                reason = reason,
                sessionId = _uiState.value.session?.ownerId ?: "default-session"
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(pendingApproval = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Approval submission failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun refreshHealthAndMessages() {
        viewModelScope.launch {
            val healthRes = ApiClient.getOrgHealth()
            if (healthRes.isSuccess) {
                _uiState.value = _uiState.value.copy(orgHealth = healthRes.getOrThrow())
            }
            val msgsRes = ApiClient.getMessages()
            if (msgsRes.isSuccess) {
                _uiState.value = _uiState.value.copy(ceoMessages = msgsRes.getOrThrow())
            }
        }
    }

    fun cancelActiveStream() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        streamClient.cancelCurrentStream()
        if (_uiState.value.isStreaming) {
            _uiState.value = _uiState.value.copy(isStreaming = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveStream()
    }
}
