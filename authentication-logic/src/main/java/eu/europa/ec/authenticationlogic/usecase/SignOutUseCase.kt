/*
 * Copyright (c) 2024 European Commission
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

import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsController

/**
 * Secure sign out implementation that ensures complete data cleanup.
 *
 * CRITICAL SECURITY FEATURE: Prevents data leakage between user sessions by:
 * - Clearing Supabase session
 * - Clearing user-scoped preferences
 * - Clearing biometric keys from KeyStore
 * - Resetting wallet activation status
 */
interface SignOutUseCase {
    suspend operator fun invoke()
}

class SignOutUseCaseImpl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val prefsController: PrefsController,
    private val keystoreController: KeystoreController,
    private val prefKeys: PrefKeys,
    private val logController: LogController
) : SignOutUseCase {

    override suspend fun invoke() {
        try {
            logController.d("SignOutUseCase", "Starting secure sign out process...")

            // 1. Get current user's biometric key alias before clearing session
            val biometricAlias = prefKeys.getBiometricAlias()

            // 2. Clear Supabase authentication session
            supabaseAuthRepository.signOut()
            logController.d("SignOutUseCase", "Supabase session cleared")

            // 3. Clear all preferences for the current user
            prefsController.clearCurrentUserData()
            logController.d("SignOutUseCase", "User-scoped preferences cleared")

            // 4. Clear biometric key from AndroidKeyStore
            if (biometricAlias.isNotEmpty()) {
                keystoreController.deleteBiometricSecretKey(biometricAlias)
            }

            // 5. Reset user context in the preferences controller
            prefsController.setCurrentUser(null)
            logController.d("SignOutUseCase", "User context reset")

            logController.i("SignOutUseCase") { "Secure sign out completed successfully" }

        } catch (e: Exception) {
            logController.e("SignOutUseCase", e)
            // Still attempt to clear user context even if other operations failed
            prefsController.setCurrentUser(null)
            throw e // Rethrow to allow upstream handling
        }
    }
} 