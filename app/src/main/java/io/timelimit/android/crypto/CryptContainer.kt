/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.timelimit.android.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptContainer {
    const val KEY_SIZE = 16
    private const val AUTH_TAG_BITS = 128
    private const val AUTH_TAG_BYTES = AUTH_TAG_BITS / 8

    data class EncryptParameters(val generation: Long, val counter: Long, val key: ByteArray) {
        companion object {
            fun generate() = EncryptParameters(
                key = generateKey(),
                generation = 0,
                counter = 0
            )
        }
    }

    data class Header (
        val generation: Long,
        val counter: Long,
        val iv: Int
    ) {
        companion object {
            const val SIZE = 8 + 8 + 4

            fun read(input: ByteArray): Header {
                validate(input)

                val buffer = ByteBuffer.wrap(input)

                val generation = buffer.getLong(0)
                val counter = buffer.getLong(8)
                val iv = buffer.getInt(16)

                return Header(
                    generation = generation,
                    counter = counter,
                    iv = iv
                )
            }
        }

        fun write(output: ByteBuffer) {
            output.putLong(0, generation)
            output.putLong(8, counter)
            output.putInt(16, iv)
        }
    }

    fun validate(input: ByteArray) {
        if (input.size < Header.SIZE + AUTH_TAG_BYTES) throw CryptException.InvalidContainer()
    }

    fun generateKey() = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }

    private fun buildIV(counter: Long, iv: Int): ByteArray {
        val result = ByteArray(12)

        ByteBuffer.wrap(result).also {
            it.putInt(0, iv)
            it.putLong(4, counter)
        }

        return result
    }

    private fun buildSecretKey(key: ByteArray): SecretKeySpec {
        if (key.size != KEY_SIZE) throw CryptException.InvalidKey()

        return SecretKeySpec(key, "AES")
    }

    private fun buildAAD(generation: Long): ByteArray = ByteArray(8).also { result ->
        ByteBuffer.wrap(result).putLong(0, generation)
    }

    fun decrypt(key: ByteArray, input: ByteArray): ByteArray {
        val header = Header.read(input)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(Cipher.DECRYPT_MODE, buildSecretKey(key), GCMParameterSpec(AUTH_TAG_BITS, buildIV(header.counter, header.iv)))
            it.updateAAD(buildAAD(header.generation))
        }

        try {
            return cipher.doFinal(input, Header.SIZE, input.size - Header.SIZE)
        } catch (ex: AEADBadTagException) {
            throw CryptException.WrongKey()
        }
    }

    fun encrypt(input: ByteArray, params: EncryptParameters): ByteArray {
        val iv = SecureRandom().nextInt()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(Cipher.ENCRYPT_MODE, buildSecretKey(params.key), GCMParameterSpec(AUTH_TAG_BITS, buildIV(params.counter, iv)))
            it.updateAAD(buildAAD(params.generation))
        }

        val result = ByteArray(Header.SIZE + input.size + AUTH_TAG_BYTES)
        val buffer = ByteBuffer.wrap(result)

        Header(
            iv = iv,
            counter = params.counter,
            generation = params.generation
        ).write(buffer)

        if (cipher.doFinal(input, 0, input.size, result, Header.SIZE) != input.size + AUTH_TAG_BYTES) {
            throw IllegalStateException()
        }

        return result
    }
}