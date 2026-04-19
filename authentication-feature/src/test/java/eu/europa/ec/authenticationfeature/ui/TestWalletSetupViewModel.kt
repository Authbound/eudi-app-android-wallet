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

package eu.europa.ec.authenticationfeature.ui

import app.cash.turbine.test
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LocalAuthRouteDecision
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.RecoveryCheckpoint
import eu.europa.ec.authenticationlogic.usecase.FinalizeWalletActivationStateUseCase
import eu.europa.ec.authenticationlogic.usecase.PrepareWalletRecoveryUseCase
import eu.europa.ec.authenticationlogic.usecase.ResolveLocalAuthRouteUseCase
import eu.europa.ec.authenticationlogic.usecase.ResetLocalWalletForRecoveryUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceSecurityState
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
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

class TestWalletSetupViewModel {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var createWalletAttestationUseCase: CreateWalletAttestationUseCase

    @Mock
    private lateinit var deleteWalletActivationUseCase: DeleteWalletActivationUseCase

    @Mock
    private lateinit var signOutUseCase: SignOutUseCase

    @Mock
    private lateinit var deviceController: DeviceController

    @Mock
    private lateinit var biometricAuthenticationController: eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController

    @Mock
    private lateinit var pushNotificationController: PushNotificationController

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var pinStorageController: PinStorageController

    @Mock
    private lateinit var resolveLocalAuthRouteUseCase: ResolveLocalAuthRouteUseCase

    @Mock
    private lateinit var finalizeWalletActivationStateUseCase: FinalizeWalletActivationStateUseCase

    @Mock
    private lateinit var recoveryCheckpointController: RecoveryCheckpointController

    @Mock
    private lateinit var prepareWalletRecoveryUseCase: PrepareWalletRecoveryUseCase

    @Mock
    private lateinit var resetLocalWalletForRecoveryUseCase: ResetLocalWalletForRecoveryUseCase

    @Mock
    private lateinit var localUnlockTracker: LocalUnlockTracker

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given wallet already activated and route requires PIN verification, When continuing to home, Then navigates to PIN verify`() =
        coroutineRule.runTest {
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(recoveryCheckpointController.getCheckpoint()).thenReturn(RecoveryCheckpoint.NONE)
            whenever(pinStorageController.getLocalUnlockStatus()).thenReturn(LocalUnlockStatus.ReadyForPin)
            whenever(prefsController.safeBool(any(), any())).thenReturn(false)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(false)
            whenever(
                resolveLocalAuthRouteUseCase.invoke(
                    LocalUnlockStatus.ReadyForPin,
                    false,
                    RecoveryCheckpoint.NONE,
                    false
                )
            ).thenReturn(LocalAuthRouteDecision.PinVerificationRequired)

            val viewModel = createViewModel()
            assertTrue(viewModel.viewState.value.isWalletAlreadyActivated)

            viewModel.effect.test {
                viewModel.setEvent(WalletSetupEvent.ContinueToHome)
                assertEquals(WalletSetupEffect.NavigateToPinVerify, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Given wallet already activated and route is security error, When continuing to home, Then navigates to login`() =
        coroutineRule.runTest {
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(true)
            whenever(recoveryCheckpointController.getCheckpoint()).thenReturn(RecoveryCheckpoint.NONE)
            whenever(pinStorageController.getLocalUnlockStatus()).thenReturn(LocalUnlockStatus.TamperDetected)
            whenever(prefsController.safeBool(any(), any())).thenReturn(false)
            whenever(localUnlockTracker.isUnlocked()).thenReturn(false)
            whenever(
                resolveLocalAuthRouteUseCase.invoke(
                    LocalUnlockStatus.TamperDetected,
                    false,
                    RecoveryCheckpoint.NONE,
                    false
                )
            ).thenReturn(
                LocalAuthRouteDecision.SecurityError("tampered")
            )

            val viewModel = createViewModel()
            assertTrue(viewModel.viewState.value.isWalletAlreadyActivated)

            viewModel.effect.test {
                viewModel.setEvent(WalletSetupEvent.ContinueToHome)
                assertEquals(WalletSetupEffect.NavigateToLogin, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Given online reactivation required and push registration fails, When activating wallet, Then recovery prepare is not requested`() =
        coroutineRule.runTest {
            whenever(prefKeys.isWalletActivatedSafe()).thenReturn(false)
            whenever(recoveryCheckpointController.getCheckpoint())
                .thenReturn(RecoveryCheckpoint.ONLINE_REACTIVATION_REQUIRED)
            whenever(deviceController.getDeviceSecurityState()).thenReturn(
                DeviceSecurityState(
                    isDeviceSecure = true,
                    canAuthenticateWithDeviceCredential = true,
                    canUseStrongBiometrics = true
                )
            )
            whenever(pushNotificationController.registerForPushNotifications()).thenReturn(
                Result.failure(IllegalStateException("fcm failed"))
            )
            whenever(deviceController.getDeviceInfo()).thenReturn(MOCK_DEVICE_INFO)

            val viewModel = createViewModel()

            viewModel.setEvent(WalletSetupEvent.ActivateWallet)
            coroutineRule.testScope.advanceUntilIdle()

            verify(prepareWalletRecoveryUseCase, never()).invoke(any())
        }

    private fun createViewModel(): WalletSetupViewModel {
        return WalletSetupViewModel(
            createWalletAttestationUseCase = createWalletAttestationUseCase,
            deleteWalletActivationUseCase = deleteWalletActivationUseCase,
            signOutUseCase = signOutUseCase,
            deviceController = deviceController,
            biometricAuthenticationController = biometricAuthenticationController,
            pushNotificationController = pushNotificationController,
            prefKeys = prefKeys,
            prefsController = prefsController,
            logController = logController,
            pinStorageController = pinStorageController,
            resolveLocalAuthRouteUseCase = resolveLocalAuthRouteUseCase,
            finalizeWalletActivationStateUseCase = finalizeWalletActivationStateUseCase,
            recoveryCheckpointController = recoveryCheckpointController,
            prepareWalletRecoveryUseCase = prepareWalletRecoveryUseCase,
            resetLocalWalletForRecoveryUseCase = resetLocalWalletForRecoveryUseCase,
            localUnlockTracker = localUnlockTracker
        )
    }

    private companion object {
        private val MOCK_DEVICE_INFO = DeviceInfo(
            deviceId = "device-123",
            deviceName = "Test Device",
            deviceModel = "Pixel 7",
            deviceOs = "Android 14",
            deviceOsVersion = "34",
            securityPatchLevel = "2026-04-01",
            hasSecureElement = true,
            hasHardwareKeystore = true,
            hasStrongBox = true,
            attestationSupported = true,
            deviceVerifiedBoot = true,
            playProtectVerified = true,
            hasBiometricHardware = true
        )
    }
}
