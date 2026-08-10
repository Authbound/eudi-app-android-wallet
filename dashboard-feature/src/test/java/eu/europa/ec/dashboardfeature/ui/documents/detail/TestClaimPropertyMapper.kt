package eu.europa.ec.dashboardfeature.ui.documents.detail

import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.wrap.CredentialVisualType
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestClaimPropertyMapper {

    @Test
    fun `Given mixed claims for PID, When mapped, Then preferred identity keys come first`() {
        val claims = listOf(
            single("doc,age_over_18", "Age over 18", "true"),
            single("doc,family_name", "Family name", "Jack"),
            single("doc,given_name", "Given name", "Sergio"),
            single("doc,birth_date", "Date of birth", "01/01/1990"),
            single("doc,portrait", "Portrait", ""),
        )

        val result = ClaimPropertyMapper.toClaimProperties(
            claims = claims,
            visualType = CredentialVisualType.AUTHBOUND,
        )

        assertEquals(listOf("given_name", "family_name", "birth_date", "age_over_18"), result.map { it.key })
        assertTrue(result.none { it.key == "portrait" })
    }

    @Test
    fun `Given generic credential, When mapped, Then original order is preserved without image claims`() {
        val claims = listOf(
            single("doc,degree", "Degree", "MSc"),
            single("doc,issuer_code", "Issuer code", "X1"),
            single("doc,portrait", "Portrait", "abc"),
        )

        val result = ClaimPropertyMapper.toClaimProperties(
            claims = claims,
            visualType = CredentialVisualType.DIPLOMA,
        )

        assertEquals(listOf("degree", "issuer_code"), result.map { it.key })
    }

    private fun single(itemId: String, label: String, value: String): ExpandableListItemUi.SingleListItem =
        ExpandableListItemUi.SingleListItem(
            header = ListItemDataUi(
                itemId = itemId,
                mainContentData = ListItemMainContentDataUi.Text(text = value),
                overlineText = label,
            )
        )
}
