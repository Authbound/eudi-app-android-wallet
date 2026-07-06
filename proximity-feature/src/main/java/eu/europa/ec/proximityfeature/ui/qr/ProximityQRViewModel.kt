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

package eu.europa.ec.proximityfeature.ui.qr

import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrNullKoinScope
import eu.europa.ec.proximityfeature.interactor.ProximityPresentingDocumentUi
import eu.europa.ec.proximityfeature.interactor.ProximityQRInteractor
import eu.europa.ec.proximityfeature.interactor.ProximityQRPartialState
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.ProximityScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,

    val qrCode: String = "",
    val presentationScopeId: String = "",
    val presentingDocumentId: String? = null,
    val isLoadingPresentingDocument: Boolean = true,
    val presentingDocument: ProximityPresentingDocumentUi? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object GoBack : Event()
    data class NfcEngagement(
        val componentActivity: ComponentActivity,
        val enable: Boolean
    ) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String
        ) : Navigation()

        data object Pop : Navigation()
    }
}

@KoinViewModel
class ProximityQRViewModel(
    private val interactor: ProximityQRInteractor,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val requestUriConfigRaw: String,
) : MviViewModel<Event, State, Effect>() {

    private var interactorJob: Job? = null

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                initializeConfig()
                loadPresentingDocument()
                generateQrCode()
            }

            is Event.GoBack -> {
                cleanUp()
                setState { copy(error = null) }
                setEffect { Effect.Navigation.Pop }
            }

            is Event.NfcEngagement -> {
                interactor.toggleNfcEngagement(
                    event.componentActivity,
                    event.enable
                )
            }
        }
    }

    private fun initializeConfig() {
        val requestUriConfig = uiSerializer.fromBase64(
            requestUriConfigRaw,
            RequestUriConfig::class.java,
            RequestUriConfig.Parser
        ) ?: throw RuntimeException("RequestUriConfig:: is Missing or invalid")

        setState {
            copy(
                presentationScopeId = requestUriConfig.presentationScopeId,
                presentingDocumentId = requestUriConfig.presentingDocumentId
            )
        }

        interactor.setConfig(requestUriConfig)
    }

    /** Fetches the document summary shown under the QR; failures simply hide the row. */
    private fun loadPresentingDocument() {
        setState { copy(isLoadingPresentingDocument = true) }
        viewModelScope.launch {
            val presentingDocument = interactor.getPresentingDocument()
            setState {
                copy(
                    presentingDocument = presentingDocument,
                    isLoadingPresentingDocument = false
                )
            }
        }
    }

    private fun generateQrCode() {
        // No full-screen loader: while the QR is not ready the screen shows an
        // in-place shimmer placeholder (keyed on an empty qrCode with no error).
        setState {
            copy(
                isLoading = false,
                error = null
            )
        }

        interactorJob = viewModelScope.launch {
            interactor.startQrEngagement().collect { response ->
                when (response) {
                    is ProximityQRPartialState.Error -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.Init) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.GoBack) }
                                )
                            )
                        }
                    }

                    is ProximityQRPartialState.QrReady -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                qrCode = response.qrCode
                            )
                        }
                    }

                    is ProximityQRPartialState.Connected -> {
                        unsubscribe()
                        setEffect {
                            val arguments: Map<String, String> = buildMap {
                                put("scopeId", viewState.value.presentationScopeId)
                                viewState.value.presentingDocumentId
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { put("presentingDocumentId", it) }
                            }
                            Effect.Navigation.SwitchScreen(
                                screenRoute = generateComposableNavigationLink(
                                    screen = ProximityScreens.Request,
                                    arguments = generateComposableArguments(arguments)
                                )
                            )
                        }
                    }

                    is ProximityQRPartialState.Disconnected -> {
                        unsubscribe()
                        setEvent(Event.GoBack)
                    }
                }
            }
        }
    }

    /**
     * Required in order to stop receiving emissions from interactor Flow
     * */
    private fun unsubscribe() {
        interactorJob?.cancel()
    }

    /**
     * Stop presentation and remove scope/listeners
     * */
    private fun cleanUp() {
        unsubscribe()
        interactor.cancelTransfer()
        getOrNullKoinScope(viewState.value.presentationScopeId)?.close()
    }
}
