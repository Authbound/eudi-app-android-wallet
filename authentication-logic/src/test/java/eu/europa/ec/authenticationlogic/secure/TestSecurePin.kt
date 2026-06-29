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

package eu.europa.ec.authenticationlogic.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSecurePin {

    @Test
    fun `Given a SecurePin, When it is consumed, Then the original pin is cleared`() {
        val pin = SecurePinImpl("123456")

        val data = pin.getAndClear()

        assertTrue(pin.isCleared)
        assertEquals(6, data.length)
        data.close()
        assertEquals(0, data.length)
        assertThrows(IllegalStateException::class.java) {
            pin.getAndClear()
        }
    }

    @Test
    fun `Given a SecurePin, When toString is called, Then the value is redacted`() {
        val pin = SecurePinImpl("123456")

        assertFalse(pin.toString().contains("123456"))

        pin.close()
    }

    @Test
    fun `Given two SecurePins, When content is compared, Then equal pins match without clearing`() {
        val first = SecurePinImpl("123456")
        val second = SecurePinImpl("123456")

        assertTrue(first.contentEquals(second))
        assertFalse(first.isCleared)
        assertFalse(second.isCleared)

        first.close()
        second.close()
    }
}
