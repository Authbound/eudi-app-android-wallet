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

import android.util.Base64

/**
 * MRZ data extracted from passport OCR scan.
 *
 * Contains the essential data needed for NFC Basic Access Control (BAC)
 * authentication, as well as optional user-facing information.
 */
data class MrzData(
    /**
     * Document number from MRZ.
     */
    val documentNumber: String,

    /**
     * Date of birth in YYMMDD format (for NFC BAC).
     */
    val dateOfBirth: String,

    /**
     * Passport expiry date in YYMMDD format (for NFC BAC).
     */
    val dateOfExpiry: String,

    /**
     * First name extracted from passport (optional).
     */
    val firstName: String? = null,

    /**
     * Last name extracted from passport (optional).
     */
    val lastName: String? = null,

    /**
     * Issuing country code (3-letter ISO) (optional).
     */
    val issuingCountry: String? = null,

    /**
     * Base64-encoded passport photo extracted from document (optional).
     * Used for face matching before NFC read.
     */
    val passportPhotoBase64: String? = null
) {
    /**
     * Converts this MRZ data to an MrzConfig for NFC reading.
     */
    fun toMrzConfig(): MrzConfig = MrzConfig(
        documentNumber = documentNumber,
        dateOfBirth = dateOfBirth,
        expiryDate = dateOfExpiry
    )

    /**
     * Returns the passport photo as decoded bytes, or null if not available.
     */
    fun getPassportPhotoBytes(): ByteArray? {
        return passportPhotoBase64?.let {
            try {
                Base64.decode(it, Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Returns the full name from first and last name.
     */
    fun getFullName(): String? {
        val first = firstName?.trim()
        val last = lastName?.trim()
        return when {
            first != null && last != null -> "$first $last"
            first != null -> first
            last != null -> last
            else -> null
        }
    }
}
