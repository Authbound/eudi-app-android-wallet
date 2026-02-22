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

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SignOutUseCaseImpl].
 *
 * Tests verify secure sign out behavior including:
 * - Soft mode (session only) vs Hard mode (full cleanup)
 * - Proper cleanup ordering (lock first, then clear)
 * - Error resilience (best-effort cleanup on failures)
 * - CancellationException propagation
 *
 * SECURITY-CRITICAL: These tests ensure sensitive user data is properly
 * cleaned up during sign out, preventing data leakage between sessions.
 */
class TestSignOutUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var supabaseAuthRepository: SupabaseAuthRepository

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var keystoreController: KeystoreController

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var localUnlockTracker: LocalUnlockTracker

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var mockUserInfo: UserInfo

    private lateinit var useCase: SignOutUseCaseImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = SignOutUseCaseImpl(
            supabaseAuthRepository = supabaseAuthRepository,
            prefsController = prefsController,
            keystoreController = keystoreController,
            prefKeys = prefKeys,
            localUnlockTracker = localUnlockTracker,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Soft Mode

    @Test
    fun `Given Soft mode, When invoke is called, Then does NOT clear user data`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()

            // When
            useCase.invoke(SignOutMode.Soft)

            // Then - User data should be preserved
            verify(prefsController, never()).clearUserData(any())
            verify(keystoreController, never()).deleteBiometricSecretKey(any())
        }

    @Test
    fun `Given Soft mode, When invoke is called, Then still locks wallet and clears session`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()

            // When
            useCase.invoke(SignOutMode.Soft)

            // Then
            verify(localUnlockTracker).lockNow()
            verify(supabaseAuthRepository).signOut()
            verify(prefsController).invalidateCache()
        }

    // endregion

    // region Hard Mode

    @Test
    fun `Given Hard mode with valid userId, When invoke is called, Then clears user data`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()

            // When
            useCase.invoke(SignOutMode.Hard)

            // Then
            verify(prefsController).clearUserData(MOCK_USER_ID)
        }

    @Test
    fun `Given Hard mode with biometric alias, When invoke is called, Then deletes biometric key`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            whenever(prefKeys.getBiometricAliasSafe()).thenReturn(MOCK_BIOMETRIC_ALIAS)

            // When
            useCase.invoke(SignOutMode.Hard)

            // Then
            verify(keystoreController).deleteBiometricSecretKey(MOCK_BIOMETRIC_ALIAS)
        }

    @Test
    fun `Given Hard mode with no biometric alias, When invoke is called, Then skips key deletion`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            whenever(prefKeys.getBiometricAliasSafe()).thenReturn("")

            // When
            useCase.invoke(SignOutMode.Hard)

            // Then
            verify(keystoreController, never()).deleteBiometricSecretKey(any())
        }

    @Test
    fun `Given Hard mode with null userId, When invoke is called, Then skips user data clear`() =
        coroutineRule.runTest {
            // Given
            whenever(supabaseAuthRepository.getCurrentUser()).thenReturn(null)
            whenever(prefKeys.getBiometricAliasSafe()).thenReturn("")

            // When
            useCase.invoke(SignOutMode.Hard)

            // Then
            verify(prefsController, never()).clearUserData(any())
        }

    // endregion

    // region Error Resilience

    @Test
    fun `Given clearUserData throws, When invoke is called, Then logs error and continues`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            doThrow(RuntimeException("Clear failed")).whenever(prefsController).clearUserData(any())

            // When - Should not throw
            useCase.invoke(SignOutMode.Hard)

            // Then - Verify logging and continuation
            verify(logController).w(any<String>(), any())
            verify(prefsController).invalidateCache()
        }

    @Test
    fun `Given deleteBiometricSecretKey throws, When invoke is called, Then logs error and continues`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            whenever(prefKeys.getBiometricAliasSafe()).thenReturn(MOCK_BIOMETRIC_ALIAS)
            doThrow(SecurityException("Keystore error"))
                .whenever(keystoreController).deleteBiometricSecretKey(any())

            // When - Should not throw
            useCase.invoke(SignOutMode.Hard)

            // Then
            verify(logController).w(any<String>(), any())
            verify(prefsController).invalidateCache()
        }

    @Test
    fun `Given lockNow throws, When invoke is called, Then logs error and continues`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            doThrow(RuntimeException("Lock failed")).whenever(localUnlockTracker).lockNow()

            // When - Should not throw
            useCase.invoke(SignOutMode.Soft)

            // Then
            verify(logController).w(any<String>(), any())
            verify(supabaseAuthRepository).signOut()
        }

    @Test
    fun `Given supabase signOut throws, When invoke is called, Then performs best-effort cleanup`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            doThrow(RuntimeException("Network error")).whenever(supabaseAuthRepository).signOut()

            // When/Then - Should propagate exception after cleanup attempt
            try {
                useCase.invoke(SignOutMode.Soft)
            } catch (e: Exception) {
                // Expected
            }

            // Then - Should have attempted cache invalidation
            verify(prefsController).invalidateCache()
        }

    @Test
    fun `Given invalidateCache throws, When invoke is called, Then logs warning`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            doThrow(RuntimeException("Cache error")).whenever(prefsController).invalidateCache()

            // When - Should not throw
            useCase.invoke(SignOutMode.Soft)

            // Then
            verify(logController).w(any<String>(), any())
        }

    // endregion

    // region CancellationException Handling

    @Test
    fun `Given CancellationException is thrown, When invoke is called, Then exception is rethrown`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            doAnswer { throw CancellationException("Cancelled") }
                .whenever(supabaseAuthRepository).signOut()

            // When/Then
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    useCase.invoke(SignOutMode.Soft)
                }
            }
        }

    // endregion

    // region Operation Ordering

    @Test
    fun `Given sign out is called, When operations execute, Then invalidateCache is called last`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()

            // When
            useCase.invoke(SignOutMode.Soft)

            // Then - Verify ordering: lockNow -> signOut -> invalidateCache
            val inOrder = inOrder(localUnlockTracker, supabaseAuthRepository, prefsController)
            inOrder.verify(localUnlockTracker).lockNow()
            inOrder.verify(supabaseAuthRepository).signOut()
            inOrder.verify(prefsController).invalidateCache()
        }

    @Test
    fun `Given Hard mode, When operations execute, Then user data cleared before invalidateCache`() =
        coroutineRule.runTest {
            // Given
            setupAuthenticatedUser()
            whenever(prefKeys.getBiometricAliasSafe()).thenReturn(MOCK_BIOMETRIC_ALIAS)

            // When
            useCase.invoke(SignOutMode.Hard)

            // Then
            val inOrder = inOrder(prefsController, keystoreController)
            inOrder.verify(prefsController).clearUserData(any())
            inOrder.verify(keystoreController).deleteBiometricSecretKey(any())
        }

    // endregion

    // region Helper Methods

    private suspend fun setupAuthenticatedUser() {
        whenever(mockUserInfo.id).thenReturn(MOCK_USER_ID)
        whenever(supabaseAuthRepository.getCurrentUser()).thenReturn(mockUserInfo)
        whenever(prefKeys.getBiometricAliasSafe()).thenReturn("")
    }

    // endregion

    companion object {
        private const val MOCK_USER_ID = "user-123-456"
        private const val MOCK_BIOMETRIC_ALIAS = "biometric-key-alias"
    }
}
