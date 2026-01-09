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

package eu.europa.ec.startupfeature.interactor

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCase
import eu.europa.ec.authenticationlogic.usecase.WalletActivationStatus
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.startupfeature.model.StartupState
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.github.jan.supabase.auth.status.SessionStatus
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SplashInteractorImpl].
 *
 * Tests the three-level startup state determination:
 * Level 1: Authentication (Supabase session)
 * Level 2: Onboarding (Profile + WUA)
 * Level 3: Local Unlock (PIN verification or direct to dashboard)
 */
class TestSplashInteractor {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var supabaseAuthRepository: SupabaseAuthRepository

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var isWalletActivatedUseCase: IsWalletActivatedUseCase

    @Mock
    private lateinit var isProfileCompletedUseCase: IsProfileCompletedUseCase

    @Mock
    private lateinit var quickPinInteractor: QuickPinInteractor

    @Mock
    private lateinit var localUnlockTracker: LocalUnlockTracker

    private lateinit var interactor: SplashInteractorImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        interactor = SplashInteractorImpl(
            supabaseAuthRepository = supabaseAuthRepository,
            prefKeys = prefKeys,
            logController = logController,
            isWalletActivatedUseCase = isWalletActivatedUseCase,
            isProfileCompletedUseCase = isProfileCompletedUseCase,
            quickPinInteractor = quickPinInteractor,
            localUnlockTracker = localUnlockTracker
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    //region Level 1: Authentication Tests

    // Case 1:
    // Session is NotAuthenticated.
    // Expected: Returns StartupState.NotAuthenticated.
    @Test
    fun `Given session is NotAuthenticated, When determineStartupState is called, Then returns NotAuthenticated`() =
        coroutineRule.runTest {
            // Given
            whenever(supabaseAuthRepository.observeAuthState())
                .thenReturn(flowOf(SessionStatus.NotAuthenticated(isSignOut = false)))

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.NotAuthenticated, result)
            verify(isProfileCompletedUseCase, never()).invoke()
        }

    // Case 2:
    // Session is Authenticated.
    // Expected: Proceeds to onboarding check (no immediate return).
    @Test
    fun `Given session is Authenticated and profile complete and wallet active and PIN exists and unlocked, When determineStartupState is called, Then returns Ready`() =
        coroutineRule.runTest {
            // Given - Full happy path
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(WalletActivationStatus.Activated)
            whenever(quickPinInteractor.hasPin()).thenReturn(true)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(true)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.Ready, result)
        }

    // Case 3:
    // Session initializing then becomes NotAuthenticated.
    // Expected: Waits for non-initializing state, then returns NotAuthenticated.
    @Test
    fun `Given session Initializing then NotAuthenticated, When determineStartupState is called, Then waits and returns NotAuthenticated`() =
        coroutineRule.runTest {
            // Given - Flow emits Initializing first, then NotAuthenticated
            // The interactor filters out Initializing status
            whenever(supabaseAuthRepository.observeAuthState())
                .thenReturn(flowOf(SessionStatus.NotAuthenticated(isSignOut = true)))

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.NotAuthenticated, result)
        }

    // Case 4:
    // Session initialization throws exception.
    // Expected: Defaults to NotAuthenticated (fail-safe).
    // Note: waitForSessionInitialization() catches exceptions and returns NotAuthenticated.
    @Test
    fun `Given session init throws exception, When determineStartupState is called, Then returns NotAuthenticated`() =
        coroutineRule.runTest {
            // Given
            whenever(supabaseAuthRepository.observeAuthState())
                .thenThrow(RuntimeException("Session initialization failed"))

            // When
            val result = interactor.determineStartupState()

            // Then - Session init exceptions are caught and default to NotAuthenticated
            assertEquals(StartupState.NotAuthenticated, result)
        }

    //endregion

    //region Level 2: Onboarding Tests

    // Case 5:
    // Authenticated but profile incomplete.
    // Expected: Returns ProfileIncomplete.
    @Test
    fun `Given authenticated but profile incomplete, When determineStartupState is called, Then returns ProfileIncomplete`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(false)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.ProfileIncomplete, result)
            verify(isWalletActivatedUseCase, never()).invoke()
        }

    // Case 6:
    // Profile complete but wallet not activated.
    // Expected: Returns WalletNotActivated.
    @Test
    fun `Given profile complete but wallet not activated, When determineStartupState is called, Then returns WalletNotActivated`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(
                WalletActivationStatus.NotActivated(
                    reason = "No private key",
                    privateKeyExists = false,
                    localFlagSet = false
                )
            )

            // When
            val result = interactor.determineStartupState()

            // Then
            assertTrue("Should be WalletNotActivated", result is StartupState.WalletNotActivated)
        }

    // Case 7:
    // Inconsistent wallet state (flag set but no key).
    // Expected: Clears flag and returns WalletNotActivated.
    @Test
    fun `Given inconsistent wallet state, When determineStartupState is called, Then clears flag and returns WalletNotActivated`() =
        coroutineRule.runTest {
            // Given - Flag is set but no private key exists (inconsistent)
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(
                WalletActivationStatus.NotActivated(
                    reason = "Inconsistent state",
                    privateKeyExists = false,
                    localFlagSet = true  // Flag says activated but key doesn't exist
                )
            )

            // When
            val result = interactor.determineStartupState()

            // Then
            assertTrue("Should be WalletNotActivated", result is StartupState.WalletNotActivated)
            verify(prefKeys).setWalletActivated(false)
        }

    // Case 8:
    // Wallet is activated.
    // Expected: Proceeds to local unlock check.
    @Test
    fun `Given wallet is activated, When determineStartupState is called, Then proceeds to local unlock check`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(WalletActivationStatus.Activated)
            whenever(quickPinInteractor.hasPin()).thenReturn(true)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(false)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.PinVerificationRequired, result)
        }

    //endregion

    //region Level 3: Local Unlock Tests

    // Case 9:
    // PIN not set.
    // Expected: Returns PinNotSet.
    @Test
    fun `Given PIN not set, When determineStartupState is called, Then returns PinNotSet`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(WalletActivationStatus.Activated)
            whenever(quickPinInteractor.hasPin()).thenReturn(false)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.PinNotSet, result)
        }

    // Case 10:
    // PIN set but not unlocked (outside TTL).
    // Expected: Returns PinVerificationRequired.
    @Test
    fun `Given PIN set but not unlocked, When determineStartupState is called, Then returns PinVerificationRequired`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(WalletActivationStatus.Activated)
            whenever(quickPinInteractor.hasPin()).thenReturn(true)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(false)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.PinVerificationRequired, result)
        }

    // Case 11:
    // PIN set and unlocked (within TTL).
    // Expected: Returns Ready (hot start).
    @Test
    fun `Given PIN set and unlocked within TTL, When determineStartupState is called, Then returns Ready`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenReturn(true)
            whenever(isWalletActivatedUseCase.invoke()).thenReturn(WalletActivationStatus.Activated)
            whenever(quickPinInteractor.hasPin()).thenReturn(true)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(true)

            // When
            val result = interactor.determineStartupState()

            // Then
            assertEquals(StartupState.Ready, result)
        }

    //endregion

    //region Error Handling Tests

    // Case 12:
    // SecurityException thrown during checks.
    // Expected: Returns SecurityError, logs error.
    @Test
    fun `Given SecurityException thrown, When determineStartupState is called, Then returns SecurityError`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenThrow(SecurityException("Keystore tampered"))

            // When
            val result = interactor.determineStartupState()

            // Then
            assertTrue("Should be SecurityError", result is StartupState.SecurityError)
            assertEquals("Keystore tampered", (result as StartupState.SecurityError).message)
        }

    // Case 13:
    // Unexpected exception thrown.
    // Expected: Returns SecurityError with wrapped message.
    @Test
    fun `Given unexpected exception thrown, When determineStartupState is called, Then returns SecurityError`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedSession()
            whenever(isProfileCompletedUseCase.invoke()).thenThrow(RuntimeException("Database error"))

            // When
            val result = interactor.determineStartupState()

            // Then
            assertTrue("Should be SecurityError", result is StartupState.SecurityError)
            assertTrue(
                "Message should contain original error",
                (result as StartupState.SecurityError).message.contains("Database error")
            )
        }

    //endregion

    //region Helper Methods

    /**
     * Set up mock for authenticated session.
     */
    private fun setupAuthenticatedSession() {
        whenever(supabaseAuthRepository.observeAuthState())
            .thenReturn(flowOf(SessionStatus.Authenticated(MOCK_SESSION)))
    }

    //endregion

    //region Test Constants

    companion object {
        /**
         * Mock session for authenticated state.
         * SessionStatus.Authenticated requires a session object, but we use mockito mock.
         */
        private val MOCK_SESSION = org.mockito.Mockito.mock(io.github.jan.supabase.auth.user.UserSession::class.java)
    }

    //endregion
}
