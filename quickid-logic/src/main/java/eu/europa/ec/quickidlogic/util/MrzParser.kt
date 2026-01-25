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

package eu.europa.ec.quickidlogic.util

import android.util.Log
import eu.europa.ec.quickidlogic.model.MrzData

/**
 * Parser for Machine Readable Zone (MRZ) data from passport documents.
 *
 * Supports TD3 format (passport): 2 lines x 44 characters.
 *
 * MRZ TD3 Format:
 * ```
 * Line 1: P<GBRSMITH<<JOHN<WILLIAM<<<<<<<<<<<<<<<<<<<<<
 *         ^^ ^^^-------------------------------------
 *         |  |  |
 *         |  |  Name (last<<first<middle)
 *         |  Issuing country (3 chars)
 *         Document type (P = passport)
 *
 * Line 2: 1234567890GBR9001011M3006017<<<<<<<<<<<<<<04
 *         ^^^^^^^^^^ ^^^ ^^^^^^ ^^^^^^ ^^^^^^^^^^^^^^ ^^
 *         |        | |   |    | |    | |              |
 *         |        | |   |    | |    | Optional data  Composite check
 *         |        | |   |    | |    Expiry check digit
 *         |        | |   |    | Expiry date (YYMMDD)
 *         |        | |   |    Sex (M/F/<)
 *         |        | |   DOB check digit
 *         |        | |   Date of birth (YYMMDD)
 *         |        | Nationality (3 chars)
 *         |        Doc number check digit
 *         Document number (9 chars)
 * ```
 */
object MrzParser {

    private const val TAG = "MrzParser"
    private const val TD3_LINE_LENGTH = 44
    private const val TD3_LINE_COUNT = 2

    /**
     * Parses raw text from OCR to extract MRZ data.
     *
     * @param rawText The raw text output from ML Kit text recognition
     * @return Result containing parsed MrzData or an error
     */
    fun parse(rawText: String): Result<MrzData> {
        Log.d(TAG, "Parsing OCR text (${rawText.length} chars):\n$rawText")

        return try {
            val mrzLines = extractMrzLines(rawText)
            Log.d(TAG, "Found ${mrzLines.size} potential MRZ lines: $mrzLines")

            if (mrzLines.size < TD3_LINE_COUNT) {
                Log.w(TAG, "MRZ not found - need 2 lines, got ${mrzLines.size}")
                return Result.failure(MrzParseException("MRZ not found. Expected 2 lines."))
            }

            val line1 = normalizeMrzLine(mrzLines[0])
            val line2 = normalizeMrzLine(mrzLines[1])
            Log.d(TAG, "Normalized MRZ lines:\n  Line1: $line1\n  Line2: $line2")

            if (line1.length < TD3_LINE_LENGTH || line2.length < TD3_LINE_LENGTH) {
                Log.w(TAG, "Lines too short: line1=${line1.length}, line2=${line2.length}")
                return Result.failure(MrzParseException("Invalid MRZ format. Lines too short."))
            }

            parseTd3(line1, line2)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MRZ", e)
            Result.failure(MrzParseException("Failed to parse MRZ: ${e.message}"))
        }
    }

    /**
     * Extracts potential MRZ lines from raw OCR text.
     *
     * Looks for lines that match MRZ characteristics:
     * - 44 characters long (allowing some tolerance)
     * - Contains mostly uppercase letters, digits, and '<' filler
     */
    private fun extractMrzLines(rawText: String): List<String> {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Find lines that look like MRZ (uppercase, digits, '<' characters)
        // Allow 'O' since it might be legitimate in names/countries - we'll fix specific positions later
        val mrzPattern = Regex("^[A-Z0-9<O]{30,}$", RegexOption.IGNORE_CASE)

        val potentialMrzLines = lines
            .map { prepareLineForMatching(it) }
            .filter { mrzPattern.matches(it) && it.length >= 40 }
            .sortedByDescending { it.length }
            .take(TD3_LINE_COUNT)

        // If we found 2 lines, sort them properly (P< line first)
        return if (potentialMrzLines.size == TD3_LINE_COUNT) {
            potentialMrzLines.sortedBy { if (it.startsWith("P")) 0 else 1 }
        } else {
            potentialMrzLines
        }
    }

    /**
     * Prepares a line for MRZ pattern matching (minimal cleaning).
     */
    private fun prepareLineForMatching(line: String): String {
        return line
            .uppercase()
            .replace(" ", "")   // Remove spaces
            .replace("«", "<")  // Some OCR reads << as «
            .filter { it.isLetterOrDigit() || it == '<' }
    }

    /**
     * Cleans OCR output in numeric-only sections to fix common recognition errors.
     * Only applies O→0 conversion in positions that should be numeric.
     */
    private fun cleanNumericSection(section: String): String {
        return section.map { char ->
            when (char) {
                'O' -> '0'  // Common OCR mistake in numeric sections
                'I' -> '1'  // Common OCR mistake
                'S' -> '5'  // Sometimes misread
                'B' -> '8'  // Sometimes misread
                else -> char
            }
        }.joinToString("")
    }

    /**
     * Normalizes an MRZ line to exactly 44 characters.
     */
    private fun normalizeMrzLine(line: String): String {
        val prepared = prepareLineForMatching(line)
        return if (prepared.length >= TD3_LINE_LENGTH) {
            prepared.take(TD3_LINE_LENGTH)
        } else {
            prepared.padEnd(TD3_LINE_LENGTH, '<')
        }
    }

    /**
     * Parses TD3 (passport) MRZ format.
     */
    private fun parseTd3(line1: String, line2: String): Result<MrzData> {
        // Line 1 parsing
        val documentType = line1.substring(0, 2)
        if (!documentType.startsWith("P")) {
            return Result.failure(MrzParseException("Not a passport document type: $documentType"))
        }

        val issuingCountry = line1.substring(2, 5).replace("<", "")
        val nameField = line1.substring(5)
        val (lastName, firstName) = parseNames(nameField)

        // Line 2 parsing
        // Note: Document number can contain letters, so don't apply numeric cleaning to it
        val documentNumber = line2.substring(0, 9).replace("<", "")
        val documentNumberCheckDigit = line2[9]

        val nationality = line2.substring(10, 13).replace("<", "")

        // Dates are always numeric (YYMMDD) - apply OCR fixes for common mistakes
        val rawDateOfBirth = line2.substring(13, 19)
        val dateOfBirth = cleanNumericSection(rawDateOfBirth)
        val dobCheckDigit = cleanNumericSection(line2.substring(19, 20))[0]

        val sex = line2[20]

        val rawDateOfExpiry = line2.substring(21, 27)
        val dateOfExpiry = cleanNumericSection(rawDateOfExpiry)
        val expiryCheckDigit = cleanNumericSection(line2.substring(27, 28))[0]

        // Validate check digits
        if (!validateCheckDigit(documentNumber, documentNumberCheckDigit)) {
            return Result.failure(MrzParseException("Document number check digit validation failed"))
        }

        if (!validateCheckDigit(dateOfBirth, dobCheckDigit)) {
            return Result.failure(MrzParseException("Date of birth check digit validation failed"))
        }

        if (!validateCheckDigit(dateOfExpiry, expiryCheckDigit)) {
            return Result.failure(MrzParseException("Expiry date check digit validation failed"))
        }

        return Result.success(
            MrzData(
                documentNumber = documentNumber,
                dateOfBirth = dateOfBirth,
                dateOfExpiry = dateOfExpiry,
                firstName = firstName.takeIf { it.isNotEmpty() },
                lastName = lastName.takeIf { it.isNotEmpty() },
                issuingCountry = issuingCountry.takeIf { it.isNotEmpty() }
            )
        )
    }

    /**
     * Parses the name field from MRZ line 1.
     *
     * Format: LASTNAME<<FIRSTNAME<MIDDLE<NAMES<<<<<<
     *
     * @return Pair of (lastName, firstName)
     */
    private fun parseNames(nameField: String): Pair<String, String> {
        val parts = nameField.split("<<")
        val lastName = parts.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
        val firstName = parts.getOrNull(1)?.replace("<", " ")?.trim() ?: ""
        return Pair(lastName, firstName)
    }

    /**
     * Validates an MRZ check digit using the standard MRZ algorithm.
     *
     * The algorithm uses weights [7, 3, 1] repeating and calculates:
     * sum = Σ (char_value × weight) mod 10
     *
     * Character values:
     * - '0'-'9' = 0-9
     * - 'A'-'Z' = 10-35
     * - '<' = 0
     */
    private fun validateCheckDigit(data: String, checkDigit: Char): Boolean {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0

        for (i in data.indices) {
            val char = data[i]
            val value = when {
                char.isDigit() -> char.digitToInt()
                char.isLetter() -> char.code - 'A'.code + 10
                char == '<' -> 0
                else -> 0
            }
            sum += value * weights[i % 3]
        }

        val expectedCheckDigit = (sum % 10).digitToChar()
        return checkDigit == expectedCheckDigit || checkDigit == '<' && expectedCheckDigit == '0'
    }
}

/**
 * Exception thrown when MRZ parsing fails.
 */
class MrzParseException(message: String) : Exception(message)
