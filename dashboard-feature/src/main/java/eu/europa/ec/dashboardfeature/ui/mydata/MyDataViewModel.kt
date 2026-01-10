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

package eu.europa.ec.dashboardfeature.ui.mydata

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.MyDataInteractor
import eu.europa.ec.dashboardfeature.interactor.MyDataInteractorPartialState
import eu.europa.ec.dashboardfeature.ui.mydata.model.DataCategoryUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val categories: List<DataCategoryUi> = emptyList(),
    val lastUpdated: String? = null,
    val showEmptyState: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object OnResume : Event()
    data object Pop : Event()
    data class FieldClicked(val fieldId: String) : Event()
    data class VerifyFieldClicked(val fieldId: String) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
    }
    data class ShowToast(val message: String) : Effect()
}

@KoinViewModel
class MyDataViewModel(
    private val interactor: MyDataInteractor,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> loadMyData()
            is Event.OnResume -> loadMyData()
            is Event.Pop -> setEffect { Effect.Navigation.Pop }
            is Event.FieldClicked -> handleFieldClick(event.fieldId)
            is Event.VerifyFieldClicked -> handleVerifyClick(event.fieldId)
        }
    }

    private fun loadMyData() {
        setState { copy(isLoading = categories.isEmpty(), error = null) }

        viewModelScope.launch {
            interactor.getMyData().collect { result ->
                when (result) {
                    is MyDataInteractorPartialState.Success -> {
                        setState {
                            copy(
                                isLoading = false,
                                categories = result.data.categories,
                                lastUpdated = result.data.lastUpdated,
                                showEmptyState = result.data.categories.isEmpty(),
                                error = null
                            )
                        }
                    }
                    is MyDataInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.Init) },
                                    errorSubTitle = result.error,
                                    onCancel = { setState { copy(error = null) } }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleFieldClick(fieldId: String) {
        // Future: Navigate to field details or edit
        setEffect {
            Effect.ShowToast(resourceProvider.getString(R.string.feature_coming_soon))
        }
    }

    private fun handleVerifyClick(fieldId: String) {
        // Future: Initiate verification flow
        setEffect {
            Effect.ShowToast(resourceProvider.getString(R.string.feature_coming_soon))
        }
    }
}
