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

    suspend fun getEncryptedInstalledAppsFromDatabase(database: Database, deviceId: String): EncryptedInstalledApps {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getEncryptedInstalledAppsFromDatabase()")
        }

        val (baseValue, diffValue) = Threads.database.executeAndWait {
            database.runInTransaction {
                val baseValue = database.cryptContainer().getCryptoFullDataSyncByDeviceId(
                    deviceId = deviceId,
                    type = CryptContainerMetadata.TYPE_APP_LIST_BASE
                )

                val diffValue = database.cryptContainer().getCryptoFullDataSyncByDeviceId(
                    deviceId = deviceId,
                    type = CryptContainerMetadata.TYPE_APP_LIST_DIFF
                )

                Pair(baseValue, diffValue)
            }
        }

        return Threads.crypto.executeAndWait {
            val baseDecrypted = try {
                if (
                    baseValue != null &&
                    baseValue.metadata.currentGenerationKey != null &&
                    baseValue.metadata.status == CryptContainerMetadata.ProcessingStatus.Finished
                ) {
                    val baseHeader = CryptContainer.Header.read(baseValue.encryptedData)

                    val baseDecrypted = CryptContainer.decrypt(
                        baseValue.metadata.currentGenerationKey,
                        baseValue.encryptedData
                    )

                    val baseData = InstalledAppsProto.ADAPTER.decodeInflated(baseDecrypted)

                    Decrypted(
                        data = baseData,
                        header = baseHeader
                    )
                } else null
            } catch (ex: CryptException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not decrypt previous base data", ex)
                }

                null
            } catch (ex: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not decode previous base data", ex)
                }

                null
            }

            val diffDecrypted = try {
                if (
                    diffValue != null &&
                    diffValue.metadata.currentGenerationKey != null &&
                    diffValue.metadata.status == CryptContainerMetadata.ProcessingStatus.Finished
                ) {
                    val diffHeader = CryptContainer.Header.read(diffValue.encryptedData)

                    val diffDecrypted = CryptContainer.decrypt(
                        diffValue.metadata.currentGenerationKey,
                        diffValue.encryptedData
                    )

                    val diffData =
                        SavedAppsDifferenceProto.ADAPTER.decodeInflated(diffDecrypted).apps
                            ?: InstalledAppsDifferenceProto()

                    Decrypted(
                        data = diffData,
                        header = diffHeader
                    )
                } else null
            } catch (ex: CryptException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not decrypt previous diff data", ex)
                }

                null
            } catch (ex: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not decode previous diff data", ex)
                }

                null
            }

            EncryptedInstalledApps(
                base = baseValue?.let { Encrypted(it.metadata, baseDecrypted) },
                diff = diffValue?.let { Encrypted(it.metadata, diffDecrypted) }
            )
        }
    }

    data class EncryptedInstalledApps(
        val base: Encrypted<InstalledAppsProto>?,
        val diff: Encrypted<InstalledAppsDifferenceProto>?
    )

    data class Encrypted<T>(val meta: CryptContainerMetadata, val decrypted: Decrypted<T>?)

    data class Decrypted<T>(val data: T, val header: CryptContainer.Header)

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