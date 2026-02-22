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
package eu.europa.ec.walletactivationlogic.usecase

import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository
import org.junit.After
import org.junit.Assert.assertTrue
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
 * Unit tests for [DeleteWalletActivationUseCaseImpl].
 *
 * Verifies the wallet deletion flow including:
 * - Backend deletion as critical gate
 * - WUA key cleanup (ARF compliance)
 * - Local document and DB cleanup
 * - Best-effort resilience when local steps fail
 */
class TestDeleteWalletActivationUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var walletActivationRepository: WalletActivationRepository

    @Mock
    private lateinit var prefKeys: PrefKeys

    @Mock
    private lateinit var signOutUseCase: SignOutUseCase

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var cryptoController: CryptoController

    @Mock
    private lateinit var localWalletCleanupController: LocalWalletCleanupController

    private lateinit var useCase: DeleteWalletActivationUseCaseImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = DeleteWalletActivationUseCaseImpl(
            walletActivationRepository = walletActivationRepository,
            prefKeys = prefKeys,
            signOutUseCase = signOutUseCase,
            logController = logController,
            cryptoController = cryptoController,
            localWalletCleanupController = localWalletCleanupController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Happy Path

    @Test
    fun `Given backend deletion succeeds, When invoke is called, Then returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            setupLocalCleanupDefaults()

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given backend deletion succeeds, When invoke is called, Then WUA key is deleted`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            setupLocalCleanupDefaults()

            // When
            useCase.invoke()

            // Then — ARF compliance: WUA key must be cleaned from Keystore
            verify(cryptoController).deleteWuaKey()
        }

    @Test
    fun `Given backend deletion succeeds, When invoke is called, Then local wallet data is cleaned`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            setupLocalCleanupDefaults()

            // When
            useCase.invoke()

            // Then
            verify(localWalletCleanupController).cleanupLocalWalletData()
        }

    @Test
    fun `Given backend deletion succeeds, When invoke is called, Then activation flag is cleared`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            setupLocalCleanupDefaults()

            // When
            useCase.invoke()

            // Then
            verify(prefKeys).setWalletActivated(false)
        }

    @Test
    fun `Given backend deletion succeeds, When invoke is called, Then hard sign-out is called`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            setupLocalCleanupDefaults()

            // When
            useCase.invoke()

            // Then
            verify(signOutUseCase).invoke(SignOutMode.Hard)
        }

    // endregion

    // region Backend Failure

    @Test
    fun `Given backend deletion fails, When invoke is called, Then returns failure`() =
        coroutineRule.runTest {
            // Given
            whenever(walletActivationRepository.deleteWalletActivation())
                .thenReturn(Result.failure(Exception("Server error")))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be failure", result.isFailure)
        }

    @Test
    fun `Given backend deletion fails, When invoke is called, Then no local cleanup is performed`() =
        coroutineRule.runTest {
            // Given
            whenever(walletActivationRepository.deleteWalletActivation())
                .thenReturn(Result.failure(Exception("Server error")))

            // When
            useCase.invoke()

            // Then — no premature cleanup
            verify(cryptoController, never()).deleteWuaKey()
            verify(localWalletCleanupController, never()).cleanupLocalWalletData()
            verify(prefKeys, never()).setWalletActivated(any())
            verify(signOutUseCase, never()).invoke(any())
        }

    // endregion

    // region Best-Effort Resilience

    @Test
    fun `Given WUA key deletion fails, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            whenever(cryptoController.deleteWuaKey()).thenReturn(false)
            setupLocalCleanupDefaults()

            // When
            val result = useCase.invoke()

            // Then — best-effort: backend succeeded, so overall is success
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given WUA key deletion throws, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            // Setup local cleanup defaults BEFORE overriding crypto stub
            whenever(localWalletCleanupController.cleanupLocalWalletData())
                .thenReturn(emptyList())
            whenever(cryptoController.deleteWuaKey()).thenThrow(RuntimeException("Keystore locked"))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given local cleanup reports failures, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)
            whenever(localWalletCleanupController.cleanupLocalWalletData())
                .thenReturn(listOf("Delete document abc", "Clear bookmarks"))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given pref clear throws, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)
            whenever(localWalletCleanupController.cleanupLocalWalletData())
                .thenReturn(emptyList())
            whenever(prefKeys.setWalletActivated(false))
                .thenThrow(SecurityException("Encrypted prefs locked"))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given sign-out throws, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given
            setupBackendSuccess()
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)
            whenever(localWalletCleanupController.cleanupLocalWalletData())
                .thenReturn(emptyList())
            whenever(signOutUseCase.invoke(SignOutMode.Hard))
                .thenThrow(RuntimeException("Session already expired"))

            // When
            val result = useCase.invoke()

            // Then
            assertTrue("Result should be success", result.isSuccess)
        }

    @Test
    fun `Given ALL local steps fail, When invoke is called, Then still returns success`() =
        coroutineRule.runTest {
            // Given — worst case: every local step fails
            setupBackendSuccess()
            whenever(cryptoController.deleteWuaKey()).thenThrow(RuntimeException("Keystore locked"))
            whenever(localWalletCleanupController.cleanupLocalWalletData())
                .thenThrow(RuntimeException("DB locked"))
            whenever(prefKeys.setWalletActivated(false))
                .thenThrow(SecurityException("Prefs locked"))
            whenever(signOutUseCase.invoke(SignOutMode.Hard))
                .thenThrow(RuntimeException("Network error"))

            // When
            val result = useCase.invoke()

            // Then — backend deletion succeeded, so overall is success
            assertTrue("Result should be success", result.isSuccess)
        }

    // endregion

    // region Helpers

    private suspend fun setupBackendSuccess() {
        whenever(walletActivationRepository.deleteWalletActivation())
            .thenReturn(Result.success(Unit))
    }

    private suspend fun setupLocalCleanupDefaults() {
        whenever(cryptoController.deleteWuaKey()).thenReturn(true)
        whenever(localWalletCleanupController.cleanupLocalWalletData())
            .thenReturn(emptyList())
    }

    // endregion
}
