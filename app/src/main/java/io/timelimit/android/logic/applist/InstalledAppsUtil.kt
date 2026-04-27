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
import io.timelimit.android.logic.applist.data.InstalledAppsDer
import io.timelimit.android.logic.applist.data.InstalledAppsDifferenceDer
import io.timelimit.android.logic.applist.data.SavedAppsDifferenceDer
import io.timelimit.android.proto.decodeInflated
import io.timelimit.android.proto.decodeInflatedDer
import io.timelimit.android.proto.toDer
import io.timelimit.proto.applist.InstalledAppsProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto
import java.io.IOException

object InstalledAppsUtil {
    private const val LOG_TAG = "InstalledAppsUtil"

    suspend fun getInstalledAppsFromPlainDatabaseAsync(database: Database, deviceId: String): InstalledAppsDer {
        return InstalledAppsDer(
            apps = database.app().getAppsByDeviceIdAsync(deviceId = deviceId).waitForNonNullValue().map { it.toDer() },
            activities = database.appActivity().getAppActivitiesByDeviceIds(deviceIds = listOf(deviceId)).waitForNonNullValue().map { it.toDer() }
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

                    val baseData = decryptAppList(
                        key = baseValue.metadata.currentGenerationKey,
                        data = baseValue.encryptedData
                    )

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

                    val diffData = decryptAppDiff(
                        key = diffValue.metadata.currentGenerationKey,
                        data = diffValue.encryptedData
                    )

                    Decrypted(
                        data = diffData.apps,
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
        val base: Encrypted<InstalledAppsDer>?,
        val diff: Encrypted<InstalledAppsDifferenceDer>?
    )

    data class Encrypted<T>(val meta: CryptContainerMetadata, val decrypted: Decrypted<T>?)

    data class Decrypted<T>(val data: T, val header: CryptContainer.Header)

    suspend fun getInstalledAppsFromOs(appLogic: AppLogic, deviceState: DeviceState): InstalledAppsDer {
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

            (currentlyInstalled + featureDummyApps).map { it.toDer() }
        }

        val activities = if (deviceState.enableActivityLevelBlocking)
            Threads.backgroundOSInteraction.executeAndWait {
                val realActivities = appLogic.platformIntegration.getLocalAppActivities(deviceId = deviceState.id)
                val dummyActivities = apps.map { app ->
                    AppActivity(
                        deviceId = deviceState.id,
                        appPackageName = app.packageName,
                        activityClassName = DummyApps.ACTIVITY_BACKGROUND_AUDIO,
                        title = appLogic.context.getString(R.string.dummy_app_activity_audio)
                    )
                }

                (realActivities + dummyActivities).map { it.toDer() }
            }
        else
            emptyList()

        return InstalledAppsDer(
            apps = apps,
            activities = activities
        )
    }

    fun decryptAppList(key: ByteArray, data: ByteArray) = try {
        val baseDecrypted = CryptContainer.decrypt(
            key,
            data,
            CryptContainer.FORMAT_APP_LIST_V2
        )

        baseDecrypted.decodeInflatedDer(InstalledAppsDer::derDecode)
    } catch (_: CryptException.WrongKey) {
        val baseDecrypted = CryptContainer.decrypt(
            key,
            data,
            CryptContainer.FORMAT_LEGACY
        )

        InstalledAppsProto.ADAPTER.decodeInflated(baseDecrypted).let {
            InstalledAppsDer.fromProto(it)
        }
    }

    fun decryptAppDiff(key: ByteArray, data: ByteArray) = try {
        val diffDecrypted = CryptContainer.decrypt(
            key,
            data,
            CryptContainer.FORMAT_APP_DIFF_V2
        )

        diffDecrypted.decodeInflatedDer(SavedAppsDifferenceDer::derDecode)
    } catch (_: CryptException.WrongKey) {
        val diffDecrypted = CryptContainer.decrypt(
            key,
            data,
            CryptContainer.FORMAT_LEGACY
        )

        SavedAppsDifferenceDer.fromProto(
            SavedAppsDifferenceProto.ADAPTER.decodeInflated(diffDecrypted)
        )
    }
}