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

package eu.europa.ec.authboundpidfeature.ui.result

import androidx.lifecycle.SavedStateHandle
import eu.europa.ec.authboundpidfeature.model.AuthboundPidResultType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

data class State(
    val title: String = "",
    val message: String = "",
    val showRetryButton: Boolean = false,
    val retryButtonText: String = "",
    val isError: Boolean = true
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object Retry : Event()
    data object GoToDashboard : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object PopToIntro : Navigation()
        data object PopToDashboard : Navigation()
    }
}

@KoinViewModel
class AuthboundPidResultViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    private var initialized = false

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> initialize()
            is Event.Retry -> setEffect { Effect.Navigation.PopToIntro }
            is Event.GoToDashboard -> setEffect { Effect.Navigation.PopToDashboard }
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true

        val resultTypeStr = savedStateHandle.get<String>("resultType") ?: ""
        val resultType = try {
            AuthboundPidResultType.valueOf(resultTypeStr)
        } catch (_: IllegalArgumentException) {
            AuthboundPidResultType.UNKNOWN
        }

        val tryAgain = resourceProvider.getString(R.string.authboundpid_try_again)
        val startOver = resourceProvider.getString(R.string.authboundpid_start_over)

        setState {
            when (resultType) {
                AuthboundPidResultType.CANCELED -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_cancelled_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_cancelled_message),
                    showRetryButton = true,
                    retryButtonText = tryAgain,
                    isError = false
                )
                AuthboundPidResultType.CANCELLED_UNSUPPORTED_DEVICE -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_unsupported_device_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_unsupported_device_message),
                    showRetryButton = false,
                    isError = true
                )
                AuthboundPidResultType.NO_INTERNET -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_no_internet_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_no_internet_message),
                    showRetryButton = true,
                    retryButtonText = tryAgain,
                    isError = true
                )
                AuthboundPidResultType.UNAVAILABLE -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_unavailable_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_unavailable_message),
                    showRetryButton = true,
                    retryButtonText = startOver,
                    isError = true
                )
                AuthboundPidResultType.FAILED -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_failed_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_failed_message),
                    showRetryButton = true,
                    retryButtonText = tryAgain,
                    isError = true
                )
                AuthboundPidResultType.EXPIRED -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_expired_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_expired_message),
                    showRetryButton = true,
                    retryButtonText = startOver,
                    isError = true
                )
                AuthboundPidResultType.TIMEOUT -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_timeout_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_timeout_message),
                    showRetryButton = true,
                    retryButtonText = tryAgain,
                    isError = true
                )
                AuthboundPidResultType.UNKNOWN -> copy(
                    title = resourceProvider.getString(R.string.authboundpid_result_unknown_title),
                    message = resourceProvider.getString(R.string.authboundpid_result_unknown_message),
                    showRetryButton = true,
                    retryButtonText = tryAgain,
                    isError = true
                )
            }
        }
    }
}
