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

package eu.europa.ec.dashboardfeature.ui.health

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.HealthConnectionPartialState
import eu.europa.ec.dashboardfeature.interactor.HealthInteractor
import eu.europa.ec.dashboardfeature.interactor.HealthInteractorPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * UI State for the Health tab.
 */
data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val sheetContent: HealthSheetContent = HealthSheetContent.None,

    // Connection
    val kantaStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val maisaStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val lastSyncTime: String? = null,
    val isConnecting: Boolean = false,

    // Health data (grouped by category)
    val medications: List<MedicationUi> = emptyList(),
    val prescriptions: List<PrescriptionUi> = emptyList(),
    val healthRecords: List<HealthRecordUi> = emptyList(),
    val vaccinations: List<VaccinationUi> = emptyList(),

    // Search/Filter
    val searchQuery: String = "",
    val showEmptyState: Boolean = true,
    val isRefreshing: Boolean = false
) : ViewState

/**
 * Events that can be triggered from the Health UI.
 */
sealed class Event : ViewEvent {
    data object Init : Event()
    data object OnResume : Event()
    data object ImportPressed : Event()
    data class SearchChanged(val query: String) : Event()
    data class CredentialClicked(val id: String, val type: HealthCategoryUi) : Event()
    data class SharePressed(val id: String, val type: HealthCategoryUi) : Event()
    data object ConnectKantaPressed : Event()
    data object ConnectMaisaPressed : Event()
    data object RefreshPressed : Event()
    data object DismissSheet : Event()
    data class ServiceInfoPressed(val service: HealthService) : Event()
}

/**
 * Side effects emitted by the HealthViewModel.
 */
sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class ShowToast(val message: String) : Effect()
    data class LaunchMaisaAuth(val authorizationUrl: String) : Effect()
}

/**
 * ViewModel for the Health tab following MVI pattern.
 */
@KoinViewModel
class HealthViewModel(
    private val interactor: HealthInteractor,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                loadConnectionStatus()
                loadHealthData()
            }
            is Event.OnResume -> {
                loadConnectionStatus()
                loadHealthData()
            }
            is Event.ImportPressed -> handleImportPressed()
            is Event.SearchChanged -> handleSearchChanged(event.query)
            is Event.CredentialClicked -> handleCredentialClicked(event.id, event.type)
            is Event.SharePressed -> handleSharePressed(event.id, event.type)
            is Event.ConnectKantaPressed -> handleConnectService(HealthService.KANTA)
            is Event.ConnectMaisaPressed -> handleConnectService(HealthService.MAISA)
            is Event.RefreshPressed -> handleRefresh()
            is Event.DismissSheet -> setState { copy(sheetContent = HealthSheetContent.None) }
            is Event.ServiceInfoPressed -> setState {
                copy(sheetContent = HealthSheetContent.ServiceInfo(event.service))
            }
        }
    }

    private fun loadConnectionStatus() {
        viewModelScope.launch {
            interactor.getConnectionStatus().collect { result ->
                when (result) {
                    is HealthConnectionPartialState.Success -> {
                        setState {
                            copy(
                                kantaStatus = result.kantaStatus,
                                maisaStatus = result.maisaStatus,
                                lastSyncTime = result.lastSyncTime
                            )
                        }
                    }
                    is HealthConnectionPartialState.Failure -> {
                        // Connection status failure is non-critical, just log it
                    }
                }
            }
        }
    }

    private fun loadHealthData() {
        setState { copy(isLoading = medications.isEmpty() && prescriptions.isEmpty(), error = null) }

        viewModelScope.launch {
            interactor.getHealthData().collect { result ->
                when (result) {
                    is HealthInteractorPartialState.Success -> {
                        val hasData = result.medications.isNotEmpty() ||
                                result.prescriptions.isNotEmpty() ||
                                result.healthRecords.isNotEmpty() ||
                                result.vaccinations.isNotEmpty()

                        setState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                medications = result.medications,
                                prescriptions = result.prescriptions,
                                healthRecords = result.healthRecords,
                                vaccinations = result.vaccinations,
                                showEmptyState = !hasData,
                                error = null
                            )
                        }
                    }
                    is HealthInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
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

    private fun handleImportPressed() {
        // Show service selection sheet
        setState { copy(sheetContent = HealthSheetContent.ServiceInfo(HealthService.KANTA)) }
    }

    private fun handleSearchChanged(query: String) {
        setState { copy(searchQuery = query) }
        // Filter logic will be applied in the UI based on searchQuery
    }

    private fun handleCredentialClicked(id: String, type: HealthCategoryUi) {
        // Show detail sheet for the credential
        when (type) {
            is HealthCategoryUi.Medications -> {
                val medication = viewState.value.medications.find { it.id == id }
                if (medication != null) {
                    setState { copy(sheetContent = HealthSheetContent.MedicationDetail(medication)) }
                }
            }
            else -> {
                setEffect {
                    Effect.ShowToast(resourceProvider.getString(R.string.feature_coming_soon))
                }
            }
        }
    }

    private fun handleSharePressed(id: String, type: HealthCategoryUi) {
        setState { copy(sheetContent = HealthSheetContent.ShareOptions(id)) }
    }

    private fun handleConnectService(service: HealthService) {
        setState { copy(isConnecting = true, sheetContent = HealthSheetContent.None) }

        viewModelScope.launch {
            when (service) {
                HealthService.KANTA -> {
                    interactor.connectToService(service).fold(
                        onSuccess = {
                            setState { copy(isConnecting = false) }
                            val serviceName = "Kanta.fi"
                            setEffect {
                                Effect.ShowToast(
                                    resourceProvider.getString(R.string.health_connected_to_service, serviceName)
                                )
                            }
                            loadConnectionStatus()
                            loadHealthData()
                        },
                        onFailure = { error ->
                            setState { copy(isConnecting = false) }
                            setEffect {
                                Effect.ShowToast(error.localizedMessage ?: "Connection failed")
                            }
                        }
                    )
                }
                HealthService.MAISA -> {
                    interactor.startMaisaAuth().fold(
                        onSuccess = { authorizationUrl ->
                            setState { copy(isConnecting = false) }
                            setEffect { Effect.LaunchMaisaAuth(authorizationUrl) }
                        },
                        onFailure = { error ->
                            setState { copy(isConnecting = false) }
                            setEffect {
                                Effect.ShowToast(error.localizedMessage ?: "Maisa login failed")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleRefresh() {
        setState { copy(isRefreshing = true) }

        viewModelScope.launch {
            interactor.refreshHealthData().fold(
                onSuccess = {
                    loadHealthData()
                },
                onFailure = { error ->
                    setState { copy(isRefreshing = false) }
                    setEffect {
                        Effect.ShowToast(error.localizedMessage ?: "Refresh failed")
                    }
                }
            )
        }
    }
}
