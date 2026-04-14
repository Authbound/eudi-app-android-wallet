/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.uilogic.navigation

interface NavigatableItem

open class Screen(name: String, parameters: String = "") : NavigatableItem {
    val screenRoute: String = name + parameters
    val screenName = name
}

sealed class StartupScreens {
    data object Splash : Screen(name = "SPLASH")
}

sealed class CommonScreens {
    data object Success : Screen(name = "SUCCESS", parameters = "?successConfig={successConfig}")
    data object Biometric : Screen(
        name = "BIOMETRIC",
        parameters = "?biometricConfig={biometricConfig}"
    )

    data object QuickPin :
        Screen(name = "QUICK_PIN", parameters = "?pinFlow={pinFlow}")

    data object QrScan : Screen(
        name = "QR_SCAN",
        parameters = "?qrScanConfig={qrScanConfig}"
    )
}

sealed class DashboardScreens {
    data object Dashboard : Screen(name = "DASHBOARD")

    data object Settings : Screen(name = "SETTINGS")

    data object PrivacyData : Screen(name = "PRIVACY_DATA")

    data object AccountDetails : Screen(name = "ACCOUNT_DETAILS")

    data object DocumentSign : Screen(name = "DOCUMENT_SIGN")

    data object DocumentDetails : Screen(
        name = "DOCUMENT_DETAILS",
        parameters = "?documentId={documentId}"
    )

    data object TransactionDetails : Screen(
        name = "TRANSACTION_DETAILS",
        parameters = "?transactionId={transactionId}"
    )

    data object VerificationTemplateSelection : Screen(name = "VERIFICATION_TEMPLATE_SELECTION")

    data object VerificationCustomCreation : Screen(
        name = "VERIFICATION_CUSTOM_CREATION",
        parameters = "?templateType={templateType}"
    )

    data object VerificationSharing : Screen(
        name = "VERIFICATION_SHARING",
        parameters = "?sessionId={sessionId}"
    )

    data object VerificationRecipient : Screen(
        name = "VERIFICATION_RECIPIENT",
        parameters = "?sessionId={sessionId}&accessToken={accessToken}&verificationUrl={verificationUrl}"
    )

    data object MyData : Screen(name = "MY_DATA")
}

sealed class PresentationScreens {
    data object PresentationRequest : Screen(
        name = "PRESENTATION_REQUEST",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object PresentationLoading : Screen(name = "PRESENTATION_LOADING")

    data object PresentationSuccess : Screen(name = "PRESENTATION_SUCCESS")

    data object QrScan : PresentationScreens()
}

sealed class ProximityScreens {
    data object QR : Screen(
        name = "PROXIMITY_QR",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object Request : Screen(
        name = "PROXIMITY_REQUEST",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object Loading : Screen(name = "PROXIMITY_LOADING")

    data object Success : Screen(name = "PROXIMITY_SUCCESS")
}

sealed class IssuanceScreens {
    data object AddDocument : Screen(
        name = "ISSUANCE_ADD_DOCUMENT",
        parameters = "?issuanceConfig={issuanceConfig}"
    )

    data object DocumentOffer : Screen(
        name = "ISSUANCE_DOCUMENT_OFFER",
        parameters = "?offerConfig={offerConfig}"
    )

    data object DocumentOfferCode : Screen(
        name = "ISSUANCE_DOCUMENT_OFFER_CODE",
        parameters = "?offerCodeConfig={offerCodeConfig}"
    )

    data object DocumentIssuanceSuccess : Screen(
        name = "ISSUANCE_DOCUMENT_SUCCESS",
        parameters = "?issuanceSuccessConfig={issuanceSuccessConfig}"
    )
}

sealed class AuthboundPidScreens {
    data object Intro : Screen(name = "AUTHBOUNDPID_INTRO")

    data object Result : Screen(
        name = "AUTHBOUNDPID_RESULT",
        parameters = "?resultType={resultType}"
    )
}

sealed class ModuleRoute(val route: String) : NavigatableItem {
    data object StartupModule : ModuleRoute("STARTUP_MODULE")
    data object CommonModule : ModuleRoute("COMMON_MODULE")
    data object DashboardModule : ModuleRoute("DASHBOARD_MODULE")
    data object PresentationModule : ModuleRoute("PRESENTATION_MODULE")
    data object ProximityModule : ModuleRoute("PROXIMITY_MODULE")
    data object IssuanceModule : ModuleRoute("ISSUANCE_MODULE")
    data object AuthboundPidModule : ModuleRoute("AUTHBOUNDPID_MODULE")
}

sealed class AuthenticationScreens(name: String, parameters: String = "") : Screen(name, parameters) {
    data object Login : AuthenticationScreens("login")
    data object AccountDeletionScheduled : AuthenticationScreens("account_deletion_scheduled")
    data object LegalAcceptance : AuthenticationScreens("legal_acceptance")
    data object WalletSetup : AuthenticationScreens("wallet_setup")
    data object DeviceSecurityRequired : AuthenticationScreens("device_security_required")
    data object ProfileCompletion : AuthenticationScreens("profileCompletion")
}
