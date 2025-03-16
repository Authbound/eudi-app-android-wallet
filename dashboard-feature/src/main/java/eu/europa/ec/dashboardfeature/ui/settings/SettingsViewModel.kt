

package eu.europa.ec.dashboardfeature.ui.settings

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.dashboardfeature.ui.BottomNavigationItem

import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.serializer.UiSerializer

import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class ProfileData(
    val name: String,
    val email: String,
    val phone: String,
    val avatarText: String
)

data class SecuritySettings(
    val isBiometricEnabled: Boolean
)

data class State(
    val isLoading: Boolean = false,
    val profile: ProfileData = ProfileData(
        name = "John Doe",
        email = "john.doe@example.com",
        phone = "+1234567890",
        avatarText = "JD"
    ),
    val securitySettings: SecuritySettings = SecuritySettings(
        isBiometricEnabled = false
    ),
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: SettingsBottomSheetContent? = null
) : ViewState




sealed class Event : ViewEvent {
    object Init : Event()
    object EditProfilePressed : Event()
    data class BiometricToggled(val enabled: Boolean) : Event()
    object ChangePinPressed : Event()
    object ChangePasswordPressed : Event()
    object DataSharingPressed : Event()
    object ActivityLogPressed : Event()
    object DeleteDataPressed : Event()
    object NotificationsPressed : Event()

    sealed class BottomSheet {
        data class UpdateBottomSheetState(val isOpen: Boolean) : Event()
        object ConfirmDeleteData : Event()
        object Close : Event()
    }
}

sealed class Effect : ViewSideEffect{
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
    }

    object ShowBiometricPrompt : Effect()
    object ShowBottomSheet : Effect()
    object CloseBottomSheet : Effect()
}

sealed class SettingsBottomSheetContent {
    object DeleteConfirmation : SettingsBottomSheetContent()
}

@KoinViewModel
class SettingsViewModel(val interactor: SettingsInteractor,
                        private val uiSerializer: UiSerializer,
                        private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> loadUserProfile()
            is Event.EditProfilePressed -> navigateToEditProfile()
            is Event.BiometricToggled -> toggleBiometric(event.enabled)
            is Event.ChangePinPressed -> navigateToChangePin()
            is Event.ChangePasswordPressed -> navigateToChangePassword()
            is Event.DataSharingPressed -> navigateToDataSharing()
            is Event.ActivityLogPressed -> navigateToActivityLog()
            is Event.DeleteDataPressed -> showDeleteConfirmation()
            is Event.NotificationsPressed -> navigateToNotifications()
            is Event.BottomSheet.UpdateBottomSheetState -> updateBottomSheetState(event.isOpen)
            is Event.BottomSheet.ConfirmDeleteData -> deleteUserData()
            is Event.BottomSheet.Close -> closeBottomSheet()
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // TODO: Load user profile from repository
            setState { copy(isLoading = false) }
        }
    }

    private fun navigateToEditProfile() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                setEffect { Effect.ShowBiometricPrompt }
            } else {
                setState { copy(securitySettings = securitySettings.copy(isBiometricEnabled = false)) }
            }
        }
    }

    private fun navigateToChangePin() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToChangePassword() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToDataSharing() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToActivityLog() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToNotifications() {
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun showDeleteConfirmation() {
        setState { copy(
            isBottomSheetOpen = true,
            sheetContent = SettingsBottomSheetContent.DeleteConfirmation
        ) }
        setEffect { Effect.ShowBottomSheet }
    }

    private fun deleteUserData() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // TODO: Delete user data from repository
            setState { copy(isLoading = false) }
            closeBottomSheet()
        }
    }

    private fun updateBottomSheetState(isOpen: Boolean) {
        setState { copy(isBottomSheetOpen = isOpen) }
    }

    private fun closeBottomSheet() {
        setState { copy(
            isBottomSheetOpen = false,
            sheetContent = null
        ) }
        setEffect { Effect.CloseBottomSheet }
    }
}