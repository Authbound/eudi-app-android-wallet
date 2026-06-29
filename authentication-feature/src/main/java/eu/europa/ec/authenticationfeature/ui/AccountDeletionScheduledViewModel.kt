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
package eu.europa.ec.authenticationfeature.ui

import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.model.AccountDeletion
import eu.europa.ec.authenticationlogic.usecase.CancelAccountDeletionUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class AccountDeletionScheduledState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val scheduledFor: String? = null,
    val canCancel: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
) : ViewState

sealed class AccountDeletionScheduledEvent : ViewEvent {
    data object Retry : AccountDeletionScheduledEvent()
    data object CancelDeletion : AccountDeletionScheduledEvent()
    data object SignOut : AccountDeletionScheduledEvent()
}

sealed class AccountDeletionScheduledEffect : ViewSideEffect {
    sealed class Navigation : AccountDeletionScheduledEffect() {
        data object NavigateToStartup : Navigation()
        data object NavigateToLogin : Navigation()
    }

    data class ShowError(val message: String) : AccountDeletionScheduledEffect()
}

@KoinViewModel
class AccountDeletionScheduledViewModel(
    private val getMyProfileUseCase: GetMyProfileUseCase,
    private val cancelAccountDeletionUseCase: CancelAccountDeletionUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<AccountDeletionScheduledEvent, AccountDeletionScheduledState, AccountDeletionScheduledEffect>() {

    override fun setInitialState(): AccountDeletionScheduledState = AccountDeletionScheduledState()

    init {
        refreshState()
    }

    override fun handleEvents(event: AccountDeletionScheduledEvent) {
        when (event) {
            is AccountDeletionScheduledEvent.Retry -> refreshState()
            is AccountDeletionScheduledEvent.CancelDeletion -> cancelDeletion()
            is AccountDeletionScheduledEvent.SignOut -> signOut()
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            getMyProfileUseCase().fold(
                onSuccess = { profile ->
                    val accountDeletion: AccountDeletion? = profile.accountDeletion
                    if (accountDeletion?.isBlocked == true) {
                        setState {
                            copy(
                                isLoading = false,
                                isSubmitting = false,
                                scheduledFor = accountDeletion.scheduledFor,
                                canCancel = accountDeletion.canCancel,
                                isProcessing = accountDeletion.isProcessing,
                                error = null
                            )
                        }
                    } else {
                        setEffect { AccountDeletionScheduledEffect.Navigation.NavigateToStartup }
                    }
                },
                onFailure = { throwable ->
                    setState {
                        copy(
                            isLoading = false,
                            isSubmitting = false,
                            error = throwable.localizedMessage ?: resourceProvider.genericNetworkErrorMessage()
                        )
                    }
                }
            )
        }
    }

    private fun cancelDeletion() {
        viewModelScope.launch {
            setState { copy(isSubmitting = true, error = null) }
            cancelAccountDeletionUseCase().fold(
                onSuccess = {
                    setState { copy(isSubmitting = false) }
                    setEffect { AccountDeletionScheduledEffect.Navigation.NavigateToStartup }
                },
                onFailure = { throwable ->
                    val message: String =
                        throwable.localizedMessage ?: resourceProvider.genericErrorMessage()
                    setState { copy(isSubmitting = false, error = message) }
                    setEffect { AccountDeletionScheduledEffect.ShowError(message) }
                }
            )
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            runCatching {
                signOutUseCase(SignOutMode.Soft)
            }.onFailure { throwable ->
                val message: String =
                    throwable.localizedMessage ?: resourceProvider.genericErrorMessage()
                setEffect { AccountDeletionScheduledEffect.ShowError(message) }
            }
            setState { copy(isSubmitting = false) }
            setEffect { AccountDeletionScheduledEffect.Navigation.NavigateToLogin }
        }
    }
}
