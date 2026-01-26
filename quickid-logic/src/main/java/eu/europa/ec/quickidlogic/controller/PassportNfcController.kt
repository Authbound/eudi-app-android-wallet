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

package eu.europa.ec.quickidlogic.controller

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.util.Base64
import eu.europa.ec.quickidlogic.model.MrzConfig
import eu.europa.ec.quickidlogic.model.PassportData
import eu.europa.ec.quickidlogic.util.MrzDateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Partial states for passport NFC reading progress.
 */
sealed class PassportReadPartialState {
    /**
     * Waiting for user to place phone on passport.
     */
    data object WaitingForTag : PassportReadPartialState()

    /**
     * Authenticating with passport chip (BAC/PACE).
     */
    data object Authenticating : PassportReadPartialState()

    /**
     * Reading data from passport chip.
     */
    data class ReadingProgress(val step: String, val progress: Int) : PassportReadPartialState()

    /**
     * Successfully read passport data.
     */
    data class Success(val passportData: PassportData) : PassportReadPartialState()

    /**
     * Failed to read passport data.
     */
    data class Failure(
        val error: String,
        val errorCode: PassportReadErrorCode,
        val isRecoverable: Boolean
    ) : PassportReadPartialState()
}

/**
 * Error codes for passport reading failures.
 */
enum class PassportReadErrorCode {
    /** NFC tag lost during reading */
    TAG_LOST,
    /** Authentication failed (wrong MRZ data) */
    BAC_FAILED,
    /** PACE authentication failed */
    PACE_FAILED,
    /** Failed to read DG1 (MRZ data) */
    DG1_READ_FAILED,
    /** Failed to read DG2 (face image) */
    DG2_READ_FAILED,
    /** No face image found in passport */
    NO_FACE_IMAGE,
    /** Generic read error */
    READ_ERROR,
    /** NFC not available on device */
    NFC_NOT_AVAILABLE,
    /** Unknown error */
    UNKNOWN
}

/**
 * Controller for reading passport data via NFC using JMRTD.
 *
 * Handles Basic Access Control (BAC) authentication and reads
 * DG1 (MRZ data) and DG2 (face image) from the passport chip.
 */
interface PassportNfcController {
    /**
     * Reads passport data from an NFC tag.
     *
     * @param tag The NFC tag discovered
     * @param mrzConfig The MRZ configuration for BAC authentication
     * @return Flow emitting progress states and final result
     */
    fun readPassport(tag: Tag, mrzConfig: MrzConfig): Flow<PassportReadPartialState>
}

class PassportNfcControllerImpl : PassportNfcController {

    companion object {
        private const val TAG = "PassportNfcController"

        // Industry standard timeouts for passport NFC reading
        // Passport chips can be slow, especially for biometric data
        private const val ISO_DEP_TIMEOUT_MS = 20000 // 20 seconds

        // Maximum retry attempts for transient NFC errors
        private const val MAX_AUTH_RETRIES = 2

        // =======================================================
        // DEBUG: Manual MRZ override for testing
        // These are the VERIFIED values from the physical passport
        // =======================================================
        private val DEBUG_DOCUMENT_NUMBER: String? = "FP3809264"
        private val DEBUG_DATE_OF_BIRTH: String? = "911024"
        private val DEBUG_DATE_OF_EXPIRY: String? = "240813"
        // =======================================================
    }

    override fun readPassport(tag: Tag, mrzConfig: MrzConfig): Flow<PassportReadPartialState> = flow {
        android.util.Log.d(TAG, "Starting passport read with MRZ config:")
        android.util.Log.d(TAG, "  Document number: '${mrzConfig.documentNumber}' (${mrzConfig.documentNumber.length} chars)")
        android.util.Log.d(TAG, "  Date of birth: '${mrzConfig.dateOfBirth}' (${mrzConfig.dateOfBirth.length} chars)")
        android.util.Log.d(TAG, "  Expiry date: '${mrzConfig.expiryDate}' (${mrzConfig.expiryDate.length} chars)")

        emit(PassportReadPartialState.Authenticating)

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            emit(PassportReadPartialState.Failure(
                error = "NFC tag does not support IsoDep",
                errorCode = PassportReadErrorCode.NFC_NOT_AVAILABLE,
                isRecoverable = true
            ))
            return@flow
        }

        try {
            // Use longer timeout - passport chips can be slow
            isoDep.timeout = ISO_DEP_TIMEOUT_MS
            isoDep.connect()

            val cardService = CardService.getInstance(isoDep)
            cardService.open()

            val passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                true
            )
            passportService.open()

            // Create BAC key from MRZ data (or debug overrides)
            val effectiveDocNumber = DEBUG_DOCUMENT_NUMBER ?: mrzConfig.documentNumber
            val effectiveDob = DEBUG_DATE_OF_BIRTH ?: mrzConfig.dateOfBirth
            val effectiveExpiry = DEBUG_DATE_OF_EXPIRY ?: mrzConfig.expiryDate

            // DEBUG: Log exact values being used for BAC
            android.util.Log.d(TAG, "=== BAC KEY DEBUG ===")
            if (DEBUG_DOCUMENT_NUMBER != null || DEBUG_DATE_OF_BIRTH != null || DEBUG_DATE_OF_EXPIRY != null) {
                android.util.Log.w(TAG, "*** USING DEBUG OVERRIDE VALUES ***")
            }
            android.util.Log.d(TAG, "Document number: '$effectiveDocNumber'")
            android.util.Log.d(TAG, "  - Length: ${effectiveDocNumber.length}")
            android.util.Log.d(TAG, "  - Chars: ${effectiveDocNumber.map { "'$it'(${it.code})" }}")
            android.util.Log.d(TAG, "DOB: '$effectiveDob'")
            android.util.Log.d(TAG, "  - Length: ${effectiveDob.length}")
            android.util.Log.d(TAG, "  - Chars: ${effectiveDob.map { "'$it'(${it.code})" }}")
            android.util.Log.d(TAG, "Expiry: '$effectiveExpiry'")
            android.util.Log.d(TAG, "  - Length: ${effectiveExpiry.length}")
            android.util.Log.d(TAG, "  - Chars: ${effectiveExpiry.map { "'$it'(${it.code})" }}")
            android.util.Log.d(TAG, "===================")

            val bacKey: BACKeySpec = BACKey(
                effectiveDocNumber,
                effectiveDob,
                effectiveExpiry
            )

            // Try PACE first, fall back to BAC
            val authResult = performAuthentication(passportService, bacKey, mrzConfig)
            if (authResult != null) {
                emit(authResult)
                return@flow
            }

            emit(PassportReadPartialState.ReadingProgress("Reading MRZ data...", 25))

            // Read DG1 (MRZ)
            val dg1File: DG1File
            try {
                android.util.Log.d(TAG, "Reading DG1 (MRZ data)...")
                val dg1InputStream = passportService.getInputStream(PassportService.EF_DG1)
                dg1File = DG1File(dg1InputStream)
                android.util.Log.d(TAG, "DG1 read successfully")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to read DG1", e)
                val (errorMsg, errorCode) = when {
                    isTagLostException(e) -> {
                        "Connection lost while reading. Please hold steady and try again." to PassportReadErrorCode.TAG_LOST
                    }
                    isSecurityStatusError(e) -> {
                        // This shouldn't happen with BAC, but handle it anyway
                        "Authentication issue. Please try scanning the MRZ again." to PassportReadErrorCode.BAC_FAILED
                    }
                    else -> {
                        "Failed to read document data. Please try again." to PassportReadErrorCode.DG1_READ_FAILED
                    }
                }
                emit(PassportReadPartialState.Failure(
                    error = errorMsg,
                    errorCode = errorCode,
                    isRecoverable = true
                ))
                return@flow
            }

            emit(PassportReadPartialState.ReadingProgress("Reading face image...", 50))

            // Read DG2 (Face image) - this is the largest data group and takes longest
            val dg2File: DG2File
            try {
                android.util.Log.d(TAG, "Reading DG2 (face image)... This may take a few seconds.")
                val dg2InputStream = passportService.getInputStream(PassportService.EF_DG2)
                dg2File = DG2File(dg2InputStream)
                android.util.Log.d(TAG, "DG2 read successfully")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to read DG2", e)
                val (errorMsg, errorCode) = if (isTagLostException(e)) {
                    "Connection lost while reading face image. Please hold steady and try again." to PassportReadErrorCode.TAG_LOST
                } else {
                    "Failed to read face image: ${e.message}" to PassportReadErrorCode.DG2_READ_FAILED
                }
                emit(PassportReadPartialState.Failure(
                    error = errorMsg,
                    errorCode = errorCode,
                    isRecoverable = true
                ))
                return@flow
            }

            emit(PassportReadPartialState.ReadingProgress("Processing data...", 75))

            // Extract face image
            val faceInfos = dg2File.faceInfos
            if (faceInfos.isEmpty()) {
                emit(PassportReadPartialState.Failure(
                    error = "No face image found in passport",
                    errorCode = PassportReadErrorCode.NO_FACE_IMAGE,
                    isRecoverable = false
                ))
                return@flow
            }

            val faceImageInfo = faceInfos.first().faceImageInfos.firstOrNull()
            if (faceImageInfo == null) {
                emit(PassportReadPartialState.Failure(
                    error = "No face image found in passport",
                    errorCode = PassportReadErrorCode.NO_FACE_IMAGE,
                    isRecoverable = false
                ))
                return@flow
            }

            val faceImageBytes = extractFaceImageBytes(faceImageInfo)

            emit(PassportReadPartialState.ReadingProgress("Finalizing...", 90))

            // Extract MRZ info
            val mrzInfo = dg1File.mrzInfo
            val mrzRawBase64 = Base64.encodeToString(
                dg1File.encoded,
                Base64.NO_WRAP
            )

            // Parse dates with context-aware century detection
            val dateOfBirth = MrzDateParser.parseBirthDate(mrzInfo.dateOfBirth)
            val expiryDate = MrzDateParser.parseExpiryDate(mrzInfo.dateOfExpiry)

            val passportData = PassportData(
                faceImageBytes = faceImageBytes,
                mrzRaw = mrzRawBase64,
                passportExpiry = expiryDate,
                firstName = mrzInfo.secondaryIdentifier.replace("<", " ").trim(),
                lastName = mrzInfo.primaryIdentifier.replace("<", " ").trim(),
                dateOfBirth = dateOfBirth,
                documentNumber = mrzInfo.documentNumber,
                nationality = mrzInfo.nationality
            )

            emit(PassportReadPartialState.Success(passportData))

        } catch (e: CardServiceException) {
            val errorCode = when {
                e.message?.contains("tag", ignoreCase = true) == true -> PassportReadErrorCode.TAG_LOST
                else -> PassportReadErrorCode.READ_ERROR
            }
            emit(PassportReadPartialState.Failure(
                error = "Connection lost. Please hold your phone steady on the passport.",
                errorCode = errorCode,
                isRecoverable = true
            ))
        } catch (e: Exception) {
            emit(PassportReadPartialState.Failure(
                error = e.message ?: "Unknown error reading passport",
                errorCode = PassportReadErrorCode.UNKNOWN,
                isRecoverable = true
            ))
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Performs BAC authentication with the passport chip.
     *
     * Note: We use BAC (Basic Access Control) instead of PACE because:
     * 1. BAC is universally supported by all ICAO 9303 compliant passports
     * 2. PACE implementations vary between countries and can fail silently
     * 3. Once PACE is attempted, the chip won't accept BAC fallback
     *
     * @return PassportReadPartialState.Failure if authentication failed, null if successful
     */
    private fun performAuthentication(
        passportService: PassportService,
        bacKey: BACKeySpec,
        mrzConfig: MrzConfig
    ): PassportReadPartialState? {
        android.util.Log.d(TAG, "Performing BAC authentication...")

        return when (val bacResult = tryBacAuthentication(passportService, bacKey)) {
            AuthResult.SUCCESS -> {
                android.util.Log.d(TAG, "BAC authentication successful")
                null
            }
            AuthResult.TAG_LOST -> {
                android.util.Log.w(TAG, "Tag lost during BAC authentication")
                PassportReadPartialState.Failure(
                    error = "Connection lost. Please hold your phone steady on the passport and try again.",
                    errorCode = PassportReadErrorCode.TAG_LOST,
                    isRecoverable = true
                )
            }
            AuthResult.AUTH_FAILED -> {
                android.util.Log.e(TAG, "BAC authentication failed - MRZ data may not match")
                android.util.Log.e(TAG, "  Used document number: '${mrzConfig.documentNumber}'")
                android.util.Log.e(TAG, "  Used DOB: '${mrzConfig.dateOfBirth}'")
                android.util.Log.e(TAG, "  Used expiry: '${mrzConfig.expiryDate}'")
                PassportReadPartialState.Failure(
                    error = "Authentication failed. The scanned passport data may not match. Please try scanning the MRZ again.",
                    errorCode = PassportReadErrorCode.BAC_FAILED,
                    isRecoverable = true
                )
            }
        }
    }

    /**
     * Attempts BAC authentication.
     */
    private fun tryBacAuthentication(
        passportService: PassportService,
        bacKey: BACKeySpec
    ): AuthResult {
        return try {
            passportService.doBAC(bacKey)
            AuthResult.SUCCESS
        } catch (e: Exception) {
            android.util.Log.e(TAG, "BAC exception: ${e.javaClass.simpleName}: ${e.message}")
            if (isTagLostException(e)) {
                AuthResult.TAG_LOST
            } else {
                AuthResult.AUTH_FAILED
            }
        }
    }

    /**
     * Checks if an exception (or its cause chain) is a TagLostException.
     * NFC connection errors are wrapped in various exception types.
     */
    private fun isTagLostException(e: Throwable?): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is TagLostException) {
                return true
            }
            // Also check for common NFC error messages
            val message = current.message?.lowercase() ?: ""
            if (message.contains("tag was lost") ||
                message.contains("tag lost") ||
                message.contains("transceive failed") ||
                message.contains("could not tranceive")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Checks if an exception indicates a security status error (SW 6982).
     * This happens when PACE appears to succeed but doesn't establish secure messaging.
     */
    private fun isSecurityStatusError(e: Throwable?): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val message = current.message?.lowercase() ?: ""
            // SW 6982 = SECURITY STATUS NOT SATISFIED
            if (message.contains("6982") ||
                message.contains("security status not satisfied") ||
                message.contains("security status")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Result of authentication attempt.
     */
    private enum class AuthResult {
        SUCCESS,
        TAG_LOST,
        AUTH_FAILED
    }

    /**
     * Extracts face image bytes from FaceImageInfo.
     */
    private fun extractFaceImageBytes(faceImageInfo: FaceImageInfo): ByteArray {
        val imageInputStream: InputStream = faceImageInfo.imageInputStream
        val outputStream = ByteArrayOutputStream()

        when (faceImageInfo.imageDataType) {
            FaceImageInfo.IMAGE_DATA_TYPE_JPEG,
            FaceImageInfo.IMAGE_DATA_TYPE_JPEG2000 -> {
                // Direct copy for JPEG formats
                imageInputStream.copyTo(outputStream)
            }
            else -> {
                // For other formats, still copy raw bytes
                // The server will handle conversion if needed
                imageInputStream.copyTo(outputStream)
            }
        }

        return outputStream.toByteArray()
    }
}
