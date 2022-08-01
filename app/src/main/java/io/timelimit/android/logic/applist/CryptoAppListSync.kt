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
package io.timelimit.android.logic.applist

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.CryptContainerData
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.proto.build
import io.timelimit.android.proto.encodeDeflated
import io.timelimit.android.sync.SyncUtil
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.UpdateInstalledAppsAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.proto.applist.InstalledAppsDifferenceProto
import io.timelimit.proto.applist.InstalledAppsProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto

object CryptoAppListSync {
    private const val SIZE_LIMIT_COMPRESSED = 1024 * 256

    class TooLargeException(val size: Int): RuntimeException("app list is too big: $size")

    suspend fun sync(
        deviceState: DeviceState,
        database: Database,
        installed: InstalledAppsProto,
        syncUtil: SyncUtil,
        disableLegacySync: Boolean
    ) {
        fun dispatch(action: AppLogicAction) {
            if (deviceState.isConnectedMode) {
                ApplyActionUtil.addAppLogicActionToDatabaseSync(action, database)
            }
        }

        val savedCrypt = Threads.database.executeAndWait {
            InstalledAppsUtil.getEncryptedInstalledAppsFromDatabaseSync(database, deviceState.id)
        }

        if (savedCrypt == null) {
            val baseKey = CryptContainer.EncryptParameters.generate()
            val diffKey = CryptContainer.EncryptParameters.generate()

            val baseEncrypted = CryptContainer.encrypt(installed.encodeDeflated(), baseKey)
            val diffEncrypted = CryptContainer.encrypt(SavedAppsDifferenceProto.build(baseEncrypted, InstalledAppsDifferenceProto()).encodeDeflated(), diffKey)

            if (baseEncrypted.size > SIZE_LIMIT_COMPRESSED) throw TooLargeException(baseEncrypted.size)

            Threads.database.executeAndWait {
                database.cryptContainer().removeDeviceCryptoMetadata(
                    deviceId = deviceState.id,
                    types = listOf(
                        CryptContainerMetadata.TYPE_APP_LIST_BASE,
                        CryptContainerMetadata.TYPE_APP_LIST_DIFF
                    )
                )

                val baseId = database.cryptContainer().insertMetadata(
                    CryptContainerMetadata.buildFor(
                        deviceId = deviceState.id,
                        categoryId = null,
                        type = CryptContainerMetadata.TYPE_APP_LIST_BASE,
                        params = baseKey
                    )
                )

                val diffId = database.cryptContainer().insertMetadata(
                    CryptContainerMetadata.buildFor(
                        deviceId = deviceState.id,
                        categoryId = null,
                        type = CryptContainerMetadata.TYPE_APP_LIST_DIFF,
                        params = diffKey
                    )
                )

                database.cryptContainer().insertData(
                    CryptContainerData(
                        cryptContainerId = baseId,
                        encryptedData = baseEncrypted
                    )
                )

                database.cryptContainer().insertData(
                    CryptContainerData(
                        cryptContainerId = diffId,
                        encryptedData = diffEncrypted
                    )
                )

                dispatch(UpdateInstalledAppsAction(
                    base = baseEncrypted,
                    diff = diffEncrypted,
                    wipe = disableLegacySync
                ))
            }

            syncUtil.requestImportantSync()
        } else {
            val diffCrypto = AppsDifferenceUtil.calculateAppsDifference(savedCrypt.base, installed)

            if (diffCrypto != savedCrypt.diff) {
                val baseSize = savedCrypt.base.adapter.encodedSize(savedCrypt.base)
                val diffSize = diffCrypto.adapter.encodedSize(diffCrypto)
                val needsNewBySize = diffSize >= baseSize / 10
                val baseNeedsNewGeneration = savedCrypt.baseMeta.needsNewGeneration()
                val diffNeedsNewGeneration = savedCrypt.diffMeta.needsNewGeneration() or baseNeedsNewGeneration

                val diffCryptParams = savedCrypt.diffMeta.prepareEncryption(diffNeedsNewGeneration)

                if (needsNewBySize or baseNeedsNewGeneration) {
                    val baseCryptParams = savedCrypt.baseMeta.prepareEncryption(baseNeedsNewGeneration)

                    val baseEncrypted = CryptContainer.encrypt(installed.encodeDeflated(), baseCryptParams.params)

                    val diffEncrypted = CryptContainer.encrypt(
                        SavedAppsDifferenceProto.build(baseEncrypted, InstalledAppsDifferenceProto()).encodeDeflated(),
                        diffCryptParams.params
                    )

                    if (baseEncrypted.size > SIZE_LIMIT_COMPRESSED) throw TooLargeException(baseEncrypted.size)

                    Threads.database.executeAndWait {
                        database.cryptContainer().updateMetadata(listOf(
                            baseCryptParams.newMetadata,
                            diffCryptParams.newMetadata
                        ))

                        database.cryptContainer().updateData(listOf(
                            CryptContainerData(
                                cryptContainerId = savedCrypt.baseMeta.cryptContainerId,
                                encryptedData = baseEncrypted
                            ),
                            CryptContainerData(
                                cryptContainerId = savedCrypt.diffMeta.cryptContainerId,
                                encryptedData = diffEncrypted
                            )
                        ))

                        dispatch(UpdateInstalledAppsAction(
                            base = baseEncrypted,
                            diff = diffEncrypted,
                            wipe = disableLegacySync
                        ))
                    }

                    syncUtil.requestImportantSync()
                } else {
                    val diffEncrypted = CryptContainer.encrypt(
                        SavedAppsDifferenceProto.build(savedCrypt.baseHeader, diffCrypto).encodeDeflated(),
                        diffCryptParams.params
                    )

                    if (diffEncrypted.size > SIZE_LIMIT_COMPRESSED) throw TooLargeException(diffEncrypted.size)

                    Threads.database.executeAndWait {
                        database.cryptContainer().updateMetadata(diffCryptParams.newMetadata)

                        database.cryptContainer().updateData(
                            CryptContainerData(
                                cryptContainerId = savedCrypt.diffMeta.cryptContainerId,
                                encryptedData = diffEncrypted
                            )
                        )

                        dispatch(UpdateInstalledAppsAction(
                            base = null,
                            diff = diffEncrypted,
                            wipe = disableLegacySync
                        ))
                    }

                    syncUtil.requestImportantSync()
                }
            }
        }
    }
}