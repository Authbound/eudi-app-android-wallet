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

import com.google.gson.Gson
import eu.europa.ec.uilogic.serializer.UiSerializable
import eu.europa.ec.uilogic.serializer.UiSerializableParser

/**
 * Data extracted from a passport's NFC chip (DG1 and DG2).
 *
 * Contains the MRZ data from DG1 and the face image from DG2.
 * This data is used for verification against the liveness check.
 */
data class PassportData(
    /**
     * Face image bytes (JPEG format) extracted from DG2.
     */
    val faceImageBytes: ByteArray,

    /**
     * Raw MRZ data from DG1, Base64 encoded for transmission.
     */
    val mrzRaw: String,

    /**
     * Passport expiry date in YYYY-MM-DD format.
     */
    val passportExpiry: String,

    /**
     * First name extracted from MRZ.
     */
    val firstName: String,

    /**
     * Last name extracted from MRZ.
     */
    val lastName: String,

    /**
     * Date of birth in YYYY-MM-DD format.
     */
    val dateOfBirth: String,

    /**
     * Document number from MRZ.
     */
    val documentNumber: String,

    /**
     * Nationality code (3-letter ISO country code).
     */
    val nationality: String
) {
    /**
     * Returns the MRZ data as Base64 encoded string for API transmission.
     */
    fun getMrzBase64(): String = mrzRaw

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PassportData

        if (!faceImageBytes.contentEquals(other.faceImageBytes)) return false
        if (mrzRaw != other.mrzRaw) return false
        if (passportExpiry != other.passportExpiry) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = faceImageBytes.contentHashCode()
        result = 31 * result + mrzRaw.hashCode()
        result = 31 * result + passportExpiry.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        return result
    }
}

/**
 * MRZ configuration for passport NFC reading.
 *
 * Contains the data entered by the user that's required to
 * establish Basic Access Control (BAC) with the passport chip.
 */
data class MrzConfig(
    /**
     * Document number from the passport.
     */
    val documentNumber: String,

    /**
     * Date of birth in YYMMDD format.
     */
    val dateOfBirth: String,

    /**
     * Passport expiry date in YYMMDD format.
     */
    val expiryDate: String
) : UiSerializable {
    companion object Parser : UiSerializableParser {
        override val serializedKeyName = "mrzConfig"
        override fun provideParser(): Gson = Gson()

        /**
         * Parses a date string from YYYY-MM-DD to YYMMDD format.
         */
        fun formatDateForMrz(date: String): String {
            // Input: YYYY-MM-DD, Output: YYMMDD
            return date.takeLast(8).replace("-", "").takeLast(6)
        }
    }
}
