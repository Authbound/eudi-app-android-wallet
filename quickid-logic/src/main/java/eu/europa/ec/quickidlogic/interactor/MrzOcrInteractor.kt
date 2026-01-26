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

package eu.europa.ec.quickidlogic.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.quickidlogic.controller.MrzRecognitionException
import eu.europa.ec.quickidlogic.controller.MrzTextRecognizer
import eu.europa.ec.quickidlogic.model.MrzData
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.quickidlogic.util.MrzParseException
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Partial states for MRZ OCR operations.
 */
sealed class MrzOcrPartialState {
    /**
     * OCR processing in progress.
     */
    data object Processing : MrzOcrPartialState()

    /**
     * OCR completed successfully.
     */
    data class Success(val mrzData: MrzData) : MrzOcrPartialState()

    /**
     * OCR failed.
     */
    data class Failure(
        val errorCode: String,
        val message: String,
        val isRetryable: Boolean
    ) : MrzOcrPartialState()
}

/**
 * Interactor for MRZ OCR scanning operations.
 *
 * Uses on-device ML Kit text recognition instead of server-side OCR,
 * providing faster results, offline capability, and enhanced privacy.
 */
interface MrzOcrInteractor {
    /**
     * Scans a passport image using on-device OCR to extract MRZ data.
     *
     * The result is stored in [QuickIdRepository] for subsequent NFC step.
     *
     * @param imageBytes The captured passport data page image as JPEG bytes
     * @return Flow emitting OCR processing states
     */
    fun scanPassport(imageBytes: ByteArray): Flow<MrzOcrPartialState>
}

/**
 * Implementation using Google ML Kit for on-device MRZ recognition.
 *
 * Benefits over server-side OCR:
 * - No network latency
 * - Works offline
 * - Passport image never leaves the device (privacy)
 */
class MrzOcrInteractorImpl(
    private val mrzTextRecognizer: MrzTextRecognizer,
    private val quickIdRepository: QuickIdRepository,
    private val resourceProvider: ResourceProvider
) : MrzOcrInteractor {

    companion object {
        private const val TAG = "MrzOcrInteractor"
    }

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun scanPassport(imageBytes: ByteArray): Flow<MrzOcrPartialState> = flow {
        emit(MrzOcrPartialState.Processing)

        mrzTextRecognizer.recognizeMrz(imageBytes)
            .onSuccess { mrzData ->
                android.util.Log.d(TAG, "MRZ OCR Success - storing data for NFC:")
                android.util.Log.d(TAG, "  Document number: '${mrzData.documentNumber}'")
                android.util.Log.d(TAG, "  DOB (YYMMDD): '${mrzData.dateOfBirth}'")
                android.util.Log.d(TAG, "  Expiry (YYMMDD): '${mrzData.dateOfExpiry}'")
                android.util.Log.d(TAG, "  Name: ${mrzData.firstName} ${mrzData.lastName}")
                // Store MRZ data for the NFC step
                quickIdRepository.setMrzData(mrzData)
                emit(MrzOcrPartialState.Success(mrzData))
            }
            .onFailure { error ->
                val errorCode = extractErrorCode(error)
                emit(
                    MrzOcrPartialState.Failure(
                        errorCode = errorCode,
                        message = getErrorMessage(errorCode, error.message),
                        isRetryable = isRetryableError(errorCode)
                    )
                )
            }
    }.safeAsync {
        MrzOcrPartialState.Failure(
            errorCode = "UNKNOWN",
            message = it.localizedMessage ?: genericErrorMsg,
            isRetryable = true
        )
    }

    /**
     * Extracts error code from the exception type and message.
     */
    private fun extractErrorCode(error: Throwable): String {
        return when (error) {
            is MrzRecognitionException -> when {
                error.message?.contains("No text detected") == true -> "no_text_detected"
                error.message?.contains("Failed to decode") == true -> "invalid_image"
                else -> "recognition_failed"
            }
            is MrzParseException -> when {
                error.message?.contains("MRZ not found") == true -> "mrz_not_found"
                error.message?.contains("Not a passport") == true -> "not_passport"
                error.message?.contains("check digit") == true -> "check_digit_failed"
                else -> "parse_failed"
            }
            else -> "UNKNOWN"
        }
    }

    /**
     * Returns user-friendly error message for the error code.
     */
    private fun getErrorMessage(code: String, originalMessage: String?): String {
        return when (code) {
            "no_text_detected" -> "Could not detect text. Ensure good lighting and the MRZ is fully visible."
            "invalid_image" -> "Could not process the image. Please try capturing again."
            "recognition_failed" -> "Text recognition failed. Please try again with a clearer image."
            "mrz_not_found" -> "MRZ code not found. Position the passport data page in the frame."
            "not_passport" -> "This does not appear to be a passport. Please scan the data page of your passport."
            "check_digit_failed" -> "Could not verify passport data. Please try again with a clearer image."
            "parse_failed" -> "Invalid passport data. Please try again with a clearer image."
            else -> originalMessage ?: genericErrorMsg
        }
    }

    /**
     * Determines if the error is retryable.
     * All on-device OCR errors are retryable since they're typically
     * caused by image quality issues.
     */
    private fun isRetryableError(code: String): Boolean {
        return true // All on-device errors are retryable
    }
}
