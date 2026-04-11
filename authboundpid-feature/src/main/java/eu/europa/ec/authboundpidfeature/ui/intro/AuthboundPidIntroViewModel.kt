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

package eu.europa.ec.authboundpidfeature.ui.intro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroInteractor
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroPartialState
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidVerificationPartialState
import eu.europa.ec.authboundpidfeature.model.AuthboundPidResultType
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
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.util.Locale

data class State(
    val isLoading: Boolean = false,
    val isCompleting: Boolean = false,
    val statusMessage: String = "",
    val error: ContentErrorConfig? = null
) : ViewState

sealed class Event : ViewEvent {
    data object StartVerification : Event()
    data class CandourSdkResult(val status: String) : Event()
    data object CandourSdkLaunchFailed : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class NavigateToIssuance(val screenRoute: String) : Navigation()
        data class NavigateToResult(val resultType: AuthboundPidResultType) : Navigation()
    }

    data class LaunchCandourSdk(
        val candourSessionId: String,
        val candourApiEndpoint: String
    ) : Effect()
}

@KoinViewModel
class AuthboundPidIntroViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val interactor: AuthboundPidIntroInteractor,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    companion object {
        private const val ACTIVE_SESSION_ID_KEY = "authboundpid_active_session_id"
        private const val AWAITING_SDK_RESULT_KEY = "authboundpid_awaiting_sdk_result"
    }

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.StartVerification -> startVerification()
            is Event.CandourSdkResult -> handleCandourSdkResult(event.status)
            is Event.CandourSdkLaunchFailed -> handleCandourSdkLaunchFailed()
            is Event.GoBack -> if (!viewState.value.isCompleting) {
                setEffect { Effect.Navigation.Pop }
            }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun startVerification() {
        if (viewState.value.isLoading || viewState.value.isCompleting) {
            return
        }

        setState { copy(isLoading = true, isCompleting = false, statusMessage = "", error = null) }

        viewModelScope.launch {
            interactor.createSession().collect { state ->
                when (state) {
                    is AuthboundPidIntroPartialState.CreatingSession -> {
                        setState { copy(isLoading = true, error = null) }
                    }
                    is AuthboundPidIntroPartialState.SessionCreated -> {
                        saveActiveSessionId(state.sessionId)
                        setAwaitingSdkResult(true)
                        setState {
                            copy(
                                isLoading = false,
                                isCompleting = true,
                                statusMessage = resourceProvider.getString(R.string.authboundpid_intro_launching)
                            )
                        }
                        setEffect {
                            Effect.LaunchCandourSdk(
                                candourSessionId = state.candourSessionId,
                                candourApiEndpoint = state.candourApiEndpoint
                            )
                        }
                    }
                    is AuthboundPidIntroPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                isCompleting = false,
                                statusMessage = "",
                                error = ContentErrorConfig(
                                    errorSubTitle = state.errorMessage,
                                    onCancel = { setEvent(Event.DismissError) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleCandourSdkResult(status: String) {
        if (!isAwaitingSdkResult()) {
            return
        }

        setAwaitingSdkResult(false)
        if (isSuccessfulCandourStatus(status)) {
            val sessionId = getActiveSessionId()
            if (sessionId.isNullOrBlank()) {
                navigateToResult(AuthboundPidResultType.UNKNOWN)
                return
            }
            fetchVerificationResult(sessionId)
            return
        }

        navigateToResult(status.toCandourResultType())
    }

    private fun handleCandourSdkLaunchFailed() {
        if (!isAwaitingSdkResult()) {
            return
        }

        setAwaitingSdkResult(false)
        navigateToResult(AuthboundPidResultType.UNAVAILABLE)
    }

    private fun fetchVerificationResult(sessionId: String) {
        setState {
            copy(
                isLoading = false,
                isCompleting = true,
                statusMessage = resourceProvider.getString(R.string.authboundpid_processing_status),
                error = null
            )
        }

        viewModelScope.launch {
            interactor.getVerificationResult(sessionId).collect { state ->
                when (state) {
                    is AuthboundPidVerificationPartialState.Loading -> {
                        setState {
                            copy(
                                isLoading = false,
                                isCompleting = true,
                                statusMessage = resourceProvider.getString(R.string.authboundpid_processing_status)
                            )
                        }
                    }
                    is AuthboundPidVerificationPartialState.Verified -> {
                        val offerUri = state.credentialOfferUri
                        if (offerUri.isNullOrBlank()) {
                            navigateToResult(AuthboundPidResultType.UNKNOWN)
                            return@collect
                        }

                        setState {
                            copy(
                                isLoading = false,
                                isCompleting = true,
                                statusMessage = resourceProvider.getString(R.string.authboundpid_processing_issuing)
                            )
                        }
                        handleCredentialOffer(offerUri)
                    }
                    is AuthboundPidVerificationPartialState.Failed -> {
                        navigateToResult(AuthboundPidResultType.FAILED)
                    }
                    is AuthboundPidVerificationPartialState.Expired -> {
                        navigateToResult(AuthboundPidResultType.EXPIRED)
                    }
                    is AuthboundPidVerificationPartialState.Timeout -> {
                        navigateToResult(AuthboundPidResultType.TIMEOUT)
                    }
                    is AuthboundPidVerificationPartialState.Failure -> {
                        navigateToResult(AuthboundPidResultType.UNKNOWN)
                    }
                }
            }
        }
    }

    private fun handleCredentialOffer(offerUri: String) {
        viewModelScope.launch {
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
                        if (offerConfig.isNullOrBlank()) {
                            navigateToResult(AuthboundPidResultType.UNKNOWN)
                            return@collect
                        }

                        clearSessionState()
                        val route = generateComposableNavigationLink(
                            screen = IssuanceScreens.DocumentOffer,
                            arguments = generateComposableArguments(
                                mapOf(
                                    OfferUiConfig.serializedKeyName to offerConfig
                                )
                            )
                        )
                        setState { copy(isLoading = false, isCompleting = false, statusMessage = "") }
                        setEffect { Effect.Navigation.NavigateToIssuance(route) }
                    }
                    is ResolveDocumentOfferPartialState.Failure -> {
                        navigateToResult(AuthboundPidResultType.UNKNOWN)
                    }
                }
            }
        }
    }

    private fun navigateToResult(resultType: AuthboundPidResultType) {
        clearSessionState()
        setState { copy(isLoading = false, isCompleting = false, statusMessage = "") }
        setEffect { Effect.Navigation.NavigateToResult(resultType) }
    }

    private fun saveActiveSessionId(sessionId: String) {
        savedStateHandle[ACTIVE_SESSION_ID_KEY] = sessionId
    }

    private fun getActiveSessionId(): String? = savedStateHandle[ACTIVE_SESSION_ID_KEY]

    private fun setAwaitingSdkResult(value: Boolean) {
        savedStateHandle[AWAITING_SDK_RESULT_KEY] = value
    }

    private fun isAwaitingSdkResult(): Boolean = savedStateHandle[AWAITING_SDK_RESULT_KEY] ?: false

    private fun clearSessionState() {
        savedStateHandle.remove<String>(ACTIVE_SESSION_ID_KEY)
        setAwaitingSdkResult(false)
    }

    private fun isSuccessfulCandourStatus(status: String): Boolean {
        return when (status.uppercase(Locale.ROOT)) {
            "SUCCESS",
            "SUCCESS_DATA_SAVED",
            "SUCCESS_SESSION",
            "SUCCESS_SESSION_DATA_SAVED" -> true
            else -> false
        }
    }

    private fun String.toCandourResultType(): AuthboundPidResultType {
        return when (uppercase(Locale.ROOT)) {
            "FAILED",
            "FAILED_SESSION" -> AuthboundPidResultType.FAILED
            "CANCELLED",
            "CANCELLED_SESSION" -> AuthboundPidResultType.CANCELED
            "UNSUPPORTED_NFC" -> AuthboundPidResultType.CANCELLED_UNSUPPORTED_DEVICE
            "NO_INTERNET" -> AuthboundPidResultType.NO_INTERNET
            "UNAVAILABLE",
            "LOST_SESSION" -> AuthboundPidResultType.UNAVAILABLE
            "TIMEOUT" -> AuthboundPidResultType.TIMEOUT
            "ERROR",
            "NO_VALID_SAVED_DATA" -> AuthboundPidResultType.UNKNOWN
            else -> AuthboundPidResultType.UNKNOWN
        }
    }
}
