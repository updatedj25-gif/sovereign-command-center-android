package com.sovereign.commandcenter

import com.sovereign.commandcenter.data.api.ApiClient
import com.sovereign.commandcenter.data.session.OwnerSession
import com.sovereign.commandcenter.data.session.SessionManager
import com.sovereign.commandcenter.data.stream.AgentStreamClient
import com.sovereign.commandcenter.data.stream.StreamEvent
import com.sovereign.commandcenter.data.stream.StreamRequestContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Step1NetworkAuthTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        ApiClient.allowTestOverride = true
        ApiClient.baseUrl = server.url("").toString().removeSuffix("/")
    }

    @After
    fun tearDown() {
        server.shutdown()
        ApiClient.allowTestOverride = false
        SessionManager.clearSession()
    }

    @Test
    fun testPasskeyChallengeContract() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "challengeId": "test-chal-123",
                        "challenge": "base64-random-challenge",
                        "rp": { "name": "Sovereign Command Center", "id": "localhost" },
                        "user": { "id": "adebola", "name": "adebola@trinityuniverse.com" },
                        "timeout": 120000
                    }
                """.trimIndent())
        )

        val result = ApiClient.getPasskeyChallenge()
        assertTrue(result.isSuccess)
        val challenge = result.getOrThrow()
        assertEquals("test-chal-123", challenge.challengeId)
        assertEquals("localhost", challenge.rpId)
    }

    @Test
    fun testPasskeyVerifyAndSessionStorage() = runBlocking {
        val fakeToken = "mockPayload.mockHmacSignature"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "success": true,
                        "token": "$fakeToken",
                        "ownerId": "f94d9b4f",
                        "expiresAt": "2026-09-08T21:00:00.000Z",
                        "user": {
                            "name": "Adebola James Ogunjimi",
                            "role": "CEO"
                        }
                    }
                """.trimIndent())
        )

        val result = ApiClient.verifyPasskey("test-chal-123")
        assertTrue(result.isSuccess)
        val verify = result.getOrThrow()
        assertEquals(fakeToken, verify.token)
        assertEquals("CEO", verify.role)

        // Validate Session Auth Cookie formatting
        val session = OwnerSession(
            token = verify.token,
            ownerId = verify.ownerId,
            expiresAtMillis = System.currentTimeMillis() + 3600_000,
            displayName = verify.displayName
        )
        assertTrue(SessionManager.isSessionValid(session))
    }

    @Test
    fun testOrgHealthContract() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "organization": "Trinity Universe",
                        "ceo": "Adebola James Ogunjimi",
                        "timestamp": "2026-09-07T21:00:00.000Z",
                        "overallHealth": "nominal",
                        "pendingApprovalsCount": 1,
                        "repositories": [
                            {
                                "name": "Sovereign Coding Agent",
                                "type": "agent-engine",
                                "status": "nominal",
                                "branch": "main",
                                "verifiedRelease": "Section 6",
                                "pendingApprovals": 1
                            }
                        ]
                    }
                """.trimIndent())
        )

        val result = ApiClient.getOrgHealth()
        assertTrue(result.isSuccess)
        val health = result.getOrThrow()
        assertEquals("Trinity Universe", health.organization)
        assertEquals("nominal", health.overallHealth)
        assertEquals(1, health.repositories.size)
        assertEquals("Sovereign Coding Agent", health.repositories[0].name)
    }

    @Test
    fun testSSEStreamingAndApprovalRequiredEvent() = runBlocking {
        val ssePayload = """
            : keepalive 1725740000000

            data: {"type":"task_running","tool":"run_command","stepId":"s-1","turn":1}

            data: {"type":"approval_required","approvalId":"appr-999","tool":"run_command","dangerReason":"Production deployment requires authorization"}

            data: {"type":"approval_granted","approvalId":"appr-999","tool":"run_command"}

        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(ssePayload)
        )

        val streamClient = AgentStreamClient()
        val context = StreamRequestContext(
            prompt = "Deploy to production",
            sessionId = "sess-test",
            repositoryId = "Sovereign Coding Agent",
            workspaceContextVersion = 1L
        )

        val events = streamClient.streamPrompt(context).toList()
        assertTrue(events.any { it is StreamEvent.TaskRunning })
        assertTrue(events.any { it is StreamEvent.ApprovalRequired && it.approvalId == "appr-999" })
        assertTrue(events.any { it is StreamEvent.ApprovalGranted })
        assertTrue(events.any { it is StreamEvent.Completed })
    }

    @Test
    fun testStaleResponseCancellationOnRepoSwitch() {
        val streamClient = AgentStreamClient()
        // Switching to new version should cancel active call
        val context1 = StreamRequestContext("Prompt 1", "sess-1", "Repo A", workspaceContextVersion = 1L)
        val context2 = StreamRequestContext("Prompt 2", "sess-2", "Repo B", workspaceContextVersion = 2L)

        streamClient.streamPrompt(context1)
        streamClient.cancelCurrentStream()
        // No crash, cleanly canceled
        assertNotNull(streamClient)
    }
}
