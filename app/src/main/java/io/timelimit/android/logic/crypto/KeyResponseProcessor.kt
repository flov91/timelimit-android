/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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
import io.timelimit.android.crypto.CryptException
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.CryptContainerKeyResult
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.sync.actions.FinishKeyRequestAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.ServerKeyResponse
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KeyResponseProcessor {
    private const val LOG_TAG = "KeyResponseProcessor"

    fun process(
        keyResponses: List<ServerKeyResponse>,
        database: Database,
        privateKeyAndPublicKey: ByteArray
    ): Result {
        var gotNewKeys = false

        if (keyResponses.isNotEmpty()) {
            val lastServerSequence = database.config().getLastServerKeyResponseSequenceSync()
            var newServerSequence: Long? = null

            for (response in keyResponses) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got key response: $response")
                }

                if (lastServerSequence != null && response.serverResponseSequenceNumber <= lastServerSequence) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because server sequence number repeated")
                    }

                    continue
                }

                if (newServerSequence == null || newServerSequence < response.serverResponseSequenceNumber) {
                    newServerSequence = response.serverResponseSequenceNumber
                }

                val deviceKey = database.deviceKey().getSync(response.senderDeviceId)

                if (deviceKey == null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because no device key is known")
                    }

                    continue
                }

                if (
                    deviceKey.publicKey.size != Curve25519.PUBLIC_KEY_SIZE ||
                    response.tempKey.size != Curve25519.PUBLIC_KEY_SIZE ||
                    response.signature.size != Curve25519.SIGNATURE_SIZE ||
                    response.encryptedKey.size != CryptContainer.KEY_SIZE
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because cryptographic data has the wrong size")
                    }

                    continue
                }

                val requestMeta = database.cryptContainerKeyRequest().byRequestId(response.requestSequenceId)

                if (requestMeta == null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because no request was found")
                    }

                    continue
                }

                val cryptContainerMeta = database.cryptContainer().getCryptoMetadataSyncByContainerId(requestMeta.cryptContainerId)!!

                if (
                    database.cryptContainerKeyResult().countResultItems(
                        requestSequenceNumber = requestMeta.requestSequenceId,
                        deviceId = response.senderDeviceId
                    ) > 0
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because this device sent already an invalid reply")
                    }

                    continue
                }

                val requestSignedData = KeyRequestSignedData(
                    deviceSequenceNumber = requestMeta.requestSequenceId,
                    deviceId = cryptContainerMeta.deviceId,
                    categoryId = cryptContainerMeta.categoryId,
                    type = cryptContainerMeta.type,
                    tempKey = Curve25519.getPublicKey(requestMeta.requestKey)
                )

                val responseSignedData = KeyResponseSignedData(
                    request = requestSignedData,
                    senderDevicePublicKey = Curve25519.getPublicKey(privateKeyAndPublicKey),
                    encryptedKey = response.encryptedKey,
                    tempKey = response.tempKey
                )

                if (
                    !Curve25519.validateSignature(
                        deviceKey.publicKey,
                        responseSignedData.serialize(),
                        response.signature
                    )
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "ignore because the signature is invalid")
                    }

                    continue
                }

                val sharedSecret = Curve25519.sharedSecret(
                    publicKey = response.tempKey,
                    privateKey = Curve25519.getPrivateKey(requestMeta.requestKey)
                )

                val decryptedKey = Cipher.getInstance("AES/ECB/NoPadding").let {
                    // this encrypt just a single block and the integrity is checked using async crypto
                    // actually, xor could be enough for this case
                    it.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret.copyOfRange(0, CryptContainer.KEY_SIZE), "AES"))
                    it.doFinal(response.encryptedKey)
                }

                val encryptedData = database.cryptContainer().getData(cryptContainerMeta.cryptContainerId)!!

                val encryptedDataHeader = try {
                    CryptContainer.Header.read(encryptedData.encryptedData)
                } catch (ex: CryptException.InvalidContainer) {
                    null
                }

                val isKeyValid = CryptDataHandler.isKeyValid(
                    key = decryptedKey,
                    data = encryptedData.encryptedData,
                    type = cryptContainerMeta.type
                )

                if (isKeyValid && encryptedDataHeader != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "got valid key")
                    }

                    gotNewKeys = true

                    ApplyActionUtil.addAppLogicActionToDatabaseSync(
                        action = FinishKeyRequestAction(deviceSequenceNumber = requestMeta.requestSequenceId),
                        database = database
                    )

                    database.cryptContainerKeyRequest().delete(requestMeta)

                    database.cryptContainer().updateMetadata(
                        cryptContainerMeta.copy(
                            currentGeneration = encryptedDataHeader.generation,
                            currentGenerationFirstTimestamp = System.currentTimeMillis(),
                            nextCounter = encryptedDataHeader.counter + 1,
                            currentGenerationKey = decryptedKey,
                            status = CryptContainerMetadata.ProcessingStatus.Unprocessed
                        )
                    )
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "got invalid key")
                    }

                    database.cryptContainerKeyResult().insert(
                        CryptContainerKeyResult(
                            requestSequenceId = requestMeta.requestSequenceId,
                            deviceId = response.senderDeviceId,
                            status = CryptContainerKeyResult.Status.InvalidKey
                        )
                    )
                }
            }

            newServerSequence?.let {
                database.config().setLastServerKeyResponseSequenceSync(it)
            }
        }

        return Result(gotNewKeys = gotNewKeys)
    }

    data class Result(
        val gotNewKeys: Boolean
    )
}