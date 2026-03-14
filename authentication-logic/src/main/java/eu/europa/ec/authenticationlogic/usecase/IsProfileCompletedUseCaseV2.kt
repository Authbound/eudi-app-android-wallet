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

import eu.europa.ec.authenticationlogic.repository.ProfileApiException
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * V2 implementation with retry logic and resilient error handling.
 *
 * Improvements over V1:
 * - Implements 3-retry strategy with exponential backoff (500ms, 1s, 1.5s)
 * - Falls back to cached value on network failure
 * - Comprehensive logging for debugging startup issues
 *
 * Cache strategy:
 * - If cache says "completed" and network fails → Trust cache (user shouldn't be stuck)
 * - If cache says "not completed" or no cache → Force profile completion for safety
 */
class IsProfileCompletedUseCaseV2Impl(
    private val prefKeys: PrefKeysV2,
    private val profileRepository: ProfileRepository,
    private val logController: LogController
) : IsProfileCompletedUseCase {

    private val checkMutex = Mutex()

    companion object {
        private const val TAG = "IsProfileCompletedUseCase"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 500L
    }

    /**
     * Check if user's profile is completed.
     *
     * Uses cache-first strategy with backend verification and retry logic.
     * Serialized with a Mutex to prevent concurrent backend calls that
     * can trigger duplicate key errors on the server.
     * Returns true if profile is complete, false otherwise (including on network errors).
     */
    override suspend fun invoke(): Boolean {
        return checkMutex.withLock {
            when (val result = checkProfileCompletion()) {
                is ProfileCompletionResult.Completed -> true
                is ProfileCompletionResult.NotCompleted -> false
                is ProfileCompletionResult.NetworkError -> {
                    logController.w(TAG) { "Returning cached/default value due to network error" }
                    result.cachedValue ?: false
                }
                is ProfileCompletionResult.ServerError -> {
                    // Server error with cache=false: we can't verify profile exists.
                    // Route to profile completion for safety — screen handles errors gracefully.
                    logController.w(TAG) { "Server error (HTTP ${result.httpCode}), cannot verify profile — treating as incomplete" }
                    false
                }
            }
        }
    }

    /**
     * Internal implementation with full error handling.
     */
    private suspend fun checkProfileCompletion(): ProfileCompletionResult {
        logController.d(TAG, "Checking profile completion status...")

        // Fast path: Check cache first
        val cached = prefKeys.isProfileCompletedSafe()
        logController.d(TAG, "Cached profile completion: $cached")

        if (cached) {
            logController.i(TAG) { "Profile completed (from cache)" }
            return ProfileCompletionResult.Completed
        }

        // Slow path: Verify from backend with retry
        // Validate profile content inside retry block, not just null check
        logController.d(TAG, "Cache miss - verifying from backend...")
        return try {
            val networkResult = retryWithBackoff(MAX_RETRY_ATTEMPTS) {
                val result = profileRepository.getMyProfile()

                val profile = result.getOrElse { error ->
                    if (error is ProfileApiException && error.httpCode >= 400) {
                        // Throw real HTTP errors — retryWithBackoff handles retry vs propagate:
                        // 4xx → propagated immediately (no retry)
                        // 5xx → retried, re-thrown if all attempts fail
                        // httpCode=0 (client-side parsing errors) → treated as network error
                        throw error
                    }
                    // Network/IO errors or parsing errors (httpCode=0) → return null to trigger retry
                    null
                } ?: return@retryWithBackoff null

                // Only return non-null if profile is actually complete
                // This ensures we retry for incomplete/partial profiles too
                if (profile.handle?.isNotBlank() == true &&
                    profile.displayName?.isNotBlank() == true
                ) {
                    profile
                } else {
                    null // Trigger retry for incomplete profiles
                }
            }

            when {
                networkResult != null -> {
                    // Profile is guaranteed complete here (validated in retry block)
                    logController.i(TAG) { "Backend confirmed profile is complete" }
                    // Cache the result for next time
                    try {
                        prefKeys.setProfileCompleted(true)
                        logController.d(TAG, "Cached profile completion flag")
                    } catch (e: Exception) {
                        logController.w(TAG) { "Failed to cache profile completion: ${e.message}" }
                    }
                    ProfileCompletionResult.Completed
                }

                else -> {
                    // Network failed and cached == false at this point
                    // (if cached was true, we returned at line 76)
                    // Either profile doesn't exist or is incomplete
                    logController.w(TAG) { "Network failed or profile incomplete, cached value: $cached" }
                    ProfileCompletionResult.NetworkError(cachedValue = false)
                }
            }
        } catch (e: ProfileApiException) {
            ProfileCompletionResult.ServerError(httpCode = e.httpCode)
        }
    }

    /**
     * Retry a suspend block with exponential backoff.
     *
     * @param maxAttempts Maximum number of attempts before giving up
     * @param block The suspend block to execute
     * @return The result of the block, or null if all attempts failed
     */
    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int,
        block: suspend () -> T?
    ): T? {
        var lastServerError: ProfileApiException? = null
        repeat(maxAttempts) { attempt ->
            // Check for coroutine cancellation between retries
            coroutineContext.ensureActive()

            try {
                val result = block()
                if (result != null) {
                    logController.d(TAG, "Attempt ${attempt + 1}/$maxAttempts succeeded")
                    return result
                }
                lastServerError = null
            } catch (e: ProfileApiException) {
                // 4xx errors are non-retryable — propagate immediately
                if (e.httpCode in 400..499) throw e
                // 5xx errors — track and retry
                lastServerError = e
                logController.d(TAG, "Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
            } catch (e: Exception) {
                lastServerError = null
                logController.d(TAG, "Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
            }

            if (attempt < maxAttempts - 1) {
                val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1) // 500ms, 1s, 1.5s
                logController.d(TAG, "Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }
        logController.w(TAG) { "All $maxAttempts attempts failed" }
        // Re-throw server errors so caller can distinguish from network/incomplete errors
        lastServerError?.let { throw it }
        return null
    }
}
