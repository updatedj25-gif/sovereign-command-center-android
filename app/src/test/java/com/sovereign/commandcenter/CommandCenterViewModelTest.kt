package com.sovereign.commandcenter

import com.sovereign.commandcenter.data.stream.AgentStreamClient
import com.sovereign.commandcenter.data.stream.StreamEvent
import com.sovereign.commandcenter.data.stream.StreamRequestContext
import com.sovereign.commandcenter.ui.CommandCenterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ControllableStreamClient : AgentStreamClient() {
    val emittedFlows = mutableListOf<Channel<StreamEvent>>()

    override fun streamPrompt(
        context: StreamRequestContext,
        history: List<Pair<String, String>>
    ): Flow<StreamEvent> {
        val channel = Channel<StreamEvent>(Channel.UNLIMITED)
        emittedFlows.add(channel)
        return channel.consumeAsFlow()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommandCenterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRepositorySelectionIncrementsVersion() {
        val viewModel = CommandCenterViewModel()
        assertEquals(1L, viewModel.uiState.value.contextVersion)
        assertNull(viewModel.uiState.value.selectedRepository)

        // Select Repo A
        viewModel.selectRepository("Sovereign Coding Agent")
        assertEquals("Sovereign Coding Agent", viewModel.uiState.value.selectedRepository)
        assertEquals(2L, viewModel.uiState.value.contextVersion)

        // Select Repo B
        viewModel.selectRepository("Trinity Universe Website")
        assertEquals("Trinity Universe Website", viewModel.uiState.value.selectedRepository)
        assertEquals(3L, viewModel.uiState.value.contextVersion)

        // Deselect back to Global Chat
        viewModel.selectRepository(null)
        assertNull(viewModel.uiState.value.selectedRepository)
        assertEquals(4L, viewModel.uiState.value.contextVersion)
    }

    @Test
    fun testSelectingSameRepositoryDoesNotIncrementVersion() {
        val viewModel = CommandCenterViewModel()
        viewModel.selectRepository("Sovereign Coding Agent")
        assertEquals(2L, viewModel.uiState.value.contextVersion)

        viewModel.selectRepository("Sovereign Coding Agent")
        assertEquals(2L, viewModel.uiState.value.contextVersion)
    }

    @Test
    fun testSendMessageUpdatesChatState() {
        val viewModel = CommandCenterViewModel()
        viewModel.sendMessage("Analyze system health")

        val state = viewModel.uiState.value
        assertEquals(2, state.chatMessages.size)
        assertEquals("Analyze system health", state.chatMessages[0].content)
        assertEquals("user", state.chatMessages[0].role)
        assertEquals("assistant", state.chatMessages[1].role)
        assertTrue(state.isStreaming)
    }

    @Test
    fun testCancelActiveStreamStopsStreaming() {
        val viewModel = CommandCenterViewModel()
        viewModel.sendMessage("Run deployment")
        assertTrue(viewModel.uiState.value.isStreaming)

        viewModel.cancelActiveStream()
        assertFalse(viewModel.uiState.value.isStreaming)
    }

    @Test
    fun testStaleStreamEventsFromOldRepositoryContextAreIgnored() = runTest {
        val fakeClient = ControllableStreamClient()
        val viewModel = CommandCenterViewModel(streamClient = fakeClient)

        // 1. Select Repository A
        viewModel.selectRepository("Repository-A")
        assertEquals("Repository-A", viewModel.uiState.value.selectedRepository)
        val versionA = viewModel.uiState.value.contextVersion

        // 2. Start a stream on Repository A
        viewModel.sendMessage("Build Repository A")
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeClient.emittedFlows.size)
        val repoAStream = fakeClient.emittedFlows[0]

        // 3. Immediately select Repository B
        viewModel.selectRepository("Repository-B")
        assertEquals("Repository-B", viewModel.uiState.value.selectedRepository)
        val versionB = viewModel.uiState.value.contextVersion
        assertNotEquals(versionA, versionB)

        // 4. Allow Repository A to produce a late SSE event
        repoAStream.send(StreamEvent.ContentChunk("LATE_CHUNK_FROM_REPO_A"))
        testScheduler.advanceUntilIdle()

        // 5. Confirm the late event is ignored and not appended to Repo A messages
        val messagesRepoA = viewModel.uiState.value.chatMessages.filter { it.repositoryContext == "Repository-A" }
        val assistantMsgRepoA = messagesRepoA.find { it.role == "assistant" }
        assertFalse(assistantMsgRepoA?.content?.contains("LATE_CHUNK_FROM_REPO_A") == true)

        // 6. Confirm Repository B receives only its own events
        viewModel.sendMessage("Build Repository B")
        testScheduler.advanceUntilIdle()
        assertEquals(2, fakeClient.emittedFlows.size)
        val repoBStream = fakeClient.emittedFlows[1]
        repoBStream.send(StreamEvent.ContentChunk("VALID_CHUNK_FROM_REPO_B"))
        testScheduler.advanceUntilIdle()

        val messagesRepoB = viewModel.uiState.value.chatMessages.filter { it.repositoryContext == "Repository-B" }
        val assistantMsgRepoB = messagesRepoB.find { it.role == "assistant" }
        assertTrue(assistantMsgRepoB?.content?.contains("VALID_CHUNK_FROM_REPO_B") == true)

        // 7. Confirm the UI does not revert to Repository A
        assertEquals("Repository-B", viewModel.uiState.value.selectedRepository)
    }
}
