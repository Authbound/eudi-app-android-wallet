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

package eu.europa.ec.quickidfeature.ui.intro

import androidx.lifecycle.viewModelScope
import eu.europa.ec.quickidfeature.interactor.QuickIdIntroInteractor
import eu.europa.ec.quickidfeature.interactor.QuickIdIntroPartialState
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val title: String = "",
    val description: String = "",
    val requirements: List<String> = emptyList()
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object StartVerification : Event()
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
class QuickIdIntroViewModel(
    private val interactor: QuickIdIntroInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> loadIntroContent()
            is Event.StartVerification -> startVerification()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun loadIntroContent() {
        viewModelScope.launch {
            interactor.getIntroContent().collect { state ->
                when (state) {
                    is QuickIdIntroPartialState.Ready -> {
                        setState {
                            copy(
                                title = state.title,
                                description = state.description,
                                requirements = state.requirements
                            )
                        }
                    }
                    else -> {
                        // Ignore other states for initial load
                    }
                }
            }
        }
    }

    private fun startVerification() {
        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {
            interactor.startVerification().collect { state ->
                when (state) {
                    is QuickIdIntroPartialState.CreatingSession -> {
                        setState { copy(isLoading = true) }
                    }
                    is QuickIdIntroPartialState.SessionCreated -> {
                        setState { copy(isLoading = false) }
                        setEffect {
                            Effect.Navigation.SwitchScreen(QuickIdScreens.MrzScan.screenRoute)
                        }
                    }
                    is QuickIdIntroPartialState.Failure -> {
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
                    else -> {
                        // Ignore other states
                    }
                }
            }
        }
    }
}
