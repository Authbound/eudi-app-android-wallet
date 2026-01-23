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

package eu.europa.ec.dashboardfeature.ui.verification

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class VerificationHomeState(
    val isLoading: Boolean = false,
    val activeSessions: List<VerificationSession> = emptyList(),
    val historySessions: List<VerificationSession> = emptyList(),
) : ViewState

sealed class VerificationHomeEvent : ViewEvent {
    data object Init : VerificationHomeEvent()
    data object CreateVerification : VerificationHomeEvent()
}

sealed class VerificationHomeEffect : ViewSideEffect {
    sealed class Navigation : VerificationHomeEffect() {
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }
}

@KoinViewModel
class VerificationHomeViewModel(
    private val verificationRepository: VerificationRepository,
) : MviViewModel<VerificationHomeEvent, VerificationHomeState, VerificationHomeEffect>() {

    private var isObservingSessions: Boolean = false

    override fun setInitialState(): VerificationHomeState = VerificationHomeState(isLoading = true)

    override fun handleEvents(event: VerificationHomeEvent) {
        when (event) {
            VerificationHomeEvent.Init -> observeSessions()
            VerificationHomeEvent.CreateVerification -> {
                setEffect {
                    VerificationHomeEffect.Navigation.SwitchScreen(
                        screenRoute = DashboardScreens.VerificationTemplateSelection.screenRoute
                    )
                }
            }
        }
    }

    private fun observeSessions() {
        if (isObservingSessions) return
        isObservingSessions = true
        viewModelScope.launch {
            verificationRepository.getVerificationSessions().collectLatest { sessions ->
                setState {
                    copy(
                        isLoading = false,
                        activeSessions = sessions,
                        historySessions = emptyList(),
                    )
                }
            }
        }
    }
}
