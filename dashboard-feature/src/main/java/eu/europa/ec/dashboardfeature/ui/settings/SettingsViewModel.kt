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

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.extension.toUri
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
    val screenTitle: String,
    val settingsItems: List<SettingsItemUi> = emptyList(),
    val appVersion: String = "",
    val changelogUrl: String?,
    val userEmail: String? = null,
    val showDeleteWalletConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val authInfo: AuthInfoUi = AuthInfoUi(),
) : ViewState

sealed class Event : ViewEvent {
    data object Pop : Event()
    data class ItemClicked(val itemType: SettingsMenuItemType) : Event()
    data object Logout : Event()
    data object ConfirmDeleteWallet : Event()
    data object DismissDeleteConfirmation : Event()
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
}

@KoinViewModel
class SettingsViewModel(
    private val settingsInteractor: SettingsInteractor,
    private val deleteWalletActivationUseCase: DeleteWalletActivationUseCase,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    init {
        loadInitialState()
    }

    override fun setInitialState(): State = State(
        screenTitle = resourceProvider.getString(R.string.settings_screen_title),
        changelogUrl = null
    )

    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                val changelogUrl = settingsInteractor.getChangelogUrl()
                val isAuthenticated = settingsInteractor.isUserAuthenticated()
                val user = settingsInteractor.getCurrentUser()
                val profile = settingsInteractor.getMyProfile().getOrNull()
                val settingsItems = settingsInteractor.getSettingsItemsUi(changelogUrl)
                val appVersion = settingsInteractor.getAppVersion()

                setState {
                    copy(
                        changelogUrl = changelogUrl,
                        settingsItems = settingsItems,
                        appVersion = appVersion,
                        userEmail = user?.email,
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

            is Event.Logout -> logout()
            is Event.ConfirmDeleteWallet -> deleteWalletActivation()
            is Event.DismissDeleteConfirmation -> dismissDeleteConfirmation()
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

            SettingsMenuItemType.CHANGE_PIN -> {
                val nextScreenRoute = generateComposableNavigationLink(
                    screen = CommonScreens.QuickPin,
                    arguments = generateComposableArguments(
                        mapOf("pinFlow" to PinFlow.UPDATE)
                    )
                )
                setEffect { Effect.Navigation.SwitchScreen(screenRoute = nextScreenRoute) }
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

            SettingsMenuItemType.DELETE_WALLET_ACTIVATION -> {
                setState { copy(showDeleteWalletConfirmation = true) }
            }

        }
    }

    private fun deleteWalletActivation() {
        viewModelScope.launch {
            setState { copy(showDeleteWalletConfirmation = false, isDeleting = true) }
            try {
                deleteWalletActivationUseCase().getOrThrow()
                // Navigate to login after successful wallet deletion and logout
                // The deleteWalletActivationUseCase handles both backend deletion and user logout
                setState { copy(isDeleting = false) }
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = StartupScreens.Splash.screenRoute,
                        popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                        inclusive = true
                    )
                }
            } catch (e: Exception) {
                setState { copy(isDeleting = false) }
                setEffect { Effect.Navigation.Pop }
            }
        }
    }

    private fun dismissDeleteConfirmation() {
        setState { copy(showDeleteWalletConfirmation = false) }
    }
}