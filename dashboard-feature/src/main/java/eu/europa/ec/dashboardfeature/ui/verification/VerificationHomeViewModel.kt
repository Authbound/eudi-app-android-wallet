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
import eu.europa.ec.dashboardfeature.model.verification.isVerificationHistoryStatus
import eu.europa.ec.dashboardfeature.model.verification.lastUpdatedAt
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class VerificationHomeState(
    val isLoading: Boolean = false,
    val activeSessions: List<VerificationSession> = emptyList(),
    val historySessions: List<VerificationSession> = emptyList(),
    val error: String? = null,
) : ViewState

sealed class VerificationHomeEvent : ViewEvent {
    data object Init : VerificationHomeEvent()
    data object Refresh : VerificationHomeEvent()
    data object DismissError : VerificationHomeEvent()
    data object CreateVerification : VerificationHomeEvent()
    data class OpenSession(val sessionId: String) : VerificationHomeEvent()
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
    private val resourceProvider: ResourceProvider,
) : MviViewModel<VerificationHomeEvent, VerificationHomeState, VerificationHomeEffect>() {

    private var isObservingSessions: Boolean = false
    private var isRefreshingSessions: Boolean = false

    override fun setInitialState(): VerificationHomeState = VerificationHomeState(isLoading = true)

    override fun handleEvents(event: VerificationHomeEvent) {
        when (event) {
            VerificationHomeEvent.Init -> {
                observeSessions()
                refreshSessions()
            }
            VerificationHomeEvent.Refresh -> refreshSessions()
            VerificationHomeEvent.DismissError -> setState { copy(error = null) }
            VerificationHomeEvent.CreateVerification -> {
                setEffect {
                    VerificationHomeEffect.Navigation.SwitchScreen(
                        screenRoute = DashboardScreens.VerificationTemplateSelection.screenRoute
                    )
                }
            }
            is VerificationHomeEvent.OpenSession -> {
                setEffect {
                    VerificationHomeEffect.Navigation.SwitchScreen(
                        screenRoute = generateComposableNavigationLink(
                            screen = DashboardScreens.VerificationSharing,
                            arguments = generateComposableArguments(
                                mapOf("sessionId" to event.sessionId)
                            )
                        ),
                        popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                        inclusive = false
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
                val activeSessions = sessions
                    .filterNot { isVerificationHistoryStatus(it.status) }
                    .sortedWith(activeSessionComparator)
                val historySessions = sessions
                    .filter { isVerificationHistoryStatus(it.status) }
                    .sortedWith(historySessionComparator)

                setState {
                    copy(
                        isLoading = false,
                        activeSessions = activeSessions,
                        historySessions = historySessions,
                    )
                }
            }
        }
    }

    private fun refreshSessions() {
        if (isRefreshingSessions) return
        isRefreshingSessions = true
        viewModelScope.launch {
            try {
                val hasVisibleSessions = viewState.value.activeSessions.isNotEmpty() ||
                    viewState.value.historySessions.isNotEmpty()

                setState {
                    copy(
                        isLoading = !hasVisibleSessions,
                        error = null
                    )
                }

                verificationRepository.refreshVerificationSessions().fold(
                    onSuccess = {
                        setState {
                            copy(
                                isLoading = false,
                                error = null
                            )
                        }
                    },
                    onFailure = {
                        setState {
                            copy(
                                isLoading = false,
                                error = if (hasVisibleSessions) {
                                    resourceProvider.getString(R.string.verification_home_refresh_error_cached)
                                } else {
                                    resourceProvider.getString(R.string.verification_home_refresh_error_empty)
                                }
                            )
                        }
                    }
                )
            } finally {
                isRefreshingSessions = false
            }
        }
    }
}

private val activeSessionComparator = compareBy<VerificationSession> { it.expiresAt ?: Long.MAX_VALUE }
    .thenByDescending { it.lastUpdatedAt() }
    .thenByDescending { it.createdAt }

private val historySessionComparator = compareByDescending<VerificationSession> { it.lastUpdatedAt() }
    .thenByDescending { it.createdAt }
