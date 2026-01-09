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

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCase
import eu.europa.ec.authenticationlogic.usecase.WalletActivationStatus
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.startupfeature.model.StartupState
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

/**
 * Streamlined splash interactor with proper session initialization and state-based routing.
 *
 * IMPROVEMENTS:
 * - Returns StartupState for better error handling and logging
 * - Three-level state checking: Authentication → Onboarding → Local Unlock
 * - TTL-based PIN skip for hot starts
 * - Comprehensive logging for debugging startup issues
 *
 * Startup Flow:
 * 1. Wait for Supabase session to initialize (not in Initializing state)
 * 2. Check authentication status (Supabase is source of truth)
 * 3. If authenticated: Check profile completion → wallet activation → local unlock
 * 4. Return appropriate StartupState based on checks
 */
interface SplashInteractor {
    /**
     * Determine the startup state based on authentication, profile, wallet, and unlock status.
     *
     * @return StartupState containing the appropriate screen route and log message
     */
    suspend fun determineStartupState(): StartupState
}

class SplashInteractorImpl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val prefKeys: PrefKeysV2,
    private val logController: LogController,
    private val isWalletActivatedUseCase: IsWalletActivatedUseCase,
    private val isProfileCompletedUseCase: IsProfileCompletedUseCase,
    private val quickPinInteractor: QuickPinInteractor,
    private val localUnlockTracker: LocalUnlockTracker
) : SplashInteractor {

    companion object {
        private const val TAG = "SplashInteractor"
    }

    override suspend fun determineStartupState(): StartupState {
        try {
            logController.i(TAG) { "Starting startup state determination..." }

            // Level 1: Authentication Check
            val authState = checkAuthenticationState()
            if (authState != null) {
                logController.i(TAG) { authState.logMessage }
                return authState
            }

            // Level 2: Onboarding Check (Profile + WUA)
            val onboardingState = checkOnboardingState()
            if (onboardingState != null) {
                logController.i(TAG) { onboardingState.logMessage }
                return onboardingState
            }

            // Level 3: Local Unlock Check (PIN/Biometric)
            val unlockState = checkLocalUnlockState()
            logController.i(TAG) { unlockState.logMessage }
            return unlockState

        } catch (e: SecurityException) {
            logController.e(TAG, e)
            val state = StartupState.SecurityError(e.message ?: "Security error")
            logController.w(TAG) { state.logMessage }
            return state
        } catch (e: Exception) {
            logController.e(TAG, e)
            val state = StartupState.SecurityError("Unexpected error: ${e.message}")
            logController.w(TAG) { state.logMessage }
            return state
        }
    }

    /**
     * Level 1: Check authentication status.
     *
     * @return StartupState if not authenticated, null if authenticated
     */
    private suspend fun checkAuthenticationState(): StartupState? {
        logController.d(TAG, "Level 1: Checking authentication status...")

        val sessionStatus = waitForSessionInitialization()
        logController.d(TAG, "Session status: ${sessionStatus::class.simpleName}")

        if (sessionStatus !is SessionStatus.Authenticated) {
            return StartupState.NotAuthenticated
        }

        logController.d(TAG, "User is authenticated, proceeding to onboarding check")
        return null // Continue to next level
    }

    /**
     * Level 2: Check onboarding status (profile + wallet activation).
     *
     * @return StartupState if onboarding incomplete, null if complete
     */
    private suspend fun checkOnboardingState(): StartupState? {
        logController.d(TAG, "Level 2: Checking onboarding status...")

        // Check profile completion
        val profileCompleted = isProfileCompletedUseCase()
        logController.d(TAG, "Profile completed: $profileCompleted")

        if (!profileCompleted) {
            return StartupState.ProfileIncomplete
        }

        // Check wallet activation
        val walletStatus = isWalletActivatedUseCase()
        logController.d(TAG, "Wallet status: ${walletStatus::class.simpleName}")

        when (walletStatus) {
            WalletActivationStatus.Activated -> {
                logController.d(TAG, "Wallet is activated, proceeding to local unlock check")
                return null // Continue to next level
            }

            is WalletActivationStatus.NotActivated -> {
                logController.d(TAG, "Wallet not activated: ${walletStatus.reason}")

                // Fix inconsistent state if needed
                if (walletStatus.localFlagSet && !walletStatus.privateKeyExists) {
                    logController.w(TAG) { "Inconsistent state: Clearing stale activation flag" }
                    try {
                        prefKeys.setWalletActivated(false)
                    } catch (e: Exception) {
                        logController.e(TAG, e)
                    }
                }

                return StartupState.WalletNotActivated(walletStatus.reason)
            }
        }
    }

    /**
     * Level 3: Check local unlock status (PIN/biometric).
     *
     * This determines whether the user needs to verify their PIN or can go directly to dashboard.
     *
     * @return StartupState for the appropriate screen
     */
    private suspend fun checkLocalUnlockState(): StartupState {
        logController.d(TAG, "Level 3: Checking local unlock status...")

        // Check if PIN exists
        val hasPin = quickPinInteractor.hasPin()
        logController.d(TAG, "Has PIN: $hasPin")

        if (!hasPin) {
            // PIN not set - force PIN creation (mandatory after wallet activation)
            return StartupState.PinNotSet
        }

        // Check if user is already unlocked (within TTL)
        val isUnlocked = localUnlockTracker.isUnlocked()
        logController.d(TAG, "Is unlocked (within TTL): $isUnlocked")

        if (isUnlocked) {
            // User is within TTL - go directly to dashboard (hot start)
            return StartupState.Ready
        }

        // User needs to verify PIN (warm start)
        return StartupState.PinVerificationRequired
    }

    /**
     * Wait for Supabase session to finish initializing.
     *
     * This prevents race conditions by ensuring session state
     * is fully determined before we make routing decisions.
     *
     * Supabase auto-restores sessions on app start, but this happens
     * asynchronously. We must wait for it to complete.
     */
    private suspend fun waitForSessionInitialization(): SessionStatus {
        return try {
            logController.d(TAG, "Waiting for session initialization...")
            // Wait for first non-initializing status
            supabaseAuthRepository.observeAuthState()
                .first { status -> status !is SessionStatus.Initializing }
        } catch (e: Exception) {
            logController.e(TAG, e)
            logController.w(TAG) { "Session initialization failed, defaulting to not authenticated" }
            // Default to not authenticated on error (not an explicit sign-out)
            SessionStatus.NotAuthenticated(isSignOut = false)
        }
    }
}
