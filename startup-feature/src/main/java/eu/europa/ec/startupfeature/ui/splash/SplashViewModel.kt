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

package eu.europa.ec.startupfeature.ui.splash

import androidx.lifecycle.viewModelScope
import eu.europa.ec.startupfeature.interactor.SplashInteractor
import eu.europa.ec.startupfeature.model.StartupState
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.android.annotation.KoinViewModel
import kotlin.coroutines.cancellation.CancellationException

data class State(
    val isLoading: Boolean = true,
    val logoAnimationDuration: Int = 1500
) : ViewState

sealed class Event : ViewEvent {
    data object Initialize : Event()
}

sealed class Effect : ViewSideEffect {

    sealed class Navigation : Effect() {
        data class SwitchModule(val moduleRoute: ModuleRoute) : Navigation()
        data class SwitchScreen(val route: String) : Navigation()
    }
}

@KoinViewModel
class SplashViewModel(
    private val interactor: SplashInteractor,
) : MviViewModel<Event, State, Effect>() {

    companion object {
        // 15s timeout accounts for:
        // - Retry delays in profile check (500ms + 1s + 1.5s = 3s)
        // - Supabase session initialization (network dependent)
        // - Sequential checks (auth → profile → wallet → unlock)
        private const val STARTUP_TIMEOUT_MS = 15_000L
    }

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        if (event is Event.Initialize) {
            if (viewState.value.isLoading) {
                determineInitialRoute()
            }
        }
    }

    private fun determineInitialRoute() {
        viewModelScope.launch {
            try {
                val startupState = withTimeout(STARTUP_TIMEOUT_MS) {
                    interactor.determineStartupState()
                }
                handleStartupState(startupState)
            } catch (e: TimeoutCancellationException) {
                // Startup took too long – fail safe to login
                setState { copy(isLoading = false) }
                setEffect { Effect.Navigation.SwitchScreen(AuthenticationScreens.Login.screenRoute) }
            } catch (e: CancellationException) {
                // Respect cancellation (e.g., VM cleared)
                throw e
            } catch (e: Exception) {
                // Unexpected error – fail safe to login
                setState { copy(isLoading = false) }
                setEffect { Effect.Navigation.SwitchScreen(AuthenticationScreens.Login.screenRoute) }
            }
        }
    }

    private fun handleStartupState(state: StartupState) {
        setState { copy(isLoading = false) }

        when (state) {
            is StartupState.NetworkError -> {
                // For retryable errors, we could add retry logic here
                // For now, use the cached fallback or default to login
                setEffect { Effect.Navigation.SwitchScreen(state.screenRoute) }
            }

            is StartupState.SecurityError -> {
                // Security errors always go to login for safety
                setEffect { Effect.Navigation.SwitchScreen(state.screenRoute) }
            }

            else -> {
                // All other states have their route defined in StartupState
                setEffect { Effect.Navigation.SwitchScreen(state.screenRoute) }
            }
        }
    }
}
