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

package eu.europa.ec.authenticationlogic.gate

import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [KeyGateV2Impl].
 *
 * Tests TTL-based unlock tracking, security error handling, and lifecycle operations.
 * These tests are security-critical as they verify proper unlock state management.
 */
class TestKeyGateV2 {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefs: PrefsControllerV2

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var pinStorage: PinStorageController

    @Mock
    private lateinit var logController: LogController

    private lateinit var keyGate: KeyGateV2Impl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        keyGate = KeyGateV2Impl(
            prefs = prefs,
            prefKeys = prefKeys,
            pinStorage = pinStorage,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    //region isKeyLocked() - Preconditions

    // Case 1:
    // Wallet is not activated.
    // Expected: Key is locked (true) regardless of PIN or unlock state.
    @Test
    fun `Given wallet not activated, When isKeyLocked is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(false)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked when wallet not activated", result)
        }

    // Case 2:
    // Wallet is activated but PIN does not exist.
    // Expected: Key is locked (true) because PIN is required for unlock.
    @Test
    fun `Given wallet activated but no PIN exists, When isKeyLocked is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn("")

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked when PIN not set", result)
        }

    // Case 3:
    // Wallet activated, PIN exists, but never unlocked (timestamp = 0).
    // Expected: Key is locked (true).
    @Test
    fun `Given PIN exists but never unlocked, When isKeyLocked is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn(MOCK_VALID_PIN)
            whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(0L)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked when never unlocked", result)
        }

    //endregion

    //region isKeyLocked() - TTL Expiration

    // Case 4:
    // Unlocked 5 seconds ago (well within TTL).
    // Expected: Key is unlocked (false).
    @Test
    fun `Given unlocked 5 seconds ago, When isKeyLocked is called, Then returns false`() =
        coroutineRule.runTest {
            // Given
            val currentTime = System.currentTimeMillis()
            val fiveSecondsAgo = currentTime - 5_000L

            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn(MOCK_VALID_PIN)
            whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(fiveSecondsAgo)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertFalse("Key should be unlocked when within TTL", result)
        }

    // Case 5:
    // Unlocked just inside TTL boundary (10 minutes - 100ms buffer for test execution time).
    // Expected: Key is still unlocked (false) because we use > not >=.
    // Note: Exact boundary testing is racy due to System.currentTimeMillis() drift during test execution.
    @Test
    fun `Given unlocked just inside TTL boundary, When isKeyLocked is called, Then returns false`() =
        coroutineRule.runTest {
            // Given
            // Use 100ms buffer to account for test execution time
            val currentTime = System.currentTimeMillis()
            val justInsideTTL = currentTime - LocalUnlockTracker.DEFAULT_TTL_MS + 100L

            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn(MOCK_VALID_PIN)
            whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(justInsideTTL)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertFalse("Key should still be unlocked just inside TTL boundary", result)
        }

    // Case 6:
    // Unlocked 10 minutes + 1 millisecond ago (just past TTL).
    // Expected: Key is locked (true) because TTL expired.
    @Test
    fun `Given unlocked past TTL by 1ms, When isKeyLocked is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            val currentTime = System.currentTimeMillis()
            val justPastTTL = currentTime - LocalUnlockTracker.DEFAULT_TTL_MS - 1L

            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn(MOCK_VALID_PIN)
            whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(justPastTTL)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked when TTL expired", result)
        }

    // Case 7:
    // Timestamp is 0 (never unlocked).
    // Expected: Key is locked (true).
    @Test
    fun `Given timestamp is 0, When isKeyLocked is called, Then returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenReturn(MOCK_VALID_PIN)
            whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(0L)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked when timestamp is 0", result)
        }

    //endregion

    //region isUnlocked() - Synchronous Check

    // Case 8:
    // Never unlocked (timestamp = 0).
    // Expected: Returns false.
    @Test
    fun `Given never unlocked, When isUnlocked is called, Then returns false`() {
        // Given
        whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(0L)

        // When
        val result = keyGate.isUnlocked()

        // Then
        assertFalse("isUnlocked should return false when never unlocked", result)
    }

    // Case 9:
    // Unlocked within TTL.
    // Expected: Returns true.
    @Test
    fun `Given unlocked within TTL, When isUnlocked is called, Then returns true`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val fiveMinutesAgo = currentTime - (5 * 60 * 1000L)
        whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(fiveMinutesAgo)

        // When
        val result = keyGate.isUnlocked()

        // Then
        assertTrue("isUnlocked should return true when within TTL", result)
    }

    // Case 10:
    // Unlocked past TTL.
    // Expected: Returns false.
    @Test
    fun `Given unlocked past TTL, When isUnlocked is called, Then returns false`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val elevenMinutesAgo = currentTime - (11 * 60 * 1000L)
        whenever(prefs.safeLong(eq(PREF_LAST_UNLOCK_AT), any())).thenReturn(elevenMinutesAgo)

        // When
        val result = keyGate.isUnlocked()

        // Then
        assertFalse("isUnlocked should return false when past TTL", result)
    }

    //endregion

    //region Lifecycle Operations

    // Case 11:
    // markUnlocked() is called.
    // Expected: Records current timestamp.
    @Test
    fun `Given markUnlocked is called, When operation completes, Then timestamp is recorded`() =
        coroutineRule.runTest {
            // Given - nothing special needed

            // When
            keyGate.markUnlocked()

            // Then
            verify(prefs, times(1)).setLong(eq(PREF_LAST_UNLOCK_AT), any())
        }

    // Case 12:
    // lockNow() is called.
    // Expected: Clears timestamp to 0.
    @Test
    fun `Given lockNow is called, When operation completes, Then timestamp is cleared to 0`() =
        coroutineRule.runTest {
            // Given - nothing special needed

            // When
            keyGate.lockNow()

            // Then
            verify(prefs, times(1)).setLong(PREF_LAST_UNLOCK_AT, 0L)
        }

    //endregion

    //region Security Error Handling

    // Case 13:
    // SecurityException is thrown when reading wallet activation status.
    // Expected: Logs security error, returns true (fail-safe: locked).
    @Test
    fun `Given SecurityException reading wallet status, When isKeyLocked is called, Then logs error and returns true`() =
        coroutineRule.runTest {
            // Given
            val securityException = SecurityException("Keystore tampered")
            whenever(prefKeys.isWalletActivatedSafe()).thenThrow(securityException)

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked on SecurityException (fail-safe)", result)
            verify(logController, times(1)).e(eq(TAG), eq(securityException))
        }

    // Case 14:
    // General Exception is thrown when reading PIN.
    // Expected: Logs debug message, returns true (fail-safe: locked).
    @Test
    fun `Given general Exception reading PIN, When isKeyLocked is called, Then logs debug and returns true`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(pinStorage.retrievePin()).thenThrow(RuntimeException("Storage error"))

            // When
            val result = keyGate.isKeyLocked()

            // Then
            assertTrue("Key should be locked on exception (fail-safe)", result)
        }

    // Case 15:
    // SecurityException is thrown when writing timestamp in markUnlocked().
    // Expected: Logs error, completes gracefully (no crash).
    @Test
    fun `Given SecurityException writing timestamp, When markUnlocked is called, Then logs error and completes`() =
        coroutineRule.runTest {
            // Given
            val securityException = SecurityException("Write permission denied")
            whenever(prefs.setLong(eq(PREF_LAST_UNLOCK_AT), any())).thenThrow(securityException)

            // When - should not throw
            keyGate.markUnlocked()

            // Then - verify logging happened
            verify(logController, times(1)).e(eq(TAG), eq(securityException))
        }

    //endregion

    //region Test Constants

    companion object {
        private const val PREF_LAST_UNLOCK_AT = "authbound.last_local_unlock_at_ms"
        private const val TAG = "KeyGateV2"
        private const val MOCK_VALID_PIN = "123456"
    }

    //endregion
}
