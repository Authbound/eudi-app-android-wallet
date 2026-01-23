sealed class AuthenticationScreens(
    val screenRoute: String,
) {
    data object Login : AuthenticationScreens("login")
    data object WalletSetup : AuthenticationScreens("walletSetup")
    data object DeviceSecurityRequired : AuthenticationScreens("deviceSecurityRequired")
    data object ProfileCompletion : AuthenticationScreens("profileCompletion")
}

sealed class DashboardScreens(
    val screenRoute: String,
) {
    data object Dashboard : DashboardScreens("dashboard")
    data object Settings : DashboardScreens("settings")
} 