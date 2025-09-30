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
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREF_LAST_UNLOCK_AT = "authbound.last_local_unlock_at_ms"

/**
 * V2 implementation with automatic user context handling.
 * 
 * Works with PrefsControllerV2 which automatically derives user context
 * from Supabase session. No more race conditions or manual context management.
 */
class KeyGateV2Impl(
    private val prefs: PrefsControllerV2,
    private val prefKeys: PrefKeysV2,
    private val pinStorage: PinStorageController
) : KeyGate, LocalUnlockTracker {

    override suspend fun isKeyLocked(): Boolean = withContext(Dispatchers.IO) {
        // Use safe accessors that don't throw when no session exists
        val walletActivated = safe { prefKeys.isWalletActivatedSafe() } ?: false
        if (!walletActivated) return@withContext true

        val hasPin = safe { pinStorage.isPinCreated() } ?: false
        if (!hasPin) return@withContext true

        // Use safe accessor for timestamp
        val last = prefs.safeLong(PREF_LAST_UNLOCK_AT, 0L)
        if (last == 0L) return@withContext true

        val ttl = LocalUnlockTracker.DEFAULT_TTL_MS
        (System.currentTimeMillis() - last) > ttl
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

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}
