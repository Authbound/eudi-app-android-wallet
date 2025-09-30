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

package eu.europa.ec.startupfeature.interactor

import eu.europa.ec.authenticationlogic.policy.LocalAuthPolicy
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCase
import eu.europa.ec.authenticationlogic.usecase.WalletActivationStatus
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.commonfeature.config.BiometricMode
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.commonfeature.config.OnBackNavigationConfig
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

/**
 * Streamlined splash interactor with proper session initialization.
 *
 * SECURITY IMPROVEMENT: Eliminates race conditions by:
 * - Waiting for Supabase session initialization before routing
 * - Using PrefsControllerV2 which auto-derives user context
 * - Proper error handling with fail-safe defaults
 * - Clear logging for debugging auth flow
 *
 * Startup Flow:
 * 1. Wait for Supabase session to initialize (not in Initializing state)
 * 2. Check authentication status (Supabase is source of truth)
 * 3. If authenticated: Check profile completion → wallet activation → local unlock
 * 4. Route to appropriate screen based on state
 */
interface SplashInteractorV2 {
    suspend fun getAfterSplashRoute(): String
}

class SplashInteractorV2Impl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
    private val prefKeys: PrefKeysV2,
    private val logController: LogController,
    private val localAuthPolicy: LocalAuthPolicy,
    private val isWalletActivatedUseCase: IsWalletActivatedUseCase
) : SplashInteractorV2 {

    override suspend fun getAfterSplashRoute(): String {
        try {
            logController.i("SplashInteractorV2") { "Starting splash routing..." }

            // CRITICAL: Wait for session initialization before checking anything
            // This prevents race conditions with Supabase session restoration
            logController.d("SplashInteractorV2", "Waiting for session initialization...")
            val sessionStatus = waitForSessionInitialization()
            logController.d("SplashInteractorV2", "Session status: ${sessionStatus::class.simpleName}")

            // Check authentication (Supabase is the single source of truth)
            val isAuthenticated = sessionStatus is SessionStatus.Authenticated
            logController.i("SplashInteractorV2") { "Authenticated: $isAuthenticated" }

            if (!isAuthenticated) {
                logController.i("SplashInteractorV2") { "No authenticated session → Login" }
                return AuthenticationScreens.Login.screenRoute
            }

            // User is authenticated - now check onboarding states
            // PrefsControllerV2 automatically uses the authenticated user's context

            // 1. Profile completed? (handle + displayName set)
            val profileCompleted = prefKeys.isProfileCompletedSafe()
            logController.i("SplashInteractorV2") { "Profile completed: $profileCompleted" }
            if (!profileCompleted) {
                logController.i("SplashInteractorV2") { "Profile incomplete → ProfileCompletion" }
                return AuthenticationScreens.ProfileCompletion.screenRoute
            }

            // 2. Wallet activation (WUA) completed? (EUDI-ARF: Device-bound check)
            // Check BOTH: Backend WUA exists + Private key exists in Keystore
            val walletStatus = isWalletActivatedUseCase()
            val walletActivated = walletStatus is WalletActivationStatus.Activated
            
            logController.i("SplashInteractorV2") { "Wallet activated: $walletActivated" }
            
            if (!walletActivated && walletStatus is WalletActivationStatus.NotActivated) {
                logController.i("SplashInteractorV2") { 
                    "Wallet not activated - Reason: ${walletStatus.reason} " +
                    "(Key exists: ${walletStatus.privateKeyExists}, Flag set: ${walletStatus.localFlagSet})" 
                }
                
                // Clear the activation flag if it's set but wallet is non-functional
                // This ensures clean state for re-activation
                if (walletStatus.localFlagSet && !walletStatus.privateKeyExists) {
                    logController.w("SplashInteractorV2") {
                        "Inconsistent state detected: Clearing stale activation flag"
                    }
                    try {
                        prefKeys.setWalletActivated(false)
                    } catch (e: Exception) {
                        logController.e("SplashInteractorV2", e)
                    }
                }
                
                return AuthenticationScreens.WalletSetup.screenRoute
            }

            // 3. Local unlock policy (PIN/Biometrics required?)
            val needsUnlock = localAuthPolicy.needsLocalUnlock()
            logController.i("SplashInteractorV2") { "Needs local unlock: $needsUnlock" }

            if (needsUnlock) {
                val useBiometric =
                    localAuthPolicy.isBiometricsEnabledByUser() &&
                            localAuthPolicy.isBiometricHardwareAvailable()

                logController.i("SplashInteractorV2") { "Local unlock required - Use biometric: $useBiometric" }

                return if (useBiometric) {
                    biometricUnlockConfig(onSuccess = DashboardScreens.Dashboard.screenRoute)
                } else {
                    pinUnlockConfig(onSuccess = DashboardScreens.Dashboard.screenRoute)
                }
            } else {
                // Everything checks out - go to dashboard
                logController.i("SplashInteractorV2") { "All checks passed → Dashboard" }
                return DashboardScreens.Dashboard.screenRoute
            }

        } catch (e: SecurityException) {
            // Should not happen with V2 architecture, but handle gracefully
            logController.e("SplashInteractorV2", e)
            logController.w("SplashInteractorV2") { "SecurityException during splash routing - forcing login" }
            return AuthenticationScreens.Login.screenRoute
        } catch (e: Exception) {
            // Unexpected error - force login for safety
            logController.e("SplashInteractorV2", e)
            logController.w("SplashInteractorV2") { "Unexpected error during splash routing - forcing login" }
            return AuthenticationScreens.Login.screenRoute
        }
    }

    /**
     * Wait for Supabase session to finish initializing.
     * 
     * CRITICAL: This prevents race conditions by ensuring session state
     * is fully determined before we make routing decisions.
     * 
     * Supabase auto-restores sessions on app start, but this happens
     * asynchronously. We must wait for it to complete.
     */
    private suspend fun waitForSessionInitialization(): SessionStatus {
        return try {
            // Wait for first non-initializing status
            supabaseAuthRepository.observeAuthState()
                .first { status -> status !is SessionStatus.Initializing }
        } catch (e: Exception) {
            logController.e("SplashInteractorV2", e)
            // Default to not authenticated on error (not an explicit sign-out)
            SessionStatus.NotAuthenticated(isSignOut = false)
        }
    }

    private fun pinUnlockConfig(onSuccess: String): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.QuickPin,
            arguments = generateComposableArguments(
                mapOf(
                    "onSuccessRoute" to onSuccess
                )
            )
        )
    }

    private fun biometricUnlockConfig(onSuccess: String): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.Biometric,
            arguments = generateComposableArguments(
                mapOf(
                    BiometricUiConfig.serializedKeyName to uiSerializer.toBase64(
                        BiometricUiConfig(
                            mode = BiometricMode.Login(
                                title = resourceProvider.getString(R.string.biometric_login_title),
                                subTitleWhenBiometricsEnabled = resourceProvider.getString(
                                    R.string.biometric_login_biometrics_enabled_subtitle
                                ),
                                subTitleWhenBiometricsNotEnabled = resourceProvider.getString(
                                    R.string.biometric_login_biometrics_not_enabled_subtitle
                                ),
                            ),
                            isPreAuthorization = true,
                            shouldInitializeBiometricAuthOnCreate = true,
                            onSuccessNavigation = ConfigNavigation(
                                navigationType = NavigationType.PushScreen(
                                    screen = DashboardScreens.Dashboard
                                )
                            ),
                            onBackNavigationConfig = OnBackNavigationConfig(
                                onBackNavigation = ConfigNavigation(NavigationType.Finish),
                                hasToolbarBackIcon = false
                            )
                        ),
                        BiometricUiConfig.Parser
                    ).orEmpty(),
                    "onSuccessRoute" to onSuccess
                )
            )
        )
    }
}
