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

package eu.europa.ec.authboundpidfeature.ui.processing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidProcessingInteractor
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidProcessingPartialState
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.corelogic.controller.ResolveDocumentOfferPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val statusMessage: String = ""
) : ViewState

enum class AuthboundPidResultType {
    VERIFIED,
    FAILED,
    EXPIRED,
    CANCELED,
    TIMEOUT,
    CANCELLED_UNSUPPORTED_DEVICE,
    CANCELLED_UNSUPPORTED_ID,
    UNKNOWN
}

sealed class Event : ViewEvent {
    data object Init : Event()
    data object Retry : Event()
    data object GoToDashboard : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class NavigateToIssuance(val screenRoute: String) : Navigation()
        data object PopToDashboard : Navigation()
        data class NavigateToResult(val resultType: AuthboundPidResultType) : Navigation()
        data object PopToIntro : Navigation()
    }
}

@KoinViewModel
class AuthboundPidProcessingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val processingInteractor: AuthboundPidProcessingInteractor,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    private val callbackStatus: String
        get() = savedStateHandle.get<String>("callbackStatus") ?: "success"

    private val callbackNonce: String
        get() = savedStateHandle.get<String>("nonce") ?: ""

    private var pollingJob: Job? = null
    private var offerJob: Job? = null
    private var isPollingStarted = false

    override fun setInitialState(): State = State(
        statusMessage = resourceProvider.getString(R.string.authboundpid_processing_status)
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> handleCallback()
            is Event.Retry -> setEffect { Effect.Navigation.PopToIntro }
            is Event.GoToDashboard -> setEffect { Effect.Navigation.PopToDashboard }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun handleCallback() {
        when (callbackStatus) {
            "success" -> {
                if (!isPollingStarted) {
                    isPollingStarted = true
                    startPolling()
                }
            }
            "cancelled" -> navigateToResult(AuthboundPidResultType.CANCELED)
            "cancelledUnsupportedDevice" -> navigateToResult(AuthboundPidResultType.CANCELLED_UNSUPPORTED_DEVICE)
            "cancelledUnsupportedId" -> navigateToResult(AuthboundPidResultType.CANCELLED_UNSUPPORTED_ID)
            else -> {
                if (!isPollingStarted) {
                    isPollingStarted = true
                    startPolling()
                }
            }
        }
    }

    private fun navigateToResult(type: AuthboundPidResultType) {
        setState { copy(isLoading = false) }
        viewModelScope.launch {
            processingInteractor.clearPendingSession()
        }
        setEffect { Effect.Navigation.NavigateToResult(type) }
    }

    private fun startPolling() {
        viewModelScope.launch {
            // Validate nonce before proceeding
            val expectedNonce = processingInteractor.getPendingNonce()
            if (expectedNonce != null && callbackNonce != expectedNonce) {
                setState {
                    copy(
                        isLoading = false,
                        error = ContentErrorConfig(
                            errorSubTitle = resourceProvider.getString(R.string.authboundpid_processing_session_not_found),
                            onCancel = { setEvent(Event.GoToDashboard) }
                        )
                    )
                }
                return@launch
            }

            val sessionId = processingInteractor.getPendingSessionId()
            if (sessionId.isNullOrBlank()) {
                setState {
                    copy(
                        isLoading = false,
                        error = ContentErrorConfig(
                            errorSubTitle = resourceProvider.getString(R.string.authboundpid_processing_session_not_found),
                            onCancel = { setEvent(Event.GoToDashboard) }
                        )
                    )
                }
                return@launch
            }

            // Cancel any previous polling coroutine
            pollingJob?.cancel()
            pollingJob = viewModelScope.launch {
                processingInteractor.pollForResult(sessionId).collect { state ->
                    when (state) {
                        is AuthboundPidProcessingPartialState.Polling -> {
                            setState {
                                copy(
                                    isLoading = true,
                                    statusMessage = resourceProvider.getString(R.string.authboundpid_processing_status)
                                )
                            }
                        }
                        is AuthboundPidProcessingPartialState.Verified -> {
                            setState {
                                copy(statusMessage = resourceProvider.getString(R.string.authboundpid_processing_issuing))
                            }
                            processingInteractor.clearPendingSession()
                            handleCredentialOffer(state.credentialOfferUri)
                        }
                        is AuthboundPidProcessingPartialState.VerifiedNoOffer -> {
                            processingInteractor.clearPendingSession()
                            navigateToResult(AuthboundPidResultType.VERIFIED)
                        }
                        is AuthboundPidProcessingPartialState.Failed -> {
                            processingInteractor.clearPendingSession()
                            navigateToResult(AuthboundPidResultType.FAILED)
                        }
                        is AuthboundPidProcessingPartialState.Expired -> {
                            processingInteractor.clearPendingSession()
                            navigateToResult(AuthboundPidResultType.EXPIRED)
                        }
                        is AuthboundPidProcessingPartialState.Canceled -> {
                            processingInteractor.clearPendingSession()
                            navigateToResult(AuthboundPidResultType.CANCELED)
                        }
                        is AuthboundPidProcessingPartialState.Timeout -> {
                            processingInteractor.clearPendingSession()
                            navigateToResult(AuthboundPidResultType.TIMEOUT)
                        }
                    }
                }
            }
        }
    }

    private fun handleCredentialOffer(offerUri: String) {
        offerJob?.cancel()
        offerJob = viewModelScope.launch {
            walletCoreDocumentsController.resolveDocumentOffer(offerUri).collect { response ->
                when (response) {
                    is ResolveDocumentOfferPartialState.Success -> {
                        val offerConfig = uiSerializer.toBase64(
                            OfferUiConfig(
                                offerUri = offerUri,
                                onSuccessNavigation = ConfigNavigation(
                                    navigationType = NavigationType.PopTo(
                                        screen = DashboardScreens.Dashboard
                                    )
                                ),
                                onCancelNavigation = ConfigNavigation(
                                    navigationType = NavigationType.PopTo(
                                        screen = DashboardScreens.Dashboard
                                    )
                                )
                            ),
                            OfferUiConfig.Parser
                        )
                        val route = generateComposableNavigationLink(
                            screen = IssuanceScreens.DocumentOffer,
                            arguments = generateComposableArguments(
                                mapOf(
                                    OfferUiConfig.serializedKeyName to (offerConfig ?: "")
                                )
                            )
                        )
                        setEffect { Effect.Navigation.NavigateToIssuance(route) }
                    }
                    is ResolveDocumentOfferPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    errorSubTitle = response.errorMessage,
                                    onCancel = { setEvent(Event.DismissError) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
