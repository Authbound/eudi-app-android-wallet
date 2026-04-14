/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.uilogic.extension

import android.content.Intent
import android.net.Uri
import eu.europa.ec.testlogic.base.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ContextExtensionsTest {

    @Test
    fun `create browser only intent returns direct browser target when one package is available`() {
        val intent = createBrowserOnlyIntent(
            uri = Uri.parse("https://app.authbound.io/verify/session-1?token=token-1"),
            browserPackages = listOf("com.example.browser")
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("com.example.browser", intent?.`package`)
    }

    @Test
    fun `create browser only intent returns chooser with packaged browser intents when multiple packages are available`() {
        val intent = createBrowserOnlyIntent(
            uri = Uri.parse("https://app.authbound.io/verify/session-1?token=token-1"),
            browserPackages = listOf("com.example.browser", "org.mozilla.firefox")
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_CHOOSER, intent?.action)

        val primaryIntent = intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals("com.example.browser", primaryIntent?.`package`)

        @Suppress("DEPRECATION")
        val additionalIntents = intent?.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)
        val secondaryIntent = additionalIntents?.firstOrNull() as? Intent
        assertEquals("org.mozilla.firefox", secondaryIntent?.`package`)
    }

    @Test
    fun `create browser only intent returns null when no browser package is available`() {
        val intent = createBrowserOnlyIntent(
            uri = Uri.parse("https://app.authbound.io/verify/session-1?token=token-1"),
            browserPackages = emptyList()
        )

        assertNull(intent)
    }
}
