/*
 * Copyright (c) 2026 European Commission
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
package eu.europa.ec.authenticationlogic.model

data class AccountDeletion(
    val status: String = STATUS_NONE,
    val requestedAt: String? = null,
    val scheduledFor: String? = null,
    val canCancel: Boolean = false,
) {
    val isScheduled: Boolean
        get() = status == STATUS_SCHEDULED

    val isProcessing: Boolean
        get() = status == STATUS_PROCESSING

    val isBlocked: Boolean
        get() = isScheduled || isProcessing

    companion object {
        const val STATUS_NONE: String = "none"
        const val STATUS_SCHEDULED: String = "scheduled"
        const val STATUS_PROCESSING: String = "processing"
    }
}
