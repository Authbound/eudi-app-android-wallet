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

package eu.europa.ec.commonfeature.util

import android.util.Base64
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wire format for date claims (ISO 8601) and the format all cards display dates in. */
const val CLAIM_DATE_PATTERN = "yyyy-MM-dd"
const val CARD_DISPLAY_DATE_PATTERN = "dd/MM/yyyy"

/**
 * Smallest plausible base64-encoded photo. String portrait claims below this are not
 * images (e.g. stray country codes in test fixtures) and must not count as a portrait.
 */
private const val MIN_PORTRAIT_BASE64_LENGTH = 100

/**
 * The identity fields shown on document-style credential cards (hero card, details card,
 * presentation pass), extracted directly from the issued document's claims rather than
 * scraped from rendered UI lists so every card renders from the same source of truth.
 */
data class IdentityCardData(
    val holderName: String?,
    val portraitBase64: String?,
    val nationality: String?,
    val birthDate: String?,
    val expiryDate: String?,
)

suspend fun extractIdentityCardData(
    document: IssuedDocument,
    resourceProvider: ResourceProvider,
): IdentityCardData {
    val firstName = extractValueFromDocumentOrEmpty(
        document = document,
        key = DocumentJsonKeys.FIRST_NAME
    )
    val lastName = extractValueFromDocumentOrEmpty(
        document = document,
        key = DocumentJsonKeys.LAST_NAME
    )
    val holderName = listOf(firstName, lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    val expiryDate = document.getValidUntil().getOrNull()?.let { instant ->
        SimpleDateFormat(CARD_DISPLAY_DATE_PATTERN, resourceProvider.getLocale())
            .format(Date(instant.toEpochMilli()))
    }
    return IdentityCardData(
        holderName = holderName,
        portraitBase64 = extractPortrait(document),
        nationality = extractCountryCode(document),
        birthDate = extractBirthDate(document, resourceProvider.getLocale()),
        expiryDate = expiryDate,
    )
}

private fun extractPortrait(document: IssuedDocument): String? {
    val portraitClaimValue: Any? = DocumentJsonKeys.BASE64_USER_IMAGE_KEYS
        .firstNotNullOfOrNull { key ->
            document.data.claims.firstOrNull { it.identifier == key }?.value
        }
    return when (portraitClaimValue) {
        is ByteArray -> portraitClaimValue.encodeToBase64String(Base64.URL_SAFE)
        is String -> portraitClaimValue.takeIf { it.length >= MIN_PORTRAIT_BASE64_LENGTH }
        else -> null
    }
}

private fun extractBirthDate(document: IssuedDocument, userLocale: Locale): String? {
    val rawBirthDate = extractValueFromDocumentOrEmpty(
        document = document,
        key = DocumentJsonKeys.BIRTH_DATE
    ).takeIf { it.isNotBlank() } ?: return null
    val inputFormat = SimpleDateFormat(CLAIM_DATE_PATTERN, Locale.ROOT)
    val outputFormat = SimpleDateFormat(CARD_DISPLAY_DATE_PATTERN, userLocale)
    return runCatching {
        val parsedBirthDate = inputFormat.parse(rawBirthDate) ?: return@runCatching rawBirthDate
        outputFormat.format(parsedBirthDate)
    }.getOrDefault(rawBirthDate)
}

private fun extractCountryCode(document: IssuedDocument): String? {
    return listOf(
        DocumentJsonKeys.NATIONALITY,
        DocumentJsonKeys.NATIONALITIES,
        DocumentJsonKeys.ISSUING_COUNTRY
    ).firstNotNullOfOrNull { key ->
        normalizeCountryCode(extractValueFromDocumentOrEmpty(document, key))
    }
}

/**
 * Nationality/country claims may arrive as a stringified list (e.g. "[FI]") or with
 * whitespace; reduce to the first clean uppercase ISO code and prefer alpha-3 for display.
 */
private fun normalizeCountryCode(raw: String): String? {
    val countryCode = raw
        .removePrefix("[").removeSuffix("]")
        .split(",")
        .firstOrNull()
        ?.trim()
        ?.trim('"')
        ?.uppercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    if (countryCode.length == 3) return countryCode
    if (countryCode.length != 2) return countryCode
    return runCatching {
        Locale.Builder()
            .setRegion(countryCode)
            .build()
            .isO3Country
            .uppercase(Locale.ROOT)
    }.getOrDefault(countryCode)
}
