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

package eu.europa.ec.quickidfeature.ui.nfc

import android.nfc.Tag
import androidx.lifecycle.viewModelScope
import eu.europa.ec.quickidlogic.controller.PassportNfcController
import eu.europa.ec.quickidlogic.controller.PassportReadPartialState
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionInteractor
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionPartialState
import eu.europa.ec.quickidlogic.interactor.VerificationInteractor
import eu.europa.ec.quickidlogic.model.MrzConfig
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
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
    val readingState: ReadingState = ReadingState.WaitingForTag,
    val progressMessage: String = "Hold your phone to your passport",
    val progressPercent: Int = 0
) : ViewState

enum class ReadingState {
    WaitingForTag,
    Authenticating,
    Reading,
    Success,
    Failure
}

sealed class Event : ViewEvent {
    data object Init : Event()
    data class NfcTagDiscovered(val tag: Tag) : Event()
    data object Retry : Event()
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
class PassportNfcViewModel(
    private val passportNfcController: PassportNfcController,
    private val quickIdSessionInteractor: QuickIdSessionInteractor,
    private val verificationInteractor: VerificationInteractor,
    private val quickIdRepository: QuickIdRepository,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    private var mrzConfig: MrzConfig? = null

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> initialize()
            is Event.NfcTagDiscovered -> readPassport(event.tag)
            is Event.Retry -> retry()
            is Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.DismissError -> setState { copy(error = null) }
        }
    }

    private fun initialize() {
        // Get MRZ data from repository (stored by MrzScanScreen OCR)
        val mrzData = quickIdRepository.getMrzData()
        mrzConfig = mrzData?.toMrzConfig()

        if (mrzConfig == null) {
            setState {
                copy(
                    error = ContentErrorConfig(
                        errorSubTitle = resourceProvider.getString(R.string.quickid_mrz_not_found),
                        onCancel = { setEvent(Event.GoBack) }
                    )
                )
            }
        }
    }

    private fun readPassport(tag: Tag) {
        val config = mrzConfig ?: return

        viewModelScope.launch {
            passportNfcController.readPassport(tag, config).collect { state ->
                when (state) {
                    is PassportReadPartialState.WaitingForTag -> {
                        setState {
                            copy(
                                readingState = ReadingState.WaitingForTag,
                                progressMessage = resourceProvider.getString(R.string.quickid_nfc_hold_phone),
                                progressPercent = 0
                            )
                        }
                    }
                    is PassportReadPartialState.Authenticating -> {
                        setState {
                            copy(
                                readingState = ReadingState.Authenticating,
                                progressMessage = resourceProvider.getString(R.string.quickid_nfc_authenticating),
                                progressPercent = 10
                            )
                        }
                    }
                    is PassportReadPartialState.ReadingProgress -> {
                        setState {
                            copy(
                                readingState = ReadingState.Reading,
                                progressMessage = state.step,
                                progressPercent = state.progress
                            )
                        }
                    }
                    is PassportReadPartialState.Success -> {
                        setState {
                            copy(
                                readingState = ReadingState.Success,
                                progressMessage = resourceProvider.getString(R.string.quickid_nfc_success),
                                progressPercent = 100
                            )
                        }
                        // Store passport data for verification
                        verificationInteractor.storePassportData(state.passportData)
                        // Create liveness session and navigate
                        createLivenessSessionAndNavigate()
                    }
                    is PassportReadPartialState.Failure -> {
                        setState {
                            copy(
                                readingState = ReadingState.Failure,
                                progressMessage = state.error,
                                error = if (!state.isRecoverable) {
                                    ContentErrorConfig(
                                        errorSubTitle = state.error,
                                        onCancel = { setEvent(Event.GoBack) }
                                    )
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createLivenessSessionAndNavigate() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            quickIdSessionInteractor.createLivenessSession().collect { state ->
                when (state) {
                    is QuickIdSessionPartialState.LivenessSessionCreated -> {
                        setState { copy(isLoading = false) }
                        // Liveness session data is stored in repository
                        // Navigate to Liveness screen
                        setEffect {
                            Effect.Navigation.SwitchScreen(QuickIdScreens.Liveness.screenRoute)
                        }
                    }
                    is QuickIdSessionPartialState.Failure -> {
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

    private fun retry() {
        setState {
            copy(
                readingState = ReadingState.WaitingForTag,
                progressMessage = resourceProvider.getString(R.string.quickid_nfc_hold_phone),
                progressPercent = 0,
                error = null
            )
        }
    }
}
