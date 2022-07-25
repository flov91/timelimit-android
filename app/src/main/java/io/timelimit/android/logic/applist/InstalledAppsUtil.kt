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

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.crypto.CryptException
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DummyApps
import io.timelimit.android.proto.decodeInflated
import io.timelimit.android.proto.toProto
import io.timelimit.proto.applist.InstalledAppsDifferenceProto
import io.timelimit.proto.applist.InstalledAppsProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto
import java.io.IOException

object InstalledAppsUtil {
    private const val LOG_TAG = "InstalledAppsUtil"

    suspend fun getInstalledAppsFromPlainDatabaseAsync(database: Database, deviceId: String): InstalledAppsProto {
        return InstalledAppsProto(
            apps = database.app().getAppsByDeviceIdAsync(deviceId = deviceId).waitForNonNullValue().map { it.toProto() },
            activities = database.appActivity().getAppActivitiesByDeviceIds(deviceIds = listOf(deviceId)).waitForNonNullValue().map { it.toProto() }
        )
    }

    fun getEncryptedInstalledAppsFromDatabaseSync(database: Database, deviceId: String): DecryptedInstalledApps? {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getEncryptedInstalledAppsFromDatabaseSync()")
        }

        val baseValue = database.cryptContainer().getCryptoFullDataSyncByDeviceId(
            deviceId = deviceId,
            type = CryptContainerMetadata.TYPE_APP_LIST_BASE
        )

        val diffValue = database.cryptContainer().getCryptoFullDataSyncByDeviceId(
            deviceId = deviceId,
            type = CryptContainerMetadata.TYPE_APP_LIST_DIFF
        )

        if (
            baseValue == null ||
            baseValue.metadata.currentGenerationKey == null ||
            baseValue.metadata.status != CryptContainerMetadata.ProcessingStatus.Finished ||
            diffValue == null ||
            diffValue.metadata.currentGenerationKey == null ||
            diffValue.metadata.status != CryptContainerMetadata.ProcessingStatus.Finished
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "incomplete data")
            }

            return null
        }

        val (baseHeader, baseDecrypted, diffDecrypted) = try {
            val baseHeader = CryptContainer.Header.read(baseValue.encryptedData)

            val baseDecrypted = CryptContainer.decrypt(
                baseValue.metadata.currentGenerationKey,
                baseValue.encryptedData
            )

            val diffDecrypted = CryptContainer.decrypt(
                diffValue.metadata.currentGenerationKey,
                diffValue.encryptedData
            )

            Triple(baseHeader, baseDecrypted, diffDecrypted)
        } catch (ex: CryptException) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "could not decrypt previous data", ex)
            }

            return null
        }

        val (base, diff) = try {
            val base = InstalledAppsProto.ADAPTER.decodeInflated(baseDecrypted)

            val diff = SavedAppsDifferenceProto.ADAPTER.decodeInflated(diffDecrypted).apps
                ?: InstalledAppsDifferenceProto()

            Pair(base, diff)
        } catch (ex: IOException) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "could not decode data", ex)
            }

            return null
        }

        return DecryptedInstalledApps(
            base = base,
            baseMeta = baseValue.metadata,
            baseHeader = baseHeader,
            diff = diff,
            diffMeta = diffValue.metadata
        )
    }

    data class DecryptedInstalledApps(
        val base: InstalledAppsProto,
        val baseHeader: CryptContainer.Header,
        val baseMeta: CryptContainerMetadata,
        val diff: InstalledAppsDifferenceProto,
        val diffMeta: CryptContainerMetadata
    )

    suspend fun getInstalledAppsFromOs(appLogic: AppLogic, deviceState: DeviceState): InstalledAppsProto {
        val apps = kotlin.run {
            val currentlyInstalled = Threads.backgroundOSInteraction.executeAndWait {
                appLogic.platformIntegration.getLocalApps(deviceId = deviceState.id)
            }

            val featureDummyApps = appLogic.platformIntegration.getFeatures().map {
                DummyApps.forFeature(
                    id = it.id,
                    title = it.title,
                    deviceId = deviceState.id
                )
            }

            (currentlyInstalled + featureDummyApps).map { it.toProto() }
        }

        val activities = if (deviceState.enableActivityLevelBlocking)
            Threads.backgroundOSInteraction.executeAndWait {
                val realActivities = appLogic.platformIntegration.getLocalAppActivities(deviceId = deviceState.id)
                val dummyActivities = apps.map { app ->
                    AppActivity(
                        deviceId = deviceState.id,
                        appPackageName = app.package_name,
                        activityClassName = DummyApps.ACTIVITY_BACKGROUND_AUDIO,
                        title = appLogic.context.getString(R.string.dummy_app_activity_audio)
                    )
                }

                (realActivities + dummyActivities).map { it.toProto() }
            }
        else
            emptyList()

        return InstalledAppsProto(
            apps = apps,
            activities = activities
        )
    }
}