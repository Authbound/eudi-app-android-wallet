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

package eu.europa.ec.quickidlogic.model

import kotlinx.datetime.Instant

/**
 * Represents an active QuickID verification session.
 *
 * This holds session-scoped credentials and state for the verification flow.
 * The session is valid for a limited time and should be cleared after
 * completion or when the app is backgrounded.
 */
data class QuickIdSession(
    val sessionId: String,
    val clientToken: String,
    val expiresAt: Instant,
    val livenessSessionId: String? = null
) {
    /**
     * Checks if the session has expired.
     */
    fun isExpired(now: Instant): Boolean = now >= expiresAt

    /**
     * Returns a copy with the liveness session ID set.
     */
    fun withLivenessSession(livenessSessionId: String): QuickIdSession =
        copy(livenessSessionId = livenessSessionId)
}
