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
            // Load biometric settings
            try {
//                val isBiometricEnabled = interactor.isBiometricEnabled()
                setState { 
                    copy(
                        isLoading = false,
//                        securitySettings = securitySettings.copy(
//                            isBiometricEnabled = isBiometricEnabled
//                        )
                    ) 
                }
            } catch (e: Exception) {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun navigateToEditProfile() {
        // TODO: Implement when Edit Profile screen is available
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                setEffect { Effect.ShowBiometricPrompt }
            } else {
                try {
//                    val success = interactor.setBiometricEnabled(false)
//                    if (success) {
//                        setState { copy(securitySettings = securitySettings.copy(isBiometricEnabled = false)) }
//                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    private fun navigateToChangePin() {
        setEffect { Effect.Navigation.SwitchScreen(DashboardScreens.ChangePin.screenRoute) }
    }

    private fun navigateToChangePassword() {
        // TODO: Implement when Change Password screen is available
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToDataSharing() {
        // TODO: Implement when Data Sharing screen is available
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToActivityLog() {
        // TODO: Implement when Activity Log screen is available
        setEffect { Effect.Navigation.SwitchScreen(BottomNavigationItem.Settings.route) }
    }

    private fun navigateToNotifications() {
        // TODO: Implement when Notifications screen is available
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
            try {
//                val success = interactor.deleteAllUserData()
//                if (success) {
                    // Show success message or navigate to login screen
//                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                setState { copy(isLoading = false) }
                closeBottomSheet()
            }
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