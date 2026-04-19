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

import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import kotlinx.coroutines.withContext

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
enum class SignOutMode { Soft, Hard }

private class HardSignOutCleanupException(message: String) : SecurityException(message)

interface SignOutUseCase {
    suspend operator fun invoke(mode: SignOutMode)
}

class SignOutUseCaseImpl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val prefsController: PrefsControllerV2,
    private val keystoreController: KeystoreController,
    private val prefKeys: PrefKeysV2,
    private val pinStorageController: PinStorageController,
    private val localUnlockTracker: LocalUnlockTracker,
    private val logController: LogController
):SignOutUseCase {

   override suspend operator fun invoke(mode: SignOutMode) =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                logController.i("SignOutUseCase") { "Starting secure sign out (${mode.name})..." }
                val localCleanupFailures: MutableList<String> = mutableListOf()

                // 1) Capture context
                val user = supabaseAuthRepository.getCurrentUser()
                val userId = user?.id
                val biometricAlias = runCatching { prefKeys.getBiometricAliasSafe() }.getOrDefault("")

                // 2) Immediately lock local session
                runCatching { localUnlockTracker.lockNow() }
                    .onFailure { logController.w("SignOutUseCaseV2") { "Failed to lock wallet: ${it.message}" } }

                // 3) Local cleanup depends on mode
                when (mode) {
                    SignOutMode.Soft -> {
                        // Keep user-scoped secure data (PIN/EncUK, biometrics, wallet keys).
                        // Optional: clear volatile app caches if you have any.
                        logController.d("SignOutUseCaseV2", "Soft sign out: keeping user-secure data")
                    }
                    SignOutMode.Hard -> {
                        // Remove local trust and user data from device.
                        if (userId != null) {
                            runCatching {
                                prefsController.clearUserData(userId) // removes PIN/EncUK/verifier + counters, salts, flags
                            }.onSuccess {
                                logController.d("SignOutUseCaseV2", "User-scoped preferences cleared")
                            }.onFailure {
                                logController.w("SignOutUseCaseV2") { "Failed to clear user preferences: ${it.message}" }
                                localCleanupFailures += "Clear user-scoped wallet data"
                            }
                        }
                        if (biometricAlias.isNotEmpty()) {
                            runCatching { keystoreController.deleteBiometricSecretKey(biometricAlias) }
                                .onSuccess { logController.d("SignOutUseCaseV2", "Biometric key cleared from keystore") }
                                .onFailure {
                                    logController.w("SignOutUseCaseV2") { "Failed to clear biometric key: ${it.message}" }
                                    localCleanupFailures += "Delete biometric keystore alias"
                                }
                        }
                        runCatching { pinStorageController.clearPinData(userId) }
                            .onSuccess { logController.d("SignOutUseCaseV2", "Local auth material cleared") }
                            .onFailure {
                                logController.w("SignOutUseCaseV2") { "Failed to clear local auth material: ${it.message}" }
                                localCleanupFailures += "Clear local auth material"
                            }
                    }
                }

                // 4) Clear remote session after local destructive cleanup
                supabaseAuthRepository.signOut()
                logController.d("SignOutUseCaseV2", "Supabase session cleared")

                // 5) Invalidate in-memory user context
                runCatching { prefsController.invalidateCache() }
                    .onFailure { logController.w("SignOutUseCaseV2") { "Failed to invalidate cache: ${it.message}" } }

                if (mode == SignOutMode.Hard && localCleanupFailures.isNotEmpty()) {
                    throw HardSignOutCleanupException(
                        "Hard sign out completed with cleanup failures: ${localCleanupFailures.joinToString(", ")}"
                    )
                }

                logController.i("SignOutUseCaseV2") { "Sign out completed (${mode.name})" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Respect cancellation
                logController.d("SignOutUseCaseV2", "Cancelled")
                throw e
            } catch (e: HardSignOutCleanupException) {
                throw e
            } catch (e: Exception) {
                logController.e("SignOutUseCaseV2", e)
                // Best-effort remote sign-out & cache invalidation
                runCatching { supabaseAuthRepository.signOut() }
                runCatching { prefsController.invalidateCache() }
                throw e
            }
        }
}
