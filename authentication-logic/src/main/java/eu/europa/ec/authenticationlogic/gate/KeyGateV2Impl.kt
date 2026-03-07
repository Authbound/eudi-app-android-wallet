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

import android.util.Log
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREF_LAST_UNLOCK_AT = "authbound.last_local_unlock_at_ms"
private const val PREF_USE_BIOMETRICS_AUTH = "UseBiometricsAuth"
private const val TAG = "KeyGateV2"

/**
 * V2 implementation with automatic user context handling.
 *
 * Works with PrefsControllerV2 which automatically derives user context
 * from Supabase session. No more race conditions or manual context management.
 *
 * Includes two in-memory lock mechanisms for app security:
 * 1. [processAuthenticated] - volatile flag that resets on process death,
 *    ensuring PIN is always required after the app is killed from recents.
 * 2. [backgroundedAtMs] - tracks when the app went to background. If the app
 *    returns after [BACKGROUND_TIMEOUT_MS], the session is locked.
 */
class KeyGateV2Impl(
    private val prefs: PrefsControllerV2,
    private val prefKeys: PrefKeysV2,
    private val pinStorage: PinStorageController,
    private val logController: LogController
) : KeyGate, LocalUnlockTracker {

    /**
     * In-memory flag that tracks whether the user has authenticated in the current process.
     * Resets to `false` when the process is killed (volatile, not persisted).
     */
    @Volatile
    private var processAuthenticated = false

    /**
     * Timestamp (epoch ms) of when the app last went to background.
     * Used to enforce [BACKGROUND_TIMEOUT_MS]. Set by [onAppBackgrounded].
     */
    @Volatile
    private var backgroundedAtMs = 0L

    companion object {
        /** Lock the app if it has been in the background for longer than this duration. */
        const val BACKGROUND_TIMEOUT_MS: Long = 2 * 60 * 1000L // 2 minutes
    }

    /**
     * Called by [AppLockLifecycleObserver] when the app goes to background.
     * Records the timestamp so [isUnlocked] can calculate background duration.
     */
    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
        val biometricsEnabled: Boolean = prefs.safeBool(PREF_USE_BIOMETRICS_AUTH, false)
        if (biometricsEnabled) {
            processAuthenticated = false
            Log.d(TAG, ">>> onAppBackgrounded() — biometrics enabled, forcing relock for next foreground")
        }
        Log.d(TAG, ">>> onAppBackgrounded() called — backgroundedAtMs=$backgroundedAtMs, processAuthenticated=$processAuthenticated")
    }

    override suspend fun isKeyLocked(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, ">>> isKeyLocked() called — processAuthenticated=$processAuthenticated, backgroundedAtMs=$backgroundedAtMs")

        // CRITICAL: Check wallet activation first (must be activated for unlock to make sense)
        val walletActivated = safe { prefKeys.isWalletActivatedSafe() } ?: false
        if (!walletActivated) {
            Log.d(TAG, "<<< isKeyLocked() = true — wallet not activated")
            return@withContext true
        }

        // CRITICAL: Check if PIN exists (must exist for unlock to make sense)
        val hasPin = safe { pinStorage.retrievePin().isNotBlank() } ?: false
        if (!hasPin) {
            Log.d(TAG, "<<< isKeyLocked() = true — PIN not set")
            return@withContext true
        }

        // Process must have been authenticated (survives background, not process death)
        if (!processAuthenticated) {
            Log.d(TAG, "<<< isKeyLocked() = true — processAuthenticated is false")
            return@withContext true
        }

        // Check background timeout (app was in background too long)
        val bgAt = backgroundedAtMs
        val bgElapsed = if (bgAt > 0L) System.currentTimeMillis() - bgAt else -1L
        if (isBackgroundTimeoutExpired()) {
            Log.d(TAG, "<<< isKeyLocked() = true — background timeout expired (${bgElapsed}ms > ${BACKGROUND_TIMEOUT_MS}ms)")
            processAuthenticated = false
            return@withContext true
        }

        // Clear stale background timestamp after passing the timeout check
        backgroundedAtMs = 0L

        // Both wallet and PIN exist - check unlock timestamp
        val last = prefs.safeLong(PREF_LAST_UNLOCK_AT, 0L)
        if (last == 0L) {
            Log.d(TAG, "<<< isKeyLocked() = true — no unlock timestamp")
            return@withContext true
        }

        // Check if unlock TTL has expired
        val ttl = LocalUnlockTracker.DEFAULT_TTL_MS
        val timeSinceUnlock = System.currentTimeMillis() - last
        val isLocked = timeSinceUnlock > ttl
        Log.d(TAG, "<<< isKeyLocked() = $isLocked — timeSinceUnlock=${timeSinceUnlock}ms, TTL=${ttl}ms")

        return@withContext isLocked
    }

    override suspend fun markUnlocked(ttlMillis: Long): Unit = withContext(Dispatchers.IO) {
        // This will only work if user is authenticated (throws otherwise)
        // That's correct behavior - you can't unlock if not authenticated
        val now = System.currentTimeMillis()
        safe { prefs.setLong(PREF_LAST_UNLOCK_AT, now) }
        processAuthenticated = true
        backgroundedAtMs = 0L
        Log.d(TAG, ">>> markUnlocked() — timestamp=$now, processAuthenticated=true, backgroundedAtMs=0")
        Unit
    }

    override suspend fun lockNow(): Unit = withContext(Dispatchers.IO) {
        safe { prefs.setLong(PREF_LAST_UNLOCK_AT, 0L) }
        processAuthenticated = false
        Log.d(TAG, ">>> lockNow() — cleared timestamp, processAuthenticated=false")
        Unit
    }

    /**
     * Check if user is currently unlocked (within TTL).
     *
     * This is a synchronous check using safe accessors for use in startup flow.
     * Returns false if:
     * - Process has not been authenticated (cold start / process killed)
     * - App was in background longer than [BACKGROUND_TIMEOUT_MS]
     * - No unlock timestamp recorded
     * - TTL has expired
     * - Error reading timestamp
     */
    override fun isUnlocked(): Boolean {
        val now = System.currentTimeMillis()
        val bgAt = backgroundedAtMs
        val bgElapsed = if (bgAt > 0L) now - bgAt else -1L

        Log.d(TAG, ">>> isUnlocked() called — processAuthenticated=$processAuthenticated, backgroundedAtMs=$bgAt, bgElapsedMs=$bgElapsed, TIMEOUT=${BACKGROUND_TIMEOUT_MS}")

        // Process must have been authenticated in this session
        if (!processAuthenticated) {
            Log.d(TAG, "<<< isUnlocked() = false — processAuthenticated is false (cold start or was locked)")
            return false
        }

        // Check background timeout
        if (isBackgroundTimeoutExpired()) {
            Log.d(TAG, "<<< isUnlocked() = false — background timeout EXPIRED (bgElapsed=${bgElapsed}ms > ${BACKGROUND_TIMEOUT_MS}ms)")
            processAuthenticated = false
            return false
        }

        // Clear stale background timestamp after passing the timeout check.
        // This prevents old timestamps from falsely triggering a lock on
        // subsequent background/foreground cycles.
        if (bgAt > 0L) {
            Log.d(TAG, "    isUnlocked() — bg timeout NOT expired (${bgElapsed}ms < ${BACKGROUND_TIMEOUT_MS}ms), clearing bgAt")
        }
        backgroundedAtMs = 0L

        val last = prefs.safeLong(PREF_LAST_UNLOCK_AT, 0L)
        if (last == 0L) {
            Log.d(TAG, "<<< isUnlocked() = false — no unlock timestamp recorded")
            return false
        }

        val ttl = LocalUnlockTracker.DEFAULT_TTL_MS
        val timeSinceUnlock = now - last
        val result = timeSinceUnlock <= ttl
        Log.d(TAG, "<<< isUnlocked() = $result — lastUnlockAt=$last, timeSinceUnlock=${timeSinceUnlock}ms, TTL=${ttl}ms")
        return result
    }

    /**
     * Returns true if the app has been in the background longer than [BACKGROUND_TIMEOUT_MS].
     */
    private fun isBackgroundTimeoutExpired(): Boolean {
        val bgAt = backgroundedAtMs
        if (bgAt == 0L) return false
        return System.currentTimeMillis() - bgAt > BACKGROUND_TIMEOUT_MS
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
