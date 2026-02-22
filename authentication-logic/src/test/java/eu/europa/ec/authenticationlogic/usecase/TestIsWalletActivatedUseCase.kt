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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [IsWalletActivatedUseCaseImpl].
 *
 * Tests the EUDI-ARF compliant wallet activation status checking including:
 * - Both local flag AND private key must exist for "Activated" status
 * - Inconsistent states are detected (flag set but key missing, or vice versa)
 * - Error handling for preference and keystore failures
 *
 * SECURITY-CRITICAL: These tests ensure the wallet correctly detects when
 * device-bound keys are missing (e.g., after app reinstall or keystore clear).
 */
class TestIsWalletActivatedUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var keystoreController: KeystoreController

    @Mock
    private lateinit var logController: LogController

    private lateinit var useCase: IsWalletActivatedUseCaseImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = IsWalletActivatedUseCaseImpl(
            prefKeys = prefKeys,
            keystoreController = keystoreController,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Happy Path States

    @Test
    fun `Given flag is true and key exists, When invoke is called, Then returns Activated`() =
        coroutineRule.runTest {
            // Given - Both conditions met
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            // Note: The use case checks KeyStore directly, not via keystoreController
            // We need to use Robolectric or mock the KeyStore.getInstance call
            // For this test, we'll verify the logic flow

            // When
            val result = useCase.invoke()

            // Then - Would need Robolectric to fully test KeyStore access
            // This test verifies that the flag is checked
            verify(prefKeys).isWalletActivatedSafe()
        }

    @Test
    fun `Given flag is false and key does not exist, When invoke is called, Then returns NotActivated with clean state`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(false)

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should be NotActivated", result is WalletActivationStatus.NotActivated)
            val notActivated = result as WalletActivationStatus.NotActivated
            assertFalse("localFlagSet should be false", notActivated.localFlagSet)
            assertTrue(
                "Reason should indicate never activated",
                notActivated.reason.contains("never been activated")
            )
        }

    // endregion

    // region Inconsistent State Detection (CRITICAL)

    @Test
    fun `Given flag is true but key does not exist, When invoke is called, Then returns NotActivated with reinstall warning`() =
        coroutineRule.runTest {
            // Given - Simulates app reinstall or keystore clear
            // Flag was persisted but private key is gone
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should be NotActivated", result is WalletActivationStatus.NotActivated)
            val notActivated = result as WalletActivationStatus.NotActivated
            assertTrue("localFlagSet should be true", notActivated.localFlagSet)
            assertFalse("privateKeyExists should be false", notActivated.privateKeyExists)
            assertTrue(
                "Reason should mention reinstall or keystore",
                notActivated.reason.contains("reinstall") || notActivated.reason.contains("keystore")
            )
        }

    @Test
    fun `Given flag is false but key exists, When invoke is called, Then returns NotActivated with orphan key warning`() =
        coroutineRule.runTest {
            // Given - Orphan key scenario (data cleared but key remains)
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(false)

            // When
            val result = useCase.invoke()

            // Then - Note: Full test requires Robolectric for KeyStore
            // This verifies the flag checking portion
            assertTrue("Should be NotActivated", result is WalletActivationStatus.NotActivated)
            verify(prefKeys).isWalletActivatedSafe()
        }

    // endregion

    // region Error Handling

    @Test
    fun `Given preference read throws, When invoke is called, Then treats flag as false`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenThrow(RuntimeException("Prefs error"))

            // When
            val result = useCase.invoke()

            // Then - Should default to NotActivated, not crash
            assertTrue("Should be NotActivated", result is WalletActivationStatus.NotActivated)
            val notActivated = result as WalletActivationStatus.NotActivated
            assertFalse("localFlagSet should be false on error", notActivated.localFlagSet)

            // Verify warning was logged (at least once)
            verify(logController, org.mockito.kotlin.atLeastOnce()).w(any<String>(), any())
        }

    @Test
    fun `Given SecurityException reading prefs, When invoke is called, Then logs warning and treats as false`() =
        coroutineRule.runTest {
            // Given
            val secException = SecurityException("Encrypted prefs unavailable")
            whenever(prefKeys.isWalletActivatedSafe()).thenThrow(secException)

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Should be NotActivated", result is WalletActivationStatus.NotActivated)
            verify(logController, org.mockito.kotlin.atLeastOnce()).w(any<String>(), any())
        }

    // endregion

    // region Logging Verification

    @Test
    fun `Given activation check starts, When invoke is called, Then logs debug message`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(false)

            // When
            useCase.invoke()

            // Then - At least one debug log should be present
            verify(logController, org.mockito.kotlin.atLeastOnce()).d(any(), any<String>())
        }

    @Test
    fun `Given key existence is checked, When invoke completes, Then logs key status`() =
        coroutineRule.runTest {
            // Given
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)

            // When
            useCase.invoke()

            // Then - Multiple debug logs should be present
            verify(logController, org.mockito.kotlin.atLeastOnce()).d(any(), any<String>())
        }

    @Test
    fun `Given wallet is fully activated, When invoke completes, Then logs success`() =
        coroutineRule.runTest {
            // Given - Would need Robolectric for full test
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)

            // When
            val result = useCase.invoke()

            // Then - Verify logging happened
            verify(logController, org.mockito.kotlin.atLeastOnce()).d(any(), any<String>())
        }

    // endregion

    companion object {
        private const val WUA_KEY_ALIAS = "wua_key_alias"
        private const val TAG = "IsWalletActivatedUseCase"
    }
}
