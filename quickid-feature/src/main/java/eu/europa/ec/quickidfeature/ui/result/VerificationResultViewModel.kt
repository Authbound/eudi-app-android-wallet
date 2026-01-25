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

package eu.europa.ec.quickidfeature.ui.result

import androidx.lifecycle.viewModelScope
import eu.europa.ec.corelogic.controller.ResolveDocumentOfferPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.quickidlogic.interactor.CredentialIssuancePartialState
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionInteractor
import eu.europa.ec.quickidlogic.interactor.VerificationInteractor
import eu.europa.ec.quickidlogic.model.ResultUiConfig
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val isSuccess: Boolean = false,
    val firstName: String? = null,
    val lastName: String? = null,
    val errorMessage: String? = null,
    val isRecoverable: Boolean = false,
    val isIssuingCredential: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object IssueCredential : Event()
    data object Retry : Event()
    data object GoToDashboard : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data class NavigateToIssuance(val offerUri: String) : Navigation()
        data object PopToDashboard : Navigation()
        data object Pop : Navigation()
    }
}

@KoinViewModel
class VerificationResultViewModel(
    private val verificationInteractor: VerificationInteractor,
    private val quickIdSessionInteractor: QuickIdSessionInteractor,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val serializedResultConfig: String
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> initialize()
            is Event.IssueCredential -> issueCredential()
            is Event.Retry -> retry()
            is Event.GoToDashboard -> goToDashboard()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun initialize() {
        val config: ResultUiConfig? = uiSerializer.fromBase64(
            payload = serializedResultConfig,
            model = ResultUiConfig::class.java,
            parser = ResultUiConfig.Parser
        )

        if (config != null) {
            setState {
                copy(
                    isSuccess = config.isSuccess,
                    firstName = config.firstName,
                    lastName = config.lastName,
                    errorMessage = config.errorMessage,
                    isRecoverable = config.isRecoverable
                )
            }
        } else {
            setState {
                copy(
                    error = ContentErrorConfig(
                        errorSubTitle = "Invalid result configuration",
                        onCancel = { setEvent(Event.GoBack) }
                    )
                )
            }
        }
    }

    private fun issueCredential() {
        setState { copy(isIssuingCredential = true) }

        viewModelScope.launch {
            verificationInteractor.issueCredential().collect { state ->
                when (state) {
                    is CredentialIssuancePartialState.InProgress -> {
                        setState { copy(isIssuingCredential = true) }
                    }
                    is CredentialIssuancePartialState.CredentialOfferReceived -> {
                        setState { copy(isIssuingCredential = false) }
                        handleCredentialOffer(state.credentialOfferUri)
                    }
                    is CredentialIssuancePartialState.Failure -> {
                        setState {
                            copy(
                                isIssuingCredential = false,
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

    private fun handleCredentialOffer(offerUri: String) {
        viewModelScope.launch {
            walletCoreDocumentsController.resolveDocumentOffer(offerUri).collect { response ->
                when (response) {
                    is ResolveDocumentOfferPartialState.Success -> {
                        // Clear the QuickID session
                        quickIdSessionInteractor.clearSession()
                        // Navigate to the issuance flow
                        setEffect { Effect.Navigation.NavigateToIssuance(offerUri) }
                    }
                    is ResolveDocumentOfferPartialState.Failure -> {
                        setState {
                            copy(
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

    private fun retry() {
        // Clear session and go back to intro to start fresh
        quickIdSessionInteractor.clearSession()
        setEffect { Effect.Navigation.SwitchScreen(QuickIdScreens.Intro.screenRoute) }
    }

    private fun goToDashboard() {
        quickIdSessionInteractor.clearSession()
        setEffect { Effect.Navigation.PopToDashboard }
    }
}
