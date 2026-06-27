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

interface SecurePin : AutoCloseable {
    val length: Int
    val isCleared: Boolean
    fun getAndClear(): SecurePinData
    fun contentEquals(other: SecurePin): Boolean
    override fun close()
}

class SecurePinImpl private constructor(
    chars: CharArray,
    override val length: Int
) : SecurePin {
    init {
        require(length == chars.size) { "PIN length must match character data" }
    }

    constructor(text: CharSequence) : this(
        chars = CharArray(text.length) { index -> text[index] },
        length = text.length
    )

    private var chars: CharArray? = chars

    override val isCleared: Boolean
        @Synchronized get() = chars == null

    @Synchronized
    override fun getAndClear(): SecurePinData {
        val current: CharArray = chars ?: throw IllegalStateException("PIN has already been cleared")
        chars = null
        return SecurePinData(current)
    }

    override fun contentEquals(other: SecurePin): Boolean {
        if (length != other.length || other !is SecurePinImpl) {
            return false
        }
        val left: CharArray = snapshot()
            ?: throw IllegalStateException("PIN has already been cleared")
        val right: CharArray = other.snapshot()
            ?: throw IllegalStateException("PIN has already been cleared")
        var diff = 0
        for (index in left.indices) {
            diff = diff or (left[index].code xor right[index].code)
        }
        return diff == 0
    }

    @Synchronized
    override fun close() {
        chars?.fill(CLEARED_CHAR)
        chars = null
    }

    @Synchronized
    private fun snapshot(): CharArray? = chars

    override fun toString(): String = "SecurePin[$length chars]"

    companion object {
        fun from(chars: CharArray, length: Int): SecurePin {
            require(length in 0..chars.size) { "PIN length is out of range" }
            return SecurePinImpl(
                chars = chars.copyOfRange(0, length),
                length = length
            )
        }

        const val CLEARED_CHAR = '\u0000'
    }
}

class SecurePinData internal constructor(
    private var chars: CharArray?
) : AutoCloseable {
    val length: Int
        @Synchronized get() = chars?.size ?: 0

    @Synchronized
    fun <T> useChars(block: (CharArray) -> T): T {
        val current: CharArray = chars ?: throw IllegalStateException("PIN data has already been cleared")
        return block(current)
    }

    @Synchronized
    override fun close() {
        chars?.fill(CLEARED_CHAR)
        chars = null
    }

    override fun toString(): String = "[redacted]"

    private companion object {
        const val CLEARED_CHAR = '\u0000'
    }
}
