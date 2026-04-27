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
import io.timelimit.android.logic.applist.InstalledAppsUtil
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

                val (baseContent, baseHeader) = try {
                    InstalledAppsUtil.decryptAppList(
                        baseData.metadata.currentGenerationKey ?: continue,
                        baseData.encryptedData
                    ) to CryptContainer.Header.read(baseData.encryptedData)
                } catch (_: CryptException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "content damaged due to crypt exception")
                    }

                    database.cryptContainer().updateMetadata(baseData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage))

                    continue
                } catch (ex: IOException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "content damaged", ex)
                    }

                    database.cryptContainer().updateMetadata(baseData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.ContentDamage))

                    continue
                }

                val diffContent = try {
                    InstalledAppsUtil.decryptAppDiff(
                        diffData.metadata.currentGenerationKey ?: continue,
                        diffData.encryptedData
                    )
                } catch (_: CryptException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "content damaged due to crypt exception")
                    }

                    database.cryptContainer().updateMetadata(diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.CryptoDamage))

                    continue
                } catch (ex: IOException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "content damaged", ex)
                    }

                    database.cryptContainer().updateMetadata(diffData.metadata.copy(status = CryptContainerMetadata.ProcessingStatus.ContentDamage))

                    continue
                }

                if (
                    diffContent.baseGeneration != baseHeader.generation ||
                    diffContent.baseCounter != baseHeader.counter
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
                            packageName = it.packageName,
                            title = it.title,
                            isLaunchable = it.isLaunchable,
                            recommendation = it.recommendation
                        )
                    }
                )

                database.appActivity().addAppActivitiesSync(
                    baseContent.activities.map {
                        AppActivity(
                            deviceId = metadata.deviceId,
                            appPackageName = it.packageName,
                            activityClassName = it.className,
                            title = it.title
                        )
                    }
                )

                database.app().removeAppsByDeviceIdAndPackageNamesSync(
                    metadata.deviceId,
                    diffContent.apps.removedPackages
                )

                diffContent.apps.removedActivities.forEach {
                    database.appActivity().deleteAppActivitiesSync(
                        deviceId = metadata.deviceId,
                        packageName = it.packageName,
                        activities = listOf(it.className)
                    )
                }

                database.app().addAppsSync(
                    diffContent.apps.added.apps.map {
                        App(
                            deviceId = metadata.deviceId,
                            packageName = it.packageName,
                            title = it.title,
                            isLaunchable = it.isLaunchable,
                            recommendation = it.recommendation
                        )
                    }
                )

                database.appActivity().addAppActivitiesSync(
                    diffContent.apps.added.activities.map {
                        AppActivity(
                            deviceId = metadata.deviceId,
                            appPackageName = it.packageName,
                            activityClassName = it.className,
                            title = it.title
                        )
                    }
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