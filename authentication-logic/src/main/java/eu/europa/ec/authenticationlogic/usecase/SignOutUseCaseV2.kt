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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2

/**
 * V2 Sign out implementation with automatic user context handling.
 *
 * SECURITY IMPROVEMENT: Simpler and more reliable cleanup process.
 * PrefsControllerV2 handles user context automatically, so we don't need
 * to worry about race conditions when accessing user preferences.
 *
 * Cleanup Steps:
 * 1. Get current user ID (before session is cleared)
 * 2. Get biometric alias for keystore cleanup
 * 3. Sign out from Supabase (clears session)
 * 4. Clear user-scoped preferences
 * 5. Delete biometric keys from AndroidKeyStore
 * 6. Invalidate PrefsController cache
 * 7. Lock the wallet
 */
class SignOutUseCaseV2Impl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val prefsController: PrefsControllerV2,
    private val keystoreController: KeystoreController,
    private val prefKeys: PrefKeysV2,
    private val localUnlockTracker: LocalUnlockTracker,
    private val logController: LogController
) : SignOutUseCase {

    override suspend fun invoke() {
        try {
            logController.i("SignOutUseCaseV2") { "Starting secure sign out process..." }

            // 1. Get user ID before clearing session (for cleanup)
            val currentUser = supabaseAuthRepository.getCurrentUser()
            val userId = currentUser?.id

            if (userId == null) {
                logController.w("SignOutUseCaseV2") { "No current user found - clearing session only" }
                supabaseAuthRepository.signOut()
                return
            }

            logController.d("SignOutUseCaseV2", "Sign out for user: ${userId.take(8)}...")

            // 2. Get biometric alias before clearing preferences
            val biometricAlias = try {
                prefKeys.getBiometricAliasSafe()
            } catch (e: Exception) {
                logController.w("SignOutUseCaseV2") { "Failed to get biometric alias: ${e.message}" }
                ""
            }

            // 3. Lock wallet immediately
            try {
                localUnlockTracker.lockNow()
                logController.d("SignOutUseCaseV2", "Wallet locked")
            } catch (e: Exception) {
                logController.w("SignOutUseCaseV2") { "Failed to lock wallet: ${e.message}" }
            }

            // 4. Clear Supabase authentication session
            supabaseAuthRepository.signOut()
            logController.d("SignOutUseCaseV2", "Supabase session cleared")

            // 5. Clear all preferences for this user
            try {
                prefsController.clearUserData(userId)
                logController.d("SignOutUseCaseV2", "User-scoped preferences cleared")
            } catch (e: Exception) {
                logController.w("SignOutUseCaseV2") { "Failed to clear user preferences: ${e.message}" }
            }

            // 6. Clear biometric key from AndroidKeyStore
            if (biometricAlias.isNotEmpty()) {
                try {
                    keystoreController.deleteBiometricSecretKey(biometricAlias)
                    logController.d("SignOutUseCaseV2", "Biometric key cleared from keystore")
                } catch (e: Exception) {
                    logController.w("SignOutUseCaseV2") { "Failed to clear biometric key: ${e.message}" }
                }
            }

            // 7. Invalidate cached user ID in PrefsController
            try {
                prefsController.invalidateCache()
                logController.d("SignOutUseCaseV2", "User context cache invalidated")
            } catch (e: Exception) {
                logController.w("SignOutUseCaseV2") { "Failed to invalidate cache: ${e.message}" }
            }

            logController.i("SignOutUseCaseV2") { "Secure sign out completed successfully" }

        } catch (e: Exception) {
            logController.e("SignOutUseCaseV2", e)
            // Still attempt cleanup even if error occurred
            try {
                supabaseAuthRepository.signOut()
                prefsController.invalidateCache()
            } catch (cleanupError: Exception) {
                logController.e("SignOutUseCaseV2") { "Cleanup failed: ${cleanupError.message}" }
            }
            throw e // Rethrow to allow upstream handling
        }
    }
}
