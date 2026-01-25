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

package eu.europa.ec.quickidfeature.ui.liveness

import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val livenessSessionId: String = "",
    val region: String = "",
    val isLivenessComplete: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object LivenessComplete : Event()
    data class LivenessFailed(val error: String) : Event()
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
class LivenessViewModel(
    private val quickIdRepository: QuickIdRepository,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> initialize()
            is Event.LivenessComplete -> onLivenessComplete()
            is Event.LivenessFailed -> onLivenessFailed(event.error)
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun initialize() {
        // Get liveness session data from repository (stored by PassportNfcViewModel)
        val livenessData = quickIdRepository.getLivenessSessionData()

        if (livenessData != null) {
            setState {
                copy(
                    isLoading = false,
                    livenessSessionId = livenessData.livenessSessionId,
                    region = livenessData.region
                )
            }
        } else {
            setState {
                copy(
                    isLoading = false,
                    error = ContentErrorConfig(
                        errorSubTitle = resourceProvider.getString(R.string.quickid_liveness_not_found),
                        onCancel = { setEvent(Event.GoBack) }
                    )
                )
            }
        }
    }

    private fun onLivenessComplete() {
        setState { copy(isLivenessComplete = true) }
        setEffect { Effect.Navigation.SwitchScreen(QuickIdScreens.Processing.screenRoute) }
    }

    private fun onLivenessFailed(error: String) {
        setState {
            copy(
                error = ContentErrorConfig(
                    errorSubTitle = error,
                    onCancel = { setEvent(Event.DismissError) }
                )
            )
        }
    }
}
