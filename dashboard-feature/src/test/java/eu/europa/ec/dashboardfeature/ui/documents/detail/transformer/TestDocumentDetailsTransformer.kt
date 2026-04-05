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

package eu.europa.ec.dashboardfeature.ui.documents.detail.transformer

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class TestDocumentDetailsTransformer {

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(
            resourceProvider.getString(
                R.string.document_details_document_credentials_info_text,
                AVAILABLE_CREDENTIALS,
                TOTAL_CREDENTIALS
            )
        ).thenReturn(TITLE_TEXT)
        whenever(resourceProvider.getString(R.string.document_details_document_credentials_info_more_info_text))
            .thenReturn(MORE_INFO_TEXT)
        whenever(resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_text_subtitle))
            .thenReturn(EXPANDED_SUBTITLE_TEXT)
        whenever(resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_button_update_now_text))
            .thenReturn(UPDATE_NOW_TEXT)
        whenever(resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_button_hide_text))
            .thenReturn(HIDE_TEXT)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given isLowOnCredentials false, When createDocumentCredentialsInfoUi called, Then returned UI is collapsed with no update button`() {
        val result = DocumentDetailsTransformer.createDocumentCredentialsInfoUi(
            availableCredentials = AVAILABLE_CREDENTIALS,
            totalCredentials = TOTAL_CREDENTIALS,
            isLowOnCredentials = false,
            resourceProvider = resourceProvider
        )

        assertEquals(AVAILABLE_CREDENTIALS, result.availableCredentials)
        assertEquals(TOTAL_CREDENTIALS, result.totalCredentials)
        assertEquals(TITLE_TEXT, result.title)
        assertFalse(result.isExpanded)
        assertNotNull(result.expandedInfo)
        assertNull(result.expandedInfo?.updateNowButtonText)
        assertEquals(HIDE_TEXT, result.expandedInfo?.hideButtonText)
        assertEquals(EXPANDED_SUBTITLE_TEXT, result.expandedInfo?.subtitle)
        assertEquals(MORE_INFO_TEXT, result.collapsedInfo?.moreInfoText)
    }

    @Test
    fun `Given isLowOnCredentials true, When createDocumentCredentialsInfoUi called, Then returned UI is expanded with update button`() {
        val result = DocumentDetailsTransformer.createDocumentCredentialsInfoUi(
            availableCredentials = AVAILABLE_CREDENTIALS,
            totalCredentials = TOTAL_CREDENTIALS,
            isLowOnCredentials = true,
            resourceProvider = resourceProvider
        )

        assertTrue(result.isExpanded)
        assertEquals(UPDATE_NOW_TEXT, result.expandedInfo?.updateNowButtonText)
    }

    @Test
    fun `Given zero available credentials, When createDocumentCredentialsInfoUi called, Then count is forwarded verbatim`() {
        whenever(
            resourceProvider.getString(
                R.string.document_details_document_credentials_info_text,
                0,
                TOTAL_CREDENTIALS
            )
        ).thenReturn("0 / $TOTAL_CREDENTIALS")

        val result = DocumentDetailsTransformer.createDocumentCredentialsInfoUi(
            availableCredentials = 0,
            totalCredentials = TOTAL_CREDENTIALS,
            isLowOnCredentials = true,
            resourceProvider = resourceProvider
        )

        assertEquals(0, result.availableCredentials)
        assertEquals(TOTAL_CREDENTIALS, result.totalCredentials)
        assertEquals("0 / $TOTAL_CREDENTIALS", result.title)
    }

    private companion object {
        const val AVAILABLE_CREDENTIALS = 3
        const val TOTAL_CREDENTIALS = 10
        const val TITLE_TEXT = "3 / 10"
        const val MORE_INFO_TEXT = "More info"
        const val EXPANDED_SUBTITLE_TEXT = "Re-issue required after N uses."
        const val UPDATE_NOW_TEXT = "Issue new"
        const val HIDE_TEXT = "Hide"
    }
}
