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

package eu.europa.ec.authenticationlogic.gate

import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREF_LAST_UNLOCK_AT = "authbound.last_local_unlock_at_ms"
private const val TAG = "KeyGateV2"

/**
 * V2 implementation with automatic user context handling.
 *
 * Works with PrefsControllerV2 which automatically derives user context
 * from Supabase session. No more race conditions or manual context management.
 */
class KeyGateV2Impl(
    private val prefs: PrefsControllerV2,
    private val prefKeys: PrefKeysV2,
    private val pinStorage: PinStorageController,
    private val logController: LogController
) : KeyGate, LocalUnlockTracker {

    override suspend fun isKeyLocked(): Boolean = withContext(Dispatchers.IO) {
        // CRITICAL: Check wallet activation first (must be activated for unlock to make sense)
        val walletActivated = safe { prefKeys.isWalletActivatedSafe() } ?: false
        if (!walletActivated) {
            // Wallet not activated - key is considered locked
            return@withContext true
        }

        // CRITICAL: Check if PIN exists (must exist for unlock to make sense)
        val hasPin = safe { pinStorage.retrievePin().isNotBlank() } ?: false
        if (!hasPin) {
            // PIN not set - key is considered locked until PIN is created
            return@withContext true
        }

        // Both wallet and PIN exist - check unlock timestamp
        // Use safe accessor for timestamp (won't throw, returns default if unavailable)
        val last = prefs.safeLong(PREF_LAST_UNLOCK_AT, 0L)
        if (last == 0L) {
            // Never unlocked before - key is locked
            return@withContext true
        }

        // Check if unlock TTL has expired
        val ttl = LocalUnlockTracker.DEFAULT_TTL_MS
        val timeSinceUnlock = System.currentTimeMillis() - last
        val isLocked = timeSinceUnlock > ttl

        return@withContext isLocked
    }

    override suspend fun markUnlocked(ttlMillis: Long): Unit = withContext(Dispatchers.IO) {
        // This will only work if user is authenticated (throws otherwise)
        // That's correct behavior - you can't unlock if not authenticated
        safe { prefs.setLong(PREF_LAST_UNLOCK_AT, System.currentTimeMillis()) }
        Unit
    }

    override suspend fun lockNow(): Unit = withContext(Dispatchers.IO) {
        safe { prefs.setLong(PREF_LAST_UNLOCK_AT, 0L) }
        Unit
    }

    /**
     * Check if user is currently unlocked (within TTL).
     *
     * This is a synchronous check using safe accessors for use in startup flow.
     * Returns false if:
     * - No unlock timestamp recorded
     * - TTL has expired
     * - Error reading timestamp
     */
    override fun isUnlocked(): Boolean {
        val last = prefs.safeLong(PREF_LAST_UNLOCK_AT, 0L)
        if (last == 0L) {
            // Never unlocked - not unlocked
            return false
        }

        val ttl = LocalUnlockTracker.DEFAULT_TTL_MS
        val timeSinceUnlock = System.currentTimeMillis() - last
        return timeSinceUnlock <= ttl
    }

    /**
     * Execute a block safely, returning null on failure.
     *
     * Security exceptions are logged per EUDI-ARF compliance.
     * This is critical for detecting keystore tampering, encryption failures,
     * or other security-related issues.
     */
    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        // Security exceptions MUST be logged per EUDI-ARF compliance
        // These indicate potential tampering, keystore issues, or crypto failures
        logController.e(TAG, e)
        logController.w(TAG) { "Security exception in KeyGate: ${e.message}" }
        null
    } catch (e: Exception) {
        // Non-security exceptions are logged at debug level
        logController.d(TAG, "Safe accessor failed: ${e.message}")
        null
    }
}
