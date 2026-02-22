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

import androidx.lifecycle.viewModelScope
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroInteractor
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null
) : ViewState

sealed class Event : ViewEvent {
    data object StartVerification : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
    }

    /**
     * Opens the AuthboundPID redirect URL in a Chrome Custom Tab.
     * The sessionId is stored so we can poll after the user returns.
     */
    data class OpenAuthboundPidFlow(
        val redirectUrl: String,
        val sessionId: String
    ) : Effect()
}

@KoinViewModel
class AuthboundPidIntroViewModel(
    private val interactor: AuthboundPidIntroInteractor,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.StartVerification -> startVerification()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun startVerification() {
        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {
            interactor.createSession().collect { state ->
                when (state) {
                    is AuthboundPidIntroPartialState.CreatingSession -> {
                        setState { copy(isLoading = true) }
                    }
                    is AuthboundPidIntroPartialState.SessionCreated -> {
                        setState { copy(isLoading = false) }
                        if (state.redirectUrl.startsWith("https://")) {
                            setEffect {
                                Effect.OpenAuthboundPidFlow(
                                    redirectUrl = state.redirectUrl,
                                    sessionId = state.sessionId
                                )
                            }
                        } else {
                            setState {
                                copy(
                                    error = ContentErrorConfig(
                                        errorSubTitle = resourceProvider.getString(R.string.authboundpid_intro_invalid_url),
                                        onCancel = { setEvent(Event.DismissError) }
                                    )
                                )
                            }
                        }
                    }
                    is AuthboundPidIntroPartialState.Failure -> {
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
}
