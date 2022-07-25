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
package io.timelimit.android.logic.crypto

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.sync.actions.ReplyToKeyRequestAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.ServerKeyRequest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KeyRequestProcessor {
    private const val LOG_TAG = "KeyRequestProcessor"

    fun process(
        keyRequests: List<ServerKeyRequest>,
        database: Database,
        privateKeyAndPublicKey: ByteArray
    ): Result {
        var didSendReplies = false

        if (keyRequests.isNotEmpty()) {
            val lastServerSequence = database.config().getLastServerKeyRequestSequenceSync()
            var newServerSequence: Long? = null

            for (request in keyRequests) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got key request: $request")
                }

                if (lastServerSequence != null && request.serverRequestSequenceNumber <= lastServerSequence) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because server sequence number repeated")
                    }

                    continue
                }

                if (newServerSequence == null || newServerSequence < request.serverRequestSequenceNumber) {
                    newServerSequence = request.serverRequestSequenceNumber
                }

                val deviceKey = database.deviceKey().getSync(request.senderDeviceId)

                if (deviceKey == null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because no device key is known")
                    }

                    continue
                }

                if (request.senderSequenceNumber < deviceKey.nextSequenceNumber) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because client sequence number repeated")
                    }

                    continue
                }

                if (deviceKey.publicKey.size != Curve25519.PUBLIC_KEY_SIZE || request.signature.size != Curve25519.SIGNATURE_SIZE) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because cryptographic data has the wrong size")
                    }

                    continue
                }

                val requestSignedData = KeyRequestSignedData(
                    deviceSequenceNumber = request.senderSequenceNumber,
                    deviceId = request.deviceId,
                    categoryId = request.categoryId,
                    type = request.type,
                    tempKey = request.tempKey
                )

                if (
                    !Curve25519.validateSignature(
                        deviceKey.publicKey,
                        requestSignedData.serialize(),
                        request.signature
                    )
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because the signature is invalid")
                    }

                    continue
                }

                database.deviceKey().update(deviceKey.copy(nextSequenceNumber = request.senderSequenceNumber + 1))

                val metadata = if (request.deviceId != null) {
                    database.cryptContainer().getCryptoMetadataSyncByDeviceId(request.deviceId, request.type)
                } else if (request.categoryId != null) {
                    database.cryptContainer().getCryptoMetadataSyncByCategoryId(request.categoryId, request.type)
                } else {
                    database.cryptContainer().getCryptoMetadataSyncByType(request.type)
                }

                if (
                    metadata?.currentGenerationKey == null ||
                    metadata.status == CryptContainerMetadata.ProcessingStatus.MissingKey
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because the key is unknown")
                    }

                    continue
                }

                if (metadata.currentGenerationKey.size != CryptContainer.KEY_SIZE) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because the key has the wrong size")
                    }

                    continue
                }

                val selfTempKey = Curve25519.generateKeyPair()

                val sharedSecret = Curve25519.sharedSecret(
                    publicKey = request.tempKey,
                    privateKey = Curve25519.getPrivateKey(selfTempKey)
                )

                val encryptedKey = Cipher.getInstance("AES/ECB/NoPadding").let {
                    // this encrypt just a single block and the integrity is checked using async crypto
                    // actually, xor could be enough for this case
                    it.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret.copyOfRange(0, CryptContainer.KEY_SIZE), "AES"))
                    it.doFinal(metadata.currentGenerationKey)
                }

                ApplyActionUtil.addAppLogicActionToDatabaseSync(
                    action = ReplyToKeyRequestAction(
                        requestServerSequenceNumber = request.serverRequestSequenceNumber,
                        tempKey = Curve25519.getPublicKey(selfTempKey),
                        encryptedKey = encryptedKey,
                        signature = Curve25519.sign(
                            privateKey = Curve25519.getPrivateKey(privateKeyAndPublicKey),
                            message = KeyResponseSignedData(
                                request = requestSignedData,
                                senderDevicePublicKey = deviceKey.publicKey,
                                tempKey = Curve25519.getPublicKey(selfTempKey),
                                encryptedKey = encryptedKey
                            ).serialize()
                        )
                    ),
                    database = database
                )

                didSendReplies = true
            }

            newServerSequence?.let {
                database.config().setLastServerKeyRequestSequenceSync(it)
            }
        }

        return Result(
            didSendReplies = didSendReplies
        )
    }

    data class Result(val didSendReplies: Boolean)
}