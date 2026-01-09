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


interface LocalUnlockTracker {
    /** Call this after a successful PIN/biometric to start/refresh the unlock session TTL. */
    suspend fun markUnlocked(ttlMillis: Long = DEFAULT_TTL_MS)

    /** Force-lock immediately (eg. on sign-out or manual lock). */
    suspend fun lockNow()

    /**
     * Check if user is currently unlocked (within TTL).
     *
     * @return `true` if user authenticated within the TTL period, `false` otherwise.
     *         When `true`, PIN verification can be skipped during startup.
     */
    fun isUnlocked(): Boolean

    companion object { const val DEFAULT_TTL_MS: Long = 10 * 60 * 1000L } // 10 minutes
}
