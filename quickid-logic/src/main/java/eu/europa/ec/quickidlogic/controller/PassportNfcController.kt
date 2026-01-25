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
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
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

    override fun readPassport(tag: Tag, mrzConfig: MrzConfig): Flow<PassportReadPartialState> = flow {
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
            isoDep.timeout = 10000 // 10 second timeout
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

            // Create BAC key from MRZ data
            val bacKey: BACKeySpec = BACKey(
                mrzConfig.documentNumber,
                mrzConfig.dateOfBirth,
                mrzConfig.expiryDate
            )

            // Try PACE first, fall back to BAC
            try {
                val cardAccessFile = CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS))
                val paceInfos = cardAccessFile.securityInfos.filterIsInstance<PACEInfo>()
                if (paceInfos.isNotEmpty()) {
                    val paceInfo = paceInfos.first()
                    passportService.doPACE(
                        bacKey,
                        paceInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(paceInfo.parameterId),
                        paceInfo.parameterId
                    )
                } else {
                    passportService.doBAC(bacKey)
                }
            } catch (e: Exception) {
                // PACE failed, try BAC
                try {
                    passportService.doBAC(bacKey)
                } catch (bacException: Exception) {
                    emit(PassportReadPartialState.Failure(
                        error = "Authentication failed. Please check your document number, date of birth, and expiry date.",
                        errorCode = PassportReadErrorCode.BAC_FAILED,
                        isRecoverable = true
                    ))
                    return@flow
                }
            }

            emit(PassportReadPartialState.ReadingProgress("Reading MRZ data...", 25))

            // Read DG1 (MRZ)
            val dg1File: DG1File
            try {
                val dg1InputStream = passportService.getInputStream(PassportService.EF_DG1)
                dg1File = DG1File(dg1InputStream)
            } catch (e: Exception) {
                emit(PassportReadPartialState.Failure(
                    error = "Failed to read document data",
                    errorCode = PassportReadErrorCode.DG1_READ_FAILED,
                    isRecoverable = true
                ))
                return@flow
            }

            emit(PassportReadPartialState.ReadingProgress("Reading face image...", 50))

            // Read DG2 (Face image)
            val dg2File: DG2File
            try {
                val dg2InputStream = passportService.getInputStream(PassportService.EF_DG2)
                dg2File = DG2File(dg2InputStream)
            } catch (e: Exception) {
                emit(PassportReadPartialState.Failure(
                    error = "Failed to read face image",
                    errorCode = PassportReadErrorCode.DG2_READ_FAILED,
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
