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

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.europa.ec.quickidlogic.model.MrzData
import eu.europa.ec.quickidlogic.util.MrzParseException
import eu.europa.ec.quickidlogic.util.MrzParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Controller for on-device MRZ text recognition using Google ML Kit.
 *
 * This replaces the server-side OCR endpoint with on-device processing,
 * providing faster results, offline capability, and enhanced privacy
 * (passport images never leave the device).
 */
interface MrzTextRecognizer {
    /**
     * Recognizes MRZ text from a passport image and parses it to structured data.
     *
     * @param imageBytes JPEG image bytes of the passport data page
     * @return Result containing parsed MrzData or an error
     */
    suspend fun recognizeMrz(imageBytes: ByteArray): Result<MrzData>

    /**
     * Releases resources used by the text recognizer.
     * Call this when the recognizer is no longer needed.
     */
    fun close()
}

/**
 * Implementation of [MrzTextRecognizer] using Google ML Kit Text Recognition.
 */
class MrzTextRecognizerImpl : MrzTextRecognizer {

    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeMrz(imageBytes: ByteArray): Result<MrzData> {
        // Decode image bytes to Bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return Result.failure(MrzRecognitionException("Failed to decode image"))

        // Create InputImage for ML Kit
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run text recognition
        val rawText = recognizeText(inputImage)
            ?: return Result.failure(MrzRecognitionException("No text detected in image"))

        if (rawText.isBlank()) {
            return Result.failure(MrzRecognitionException("No text detected in image"))
        }

        // Parse MRZ from recognized text
        return MrzParser.parse(rawText).mapError { error ->
            when (error) {
                is MrzParseException -> MrzRecognitionException(error.message ?: "MRZ parsing failed")
                else -> MrzRecognitionException("Unexpected error: ${error.message}")
            }
        }
    }

    /**
     * Runs ML Kit text recognition on the input image.
     *
     * @return Recognized text or null if recognition failed
     */
    private suspend fun recognizeText(inputImage: InputImage): String? {
        return suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e(TAG, "Text recognition failed", exception)
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.resume(null)
                }
        }
    }

    override fun close() {
        textRecognizer.close()
    }

    companion object {
        private const val TAG = "MrzTextRecognizer"
    }
}

/**
 * Maps a Result error to a different exception type.
 */
private inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> {
    return if (isFailure) {
        Result.failure(transform(exceptionOrNull()!!))
    } else {
        this
    }
}

/**
 * Exception thrown when MRZ recognition fails.
 */
class MrzRecognitionException(message: String) : Exception(message)
