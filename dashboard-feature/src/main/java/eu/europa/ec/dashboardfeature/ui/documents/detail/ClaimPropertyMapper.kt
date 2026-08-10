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

package eu.europa.ec.dashboardfeature.ui.documents.detail

import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.keyIsSignature
import eu.europa.ec.commonfeature.util.keyIsUserImage
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.wrap.ClaimPropertyUi
import eu.europa.ec.uilogic.component.wrap.CredentialVisualType
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi

/**
 * Flattens document claim trees into display rows and optionally reorders known PID /
 * Authbound identity fields so the primary facts read first. All credentials share the
 * same property list; only ranking differs for identity documents.
 */
object ClaimPropertyMapper {

    /**
     * Preferred claim-key order for PID / Authbound identity credentials. Keys not in
     * this list still appear, after the curated block, in document order.
     */
    private val PidPreferredKeyOrder: List<String> = listOf(
        DocumentJsonKeys.FIRST_NAME,
        DocumentJsonKeys.LAST_NAME,
        DocumentJsonKeys.BIRTH_DATE,
        DocumentJsonKeys.NATIONALITY,
        DocumentJsonKeys.NATIONALITIES,
        "sex",
        "gender",
        DocumentJsonKeys.EXPIRY_DATE,
        DocumentJsonKeys.ISSUING_COUNTRY,
    )

    fun toClaimProperties(
        claims: List<ExpandableListItemUi>,
        visualType: CredentialVisualType,
    ): List<ClaimPropertyUi> {
        val flat = flattenClaims(claims)
        return if (visualType.shouldCurateAsIdentity()) {
            prioritize(flat, PidPreferredKeyOrder)
        } else {
            flat
        }
    }

    private fun CredentialVisualType.shouldCurateAsIdentity(): Boolean =
        this == CredentialVisualType.PID ||
            this == CredentialVisualType.AUTHBOUND ||
            this == CredentialVisualType.MDL

    private fun flattenClaims(claims: List<ExpandableListItemUi>): List<ClaimPropertyUi> {
        val result = mutableListOf<ClaimPropertyUi>()
        fun visit(item: ExpandableListItemUi) {
            when (item) {
                is ExpandableListItemUi.SingleListItem -> {
                    toProperty(item)?.let { result.add(it) }
                }

                is ExpandableListItemUi.NestedListItem -> {
                    item.nestedItems.forEach(::visit)
                }
            }
        }
        claims.forEach(::visit)
        return result
    }

    private fun toProperty(item: ExpandableListItemUi.SingleListItem): ClaimPropertyUi? {
        val header = item.header
        val key = header.itemId.substringAfterLast(',').substringAfterLast('.').trim()
        if (keyIsUserImage(key) || keyIsSignature(key)) return null
        if (header.leadingContentData is ListItemLeadingContentDataUi.UserImage) return null
        val value = when (val content = header.mainContentData) {
            is ListItemMainContentDataUi.Text -> content.text.trim()
            is ListItemMainContentDataUi.Image -> return null
        }
        if (value.isBlank()) return null
        val label = header.overlineText?.trim()?.takeIf { it.isNotBlank() }
            ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        return ClaimPropertyUi(
            key = key.lowercase(),
            label = label,
            value = value,
        )
    }

    private fun prioritize(
        properties: List<ClaimPropertyUi>,
        preferredKeys: List<String>,
    ): List<ClaimPropertyUi> {
        val preferredLower = preferredKeys.map { it.lowercase() }
        val byKey = properties.groupBy { it.key.lowercase() }
        val ordered = mutableListOf<ClaimPropertyUi>()
        val used = mutableSetOf<String>()
        preferredLower.forEach { key ->
            byKey[key]?.forEach { property ->
                if (used.add(property.key + "\u0000" + property.value)) {
                    ordered.add(property)
                }
            }
        }
        properties.forEach { property ->
            val id = property.key + "\u0000" + property.value
            if (used.add(id)) {
                ordered.add(property)
            }
        }
        return ordered
    }
}
