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

/**
 * Represents the result of checking profile completion status.
 *
 * This sealed class provides a more nuanced result than a simple boolean,
 * allowing the caller to differentiate between:
 * - Definitively completed
 * - Definitively not completed
 * - Network error (with optional cached value for fallback)
 *
 * This enables resilient startup flows that can use cached values when
 * network is unavailable, preventing users from being stuck in profile
 * completion loops on poor networks.
 */
sealed class ProfileCompletionResult {
    /**
     * Profile is definitively completed (verified from cache or backend).
     */
    data object Completed : ProfileCompletionResult()

    /**
     * Profile is definitively not completed (verified from backend).
     */
    data object NotCompleted : ProfileCompletionResult()

    /**
     * Network error occurred while checking profile status.
     *
     * @param cachedValue The cached profile completion status, if available.
     *                    - `true` = Cache says profile is complete (trust it on network failure)
     *                    - `false` = Cache says profile is not complete (force completion)
     *                    - `null` = No cached value available (force completion for safety)
     */
    data class NetworkError(val cachedValue: Boolean?) : ProfileCompletionResult()
}
