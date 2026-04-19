/*
 * Copyright (c) 2026 European Commission
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

import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LocalRecoveryResetResult
import eu.europa.ec.authenticationlogic.model.RecoveryCheckpoint
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.github.jan.supabase.auth.user.UserInfo
import junit.framework.TestCase.assertTrue
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

class TestResetLocalWalletForRecoveryUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var supabaseAuthRepository: SupabaseAuthRepository

    @Mock
    private lateinit var pinStorageController: PinStorageController

    @Mock
    private lateinit var localUnlockTracker: LocalUnlockTracker

    @Mock
    private lateinit var biometryStorageController: BiometryStorageController

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var cryptoController: CryptoController

    @Mock
    private lateinit var keystoreController: KeystoreController

    @Mock
    private lateinit var localWalletCleanupController: LocalWalletCleanupController

    @Mock
    private lateinit var recoveryCheckpointController: RecoveryCheckpointController

    @Mock
    private lateinit var walletRecoveryChallengeController: WalletRecoveryChallengeController

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var userInfo: UserInfo

    private lateinit var useCase: ResetLocalWalletForRecoveryUseCase
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = ResetLocalWalletForRecoveryUseCaseImpl(
            supabaseAuthRepository = supabaseAuthRepository,
            pinStorageController = pinStorageController,
            localUnlockTracker = localUnlockTracker,
            biometryStorageController = biometryStorageController,
            prefsController = prefsController,
            prefKeys = prefKeys,
            cryptoController = cryptoController,
            keystoreController = keystoreController,
            localWalletCleanupController = localWalletCleanupController,
            recoveryCheckpointController = recoveryCheckpointController,
            walletRecoveryChallengeController = walletRecoveryChallengeController,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given no authenticated user, When reset starts, Then checkpoint is rolled back`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUser()).thenReturn(null)

            val result = useCase()

            assertTrue(result is LocalRecoveryResetResult.SecurityFailure)
            verify(recoveryCheckpointController).setCheckpoint(RecoveryCheckpoint.LOCAL_RESET_IN_PROGRESS)
            verify(recoveryCheckpointController).clearCheckpoint()
            verify(pinStorageController, never()).clearPinData(any())
        }
}
