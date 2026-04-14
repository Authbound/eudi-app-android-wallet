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

package eu.europa.ec.dashboardfeature.ui.verification

import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientSession
import eu.europa.ec.dashboardfeature.model.verification.isVerificationHistoryStatus
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.koin.android.annotation.KoinViewModel

data class VerificationRecipientState(
    val isLoading: Boolean = false,
    val hasConsent: Boolean = false,
    val session: VerificationRecipientSession? = null,
    val error: String? = null,
    val incomingVerificationUrl: String? = null
) : ViewState

sealed class VerificationRecipientEvent : ViewEvent {
    data class Init(
        val sessionId: String,
        val accessToken: String,
        val verificationUrl: String? = null
    ) : VerificationRecipientEvent()

    data object Refresh : VerificationRecipientEvent()
    data class ConsentChanged(val accepted: Boolean) : VerificationRecipientEvent()
    data object StartVerification : VerificationRecipientEvent()
    data object OpenInBrowser : VerificationRecipientEvent()
    data object NavigateBack : VerificationRecipientEvent()
    data object DismissError : VerificationRecipientEvent()
}

sealed class VerificationRecipientEffect : ViewSideEffect {
    sealed class Navigation : VerificationRecipientEffect() {
        data object Back : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class OpenUrlInBrowser(val url: String) : VerificationRecipientEffect()
    data class ShowToast(val message: String) : VerificationRecipientEffect()
}

@KoinViewModel
class VerificationRecipientViewModel(
    private val verificationRepository: VerificationRepository,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<VerificationRecipientEvent, VerificationRecipientState, VerificationRecipientEffect>() {

    private var sessionId: String? = null
    private var accessToken: String? = null
    private var refreshJob: Job? = null
    private var statusStreamJob: Job? = null
    private var activeStatusStreamUrl: String? = null
    private var isLoadingSession: Boolean = false

    override fun setInitialState(): VerificationRecipientState = VerificationRecipientState(
        isLoading = true
    )

    override fun handleEvents(event: VerificationRecipientEvent) {
        when (event) {
            is VerificationRecipientEvent.Init -> initializeSession(
                sessionId = event.sessionId,
                accessToken = event.accessToken,
                verificationUrl = event.verificationUrl
            )

            VerificationRecipientEvent.Refresh -> loadSession(showLoading = false)
            is VerificationRecipientEvent.ConsentChanged -> setState { copy(hasConsent = event.accepted) }
            VerificationRecipientEvent.StartVerification -> startVerification()
            VerificationRecipientEvent.OpenInBrowser -> openInBrowser()
            VerificationRecipientEvent.NavigateBack -> setEffect {
                VerificationRecipientEffect.Navigation.Back
            }

            VerificationRecipientEvent.DismissError -> setState { copy(error = null) }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        statusStreamJob?.cancel()
        super.onCleared()
    }

    private fun initializeSession(sessionId: String, accessToken: String, verificationUrl: String?) {
        setState {
            copy(
                incomingVerificationUrl = verificationUrl?.takeIf(String::isNotBlank)
                    ?: incomingVerificationUrl
            )
        }
        if (this.sessionId == sessionId && this.accessToken == accessToken && viewState.value.session != null) {
            return
        }
        this.sessionId = sessionId
        this.accessToken = accessToken
        loadSession(showLoading = true)
    }

    private fun loadSession(showLoading: Boolean) {
        val sessionId = sessionId ?: return
        val accessToken = accessToken ?: return
        if (isLoadingSession) return
        isLoadingSession = true
        if (showLoading) {
            setState {
                copy(
                    isLoading = true,
                    error = null
                )
            }
        }
        viewModelScope.launch {
            try {
                verificationRepository.getPublicVerificationSession(sessionId, accessToken).fold(
                    onSuccess = { session ->
                        setState {
                            copy(
                                isLoading = false,
                                session = session,
                                error = null
                            )
                        }
                        if (isVerificationHistoryStatus(session.status)) {
                            refreshJob?.cancel()
                            statusStreamJob?.cancel()
                            activeStatusStreamUrl = null
                        } else {
                            ensureLiveUpdates(session)
                        }
                    },
                    onFailure = {
                        setState {
                            copy(
                                isLoading = false,
                                error = if (viewState.value.session == null) {
                                    resourceProvider.getString(R.string.verification_recipient_refresh_error_empty)
                                } else {
                                    resourceProvider.getString(R.string.verification_recipient_refresh_error_cached)
                                }
                            )
                        }
                    }
                )
            } finally {
                isLoadingSession = false
            }
        }
    }

    private fun ensurePolling() {
        if (refreshJob?.isActive == true) return
        statusStreamJob?.cancel()
        activeStatusStreamUrl = null
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val currentSession = viewState.value.session ?: continue
                if (isVerificationHistoryStatus(currentSession.status)) {
                    break
                }
                loadSession(showLoading = false)
            }
        }
    }

    private fun ensureLiveUpdates(session: VerificationRecipientSession) {
        val statusStreamUrl = session.statusStreamUrl
        if (statusStreamUrl.isNullOrBlank()) {
            ensurePolling()
            return
        }
        if (activeStatusStreamUrl == statusStreamUrl && statusStreamJob?.isActive == true) {
            return
        }
        refreshJob?.cancel()
        statusStreamJob?.cancel()
        activeStatusStreamUrl = statusStreamUrl
        statusStreamJob = viewModelScope.launch {
            try {
                verificationRepository.observePublicVerificationSessionStatus(statusStreamUrl).collect {
                    loadSession(showLoading = false)
                }
            } catch (_: Exception) {
                activeStatusStreamUrl = null
                ensurePolling()
                return@launch
            }

            activeStatusStreamUrl = null
            val currentSession = viewState.value.session
            if (currentSession != null && !isVerificationHistoryStatus(currentSession.status)) {
                ensurePolling()
            }
        }
    }

    private fun startVerification() {
        val session = viewState.value.session ?: return
        if (!viewState.value.hasConsent) {
            setEffect {
                VerificationRecipientEffect.ShowToast(
                    resourceProvider.getString(R.string.verification_recipient_consent_required)
                )
            }
            return
        }
        val requestUri = session.requestUri
        if (requestUri.isNullOrBlank()) {
            setEffect {
                VerificationRecipientEffect.ShowToast(
                    resourceProvider.getString(R.string.verification_recipient_request_unavailable)
                )
            }
            return
        }
        val serializedConfig = uiSerializer.toBase64(
            RequestUriConfig(
                PresentationMode.OpenId4Vp(
                    uri = requestUri,
                    initiatorRoute = recipientRoute()
                )
            ),
            RequestUriConfig.Parser
        )
        val screenRoute = generateComposableNavigationLink(
            screen = PresentationScreens.PresentationRequest,
            arguments = generateComposableArguments(
                mapOf(RequestUriConfig.serializedKeyName to serializedConfig)
            )
        )
        setEffect {
            VerificationRecipientEffect.Navigation.SwitchScreen(screenRoute = screenRoute)
        }
    }

    private fun openInBrowser() {
        val browserUrl = viewState.value.session?.publicUrl
            ?.takeIf(String::isNotBlank)
            ?: viewState.value.incomingVerificationUrl
                ?.takeIf(String::isNotBlank)
        browserUrl?.let { url ->
            setEffect {
                VerificationRecipientEffect.OpenUrlInBrowser(url)
            }
            return
        }
        setEffect {
            VerificationRecipientEffect.ShowToast(
                resourceProvider.getString(R.string.verification_recipient_public_link_unavailable)
            )
        }
    }

    private fun recipientRoute(): String {
        val sessionId = sessionId ?: return DashboardScreens.Dashboard.screenRoute
        val accessToken = accessToken ?: return DashboardScreens.Dashboard.screenRoute
        val arguments = mutableMapOf(
            "sessionId" to sessionId,
            "accessToken" to accessToken
        )
        viewState.value.incomingVerificationUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { arguments["verificationUrl"] = Uri.encode(it) }
        return generateComposableNavigationLink(
            screen = DashboardScreens.VerificationRecipient,
            arguments = generateComposableArguments(
                arguments
            )
        )
    }
}
