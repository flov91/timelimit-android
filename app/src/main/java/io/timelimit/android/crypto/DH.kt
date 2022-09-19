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

import io.timelimit.android.extensions.base64
import io.timelimit.android.sync.network.ServerDhKey
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DHHandshake(
    val keyVersion: String, val otherPublicKey: ByteArray,
    val ownPublicKey: ByteArray, val sharedSecret: ByteArray
) {
    companion object {
        private const val SHARED_SECRET_LENGTH = 32
        private const val AES_KEY_SIZE = 16
        private const val AES_AUTH_TAG_BITS = 128

        fun fromServerKey(serverDhKey: ServerDhKey): DHHandshake {
            val otherPublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(serverDhKey.key))

            val ownKeypair = KeyPairGenerator.getInstance("EC").let {
                it.initialize(ECGenParameterSpec("secp256r1"))
                it.genKeyPair()
            }

            val ownPublicKey = ownKeypair.public.encoded

            val sharedSecret = KeyAgreement.getInstance("ECDH").let {
                it.init(ownKeypair.private)
                it.doPhase(otherPublicKey, true)
                it.generateSecret()
            }

            return DHHandshake(
                keyVersion = serverDhKey.version,
                otherPublicKey = serverDhKey.key,
                ownPublicKey = ownPublicKey,
                sharedSecret = sharedSecret
            )
        }
    }

    init {
        if (sharedSecret.size != SHARED_SECRET_LENGTH) {
            throw IllegalArgumentException()
        }
    }

    fun encrypt(cryptData: ByteArray, authData: ByteArray): String {
        val aesKey = SecretKeySpec(sharedSecret.sliceArray(0 until AES_KEY_SIZE), "AES")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(AES_AUTH_TAG_BITS, iv))
                it.updateAAD(authData)
        }

        val encryptedData = cipher.doFinal(cryptData)

        return (iv + encryptedData).base64() + "." + ownPublicKey.base64() + "." + keyVersion
    }
}