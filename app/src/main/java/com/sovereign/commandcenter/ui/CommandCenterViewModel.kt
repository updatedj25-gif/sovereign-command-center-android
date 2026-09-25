package com.sovereign.commandcenter.ui

import com.sovereign.commandcenter.voice.VoiceManager
import com.sovereign.commandcenter.voice.VoiceInputState
import com.sovereign.commandcenter.network.ResilientSseParser
import com.sovereign.commandcenter.network.ResilientSseEvent
import com.sovereign.commandcenter.data.models.DurableSession
import com.sovereign.commandcenter.data.models.SessionTranscriptMessage
import com.sovereign.commandcenter.data.models.DurablePacedStep
import com.sovereign.commandcenter.preview.TruthfulPreviewCoordinator
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.update

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
import com.sovereign.commandcenter.data.api.RepoTreeEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ActionCardData(
    val tool: String,
    val description: String,
    val status: String
)

data class SelfHealingTraceData(
    val isAutonomous: Boolean = true,
    val failureReason: String,
    val logicalResolution: String,
    val resolvedCleanly: Boolean = true
)

data class PacedStepData(
    val stepIndex: Int,
    val totalSteps: Int,
    val conversationalPrelude: String,
    val actionCard: ActionCardData,
    val selfHealingTrace: SelfHealingTraceData? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val repositoryContext: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val pacedStep: PacedStepData? = null
)

data class PendingApprovalData(
    val approvalId: String,
    val tool: String,
    val dangerReason: String,
    val repository: String?
)


data class ChatSessionHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val repositoryContext: String?,
    val messages: List<ChatMessage>
)

data class CommandCenterUiState(
    val stagedVoiceInput: String? = null,
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
    val errorMessage: String? = null,
    val repoTree: List<RepoTreeEntry> = emptyList(),
    val isTreeLoading: Boolean = false,
    val treeError: String? = null,
    val chatHistory: List<ChatSessionHistoryItem> = emptyList()
)

class CommandCenterViewModel(
    private val streamClient: AgentStreamClient = AgentStreamClient()
) : ViewModel() {

    // =========================================================================
    // GROUP 4: SEQUENTIAL STEP ACCORDION RUNTIME & VM DIGNITY ERROR INTERCEPT
    // =========================================================================
    
    val sseParser = ResilientSseParser()
    private var voiceManager: VoiceManager? = null

    fun initializeVoice(context: Context) {
        if (voiceManager == null) {
            voiceManager = VoiceManager(context.applicationContext)
            viewModelScope.launch {
                voiceManager?.inputState?.collect { state ->
                    when (state) {
                        is VoiceInputState.ReviewTranscript -> {
                            // Stage dictated text in composer for CEO review; zero silent auto-send
                            stageVoiceTranscript(state.recognizedText)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun startVoiceDictation() {
        voiceManager?.startListening()
    }

    fun stopVoiceDictation() {
        voiceManager?.stopListening()
    }

    fun stopSpeaking() {
        // Immediate halt of speech without cancelling background execution plane
        voiceManager?.stopSpeaking()
    }

    fun speakSanitizedResponse(rawText: String) {
        // Strip code fences, tokens, and markdown before voice synthesis
        val sanitized = rawText
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("`[^`]*`"), "")
            .replace(Regex("(ghp_[A-Za-z0-9_]{36}|sk-[A-Za-z0-9]{32,}|Bearer\\s+[A-Za-z0-9._~+/-]+)"), "[Credential Redacted]")
            .replace(Regex("[#*_>~]"), "")
            .trim()
        if (sanitized.isNotBlank()) {
            voiceManager?.speakResponse(sanitized, true)
        }
    }

    private fun stageVoiceTranscript(transcription: String) {
        _uiState.update { current ->
            current.copy(stagedVoiceInput = transcription)
        }
    }

    fun clearStagedVoiceInput() {
        _uiState.update { it.copy(stagedVoiceInput = null) }
    }

    // VM Dignity Error Guard: captures strictly terminal 15-20 lines on failure
    fun extractVmDignityTail(rawLog: String): String {
        val nonBlankLines = rawLog.lines().filter { it.isNotBlank() }
        val tail = nonBlankLines.takeLast(20)
        return tail.joinToString("\n")
    }

    fun applyVmDignityErrorFreeze(
        failedStepTitle: String,
        errorTrace: String,
        completedCount: Int,
        totalCount: Int
    ) {
        val boundedTail = extractVmDignityTail(errorTrace)
        val remaining = (totalCount - completedCount - 1).coerceAtLeast(0)
        
        val failureDiagnosis = "VM Dignity Freeze on '$failedStepTitle':\n" +
            "--------------------------------------------------\n" +
            boundedTail + "\n" +
            "--------------------------------------------------\n" +
            "Task Ledger -> Done: $completedCount | In Progress: 0 | Remaining: $remaining"

        val safeRemedy = "Downstream execution halted. Proposing resource-conscious resolution without cache wipe."

        val dignityAccordion = PacedStepData(
            stepIndex = completedCount + 1,
            totalSteps = totalCount,
            conversationalPrelude = "Execution stopped at hurdle. VM Dignity preserved.",
            actionCard = ActionCardData(
                tool = failedStepTitle,
                description = "Halted: $failedStepTitle",
                status = "FAILED"
            ),
            selfHealingTrace = SelfHealingTraceData(
                isAutonomous = false,
                failureReason = failureDiagnosis,
                logicalResolution = safeRemedy,
                resolvedCleanly = false
            )
        )

        _uiState.update { state ->
            val updatedMessages = state.chatMessages + ChatMessage(
                role = "assistant",
                content = "[VM Dignity Error Guard Activated] Execution safely paused on hurdle.",
                repositoryContext = state.selectedRepository,
                pacedStep = dignityAccordion
            )
            state.copy(
                chatMessages = updatedMessages,
                isStreaming = false,
                errorMessage = "Execution frozen by VM Dignity Guard."
            )
        }
    }


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
        loadRepoTree(repo)
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
                        // Defect 1 & 3 Fix: Halt execution, do not pollute text bubble with raw error prose
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            errorMessage = "Task paused: " + event.summary.take(80)
                        )
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
        // Stop any active spoken audio playback on stream cancellation or session switch
        voiceManager?.stopSpeaking()
        val currentSessionId = _uiState.value.session?.ownerId ?: "ceo-command-session"
        activeStreamJob?.cancel()
        activeStreamJob = null
        streamClient.cancelCurrentStream()
        if (_uiState.value.isStreaming) {
            _uiState.value = _uiState.value.copy(isStreaming = false)
        }
        viewModelScope.launch {
            ApiClient.stopAgentSession(sessionId = currentSessionId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveStream()
    }

        fun startNewChat() {
        val currentMsgs = _uiState.value.chatMessages
        val updatedHistory = if (currentMsgs.isNotEmpty()) {
            val firstUserPrompt = currentMsgs.firstOrNull { it.role == "user" }?.content?.take(35) ?: "CEO Session"
            val item = ChatSessionHistoryItem(
                title = firstUserPrompt,
                repositoryContext = _uiState.value.selectedRepository,
                messages = currentMsgs
            )
            listOf(item) + _uiState.value.chatHistory.take(19)
        } else {
            _uiState.value.chatHistory
        }
        _uiState.value = _uiState.value.copy(
            chatMessages = emptyList(),
            isStreaming = false,
            chatHistory = updatedHistory
        )
    }

    fun restoreSession(historyItem: ChatSessionHistoryItem) {
        cancelActiveStream()
        _uiState.value = _uiState.value.copy(
            chatMessages = historyItem.messages,
            selectedRepository = historyItem.repositoryContext,
            isStreaming = false
        )
        loadRepoTree(historyItem.repositoryContext)
    }

        private var activeTreeJob: Job? = null

    fun loadRepoTree(rawRepo: String?, branch: String = _uiState.value.selectedBranch) {
        activeTreeJob?.cancel()
        if (rawRepo.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                repoTree = emptyList(),
                isTreeLoading = false,
                treeError = null
            )
            return
        }
        val parts = rawRepo.split("/")
        val owner = if (parts.size >= 2) parts[0] else (_uiState.value.orgHealth?.organization ?: "sovereign")
        val repo = if (parts.size >= 2) parts[1] else parts[0]

        _uiState.value = _uiState.value.copy(isTreeLoading = true, treeError = null)
        activeTreeJob = viewModelScope.launch {
            val result = withTimeoutOrNull(8000L) {
                ApiClient.fetchRepoTree(owner, repo, branch)
            }
            if (result == null) {
                // Defect 2 Fix: Bounded 8s timeout with retry state
                _uiState.value = _uiState.value.copy(
                    repoTree = emptyList(),
                    isTreeLoading = false,
                    treeError = "Tree request timed out after 8s. Tap to retry."
                )
            } else {
                result.onSuccess { entries ->
                    _uiState.value = _uiState.value.copy(
                        repoTree = entries,
                        isTreeLoading = false,
                        treeError = null
                    )
                }.onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        repoTree = emptyList(),
                        isTreeLoading = false,
                        treeError = err.message ?: "Failed to load repo tree"
                    )
                }
            }
        }
    }

}