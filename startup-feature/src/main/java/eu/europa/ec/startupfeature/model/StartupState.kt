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

package eu.europa.ec.startupfeature.model

import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink

/**
 * Represents the possible states during app startup routing.
 *
 * The startup flow checks authentication, profile completion, wallet activation,
 * and local unlock status to determine where to route the user.
 *
 * State hierarchy:
 * 1. Authentication (Supabase session)
 * 2. Onboarding (Profile + WUA)
 * 3. Local Unlock (PIN verification or direct to dashboard)
 */
sealed class StartupState {
    /**
     * The navigation route for this state.
     */
    abstract val screenRoute: String

    /**
     * Log message for debugging startup flow.
     */
    abstract val logMessage: String

    /**
     * User is not authenticated. Route to login screen.
     */
    data object NotAuthenticated : StartupState() {
        override val screenRoute: String = AuthenticationScreens.Login.screenRoute
        override val logMessage: String = "User not authenticated → Login"
    }

    /**
     * User profile is incomplete. Route to profile completion screen.
     */
    data object ProfileIncomplete : StartupState() {
        override val screenRoute: String = AuthenticationScreens.ProfileCompletion.screenRoute
        override val logMessage: String = "Profile incomplete → ProfileCompletion"
    }

    data object AccountDeletionScheduled : StartupState() {
        override val screenRoute: String = AuthenticationScreens.AccountDeletionScheduled.screenRoute
        override val logMessage: String = "Account deletion scheduled → AccountDeletionScheduled"
    }

    data object LegalAcceptanceRequired : StartupState() {
        override val screenRoute: String = AuthenticationScreens.LegalAcceptance.screenRoute
        override val logMessage: String = "Legal acceptance required → LegalAcceptance"
    }

    /**
     * Wallet Unit Attestation (WUA) not activated. Route to device security preflight.
     */
    data class WalletNotActivated(val reason: String) : StartupState() {
        override val screenRoute: String = AuthenticationScreens.DeviceSecurityRequired.screenRoute
        override val logMessage: String = "Wallet not activated ($reason) → DeviceSecurityRequired"
    }

    /**
     * Device security missing. Route to device security preflight screen.
     */
    data object DeviceSecurityRequired : StartupState() {
        override val screenRoute: String = AuthenticationScreens.DeviceSecurityRequired.screenRoute
        override val logMessage: String = "Device security missing → DeviceSecurityRequired"
    }

    /**
     * PIN not set. Route to PIN creation screen.
     * This ensures PIN is mandatory after wallet activation.
     */
    data object PinNotSet : StartupState() {
        override val screenRoute: String
            get() = generateComposableNavigationLink(
                screen = CommonScreens.QuickPin,
                arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE_WITHOUT_ACTIVATION))
            )
        override val logMessage: String = "PIN not set → QuickPin (CREATE)"
    }

    /**
     * PIN verification required (user is outside TTL).
     * Route to PIN verification screen (single entry, no re-enter).
     */
    data object PinVerificationRequired : StartupState() {
        override val screenRoute: String
            get() = generateComposableNavigationLink(
                screen = CommonScreens.QuickPin,
                arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.VERIFY))
            )
        override val logMessage: String = "PIN verification required → QuickPin (VERIFY)"
    }

    data object PinRecoveryRequired : StartupState() {
        override val screenRoute: String
            get() = generateComposableNavigationLink(
                screen = CommonScreens.QuickPin,
                arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.VERIFY))
            )
        override val logMessage: String = "PIN recovery required → QuickPin (VERIFY)"
    }

    /**
     * User is fully authenticated and unlocked. Route directly to dashboard.
     * This happens when user is within the TTL (10 minutes by default).
     */
    data object Ready : StartupState() {
        override val screenRoute: String = DashboardScreens.Dashboard.screenRoute
        override val logMessage: String = "User ready → Dashboard"
    }

    /**
     * Network error occurred during startup checks.
     * May be retryable depending on the error type.
     */
    data class NetworkError(
        val retryable: Boolean,
        val cachedFallback: String?
    ) : StartupState() {
        override val screenRoute: String = cachedFallback ?: AuthenticationScreens.Login.screenRoute
        override val logMessage: String = "Network error (retryable: $retryable) → ${screenRoute.substringAfterLast("/")}"
    }

    /**
     * Security error occurred (e.g., keystore access failure, tampered data).
     * Force logout for security.
     */
    data class SecurityError(val message: String) : StartupState() {
        override val screenRoute: String = AuthenticationScreens.Login.screenRoute
        override val logMessage: String = "Security error: $message → Login"
    }
}
