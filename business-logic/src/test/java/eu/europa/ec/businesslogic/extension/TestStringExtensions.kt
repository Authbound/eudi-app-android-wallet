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

package eu.europa.ec.businesslogic.extension

import android.util.Base64
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestStringExtensions {

    @Test
    fun `Given unpadded data URI Base64, When decoding candidates, Then original bytes are included`() {
        val value: String = "hello"
        val encoded: String = Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
            .trimEnd('=')
        val dataUri: String = "data:image/png;base64,\n$encoded"
        val decodedValues: List<String> = dataUri.decodeBase64ToByteArrays()
            .map { bytes -> bytes.toString(Charsets.UTF_8) }
        assertTrue(decodedValues.contains(value))
    }
}
