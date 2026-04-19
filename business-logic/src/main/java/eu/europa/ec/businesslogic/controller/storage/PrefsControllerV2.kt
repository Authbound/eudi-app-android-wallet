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

package eu.europa.ec.businesslogic.controller.storage

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.europa.ec.businesslogic.config.E2eRuntimeConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * User-scoped preferences controller with automatic user context derivation.
 *
 * SECURITY IMPROVEMENT: Eliminates race conditions by automatically deriving
 * user context from Supabase session instead of requiring manual setCurrentUser() calls.
 *
 * Key Features:
 * - Single source of truth: Supabase session
 * - Automatic user ID derivation when accessing preferences
 * - Caching for performance (invalidated on sign-out)
 * - Fail-safe: Returns defaults when no session exists
 * - Thread-safe with coroutine synchronization
 *
 * Benefits:
 * - No more race conditions on app startup
 * - No manual state management required
 * - Predictable behavior across app lifecycle
 * - EUDI-ARF compliant user data isolation
 */
interface PrefsControllerV2 {
    /**
     * Check if there's an active authenticated user session.
     * This is the ONLY reliable way to check auth state.
     */
    fun hasAuthenticatedUser(): Boolean

    /**
     * Clear all data for the current authenticated user.
     * Requires an active session.
     */
    suspend fun clearCurrentUserData()

    /**
     * Clear all data for a specific user (used during sign-out).
     */
    suspend fun clearUserData(userId: String)
    suspend fun clearForUser(userId: String, key: String)

    // Standard preference accessors - automatically use current user's context
    suspend fun getString(key: String, defaultValue: String): String
    suspend fun setString(key: String, value: String)
    suspend fun getBool(key: String, defaultValue: Boolean): Boolean
    suspend fun setBool(key: String, value: Boolean)
    suspend fun getLong(key: String, defaultValue: Long): Long
    suspend fun setLong(key: String, value: Long)
    suspend fun getInt(key: String, defaultValue: Int): Int
    suspend fun setInt(key: String, value: Int)
    suspend fun contains(key: String): Boolean
    suspend fun clear(key: String)
    suspend fun clearAll()

    // Synchronous safe methods (return defaults if no session)
    fun safeBool(key: String, defaultValue: Boolean): Boolean
    fun safeString(key: String, defaultValue: String): String
    fun safeLong(key: String, defaultValue: Long): Long

    /**
     * Invalidate the cached user ID. Called automatically on sign-out.
     * You typically don't need to call this manually.
     */
    suspend fun invalidateCache()
}

class PrefsControllerV2Impl(
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
    private val supabaseClient: SupabaseClient
) : PrefsControllerV2 {

    // Cache the user ID for performance (invalidated on sign-out)
    @Volatile
    private var cachedUserId: String? = null
    private val cacheMutex = Mutex()

    companion object {
        private const val USER_PREFS_PREFIX = "authbound-wallet-user-"
    }

    /**
     * Get current user ID from Supabase session.
     * Uses caching for performance (< 1ms after first access).
     */
    private suspend fun getCurrentUserId(): String? = cacheMutex.withLock {
        // Return cached value if available
        cachedUserId?.let { return@withLock it }

        // Fetch from Supabase (single source of truth)
        val userId = try {
            supabaseClient.auth.currentSessionOrNull()?.user?.id
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Failed to get current user from Supabase: ${e.message}" }
            null
        } ?: getSyntheticUserId()

        // Cache for future accesses
        if (userId != null) {
            cachedUserId = userId
            logController.d("PrefsControllerV2", "User context cached: ${userId.take(8)}...")
        }

        return@withLock userId
    }

    /**
     * Get SharedPreferences for a specific user.
     */
    private fun getUserPrefs(userId: String): SharedPreferences {
        return resourceProvider.provideContext()
            .getSharedPreferences("$USER_PREFS_PREFIX$userId", MODE_PRIVATE)
    }

    /**
     * Get SharedPreferences for the current authenticated user.
     * Returns null if no user is authenticated.
     */
    private suspend fun getCurrentUserPrefs(): SharedPreferences? {
        val userId = getCurrentUserId() ?: return null
        return getUserPrefs(userId)
    }

    override fun hasAuthenticatedUser(): Boolean {
        return try {
            supabaseClient.auth.currentSessionOrNull() != null || E2eRuntimeConfig.isEnabled
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Failed to check auth state: ${e.message}" }
            E2eRuntimeConfig.isEnabled
        }
    }

    override suspend fun clearCurrentUserData() {
        val userId = getCurrentUserId()
        if (userId != null) {
            getUserPrefs(userId).edit { clear() }
            logController.i("PrefsControllerV2") { "Cleared all data for current user: ${userId.take(8)}..." }
        } else {
            logController.w("PrefsControllerV2") { "Cannot clear data: No authenticated user" }
        }
    }

    override suspend fun clearUserData(userId: String) {
        getUserPrefs(userId).edit { clear() }
        logController.i("PrefsControllerV2") { "Cleared all data for user: ${userId.take(8)}..." }
    }

    override suspend fun clearForUser(userId: String, key: String) {
        getUserPrefs(userId).edit { remove(key) }
    }

    override suspend fun invalidateCache() = cacheMutex.withLock {
        cachedUserId = null
        logController.d("PrefsControllerV2", "User context cache invalidated")
    }

    // ============================================================
    // Standard Preference Accessors (Suspend - Throw on No Session)
    // ============================================================

    override suspend fun setString(key: String, value: String) {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        prefs.edit { putString(key, value) }
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override suspend fun setLong(key: String, value: Long) {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        prefs.edit { putLong(key, value) }
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        return prefs.getLong(key, defaultValue)
    }

    override suspend fun setBool(key: String, value: Boolean) {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        prefs.edit { putBoolean(key, value) }
    }

    override suspend fun getBool(key: String, defaultValue: Boolean): Boolean {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        return prefs.getBoolean(key, defaultValue)
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        return prefs.getInt(key, defaultValue)
    }

    override suspend fun setInt(key: String, value: Int) {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        prefs.edit { putInt(key, value) }
    }

    override suspend fun contains(key: String): Boolean {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        return prefs.contains(key)
    }

    override suspend fun clear(key: String) {
        val prefs = getCurrentUserPrefs()
            ?: throw SecurityException("Cannot access preferences: No authenticated user session")
        prefs.edit { remove(key) }
    }

    override suspend fun clearAll() {
        clearCurrentUserData()
    }

    // ============================================================
    // Safe Accessors (Synchronous - Return Defaults on No Session)
    // ============================================================

    override fun safeBool(key: String, defaultValue: Boolean): Boolean {
        return try {
            val userId = resolveCurrentUserIdSync()
            if (userId != null) {
                getUserPrefs(userId).getBoolean(key, defaultValue)
            } else {
                logController.d("PrefsControllerV2", "No user session for safe access to key: $key, returning default: $defaultValue")
                defaultValue
            }
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Safe bool access failed for key: $key, returning default: ${e.message}" }
            defaultValue
        }
    }

    override fun safeString(key: String, defaultValue: String): String {
        return try {
            val userId = resolveCurrentUserIdSync()
            if (userId != null) {
                getUserPrefs(userId).getString(key, defaultValue) ?: defaultValue
            } else {
                logController.d("PrefsControllerV2", "No user session for safe string access: $key, returning default: $defaultValue")
                defaultValue
            }
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Safe string access failed for key: $key, returning default: ${e.message}" }
            defaultValue
        }
    }

    override fun safeLong(key: String, defaultValue: Long): Long {
        return try {
            val userId = resolveCurrentUserIdSync()
            if (userId != null) {
                getUserPrefs(userId).getLong(key, defaultValue)
            } else {
                logController.d("PrefsControllerV2", "No user session for safe long access: $key, returning default: $defaultValue")
                defaultValue
            }
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Safe long access failed for key: $key, returning default: ${e.message}" }
            defaultValue
        }
    }

    private fun resolveCurrentUserIdSync(): String? {
        return try {
            supabaseClient.auth.currentSessionOrNull()?.user?.id
        } catch (e: Exception) {
            logController.w("PrefsControllerV2") { "Failed to resolve current user synchronously: ${e.message}" }
            null
        } ?: getSyntheticUserId()
    }

    private fun getSyntheticUserId(): String? {
        return if (E2eRuntimeConfig.isEnabled) {
            E2eRuntimeConfig.SYNTHETIC_USER_ID
        } else {
            null
        }
    }
}
