/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.quickidlogic.model

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.datetime.Instant
import org.junit.Test

/**
 * Tests for QuickIdSession expiry and state management.
 *
 * These tests are security-critical because:
 * 1. Expired sessions MUST be rejected to prevent replay attacks
 * 2. Session state must be properly isolated and cleared
 * 3. Token handling must be correct to prevent auth bypass
 */
class TestQuickIdSession {

    private fun createSession(
        sessionId: String = "test-session-123",
        clientToken: String = "test-token-abc",
        expiresAtEpochSeconds: Long = 1700000000L, // Some fixed point
        livenessSessionId: String? = null
    ) = QuickIdSession(
        sessionId = sessionId,
        clientToken = clientToken,
        expiresAt = Instant.fromEpochSeconds(expiresAtEpochSeconds),
        livenessSessionId = livenessSessionId
    )

    // ==================== Expiry Tests ====================

    @Test
    fun `session is not expired when now is before expiresAt`() {
        val session = createSession(expiresAtEpochSeconds = 1700000000L)
        val now = Instant.fromEpochSeconds(1699999999L) // 1 second before

        assertFalse(session.isExpired(now))
    }

    @Test
    fun `session is expired when now equals expiresAt`() {
        val session = createSession(expiresAtEpochSeconds = 1700000000L)
        val now = Instant.fromEpochSeconds(1700000000L) // Exactly at expiry

        assertTrue(session.isExpired(now))
    }

    @Test
    fun `session is expired when now is after expiresAt`() {
        val session = createSession(expiresAtEpochSeconds = 1700000000L)
        val now = Instant.fromEpochSeconds(1700000001L) // 1 second after

        assertTrue(session.isExpired(now))
    }

    @Test
    fun `session is expired when now is far after expiresAt`() {
        val session = createSession(expiresAtEpochSeconds = 1700000000L)
        val now = Instant.fromEpochSeconds(1800000000L) // ~3 years later

        assertTrue(session.isExpired(now))
    }

    @Test
    fun `session is not expired when created with future expiry`() {
        val session = createSession(expiresAtEpochSeconds = 1800000000L)
        val now = Instant.fromEpochSeconds(1700000000L) // Well before

        assertFalse(session.isExpired(now))
    }

    // ==================== Liveness Session Tests ====================

    @Test
    fun `withLivenessSession returns new session with liveness ID`() {
        val original = createSession(livenessSessionId = null)
        val updated = original.withLivenessSession("liveness-456")

        assertEquals("liveness-456", updated.livenessSessionId)
        // Original values preserved
        assertEquals(original.sessionId, updated.sessionId)
        assertEquals(original.clientToken, updated.clientToken)
        assertEquals(original.expiresAt, updated.expiresAt)
    }

    @Test
    fun `withLivenessSession does not modify original session`() {
        val original = createSession(livenessSessionId = null)
        original.withLivenessSession("liveness-456")

        assertNull(original.livenessSessionId)
    }

    @Test
    fun `withLivenessSession can replace existing liveness ID`() {
        val original = createSession(livenessSessionId = "old-liveness")
        val updated = original.withLivenessSession("new-liveness")

        assertEquals("new-liveness", updated.livenessSessionId)
    }

    // ==================== Data Integrity Tests ====================

    @Test
    fun `session preserves all data correctly`() {
        val session = QuickIdSession(
            sessionId = "precise-session-id",
            clientToken = "precise-client-token",
            expiresAt = Instant.fromEpochSeconds(1234567890L),
            livenessSessionId = "precise-liveness-id"
        )

        assertEquals("precise-session-id", session.sessionId)
        assertEquals("precise-client-token", session.clientToken)
        assertEquals(Instant.fromEpochSeconds(1234567890L), session.expiresAt)
        assertEquals("precise-liveness-id", session.livenessSessionId)
    }

    @Test
    fun `session with null livenessSessionId is valid`() {
        val session = createSession(livenessSessionId = null)

        assertNull(session.livenessSessionId)
        // Session should still function normally
        assertFalse(session.isExpired(Instant.fromEpochSeconds(0L)))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `session handles epoch zero`() {
        val session = createSession(expiresAtEpochSeconds = 0L)
        val now = Instant.fromEpochSeconds(1L)

        assertTrue(session.isExpired(now))
    }

    @Test
    fun `session handles maximum reasonable epoch`() {
        // Year 2100 epoch seconds (approximately)
        val session = createSession(expiresAtEpochSeconds = 4102444800L)
        val now = Instant.fromEpochSeconds(1700000000L) // Current-ish time

        assertFalse(session.isExpired(now))
    }

    @Test
    fun `session equality works correctly`() {
        val session1 = createSession(
            sessionId = "same-id",
            clientToken = "same-token",
            expiresAtEpochSeconds = 1700000000L
        )
        val session2 = createSession(
            sessionId = "same-id",
            clientToken = "same-token",
            expiresAtEpochSeconds = 1700000000L
        )

        assertEquals(session1, session2)
    }

    @Test
    fun `different sessions are not equal`() {
        val session1 = createSession(sessionId = "id-1")
        val session2 = createSession(sessionId = "id-2")

        assertFalse(session1 == session2)
    }
}
