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
package eu.europa.ec.authenticationfeature.ui

import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.device.DeviceSecurityState
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class DeviceSecurityRequiredViewModel(
    private val deviceController: DeviceController,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logController: LogController
) : MviViewModel<DeviceSecurityRequiredEvent, DeviceSecurityRequiredState, DeviceSecurityRequiredEffect>() {

    init {
        viewModelScope.launch {
            setState { copy(isChecking = true, infoMessage = null) }
            checkDeviceSecurity(isRetry = false)
        }
    }

    override fun setInitialState(): DeviceSecurityRequiredState = DeviceSecurityRequiredState()

    override fun handleEvents(event: DeviceSecurityRequiredEvent) {
        when (event) {
            DeviceSecurityRequiredEvent.Retry -> {
                viewModelScope.launch {
                    setState { copy(isChecking = true, infoMessage = null) }
                    checkDeviceSecurity(isRetry = true)
                }
            }
            DeviceSecurityRequiredEvent.OpenSettings -> setEffect { DeviceSecurityRequiredEffect.OpenSecuritySettings }
            DeviceSecurityRequiredEvent.DismissInfo -> setState { copy(infoMessage = null) }
        }
    }

    private suspend fun checkDeviceSecurity(isRetry: Boolean) {
        val securityState: DeviceSecurityState = deviceController.getDeviceSecurityState()
        if (securityState.isReady) {
            logController.d("DeviceSecurityRequiredViewModel", "Device security ready, continuing")
            setState { copy(isChecking = false, infoMessage = null) }
            val currentUser = getCurrentUserUseCase()
            if (currentUser == null) {
                setEffect { DeviceSecurityRequiredEffect.NavigateToLogin }
            } else {
                setEffect { DeviceSecurityRequiredEffect.NavigateToWalletSetup }
            }
            return
        }
        logController.w("DeviceSecurityRequiredViewModel") { "Device security missing" }
        val message: String? = if (isRetry) {
            "Device security is still not set up. Please add a screen lock or biometrics and try again."
        } else {
            null
        }
        setState { copy(isChecking = false, infoMessage = message) }
    }
}

data class DeviceSecurityRequiredState(
    val isChecking: Boolean = false,
    val infoMessage: String? = null
) : ViewState

sealed class DeviceSecurityRequiredEvent : ViewEvent {
    data object Retry : DeviceSecurityRequiredEvent()
    data object OpenSettings : DeviceSecurityRequiredEvent()
    data object DismissInfo : DeviceSecurityRequiredEvent()
}

sealed class DeviceSecurityRequiredEffect : ViewSideEffect {
    data object NavigateToWalletSetup : DeviceSecurityRequiredEffect()
    data object NavigateToLogin : DeviceSecurityRequiredEffect()
    data object OpenSecuritySettings : DeviceSecurityRequiredEffect()
}
