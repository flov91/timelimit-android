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

import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.crypto.CryptException
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.*
import io.timelimit.android.sync.actions.SendKeyRequestAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.ServerCryptContainer

object CryptDataHandler {
    fun process(database: Database, data: ServerCryptContainer, type: Int, deviceId: String?, categoryId: String?): Result {
        val oldItem = if (deviceId != null)
            database.cryptContainer().getCryptoMetadataSyncByDeviceId(deviceId, type)
        else if (categoryId != null)
            database.cryptContainer().getCryptoMetadataSyncByCategoryId(categoryId, type)
        else
            database.cryptContainer().getCryptoMetadataSyncByType(type)

        val isUnmodified = if (oldItem != null) {
            val oldSavedData = database.cryptContainer().getData(oldItem.cryptContainerId)

            oldSavedData != null && oldSavedData.encryptedData.contentEquals(data.data)
        } else false

        val currentItem = if (oldItem == null) {
            val baseItem = CryptContainerMetadata(
                cryptContainerId = 0,
                deviceId = deviceId,
                categoryId = categoryId,
                type = type,
                serverVersion = data.version,
                currentGeneration = 0,
                currentGenerationFirstTimestamp = System.currentTimeMillis(),
                currentGenerationKey = null,
                nextCounter = 0,
                status = CryptContainerMetadata.ProcessingStatus.MissingKey
            )

            val cryptContainerId = database.cryptContainer().insertMetadata(baseItem)

            baseItem.copy(cryptContainerId = cryptContainerId)
        } else oldItem.copy(serverVersion = data.version)

        if (deviceId == database.config().getOwnDeviceIdSync()) {
            val updatedItem = if (isUnmodified)
                currentItem
            else
                // this tells the sync logic to not use the existing encrypted data and start a new generation
                // there is no need to actually save the data from the server because it is not used at all
                // the DecryptProcessor ignores data for the device itself
                currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.Unprocessed)

            database.cryptContainer().updateMetadata(updatedItem)

            return Result(didCreateKeyRequests = false)
        }

        if (!isUnmodified) {
            CryptContainerData(
                cryptContainerId = currentItem.cryptContainerId,
                encryptedData = data.data
            ).also { item ->
                if (oldItem == null) database.cryptContainer().insertData(item)
                else database.cryptContainer().updateData(item)
            }
        }

        val header = try {
            CryptContainer.Header.read(data.data)
        } catch (ex: CryptException.InvalidContainer) {
            null
        }

        val updatedMetadata = if (isUnmodified) {
            currentItem
        } else if (header == null) {
            currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage)
        } else if (header.generation < currentItem.currentGeneration) {
            currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.DowngradeDetected)
        } else if (header.generation > currentItem.currentGeneration) {
            currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.MissingKey)
        } else if (header.counter < currentItem.nextCounter) {
            currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.DowngradeDetected)
        } else if (currentItem.currentGenerationKey == null) {
            currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.MissingKey)
        } else {
            try {
                CryptContainer.decrypt(currentItem.currentGenerationKey, data.data)

                currentItem.copy(
                    status = CryptContainerMetadata.ProcessingStatus.Unprocessed,
                    nextCounter = header.counter + 1
                )
            } catch (ex: CryptException.WrongKey) {
                currentItem.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage)
            }
        }

        database.cryptContainer().updateMetadata(updatedMetadata)

        if (updatedMetadata.status == CryptContainerMetadata.ProcessingStatus.MissingKey && header != null) {
            if (database.cryptContainerKeyRequest().byCryptContainerId(currentItem.cryptContainerId) == null) {
                val signingKey = DeviceSigningKey.getPublicAndPrivateKeySync(database)!!.let { Curve25519.getPrivateKey(it) }
                val requestKeyPair = Curve25519.generateKeyPair()
                val sequenceNumber = database.config().getNextSigningSequenceNumberAndIncrementIt()

                ApplyActionUtil.addAppLogicActionToDatabaseSync(
                    SendKeyRequestAction(
                        deviceSequenceNumber = sequenceNumber,
                        deviceId = deviceId,
                        categoryId = categoryId,
                        type = type,
                        tempKey = Curve25519.getPublicKey(requestKeyPair),
                        signature = Curve25519.sign(
                            signingKey,
                            KeyRequestSignedData(
                                deviceSequenceNumber = sequenceNumber,
                                deviceId = deviceId,
                                categoryId = categoryId,
                                type = type,
                                tempKey = Curve25519.getPublicKey(
                                    requestKeyPair
                                )
                            ).serialize()
                        )
                    ), database
                )

                database.cryptContainerKeyRequest().insert(
                    CryptContainerPendingKeyRequest(
                        cryptContainerId = currentItem.cryptContainerId,
                        requestTimeCryptContainerGeneration = header.generation,
                        requestSequenceId = sequenceNumber,
                        requestKey = requestKeyPair
                    )
                )

                return Result(didCreateKeyRequests = true)
            }
        }

        return Result(didCreateKeyRequests = false)
    }

    data class Result (val didCreateKeyRequests: Boolean) {
        fun or(other: Result) = Result(this.didCreateKeyRequests or other.didCreateKeyRequests)
    }
}