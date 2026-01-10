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

package eu.europa.ec.dashboardfeature.ui.actions

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractor
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractorPartialState
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionCategoryUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionFilterChipUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionSortOrder
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val pendingCount: Int = 0,
    val groupedActions: List<Pair<ActionCategoryUi, List<ActionUi>>> = emptyList(),
    val filteredGroupedActions: List<Pair<ActionCategoryUi, List<ActionUi>>> = emptyList(),
    val showEmptyState: Boolean = false,
    val showNoResultsState: Boolean = false,
    val isProcessingAction: String? = null,
    val isProcessingBatch: Boolean = false,
    val selectedFilter: ActionType? = null,
    val filterChips: List<ActionFilterChipUi> = emptyList(),
    val sortOrder: ActionSortOrder = ActionSortOrder.NEWEST_FIRST,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object OnResume : Event()
    data object Pop : Event()

    data class OnFilterSelected(val type: ActionType?) : Event()
    data class OnSortOrderChanged(val order: ActionSortOrder) : Event()
    data class ActionItemClicked(val actionId: String) : Event()
    data class AcceptAction(val actionId: String) : Event()
    data class DeclineAction(val actionId: String) : Event()
    data object AcceptAllPending : Event()
    data object DeclineAllPending : Event()
    data object ViewHistory : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class ShowToast(val message: String) : Effect()
    data object RefreshBadgeCount : Effect()
}

@KoinViewModel
class ActionsViewModel(
    private val interactor: ActionsInteractor,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> loadActions()
            is Event.OnResume -> loadActions()
            is Event.Pop -> setEffect { Effect.Navigation.Pop }
            is Event.OnFilterSelected -> handleFilterSelection(event.type)
            is Event.OnSortOrderChanged -> handleSortOrderChange(event.order)
            is Event.ActionItemClicked -> handleActionClick(event.actionId)
            is Event.AcceptAction -> handleAccept(event.actionId)
            is Event.DeclineAction -> handleDecline(event.actionId)
            is Event.AcceptAllPending -> handleAcceptAll()
            is Event.DeclineAllPending -> handleDeclineAll()
            is Event.ViewHistory -> handleViewHistory()
        }
    }

    private fun loadActions() {
        setState { copy(isLoading = groupedActions.isEmpty(), error = null) }

        viewModelScope.launch {
            interactor.getActions().collect { result ->
                when (result) {
                    is ActionsInteractorPartialState.Success -> {
                        val currentState = viewState.value
                        val filterChips = buildFilterChips(result.allActions, currentState.selectedFilter)
                        val filteredActions = applyFiltersAndSort(
                            result.groupedActions,
                            currentState.selectedFilter,
                            currentState.sortOrder
                        )

                        setState {
                            copy(
                                isLoading = false,
                                pendingCount = result.pendingCount,
                                groupedActions = result.groupedActions,
                                filteredGroupedActions = filteredActions,
                                filterChips = filterChips,
                                showEmptyState = result.groupedActions.isEmpty(),
                                showNoResultsState = result.groupedActions.isNotEmpty() && filteredActions.isEmpty(),
                                error = null
                            )
                        }
                    }

                    is ActionsInteractorPartialState.Failure -> {
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

    private fun handleFilterSelection(type: ActionType?) {
        val currentState = viewState.value
        val filterChips = buildFilterChips(getAllActions(), type)
        val filteredActions = applyFiltersAndSort(
            currentState.groupedActions,
            type,
            currentState.sortOrder
        )
        setState {
            copy(
                selectedFilter = type,
                filterChips = filterChips,
                filteredGroupedActions = filteredActions,
                showNoResultsState = groupedActions.isNotEmpty() && filteredActions.isEmpty()
            )
        }
    }

    private fun handleSortOrderChange(order: ActionSortOrder) {
        val currentState = viewState.value
        val filteredActions = applyFiltersAndSort(
            currentState.groupedActions,
            currentState.selectedFilter,
            order
        )
        setState {
            copy(
                sortOrder = order,
                filteredGroupedActions = filteredActions
            )
        }
    }

    private fun getAllActions(): List<ActionUi> {
        return viewState.value.groupedActions.flatMap { it.second }
    }

    private fun buildFilterChips(actions: List<ActionUi>, selectedType: ActionType?): List<ActionFilterChipUi> {
        val allCount = actions.size
        val signCount = actions.count { it.type == ActionType.SIGN_REQUEST }
        val verifyCount = actions.count { it.type == ActionType.VERIFY_REQUEST }
        val dataCount = actions.count { it.type == ActionType.DATA_REQUEST }

        return listOf(
            ActionFilterChipUi(
                type = null,
                label = resourceProvider.getString(R.string.actions_filter_all),
                count = allCount,
                isSelected = selectedType == null
            ),
            ActionFilterChipUi(
                type = ActionType.SIGN_REQUEST,
                label = resourceProvider.getString(R.string.actions_filter_sign),
                count = signCount,
                isSelected = selectedType == ActionType.SIGN_REQUEST
            ),
            ActionFilterChipUi(
                type = ActionType.VERIFY_REQUEST,
                label = resourceProvider.getString(R.string.actions_filter_verify),
                count = verifyCount,
                isSelected = selectedType == ActionType.VERIFY_REQUEST
            ),
            ActionFilterChipUi(
                type = ActionType.DATA_REQUEST,
                label = resourceProvider.getString(R.string.actions_filter_data),
                count = dataCount,
                isSelected = selectedType == ActionType.DATA_REQUEST
            )
        )
    }

    private fun applyFiltersAndSort(
        groupedActions: List<Pair<ActionCategoryUi, List<ActionUi>>>,
        filterType: ActionType?,
        sortOrder: ActionSortOrder
    ): List<Pair<ActionCategoryUi, List<ActionUi>>> {
        return groupedActions.mapNotNull { (category, actions) ->
            val filtered = actions.filter { action ->
                filterType == null || action.type == filterType
            }.let { filteredList ->
                when (sortOrder) {
                    ActionSortOrder.NEWEST_FIRST -> filteredList
                    ActionSortOrder.OLDEST_FIRST -> filteredList.reversed()
                    ActionSortOrder.BY_TYPE -> filteredList.sortedBy { it.type.name }
                    ActionSortOrder.BY_REQUESTER -> filteredList.sortedBy { it.requesterName }
                }
            }

            if (filtered.isNotEmpty()) category to filtered else null
        }
    }

    private fun handleActionClick(actionId: String) {
        // Navigate to action details (future implementation)
        setEffect {
            Effect.ShowToast(resourceProvider.getString(R.string.feature_coming_soon))
        }
    }

    private fun handleAccept(actionId: String) {
        setState { copy(isProcessingAction = actionId) }

        viewModelScope.launch {
            interactor.acceptAction(actionId).fold(
                onSuccess = {
                    setState { copy(isProcessingAction = null) }
                    setEffect {
                        Effect.ShowToast(resourceProvider.getString(R.string.actions_toast_accepted))
                    }
                    setEffect { Effect.RefreshBadgeCount }
                    loadActions() // Refresh the list
                },
                onFailure = { error ->
                    setState { copy(isProcessingAction = null) }
                    setEffect {
                        Effect.ShowToast(error.localizedMessage ?: "Failed to accept action")
                    }
                }
            )
        }
    }

    private fun handleDecline(actionId: String) {
        setState { copy(isProcessingAction = actionId) }

        viewModelScope.launch {
            interactor.declineAction(actionId).fold(
                onSuccess = {
                    setState { copy(isProcessingAction = null) }
                    setEffect {
                        Effect.ShowToast(resourceProvider.getString(R.string.actions_toast_declined))
                    }
                    setEffect { Effect.RefreshBadgeCount }
                    loadActions() // Refresh the list
                },
                onFailure = { error ->
                    setState { copy(isProcessingAction = null) }
                    setEffect {
                        Effect.ShowToast(error.localizedMessage ?: "Failed to decline action")
                    }
                }
            )
        }
    }

    private fun handleAcceptAll() {
        val pendingActions = getPendingActions()
        if (pendingActions.isEmpty()) return

        setState { copy(isProcessingBatch = true) }

        viewModelScope.launch {
            var successCount = 0
            pendingActions.forEach { action ->
                interactor.acceptAction(action.id).fold(
                    onSuccess = { successCount++ },
                    onFailure = { /* Continue with next */ }
                )
            }
            setState { copy(isProcessingBatch = false) }
            setEffect {
                Effect.ShowToast(resourceProvider.getString(R.string.actions_toast_all_accepted, successCount))
            }
            setEffect { Effect.RefreshBadgeCount }
            loadActions()
        }
    }

    private fun handleDeclineAll() {
        val pendingActions = getPendingActions()
        if (pendingActions.isEmpty()) return

        setState { copy(isProcessingBatch = true) }

        viewModelScope.launch {
            var successCount = 0
            pendingActions.forEach { action ->
                interactor.declineAction(action.id).fold(
                    onSuccess = { successCount++ },
                    onFailure = { /* Continue with next */ }
                )
            }
            setState { copy(isProcessingBatch = false) }
            setEffect {
                Effect.ShowToast(resourceProvider.getString(R.string.actions_toast_all_declined, successCount))
            }
            setEffect { Effect.RefreshBadgeCount }
            loadActions()
        }
    }

    private fun handleViewHistory() {
        // Show history/completed actions - for now show toast
        setEffect {
            Effect.ShowToast(resourceProvider.getString(R.string.feature_coming_soon))
        }
    }

    private fun getPendingActions(): List<ActionUi> {
        return viewState.value.groupedActions
            .find { it.first == ActionCategoryUi.Pending }
            ?.second
            ?: emptyList()
    }
}
