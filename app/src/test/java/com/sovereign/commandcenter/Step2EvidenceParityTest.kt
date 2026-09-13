package com.sovereign.commandcenter

import com.sovereign.commandcenter.data.api.ActivityEvidence
import com.sovereign.commandcenter.data.api.ActivityRecord
import com.sovereign.commandcenter.data.api.BrokerLease
import org.junit.Assert.*
import org.junit.Test

class Step2EvidenceParityTest {

    @Test
    fun testBrokerLeaseActiveAndExpiry() {
        val now = System.currentTimeMillis()
        val activeLease = BrokerLease(
            leaseId = "lease-android-01",
            ownerId = "updatedj25-gif",
            sessionId = "session-01",
            profileId = "sovereign",
            branch = "main",
            environment = "production",
            sequenceNumber = 1,
            capabilities = listOf("inspect", "build", "test"),
            issuedAt = now - 1000,
            expiresAt = now + 60000,
            status = "active"
        )
        assertTrue(activeLease.isActive(now))

        val expiredLease = activeLease.copy(expiresAt = now - 100)
        assertFalse(expiredLease.isActive(now))
    }

    @Test
    fun testActivityRecordEvidenceCompleteness() {
        val completeActivity = ActivityRecord(
            id = "act-01",
            sessionId = "sess-01",
            leaseId = "lease-01",
            kind = "gradle_build",
            status = "completed",
            evidence = ActivityEvidence(
                exitCode = 0,
                stdout = "BUILD SUCCESSFUL in 2s",
                stderr = "",
                durationMs = 2100,
                cwd = "/workspace/android",
                outputDigestSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            )
        )
        assertTrue(completeActivity.isEvidenceComplete())

        val incompleteActivity = completeActivity.copy(evidence = null)
        assertFalse(incompleteActivity.isEvidenceComplete())

        val noOutputActivity = ActivityRecord(
            id = "act-02",
            sessionId = "sess-01",
            leaseId = "lease-01",
            kind = "sync",
            status = "completed",
            completedWithNoOutput = true
        )
        assertTrue(noOutputActivity.isEvidenceComplete())
    }
}
