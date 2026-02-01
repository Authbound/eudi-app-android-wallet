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

import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import java.security.KeyStore

/**
 * EUDI-ARF Compliant: Robust wallet activation checker.
 * 
 * EUDI wallet is device-bound, so we must verify:
 * 1. Backend: WUA exists (future enhancement via API)
 * 2. Local: Private key exists in Android Keystore
 * 3. Local: Activation flag is set
 * 
 * ALL conditions must be met for wallet to be considered "activated".
 * 
 * ## Why This Matters:
 * - User reinstalls app → Private key lost → Wallet NOT functional
 * - User clears app data → Activation flag lost → Wallet NOT functional
 * - Backend WUA deleted → Backend check fails → Wallet NOT functional
 * 
 * This prevents users from being stuck in invalid state where they think
 * wallet is activated but it's actually unusable.
 */
interface IsWalletActivatedUseCase {
    /**
     * Comprehensively checks if wallet is activated and functional.
     * 
     * @return `WalletActivationStatus` with detailed status information
     */
    suspend operator fun invoke(): WalletActivationStatus
}

/**
 * Detailed wallet activation status with reasoning.
 */
sealed class WalletActivationStatus {
    /**
     * Wallet is fully activated and functional.
     */
    data object Activated : WalletActivationStatus()
    
    /**
     * Wallet is not activated.
     * 
     * @param reason Human-readable reason why wallet is not activated
     * @param privateKeyExists Whether WUA private key exists in keystore
     * @param localFlagSet Whether local activation flag is set
     */
    data class NotActivated(
        val reason: String,
        val privateKeyExists: Boolean,
        val localFlagSet: Boolean
    ) : WalletActivationStatus()
}

class IsWalletActivatedUseCaseImpl(
    private val prefKeys: PrefKeysV2,
    private val keystoreController: KeystoreController,
    private val logController: LogController
) : IsWalletActivatedUseCase {
    
    companion object {
        private const val WUA_KEY_ALIAS = "wua_key_alias"
        private const val TAG = "IsWalletActivatedUseCase"
    }
    
    override suspend fun invoke(): WalletActivationStatus {
        logController.d(TAG, "Checking wallet activation status...")
        
        // Step 1: Check local activation flag
        val localFlagSet = try {
            prefKeys.isWalletActivatedSafe()
        } catch (e: Exception) {
            logController.w(TAG) { "Failed to read activation flag: ${e.message}" }
            false
        }
        
        logController.d(TAG, "Local activation flag: $localFlagSet")
        
        // Step 2: Check if WUA private key exists in Android Keystore
        val privateKeyExists = checkPrivateKeyExists()
        logController.d(TAG, "WUA private key exists: $privateKeyExists")
        
        // Step 3: Determine overall status
        return when {
            localFlagSet && privateKeyExists -> {
                logController.i(TAG) { "Wallet is fully activated and functional" }
                WalletActivationStatus.Activated
            }
            !localFlagSet && !privateKeyExists -> {
                logController.i(TAG) { "Wallet not activated: No local flag and no private key" }
                WalletActivationStatus.NotActivated(
                    reason = "Wallet has never been activated on this device",
                    privateKeyExists = false,
                    localFlagSet = false
                )
            }
            localFlagSet && !privateKeyExists -> {
                logController.w(TAG) { "INCONSISTENT STATE: Activation flag set but private key missing" }
                WalletActivationStatus.NotActivated(
                    reason = "Wallet activation incomplete: Private key is missing (possible app reinstall or keystore cleared)",
                    privateKeyExists = false,
                    localFlagSet = true
                )
            }
            !localFlagSet && privateKeyExists -> {
                logController.w(TAG) { "INCONSISTENT STATE: Private key exists but activation flag not set" }
                WalletActivationStatus.NotActivated(
                    reason = "Wallet activation incomplete: Activation flag is missing (possible data cleared)",
                    privateKeyExists = true,
                    localFlagSet = false
                )
            }
            else -> {
                // Should never happen, but handle gracefully
                logController.e(TAG, Exception("Unexpected state in wallet activation check"))
                WalletActivationStatus.NotActivated(
                    reason = "Unexpected error checking wallet activation status",
                    privateKeyExists = privateKeyExists,
                    localFlagSet = localFlagSet
                )
            }
        }
    }
    
    /**
     * Checks if the WUA private key exists in Android Keystore.
     * 
     * This is critical for EUDI wallet device-binding:
     * - If key doesn't exist, wallet is non-functional
     * - Key cannot be recovered if lost
     * - User must create new WUA if key is missing
     */
    private fun checkPrivateKeyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(WUA_KEY_ALIAS)
        } catch (e: Exception) {
            logController.e(TAG, e)
            logController.w(TAG) { "Error checking private key existence: ${e.message}" }
            false
        }
    }
}
