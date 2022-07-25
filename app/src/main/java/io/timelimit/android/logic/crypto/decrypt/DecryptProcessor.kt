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
package io.timelimit.android.logic.crypto.decrypt

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.crypto.CryptException
import io.timelimit.android.data.Database
import io.timelimit.android.data.dao.CryptContainerDao
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.proto.decodeInflated
import io.timelimit.android.proto.toDb
import io.timelimit.proto.applist.InstalledAppsProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto
import java.io.IOException

object DecryptProcessor {
    private const val LOG_TAG = "DecryptProcessor"

    fun handleEncryptedApps(database: Database) {
        val unprocessed = database.cryptContainer().getMetadataByProcessingStatus(CryptContainerMetadata.ProcessingStatus.Unprocessed)
        val finishedDeviceIds = mutableSetOf<String>()

        for (metadata in unprocessed) {
            if (
                metadata.type == CryptContainerMetadata.TYPE_APP_LIST_BASE ||
                metadata.type == CryptContainerMetadata.TYPE_APP_LIST_DIFF
            ) {
                if (
                    metadata.deviceId == null ||
                    finishedDeviceIds.contains(metadata.deviceId) ||
                    metadata.deviceId == database.config().getOwnDeviceIdSync()
                ) continue

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "found data for ${metadata.deviceId}")
                }

                finishedDeviceIds.add(metadata.deviceId)

                val baseData = database.cryptContainer().getCryptoFullDataSyncByDeviceId(metadata.deviceId, CryptContainerMetadata.TYPE_APP_LIST_BASE) ?: continue
                val diffData = database.cryptContainer().getCryptoFullDataSyncByDeviceId(metadata.deviceId, CryptContainerMetadata.TYPE_APP_LIST_DIFF) ?: continue

                if (!(isReadyForProcessing(baseData) && isReadyForProcessing(diffData))) continue

                val (baseDecrypted, baseHeader) = try {
                    CryptContainer.decrypt(
                        baseData.metadata.currentGenerationKey ?: continue,
                        baseData.encryptedData
                    ) to CryptContainer.Header.read(baseData.encryptedData)
                } catch (ex: CryptException) {
                    database.cryptContainer().updateMetadata(baseData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage))

                    continue
                }

                val diffDecrypted = try {
                    CryptContainer.decrypt(
                        diffData.metadata.currentGenerationKey ?: continue,
                        diffData.encryptedData
                    )
                } catch (ex: CryptException) {
                    database.cryptContainer().updateMetadata(diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage))

                    continue
                }

                val baseContent = try {
                    InstalledAppsProto.ADAPTER.decodeInflated(baseDecrypted)
                } catch (ex: IOException) {
                    database.cryptContainer().updateMetadata(baseData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.ContentDamage))

                    continue
                }

                val diffContent = try {
                    SavedAppsDifferenceProto.ADAPTER.decodeInflated(diffDecrypted)
                } catch (ex: IOException) {
                    database.cryptContainer().updateMetadata(diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.ContentDamage))

                    continue
                }

                if (
                    diffContent.base_generation != baseHeader.generation ||
                    diffContent.base_counter != baseHeader.counter
                ) {
                    database.cryptContainer().updateMetadata(diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.ContentDamage))

                    continue
                }

                database.app().deleteAllAppsByDeviceId(metadata.deviceId)
                database.appActivity().deleteAppActivitiesByDeviceIds(listOf(metadata.deviceId))

                database.app().addAppsSync(
                    baseContent.apps.map {
                        App(
                            deviceId = metadata.deviceId,
                            packageName = it.package_name,
                            title = it.title,
                            isLaunchable = it.is_launchable,
                            recommendation = it.recommendation.toDb()
                        )
                    }
                )

                database.appActivity().addAppActivitiesSync(
                    baseContent.activities.map {
                        AppActivity(
                            deviceId = metadata.deviceId,
                            appPackageName = it.package_name,
                            activityClassName = it.class_name,
                            title = it.title
                        )
                    }
                )

                database.app().removeAppsByDeviceIdAndPackageNamesSync(
                    metadata.deviceId,
                    diffContent.apps?.removed_packages ?: emptyList()
                )

                diffContent.apps?.removed_activities?.forEach {
                    database.appActivity().deleteAppActivitiesSync(
                        deviceId = metadata.deviceId,
                        packageName = it.package_name,
                        activities = listOf(it.class_name)
                    )
                }

                database.app().addAppsSync(
                    diffContent.apps?.added?.apps?.map {
                        App(
                            deviceId = metadata.deviceId,
                            packageName = it.package_name,
                            title = it.title,
                            isLaunchable = it.is_launchable,
                            recommendation = it.recommendation.toDb()
                        )
                    } ?: emptyList()
                )

                database.appActivity().addAppActivitiesSync(
                    diffContent.apps?.added?.activities?.map {
                        AppActivity(
                            deviceId = metadata.deviceId,
                            appPackageName = it.package_name,
                            activityClassName = it.class_name,
                            title = it.title
                        )
                    } ?: emptyList()
                )

                database.cryptContainer().updateMetadata(listOf(
                    baseData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.Finished),
                    diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.Finished)
                ))
            }
        }
    }

    private fun isReadyForProcessing(value: CryptContainerDao.MetadataAndContent) = when (value.metadata.status) {
        CryptContainerMetadata.ProcessingStatus.Unprocessed -> true
        CryptContainerMetadata.ProcessingStatus.Finished -> true
        else -> false
    }
}