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

package eu.europa.ec.quickidfeature.ui.mrzscan

import androidx.lifecycle.viewModelScope
import eu.europa.ec.quickidlogic.interactor.MrzOcrInteractor
import eu.europa.ec.quickidlogic.interactor.MrzOcrPartialState
import eu.europa.ec.quickidlogic.model.MrzData
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * UI state for MRZ scanning screen.
 */
data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val scanState: MrzScanState = MrzScanState.CameraPreview,
    val flashEnabled: Boolean = false
) : ViewState

/**
 * Scanning states for the camera UI.
 */
sealed class MrzScanState {
    data object CameraPreview : MrzScanState()
    data object Capturing : MrzScanState()
    data object Processing : MrzScanState()
    data class Success(val mrzData: MrzData) : MrzScanState()
    data class Error(val message: String, val isRetryable: Boolean) : MrzScanState()
}

/**
 * Events that can occur in the MRZ scan screen.
 */
sealed class Event : ViewEvent {
    data object Init : Event()
    data class ImageCaptured(val imageBytes: ByteArray) : Event()
    data object ToggleFlash : Event()
    data object Retry : Event()
    data object Continue : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

/**
 * Side effects for navigation and other actions.
 */
sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data object Pop : Navigation()
    }
}

/**
 * ViewModel for MRZ OCR scanning screen.
 *
 * Handles camera capture and OCR API integration for extracting
 * MRZ data from passport photos.
 */
@KoinViewModel
class MrzScanViewModel(
    private val mrzOcrInteractor: MrzOcrInteractor,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> { /* Camera setup handled by Compose */ }
            is Event.ImageCaptured -> processImage(event.imageBytes)
            is Event.ToggleFlash -> toggleFlash()
            is Event.Retry -> resetToCamera()
            is Event.Continue -> navigateToNfcReading()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun processImage(imageBytes: ByteArray) {
        setState { copy(scanState = MrzScanState.Processing, isLoading = true) }

        viewModelScope.launch {
            mrzOcrInteractor.scanPassport(imageBytes).collect { partialState ->
                when (partialState) {
                    is MrzOcrPartialState.Processing -> {
                        setState { copy(scanState = MrzScanState.Processing, isLoading = true) }
                    }
                    is MrzOcrPartialState.Success -> {
                        setState {
                            copy(
                                scanState = MrzScanState.Success(partialState.mrzData),
                                isLoading = false
                            )
                        }
                    }
                    is MrzOcrPartialState.Failure -> {
                        setState {
                            copy(
                                scanState = MrzScanState.Error(
                                    message = partialState.message,
                                    isRetryable = partialState.isRetryable
                                ),
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toggleFlash() {
        setState { copy(flashEnabled = !flashEnabled) }
    }

    private fun resetToCamera() {
        setState { copy(scanState = MrzScanState.CameraPreview, error = null) }
    }

    private fun navigateToNfcReading() {
        val currentState = viewState.value.scanState
        if (currentState !is MrzScanState.Success) return

        // MRZ data is already stored in QuickIdRepository by the interactor
        // Just navigate to NFC reading
        setEffect {
            Effect.Navigation.SwitchScreen(QuickIdScreens.NfcReading.screenRoute)
        }
    }
}
