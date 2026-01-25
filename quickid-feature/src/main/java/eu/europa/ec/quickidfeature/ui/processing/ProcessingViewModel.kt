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

package eu.europa.ec.quickidfeature.ui.processing

import androidx.lifecycle.viewModelScope
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionInteractor
import eu.europa.ec.quickidlogic.interactor.VerificationInteractor
import eu.europa.ec.quickidlogic.interactor.VerificationPartialState
import eu.europa.ec.quickidlogic.model.ResultUiConfig
import eu.europa.ec.quickidlogic.model.VerificationResult
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val statusMessage: String = "Verifying your identity..."
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data object Pop : Navigation()
    }
}

@KoinViewModel
class ProcessingViewModel(
    private val verificationInteractor: VerificationInteractor,
    private val quickIdSessionInteractor: QuickIdSessionInteractor,
    private val uiSerializer: UiSerializer
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> submitVerification()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun submitVerification() {
        val passportData = verificationInteractor.getStoredPassportData()
        val livenessSessionId = quickIdSessionInteractor.getLivenessSessionId()

        if (passportData == null || livenessSessionId == null) {
            setState {
                copy(
                    isLoading = false,
                    error = ContentErrorConfig(
                        errorSubTitle = "Missing verification data. Please restart the process.",
                        onCancel = { setEvent(Event.GoBack) }
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            verificationInteractor.submitVerification(passportData, livenessSessionId)
                .collect { state ->
                    when (state) {
                        is VerificationPartialState.InProgress -> {
                            setState {
                                copy(
                                    isLoading = true,
                                    statusMessage = state.message
                                )
                            }
                        }
                        is VerificationPartialState.Verified -> {
                            navigateToResult(state.result)
                        }
                        is VerificationPartialState.Rejected -> {
                            navigateToResult(state.result)
                        }
                        is VerificationPartialState.Failure -> {
                            setState {
                                copy(
                                    isLoading = false,
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

    private fun navigateToResult(result: VerificationResult) {
        val config = when (result) {
            is VerificationResult.Verified -> ResultUiConfig(
                isSuccess = true,
                sessionId = result.sessionId,
                firstName = result.firstName,
                lastName = result.lastName
            )
            is VerificationResult.Rejected -> ResultUiConfig(
                isSuccess = false,
                sessionId = result.sessionId,
                errorMessage = result.errorMessage,
                isRecoverable = result.isRecoverable
            )
        }

        val serialized = uiSerializer.toBase64(
            model = config,
            parser = ResultUiConfig.Parser
        ) ?: return

        val route = generateComposableNavigationLink(
            screen = QuickIdScreens.Result,
            arguments = generateComposableArguments(
                mapOf(ResultUiConfig.Parser.serializedKeyName to serialized)
            )
        )

        // Clear stored data
        verificationInteractor.clearStoredPassportData()

        setEffect { Effect.Navigation.SwitchScreen(route) }
    }
}
