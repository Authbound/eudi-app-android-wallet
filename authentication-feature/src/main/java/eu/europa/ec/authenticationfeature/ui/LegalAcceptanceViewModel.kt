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

import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.authenticationlogic.usecase.GetLegalAcceptanceStateUseCase
import eu.europa.ec.authenticationlogic.usecase.RecordLegalAcceptanceUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel
import kotlinx.coroutines.launch

data class LegalAcceptanceState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasAcceptedTerms: Boolean = false,
    val hasAcknowledgedPrivacy: Boolean = false,
    val snapshot: LegalAcceptanceSnapshot = LegalAcceptanceSnapshot(),
    val error: String? = null,
) : ViewState {
    val canContinue: Boolean
        get() = !isLoading &&
            !isSubmitting &&
            snapshot.isRequirementConfigured &&
            hasAcceptedTerms &&
            hasAcknowledgedPrivacy
}

sealed class LegalAcceptanceEvent : ViewEvent {
    data class TermsToggled(val accepted: Boolean) : LegalAcceptanceEvent()
    data class PrivacyToggled(val accepted: Boolean) : LegalAcceptanceEvent()
    data object OpenTerms : LegalAcceptanceEvent()
    data object OpenPrivacy : LegalAcceptanceEvent()
    data object Retry : LegalAcceptanceEvent()
    data object Continue : LegalAcceptanceEvent()
    data object SignOut : LegalAcceptanceEvent()
}

sealed class LegalAcceptanceEffect : ViewSideEffect {
    sealed class Navigation : LegalAcceptanceEffect() {
        data class OpenUrlExternally(val url: Uri) : Navigation()
        data object NavigateToStartup : Navigation()
        data object NavigateToLogin : Navigation()
    }
    data class ShowError(val message: String) : LegalAcceptanceEffect()
}

@KoinViewModel
class LegalAcceptanceViewModel(
    private val getLegalAcceptanceStateUseCase: GetLegalAcceptanceStateUseCase,
    private val recordLegalAcceptanceUseCase: RecordLegalAcceptanceUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<LegalAcceptanceEvent, LegalAcceptanceState, LegalAcceptanceEffect>() {

    override fun setInitialState(): LegalAcceptanceState = LegalAcceptanceState()

    init {
        loadState()
    }

    override fun handleEvents(event: LegalAcceptanceEvent) {
        when (event) {
            is LegalAcceptanceEvent.TermsToggled -> setState { copy(hasAcceptedTerms = event.accepted) }
            is LegalAcceptanceEvent.PrivacyToggled -> setState { copy(hasAcknowledgedPrivacy = event.accepted) }
            is LegalAcceptanceEvent.OpenTerms -> {
                setEffect {
                    LegalAcceptanceEffect.Navigation.OpenUrlExternally(
                        resourceProvider.getString(R.string.legal_terms_alpha_url).toUri()
                    )
                }
            }
            is LegalAcceptanceEvent.OpenPrivacy -> {
                setEffect {
                    LegalAcceptanceEffect.Navigation.OpenUrlExternally(
                        resourceProvider.getString(R.string.legal_privacy_policy_url).toUri()
                    )
                }
            }
            is LegalAcceptanceEvent.Retry -> loadState()
            is LegalAcceptanceEvent.Continue -> recordAcceptance()
            is LegalAcceptanceEvent.SignOut -> signOut()
        }
    }

    private fun loadState() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            getLegalAcceptanceStateUseCase().fold(
                onSuccess = { snapshot: LegalAcceptanceSnapshot ->
                    if (snapshot.isAccepted) {
                        setEffect { LegalAcceptanceEffect.Navigation.NavigateToStartup }
                    } else {
                        setState {
                            copy(
                                isLoading = false,
                                isSubmitting = false,
                                hasAcceptedTerms = false,
                                hasAcknowledgedPrivacy = false,
                                snapshot = snapshot,
                                error = null
                            )
                        }
                    }
                },
                onFailure = {
                    setState {
                        copy(
                            isLoading = false,
                            isSubmitting = false,
                            error = it.localizedMessage ?: resourceProvider.genericNetworkErrorMessage()
                        )
                    }
                }
            )
        }
    }

    private fun recordAcceptance() {
        val snapshot: LegalAcceptanceSnapshot = viewState.value.snapshot
        viewModelScope.launch {
            setState { copy(isSubmitting = true, error = null) }
            recordLegalAcceptanceUseCase(snapshot).fold(
                onSuccess = {
                    setState { copy(isSubmitting = false) }
                    setEffect { LegalAcceptanceEffect.Navigation.NavigateToStartup }
                },
                onFailure = {
                    val message: String =
                        it.localizedMessage ?: resourceProvider.genericErrorMessage()
                    setState { copy(isSubmitting = false, error = message) }
                    setEffect { LegalAcceptanceEffect.ShowError(message) }
                }
            )
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            runCatching {
                signOutUseCase(SignOutMode.Soft)
            }.onFailure {
                val message: String = it.localizedMessage ?: resourceProvider.genericErrorMessage()
                setEffect { LegalAcceptanceEffect.ShowError(message) }
            }
            setState { copy(isSubmitting = false) }
            setEffect { LegalAcceptanceEffect.Navigation.NavigateToLogin }
        }
    }
}
