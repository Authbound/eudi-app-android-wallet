/*
 * Copyright (c) 2024 European Commission
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
package eu.europa.ec.authenticationfeature.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data class OnEmailChanged(val email: String) : Event()
    data class OnPasswordChanged(val password: String) : Event()
    data object SignInWithEmailAndPassword : Event()
    data class SignInWithOAuth(val provider: OAuthProvider, val context: Context) : Event()
}

sealed class Effect : ViewSideEffect {
    data object NavigateToHome : Effect()
    data class ShowError(val message: String) : Effect()
}

@KoinViewModel
class AuthenticationViewModel(
    private val signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    private val signInWithOAuthUseCase: SignInWithOAuthUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        observeAuthState()
        return State()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.OnEmailChanged -> setState { copy(email = event.email) }
            is Event.OnPasswordChanged -> setState { copy(password = event.password) }
            is Event.SignInWithEmailAndPassword -> signInWithEmailPassword()
            is Event.SignInWithOAuth -> signInWithOAuth(event.provider, event.context)
        }
    }

    private fun signInWithEmailPassword() {
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true, error = null) }
                val request = EmailPasswordRequest(viewState.value.email, viewState.value.password)
                signInWithEmailPasswordUseCase(request)
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true, error = null) }
                signInWithOAuthUseCase(provider, context)
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            observeAuthStateUseCase()
                .onStart { setState { copy(isLoading = true) } }
                .catch {
                    setState { copy(isLoading = false, error = it.message) }
                    setEffect { Effect.ShowError(it.message ?: "An unknown error occurred") }
                }
                .collect { status ->
                    setState { copy(isLoading = false) }
                    if (status is SessionStatus.Authenticated) {
                        setEffect { Effect.NavigateToHome }
                    }
                }
        }
    }
} 