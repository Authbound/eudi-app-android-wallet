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

package eu.europa.ec.dashboardfeature.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.dashboardfeature.interactor.CredentialSummaryUi
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.StartupScreens
import org.koin.android.annotation.KoinViewModel
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.user.UserInfo
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink

data class AuthInfoUi(
    val isAuthenticated: Boolean = false,
    val userInfo: UserInfo? = null,
    val profile: Profile? = null,
)

data class State(
    val settingsItems: List<SettingsItemUi> = emptyList(),
    val appVersion: String = "",
    val changelogUrl: String?,
    val isBiometricAuthenticationEnabled: Boolean = false,
    val isBiometricToggleInProgress: Boolean = false,
    val userEmail: String? = null,
    val showDeleteWalletConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val authInfo: AuthInfoUi = AuthInfoUi(),
    val credentialCount: Int = 0,
    val credentials: List<CredentialSummaryUi> = emptyList(),
    val pidPortraitBase64: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Pop : Event()
    data class ItemClicked(val itemType: SettingsMenuItemType) : Event()
    data class ToggleBiometricAuthentication(val context: Context) : Event()
    data object Logout : Event()
    data object ConfirmDeleteWallet : Event()
    data object DismissDeleteConfirmation : Event()
    data object RefreshPidPortrait : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()

        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String? = null,
            val inclusive: Boolean = false,
        ) : Navigation()

        data class OpenUrlExternally(val url: Uri) : Navigation()
    }

    data class ShareLogFile(val intent: Intent, val chooserTitle: String) : Effect()
    data class ShowToast(val message: String) : Effect()
}

@KoinViewModel
class SettingsViewModel(
    private val settingsInteractor: SettingsInteractor,
    private val deleteWalletActivationUseCase: DeleteWalletActivationUseCase,
    private val biometricAuthenticationController: BiometricAuthenticationController,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    init {
        loadInitialState()
    }

    override fun setInitialState(): State = State(
        changelogUrl = null
    )

    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                val changelogUrl = settingsInteractor.getChangelogUrl()
                val isAuthenticated = settingsInteractor.isUserAuthenticated()
                val user = settingsInteractor.getCurrentUser()
                val profile = settingsInteractor.getMyProfile().getOrNull()
                val pidPortraitBase64 = settingsInteractor.getMainPidPortraitBase64()
                val isBiometricAuthenticationEnabled = settingsInteractor.getBiometricsEnabled()
                val settingsItems = settingsInteractor.getSettingsItemsUi(
                    changelogUrl = changelogUrl,
                    isBiometricsEnabled = isBiometricAuthenticationEnabled
                )
                val appVersion = settingsInteractor.getAppVersion()
                val credentialCount = settingsInteractor.getCredentialCount()
                val credentials = settingsInteractor.getCredentialSummaries()

                setState {
                    copy(
                        changelogUrl = changelogUrl,
                        isBiometricAuthenticationEnabled = isBiometricAuthenticationEnabled,
                        settingsItems = settingsItems,
                        appVersion = appVersion,
                        userEmail = user?.email,
                        credentialCount = credentialCount,
                        credentials = credentials,
                        pidPortraitBase64 = pidPortraitBase64,
                        authInfo = AuthInfoUi(
                            isAuthenticated = isAuthenticated,
                            userInfo = user,
                            profile = profile
                        )
                    )
                }
            } catch (e: Exception) {
                // Handle error, e.g., show an error message
                e.printStackTrace()
            }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Pop -> setEffect { Effect.Navigation.Pop }

            is Event.ItemClicked -> handleSettingsMenuItemClicked(event.itemType)
            is Event.ToggleBiometricAuthentication -> toggleBiometricAuthentication(event.context)

            is Event.Logout -> logout()
            is Event.ConfirmDeleteWallet -> deleteWalletActivation()
            is Event.DismissDeleteConfirmation -> dismissDeleteConfirmation()
            is Event.RefreshPidPortrait -> refreshPidPortrait()
        }
    }

    private fun refreshPidPortrait() {
        viewModelScope.launch {
            val pidPortraitBase64 = settingsInteractor.getMainPidPortraitBase64()
            setState { copy(pidPortraitBase64 = pidPortraitBase64) }
        }
    }

    private fun loadUserSettings() {
        viewModelScope.launch {
            settingsInteractor.getUserEmail().collect { email ->
                setState { copy(userEmail = email) }
            }
        }
    }

    private fun loadAuthInfo() {
        viewModelScope.launch {
            try {
                val isAuthenticated = settingsInteractor.isUserAuthenticated()
                val user = settingsInteractor.getCurrentUser()
                setState {
                    copy(
                        authInfo = AuthInfoUi(
                            isAuthenticated = isAuthenticated,
                            userInfo = user,
                        )
                    )
                }
            } catch (e: Exception) {
                // Log error or handle it, for now we just prevent a crash
                e.printStackTrace()
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            settingsInteractor.logout()
            setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = StartupScreens.Splash.screenRoute,
                    popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                    inclusive = true
                )
            }
        }
    }

    private fun handleSettingsMenuItemClicked(itemType: SettingsMenuItemType) {
        when (itemType) {
            SettingsMenuItemType.ACCOUNT_DETAILS -> {
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = DashboardScreens.AccountDetails.screenRoute
                    )
                }
            }

            SettingsMenuItemType.MY_DATA -> {
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = DashboardScreens.MyData.screenRoute
                    )
                }
            }

            SettingsMenuItemType.CHANGE_PIN -> {
                val nextScreenRoute = generateComposableNavigationLink(
                    screen = CommonScreens.QuickPin,
                    arguments = generateComposableArguments(
                        mapOf("pinFlow" to PinFlow.UPDATE)
                    )
                )
                setEffect { Effect.Navigation.SwitchScreen(screenRoute = nextScreenRoute) }
            }

            SettingsMenuItemType.BIOMETRIC_AUTHENTICATION -> {
                // Handled via Event.ToggleBiometricAuthentication from the UI
            }

            SettingsMenuItemType.RETRIEVE_LOGS -> {
                val logs = settingsInteractor.retrieveLogFileUris()
                if (logs.isNotEmpty()) {
                    setEffect {
                        Effect.ShareLogFile(
                            intent = Intent().apply {
                                action = Intent.ACTION_SEND_MULTIPLE
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, logs)
                                type = "text/*"
                            },
                            chooserTitle = resourceProvider.getString(R.string.settings_intent_chooser_logs_share_title)
                        )
                    }
                }
            }

            SettingsMenuItemType.CHANGELOG -> {
                val changelogUrl = viewState.value.changelogUrl
                if (changelogUrl != null) {
                    setEffect {
                        Effect.Navigation.OpenUrlExternally(
                            url = changelogUrl.toUri()
                        )
                    }
                }
            }

            SettingsMenuItemType.RESET_HEALTH_DATA -> {
                resetHealthData()
            }

            SettingsMenuItemType.DELETE_WALLET_ACTIVATION -> {
                setState { copy(showDeleteWalletConfirmation = true) }
            }

        }
    }

    private fun toggleBiometricAuthentication(context: Context) {
        if (viewState.value.isBiometricToggleInProgress) {
            return
        }
        if (viewState.value.isBiometricAuthenticationEnabled) {
            persistBiometricAuthentication(enabled = false)
            return
        }
        setState { copy(isBiometricToggleInProgress = true) }
        biometricAuthenticationController.deviceSupportsBiometrics { availability ->
            when (availability) {
                BiometricsAvailability.CanAuthenticate -> {
                    biometricAuthenticationController.authenticate(
                        context = context,
                        notifyOnAuthenticationFailure = true
                    ) { result ->
                        when (result) {
                            BiometricsAuthenticate.Success -> {
                                persistBiometricAuthentication(enabled = true)
                            }
                            is BiometricsAuthenticate.Failed -> {
                                setState { copy(isBiometricToggleInProgress = false) }
                                setEffect { Effect.ShowToast(result.errorMessage) }
                            }
                            BiometricsAuthenticate.Cancelled -> {
                                setState { copy(isBiometricToggleInProgress = false) }
                            }
                        }
                    }
                }
                BiometricsAvailability.NonEnrolled -> {
                    setState { copy(isBiometricToggleInProgress = false) }
                    setEffect {
                        Effect.ShowToast(
                            resourceProvider.getString(R.string.quick_pin_biometric_not_enrolled_error)
                        )
                    }
                }
                is BiometricsAvailability.Failure -> {
                    setState { copy(isBiometricToggleInProgress = false) }
                    setEffect { Effect.ShowToast(availability.errorMessage) }
                }
            }
        }
    }

    private fun persistBiometricAuthentication(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsInteractor.setBiometricsEnabled(enabled)
                settingsInteractor.setBiometricsPreferenceDecided(true)
                val updatedItems = settingsInteractor.getSettingsItemsUi(
                    changelogUrl = viewState.value.changelogUrl,
                    isBiometricsEnabled = enabled
                )
                setState {
                    copy(
                        isBiometricAuthenticationEnabled = enabled,
                        settingsItems = updatedItems,
                        isBiometricToggleInProgress = false
                    )
                }
            } catch (_: Exception) {
                setState { copy(isBiometricToggleInProgress = false) }
                setEffect {
                    Effect.ShowToast(
                        resourceProvider.getString(R.string.generic_error_description)
                    )
                }
            }
        }
    }

    private fun deleteWalletActivation() {
        viewModelScope.launch {
            setState { copy(showDeleteWalletConfirmation = false, isDeleting = true) }
            deleteWalletActivationUseCase().fold(
                onSuccess = {
                    setState { copy(isDeleting = false) }
                    setEffect {
                        Effect.ShowToast(
                            resourceProvider.getString(R.string.settings_delete_wallet_success)
                        )
                    }
                    setEffect {
                        Effect.Navigation.SwitchScreen(
                            screenRoute = StartupScreens.Splash.screenRoute,
                            popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                            inclusive = true
                        )
                    }
                },
                onFailure = {
                    setState { copy(isDeleting = false) }
                    setEffect {
                        Effect.ShowToast(
                            resourceProvider.getString(R.string.settings_delete_wallet_error)
                        )
                    }
                }
            )
        }
    }

    private fun dismissDeleteConfirmation() {
        setState { copy(showDeleteWalletConfirmation = false) }
    }

    private fun resetHealthData() {
        viewModelScope.launch {
            settingsInteractor.resetHealthData().fold(
                onSuccess = {
                    setEffect {
                        Effect.ShowToast(
                            resourceProvider.getString(R.string.settings_reset_health_data_success)
                        )
                    }
                },
                onFailure = { error ->
                    setEffect {
                        Effect.ShowToast(
                            error.localizedMessage
                                ?: resourceProvider.getString(R.string.settings_reset_health_data_error)
                        )
                    }
                }
            )
        }
    }
}
