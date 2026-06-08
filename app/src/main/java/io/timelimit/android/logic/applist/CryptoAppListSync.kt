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
package io.timelimit.android.logic.applist

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.CryptContainerData
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.logic.ServerApiLevelInfo
import io.timelimit.android.logic.applist.data.InstalledAppsDer
import io.timelimit.android.logic.applist.data.InstalledAppsDifferenceDer
import io.timelimit.android.logic.applist.data.SavedAppsDifferenceDer
import io.timelimit.android.proto.build
import io.timelimit.android.proto.encodeDeflated
import io.timelimit.android.proto.encodeDeflatedDer
import io.timelimit.android.sync.SyncUtil
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.UpdateInstalledAppsAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.proto.applist.InstalledAppsDifferenceProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto

object CryptoAppListSync {
    private val ENCRYPT_LEGACY = Math.random() >= 0.9

    class TooLargeException(val size: Int): RuntimeException("app list is too big: $size")

    suspend fun sync(
        deviceState: DeviceState,
        database: Database,
        installed: InstalledAppsDer,
        syncUtil: SyncUtil,
        serverApiLevelInfo: ServerApiLevelInfo
    ) {
        val compressedDataSizeLimit =
            if (serverApiLevelInfo.hasLevelOrIsOffline(5)) 1024 * 512
            else 1024 * 256

        fun dispatchSync(action: AppLogicAction) {
            if (deviceState.isConnectedMode) {
                ApplyActionUtil.addAppLogicActionToDatabaseSync(action, database)
            }
        }

        fun <T> prepareEncryption(encrypted: InstalledAppsUtil.Encrypted<T>?, type: Int, forceNewGeneration: Boolean = false) = if (encrypted == null) {
            val key = CryptContainer.EncryptParameters.generate()

            val metadata = CryptContainerMetadata.buildFor(
                deviceId = deviceState.id,
                categoryId = null,
                type = type,
                params = key
            )

            CryptContainerMetadata.PrepareEncryptionResult(key, metadata, CryptContainerMetadata.PrepareEncryptionResult.Type.NewContainer)
        } else {
            if (encrypted.meta.type != type) throw IllegalStateException()

            encrypted.meta.copy(
                status = CryptContainerMetadata.ProcessingStatus.Finished
            ).prepareEncryption(forceNewGeneration)
        }

        fun throwIfTooLarge(data: ByteArray) {
            data.size.let {
                if (it > compressedDataSizeLimit) throw TooLargeException(it)
            }
        }

        val savedCrypt = InstalledAppsUtil.getEncryptedInstalledAppsFromDatabase(database, deviceState.id)

        val baseCryptConfig = prepareEncryption(
            encrypted = savedCrypt.base,
            type = CryptContainerMetadata.TYPE_APP_LIST_BASE
        )

        val diffCryptConfig = prepareEncryption(
            encrypted = savedCrypt.diff,
            type = CryptContainerMetadata.TYPE_APP_LIST_DIFF,
            forceNewGeneration = baseCryptConfig.type != CryptContainerMetadata.PrepareEncryptionResult.Type.IncrementedCounter || savedCrypt.base?.decrypted == null
        )

        val diffCrypto: InstalledAppsDifferenceDer? = if (savedCrypt.base?.decrypted == null) null
        else AppsDifferenceUtil.calculateAppsDifference(savedCrypt.base.decrypted.data, installed)

        if (
            savedCrypt.base?.decrypted == null ||
            savedCrypt.diff?.decrypted == null ||
            diffCrypto != savedCrypt.diff.decrypted.data
        ) {
            if (
                savedCrypt.base?.decrypted == null ||
                savedCrypt.diff?.decrypted == null ||
                diffCrypto == null ||
                baseCryptConfig.type != CryptContainerMetadata.PrepareEncryptionResult.Type.IncrementedCounter ||
                diffCrypto.encodedSize() >= savedCrypt.base.decrypted.data.encodedSize() / 10
            ) {
                val (baseEncrypted, diffEncrypted) = Threads.crypto.executeAndWait {
                    val baseEncrypted =
                        if (ENCRYPT_LEGACY)
                            CryptContainer.encrypt(
                                installed.toProto().encodeDeflated(),
                                baseCryptConfig.params,
                                CryptContainer.FORMAT_LEGACY
                            )
                    else
                            CryptContainer.encrypt(
                                encodeDeflatedDer { installed.derEncode(it) },
                                baseCryptConfig.params,
                                CryptContainer.FORMAT_APP_LIST_V2
                            )

                    val diffEncrypted =
                        if (ENCRYPT_LEGACY) {
                            CryptContainer.encrypt(
                                SavedAppsDifferenceProto.build(
                                    baseEncrypted,
                                    InstalledAppsDifferenceProto()
                                ).encodeDeflated(),
                                diffCryptConfig.params,
                                CryptContainer.FORMAT_LEGACY
                            )
                        } else {
                            val baseHeader = CryptContainer.Header.read(baseEncrypted)

                            val diff = SavedAppsDifferenceDer(
                                InstalledAppsDifferenceDer(),
                                baseHeader.generation,
                                baseHeader.counter
                            )

                            CryptContainer.encrypt(
                                encodeDeflatedDer { diff.derEncode(it) },
                                diffCryptConfig.params,
                                CryptContainer.FORMAT_APP_DIFF_V2
                            )
                        }

                    Pair(baseEncrypted, diffEncrypted)
                }

                throwIfTooLarge(baseEncrypted)
                throwIfTooLarge(diffEncrypted)

                Threads.database.executeAndWait {
                    if (savedCrypt.base == null) {
                        val baseId = database.cryptContainer().insertMetadata(baseCryptConfig.newMetadata)

                        database.cryptContainer().insertData(
                            CryptContainerData(
                                cryptContainerId = baseId,
                                encryptedData = baseEncrypted
                            )
                        )
                    } else {
                        database.cryptContainer().updateMetadata(baseCryptConfig.newMetadata)

                        database.cryptContainer().updateData(CryptContainerData(
                            cryptContainerId = savedCrypt.base.meta.cryptContainerId,
                            encryptedData = baseEncrypted
                        ))
                    }

                    if (savedCrypt.diff == null) {
                        val diffId = database.cryptContainer().insertMetadata(diffCryptConfig.newMetadata)

                        database.cryptContainer().insertData(
                            CryptContainerData(
                                cryptContainerId = diffId,
                                encryptedData = diffEncrypted
                            )
                        )
                    } else {
                        database.cryptContainer().updateMetadata(diffCryptConfig.newMetadata)

                        database.cryptContainer().updateData(CryptContainerData(
                            cryptContainerId = savedCrypt.diff.meta.cryptContainerId,
                            encryptedData = diffEncrypted
                        ))
                    }

                    dispatchSync(UpdateInstalledAppsAction(
                        base = baseEncrypted,
                        diff = diffEncrypted
                    ))
                }

                syncUtil.requestImportantSync()
            } else {
                val diffEncrypted =
                    if (ENCRYPT_LEGACY) {
                        CryptContainer.encrypt(
                            SavedAppsDifferenceProto.build(
                                savedCrypt.base.decrypted.header,
                                diffCrypto.toProto()
                            ).encodeDeflated(),
                            diffCryptConfig.params,
                            CryptContainer.FORMAT_LEGACY
                        )
                    } else {
                        val baseHeader = savedCrypt.base.decrypted.header

                        val diff = SavedAppsDifferenceDer(
                            diffCrypto,
                            baseHeader.generation,
                            baseHeader.counter
                        )

                        CryptContainer.encrypt(
                            encodeDeflatedDer { diff.derEncode(it) },
                            diffCryptConfig.params,
                            CryptContainer.FORMAT_APP_DIFF_V2
                        )
                    }

                throwIfTooLarge(diffEncrypted)

                Threads.database.executeAndWait {
                    database.cryptContainer().updateMetadata(diffCryptConfig.newMetadata)

                    database.cryptContainer().updateData(
                        CryptContainerData(
                            cryptContainerId = savedCrypt.diff.meta.cryptContainerId,
                            encryptedData = diffEncrypted
                        )
                    )

                    dispatchSync(UpdateInstalledAppsAction(
                        base = null,
                        diff = diffEncrypted
                    ))
                }

                syncUtil.requestImportantSync()
            }
        }
    }
}