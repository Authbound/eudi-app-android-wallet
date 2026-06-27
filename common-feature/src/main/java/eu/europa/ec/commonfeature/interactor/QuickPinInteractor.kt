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

package eu.europa.ec.commonfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LocalRecoveryResetResult
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.PinValidationResult
import eu.europa.ec.authenticationlogic.secure.SecurePin
import eu.europa.ec.authenticationlogic.model.WalletSecurityEventType
import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.authenticationlogic.usecase.ReportWalletSecurityIncidentUseCase
import eu.europa.ec.authenticationlogic.usecase.ResetLocalWalletForRecoveryUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.validator.FormValidator
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface QuickPinInteractor : FormValidator {
    fun setPin(newPin: SecurePin, initialPin: SecurePin): Flow<QuickPinInteractorSetPinPartialState>
    fun changePin(
        newPin: SecurePin
    ): Flow<QuickPinInteractorSetPinPartialState>

    fun isCurrentPinValid(pin: SecurePin): Flow<QuickPinInteractorPinValidPartialState>
    fun isPinMatched(
        currentPin: SecurePin,
        newPin: SecurePin
    ): Flow<QuickPinInteractorPinValidPartialState>

    suspend fun hasPin(): Boolean
    suspend fun resetPin()
    suspend fun getLocalUnlockStatus(): LocalUnlockStatus = if (hasPin()) {
        LocalUnlockStatus.ReadyForPin
    } else {
        LocalUnlockStatus.NotProvisioned
    }
    suspend fun beginPinRecovery(): LocalUnlockStatus = LocalUnlockStatus.RecoveryRequired
    suspend fun performLocalRecoveryReset(): LocalRecoveryResetResult =
        LocalRecoveryResetResult.SecurityFailure("Local wallet recovery is unavailable.")
    suspend fun reportTamperDetected(signals: List<String> = emptyList()) {}
    suspend fun reportRecoveryStarted(signals: List<String> = emptyList()) {}
    suspend fun reportRecoveryCompleted(signals: List<String> = emptyList()) {}
    suspend fun performDestructiveReset() {
        resetPin()
    }
}

class QuickPinInteractorImpl(
    private val formValidator: FormValidator,
    private val pinStorageController: PinStorageController,
    private val resourceProvider: ResourceProvider,
    private val localUnlockTracker: LocalUnlockTracker,
    private val biometryStorageController: BiometryStorageController,
    private val prefsController: PrefsControllerV2,
    private val prefKeys: PrefKeysV2,
    private val cryptoController: CryptoController,
    private val keystoreController: KeystoreController,
    private val localWalletCleanupController: LocalWalletCleanupController,
    private val reportWalletSecurityIncidentUseCase: ReportWalletSecurityIncidentUseCase,
    private val resetLocalWalletForRecoveryUseCase: ResetLocalWalletForRecoveryUseCase,
    private val signOutUseCase: SignOutUseCase
) : FormValidator by formValidator, QuickPinInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override suspend fun hasPin(): Boolean {
        return when (pinStorageController.getLocalUnlockStatus()) {
            LocalUnlockStatus.NotProvisioned -> false
            else -> true
        }
    }

    override suspend fun resetPin() {
        pinStorageController.clearPinData()
    }

    override suspend fun getLocalUnlockStatus(): LocalUnlockStatus {
        return pinStorageController.getLocalUnlockStatus()
    }

    override suspend fun beginPinRecovery(): LocalUnlockStatus {
        return pinStorageController.prepareRecovery()
    }

    override suspend fun performLocalRecoveryReset(): LocalRecoveryResetResult {
        return resetLocalWalletForRecoveryUseCase()
    }

    override suspend fun reportTamperDetected(signals: List<String>) {
        reportWalletSecurityIncidentUseCase(
            WalletSecurityEventType.LocalAuthTamperDetected,
            signals
        )
    }

    override suspend fun reportRecoveryStarted(signals: List<String>) {
        reportWalletSecurityIncidentUseCase(
            WalletSecurityEventType.LocalAuthRecoveryStarted,
            signals
        )
    }

    override suspend fun reportRecoveryCompleted(signals: List<String>) {
        reportWalletSecurityIncidentUseCase(
            WalletSecurityEventType.LocalAuthRecoveryCompleted,
            signals
        )
    }

    override suspend fun performDestructiveReset() {
        runCatching { localWalletCleanupController.cleanupLocalWalletData() }
        runCatching { cryptoController.deleteWuaKey() }
        runCatching { prefKeys.setWalletActivated(false) }
        signOutUseCase(SignOutMode.Hard)
    }

    override fun setPin(
        newPin: SecurePin,
        initialPin: SecurePin
    ): Flow<QuickPinInteractorSetPinPartialState> =
        flow {
            try {
                if (!initialPin.contentEquals(newPin)) {
                    emit(
                        QuickPinInteractorSetPinPartialState.Failed(
                            resourceProvider.getString(R.string.quick_pin_non_match)
                        )
                    )
                    return@flow
                }
                pinStorageController.setPin(newPin)
                prefsController.setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, false)
                // Mark as unlocked after successful PIN creation
                localUnlockTracker.markUnlocked()
                emit(QuickPinInteractorSetPinPartialState.Success)
            } finally {
                initialPin.close()
                newPin.close()
            }
        }.safeAsync {
            initialPin.close()
            newPin.close()
            QuickPinInteractorSetPinPartialState.Failed(
                it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun changePin(
        newPin: SecurePin
    ): Flow<QuickPinInteractorSetPinPartialState> =
        flow {
            try {
                pinStorageController.setPin(newPin)
                prefsController.setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, false)
                // Mark as unlocked after successful PIN change
                localUnlockTracker.markUnlocked()
                emit(QuickPinInteractorSetPinPartialState.Success)
            } finally {
                newPin.close()
            }
        }.safeAsync {
            newPin.close()
            QuickPinInteractorSetPinPartialState.Failed(
                it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun isCurrentPinValid(pin: SecurePin): Flow<QuickPinInteractorPinValidPartialState> =
        flow {
            try {
                when (val result = pinStorageController.verifyPin(pin)) {
                    PinValidationResult.Success -> {
                        localUnlockTracker.markUnlocked()
                        emit(QuickPinInteractorPinValidPartialState.Success)
                    }
                    is PinValidationResult.Failed -> {
                        val errorMessage: String = when {
                            result.lockedUntilMs != null -> resourceProvider.getString(
                                R.string.quick_pin_locked_error
                            )
                            else -> resourceProvider.getString(R.string.quick_pin_invalid_error)
                        }
                        emit(
                            QuickPinInteractorPinValidPartialState.Failed(
                                errorMessage = errorMessage,
                                lockedUntilMs = result.lockedUntilMs
                            )
                        )
                    }
                    PinValidationResult.RecoveryRequired -> {
                        emit(
                            QuickPinInteractorPinValidPartialState.Failed(
                                errorMessage = resourceProvider.getString(
                                    R.string.quick_pin_recovery_required_error
                                ),
                                requiresRecovery = true
                            )
                        )
                    }
                    PinValidationResult.TamperDetected -> {
                        reportTamperDetected(signals = listOf("local_auth_integrity_failure"))
                        emit(
                            QuickPinInteractorPinValidPartialState.Failed(
                                errorMessage = resourceProvider.getString(
                                    R.string.quick_pin_security_error
                                ),
                                isSecurityError = true
                            )
                        )
                    }
                }
            } finally {
                pin.close()
            }
        }.safeAsync {
            pin.close()
            QuickPinInteractorPinValidPartialState.Failed(
                it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun isPinMatched(
        currentPin: SecurePin,
        newPin: SecurePin
    ): Flow<QuickPinInteractorPinValidPartialState> =
        flow {
            try {
                if (currentPin.contentEquals(newPin)) {
                    emit(QuickPinInteractorPinValidPartialState.Success)
                } else {
                    emit(
                        QuickPinInteractorPinValidPartialState.Failed(
                            resourceProvider.getString(
                                R.string.quick_pin_invalid_error
                            )
                        )
                    )
                }
            } finally {
                currentPin.close()
                newPin.close()
            }
        }.safeAsync {
            currentPin.close()
            newPin.close()
            QuickPinInteractorPinValidPartialState.Failed(
                it.localizedMessage ?: genericErrorMsg
            )
        }
}

sealed class QuickPinInteractorSetPinPartialState {
    data object Success : QuickPinInteractorSetPinPartialState()
    data class Failed(val errorMessage: String) : QuickPinInteractorSetPinPartialState()
}

sealed class QuickPinInteractorPinValidPartialState {
    data object Success : QuickPinInteractorPinValidPartialState()
    data class Failed(
        val errorMessage: String,
        val lockedUntilMs: Long? = null,
        val requiresRecovery: Boolean = false,
        val isSecurityError: Boolean = false
    ) : QuickPinInteractorPinValidPartialState()
}
