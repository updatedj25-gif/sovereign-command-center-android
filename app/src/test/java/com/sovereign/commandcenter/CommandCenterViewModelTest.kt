package com.sovereign.commandcenter

import com.sovereign.commandcenter.ui.CommandCenterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
}
