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
 * Result of the QuickID verification process.
 */
sealed class VerificationResult {
    /**
     * Verification was successful - passport and face match confirmed.
     */
    data class Verified(
        val sessionId: String,
        val firstName: String?,
        val lastName: String?,
        val dateOfBirth: String?,
        val nationality: String?,
        val documentNumber: String?,
        val expiryDate: String?
    ) : VerificationResult()

    /**
     * Verification was rejected - face mismatch or document issue.
     */
    data class Rejected(
        val sessionId: String,
        val errorCode: String,
        val errorMessage: String,
        val isRecoverable: Boolean
    ) : VerificationResult()

    companion object {
        const val SERIALIZED_KEY_NAME = "resultConfig"
    }
}

/**
 * UI configuration for the verification result screen.
 */
data class ResultUiConfig(
    val isSuccess: Boolean,
    val sessionId: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val errorMessage: String? = null,
    val isRecoverable: Boolean = false
) : UiSerializable {
    companion object Parser : UiSerializableParser {
        override val serializedKeyName = "resultConfig"
        override fun provideParser(): Gson = Gson()
    }
}

