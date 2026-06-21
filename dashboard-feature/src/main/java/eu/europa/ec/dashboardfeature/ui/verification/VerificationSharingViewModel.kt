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

package eu.europa.ec.dashboardfeature.ui.verification

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.core.annotation.KoinViewModel
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam

data class VerificationSharingState(
    val isLoading: Boolean = false,
    val session: VerificationSession? = null,
    val error: String? = null
) : ViewState

sealed class VerificationSharingEvent : ViewEvent {
    data object Init : VerificationSharingEvent()
    data object Refresh : VerificationSharingEvent()
    data object DismissError : VerificationSharingEvent()
    data object CopyLink : VerificationSharingEvent()
    data object ShareLink : VerificationSharingEvent()
    data object NavigateBack : VerificationSharingEvent()
    data object NavigateHome : VerificationSharingEvent()
}

sealed class VerificationSharingEffect : ViewSideEffect {
    sealed class Navigation : VerificationSharingEffect() {
        data object Back : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class ShowToast(val message: String) : VerificationSharingEffect()
    data class CopyToClipboard(
        val label: String,
        val content: String,
        val confirmationMessage: String
    ) : VerificationSharingEffect()
    data class ShareContent(val title: String, val content: String) : VerificationSharingEffect()
}

@KoinViewModel
class VerificationSharingViewModel(
    private val verificationRepository: VerificationRepository,
    private val resourceProvider: ResourceProvider,
    @InjectedParam private val sessionId: String
) : MviViewModel<VerificationSharingEvent, VerificationSharingState, VerificationSharingEffect>() {

    private var isLoadingSession: Boolean = false

    override fun setInitialState(): VerificationSharingState = VerificationSharingState(isLoading = true)

    override fun handleEvents(event: VerificationSharingEvent) {
        when (event) {
            VerificationSharingEvent.Init -> loadSession(showLoading = true)
            VerificationSharingEvent.Refresh -> loadSession(showLoading = viewState.value.session == null)
            VerificationSharingEvent.DismissError -> setState { copy(error = null) }
            VerificationSharingEvent.CopyLink -> copyLink()
            VerificationSharingEvent.ShareLink -> shareLink()
            VerificationSharingEvent.NavigateBack -> setEffect { VerificationSharingEffect.Navigation.Back }
            VerificationSharingEvent.NavigateHome -> setEffect {
                VerificationSharingEffect.Navigation.SwitchScreen(
                    screenRoute = DashboardScreens.Dashboard.screenRoute,
                    popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                    inclusive = false
                )
            }
        }
    }

    private fun loadSession(showLoading: Boolean) {
        if (isLoadingSession) return
        isLoadingSession = true
        setState {
            copy(
                isLoading = showLoading,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                verificationRepository.getVerificationSession(sessionId).fold(
                    onSuccess = { session ->
                        setState { copy(isLoading = false, session = session, error = null) }
                    },
                    onFailure = {
                        setState {
                            copy(
                                isLoading = false,
                                error = if (viewState.value.session == null) {
                                    resourceProvider.getString(R.string.verification_sharing_refresh_error_empty)
                                } else {
                                    resourceProvider.getString(R.string.verification_sharing_refresh_error_cached)
                                }
                            )
                        }
                    }
                )
            } finally {
                isLoadingSession = false
            }
        }
    }

    private fun copyLink() {
        val session = viewState.value.session ?: return
        val value = shareableValue() ?: run {
            setEffect { VerificationSharingEffect.ShowToast("No shareable link available") }
            return
        }

        val isPublicLink = !session.publicUrl.isNullOrBlank()
        setEffect {
            VerificationSharingEffect.CopyToClipboard(
                label = if (isPublicLink) {
                    resourceProvider.getString(R.string.verification_sharing_link_title)
                } else {
                    resourceProvider.getString(R.string.verification_sharing_wallet_request_title)
                },
                content = value,
                confirmationMessage = if (isPublicLink) {
                    resourceProvider.getString(R.string.verification_sharing_link_copied)
                } else {
                    resourceProvider.getString(R.string.verification_sharing_wallet_request_copied)
                }
            )
        }
    }

    private fun shareLink() {
        val session = viewState.value.session ?: return
        val value = shareableValue() ?: run {
            setEffect { VerificationSharingEffect.ShowToast("No shareable link available") }
            return
        }

        setEffect {
            VerificationSharingEffect.ShareContent(
                title = resourceProvider.getString(R.string.verification_sharing_title),
                content = buildString {
                    appendLine(session.purpose)
                    appendLine()
                    append(value)
                }
            )
        }
    }

    private fun shareableValue(): String? {
        val session = viewState.value.session ?: return null
        return session.publicUrl ?: session.qrPayload
    }
}
