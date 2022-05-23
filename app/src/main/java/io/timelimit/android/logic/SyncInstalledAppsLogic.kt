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
package io.timelimit.android.logic

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.ConsentFlags
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.*
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncInstalledAppsLogic(val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "SyncInstalledAppsLogic"
    }

    private val doSyncLock = Mutex()
    private var requestSync = MutableLiveData<Boolean>().apply { value = false }

    private fun requestSync() {
        requestSync.value = true
    }

    private val deviceStateLive = mergeLiveDataWaitForValues(
        appLogic.deviceEntryIfEnabled,
        appLogic.database.config().isConsentFlagSetAsync(ConsentFlags.APP_LIST_SYNC),
        appLogic.database.config().getDeviceAuthTokenAsync().map { it.isEmpty() },
        appLogic.deviceUserEntry,
        appLogic.deviceEntryIfEnabled.switchMap { deviceEntry ->
            val defaultUser = deviceEntry?.defaultUser

            if (defaultUser.isNullOrEmpty()) liveDataFromNullableValue(null)
            else appLogic.database.user().getUserByIdLive(defaultUser)
        }
    ).map { (deviceEntry, hasSyncConsent, isLocalMode, deviceUser, deviceDefaultUser) ->
        deviceEntry?.let { device ->
            DeviceState(
                id = device.id,
                isCurrentUserChild = deviceUser?.type == UserType.Child,
                isDefaultUserChild = deviceDefaultUser?.type == UserType.Child,
                enableActivityLevelBlocking = device.enableActivityLevelBlocking,
                isDeviceOwner = device.currentProtectionLevel == ProtectionLevel.DeviceOwner,
                hasSyncConsent = hasSyncConsent,
                isLocalMode = isLocalMode
            )
        }
    }.ignoreUnchanged()

    val shouldAskForConsent = deviceStateLive.map { it?.shouldAskForConsent ?: false }.ignoreUnchanged()

    private fun getDeviceStateSync(): DeviceState? {
        val userAndDeviceData = appLogic.database.derivedDataDao().getUserAndDeviceRelatedDataSync() ?: return null
        val deviceRelatedData = userAndDeviceData.deviceRelatedData
        val device = deviceRelatedData.deviceEntry
        val defaultUser = if (device.defaultUser.isNotEmpty()) appLogic.database.user().getUserByIdSync(device.defaultUser) else null

        return DeviceState(
            id = device.id,
            isCurrentUserChild = userAndDeviceData.userRelatedData?.user?.type == UserType.Child,
            isDefaultUserChild = defaultUser?.type == UserType.Child,
            enableActivityLevelBlocking = device.enableActivityLevelBlocking,
            isDeviceOwner = device.currentProtectionLevel == ProtectionLevel.DeviceOwner,
            hasSyncConsent = deviceRelatedData.consentFlags and ConsentFlags.APP_LIST_SYNC == ConsentFlags.APP_LIST_SYNC,
            isLocalMode = deviceRelatedData.isLocalMode
        )
    }

    init {
        appLogic.platformIntegration.installedAppsChangeListener = Runnable { requestSync() }
        deviceStateLive.observeForever { requestSync() }

        runAsyncExpectForever { syncLoop() }
    }

    private suspend fun syncLoop() {
        // wait a moment before the first sync
        appLogic.timeApi.sleep(15 * 1000)

        while (true) {
            requestSync.waitUntilValueMatches { it == true }
            requestSync.value = false

            try {
                doSyncNow()

                // maximal 1 time per 5 seconds
                appLogic.timeApi.sleep(5 * 1000)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not sync installed app list", ex)
                }

                Toast.makeText(appLogic.context, R.string.background_logic_toast_sync_apps, Toast.LENGTH_SHORT).show()

                appLogic.timeApi.sleep(45 * 1000)
                requestSync.value = true
            }
        }
    }

    private suspend fun doSyncNow() {
        doSyncLock.withLock {
            val deviceState = Threads.database.executeAndWait { getDeviceStateSync() } ?: return

            if (deviceState.isLocalMode) {
                // local mode -> sync always
            } else {
                // connected mode -> don't sync always
                if (!deviceState.hasSyncConsent) return@withLock
                if (!deviceState.hasAnyChildUser) return@withLock
            }

            val deviceId = deviceState.id

            val currentlyInstalledApps = getCurrentApps(deviceId)

            run {
                val currentlySaved = appLogic.database.app().getAppsByDeviceIdAsync(deviceId = deviceId).waitForNonNullValue().associateBy { app -> app.packageName }

                // skip all items for removal which are still saved locally
                val itemsToRemove = HashMap(currentlySaved)
                currentlyInstalledApps.forEach { (packageName, _) -> itemsToRemove.remove(packageName) }

                // only add items which are not the same locally
                val itemsToAdd = currentlyInstalledApps.filter { (packageName, app) -> currentlySaved[packageName] != app }

                // save the changes
                if (itemsToRemove.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = RemoveInstalledAppsAction(packageNames = itemsToRemove.keys.toList()),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }

                if (itemsToAdd.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = AddInstalledAppsAction(
                                    apps = itemsToAdd.map {
                                        (_, app) ->

                                        InstalledApp(
                                                packageName = app.packageName,
                                                title = app.title,
                                                recommendation = app.recommendation,
                                                isLaunchable = app.isLaunchable
                                        )
                                    }
                            ),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }

            run {
                fun buildKey(activity: AppActivity) = "${activity.appPackageName}:${activity.activityClassName}"

                val currentlyInstalled = if (deviceState.enableActivityLevelBlocking)
                    Threads.backgroundOSInteraction.executeAndWait {
                        val realActivities = appLogic.platformIntegration.getLocalAppActivities(deviceId = deviceId)
                        val dummyActivities = currentlyInstalledApps.keys.map { packageName ->
                            AppActivity(
                                deviceId = deviceId,
                                appPackageName = packageName,
                                activityClassName = DummyApps.ACTIVITY_BACKGROUND_AUDIO,
                                title = appLogic.context.getString(R.string.dummy_app_activity_audio)
                            )
                        }

                        val allActivities = realActivities + dummyActivities

                        allActivities.associateBy { buildKey(it) }
                    }
                else
                    emptyMap()

                val currentlySaved = appLogic.database.appActivity().getAppActivitiesByDeviceIds(deviceIds = listOf(deviceId)).waitForNonNullValue().associateBy { buildKey(it) }

                // skip all items for removal which are still saved locally
                val itemsToRemove = HashMap(currentlySaved)
                currentlyInstalled.forEach { (packageName, _) -> itemsToRemove.remove(packageName) }

                // only add items which are not the same locally
                val itemsToAdd = currentlyInstalled.filter { (packageName, app) -> currentlySaved[packageName] != app }

                // save the changes
                if (itemsToRemove.isNotEmpty() or itemsToAdd.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = UpdateAppActivitiesAction(
                                    removedActivities = itemsToRemove.map { it.value.appPackageName to it.value.activityClassName },
                                    updatedOrAddedActivities = itemsToAdd.map { item ->
                                        AppActivityItem(
                                                packageName = item.value.appPackageName,
                                                className = item.value.activityClassName,
                                                title = item.value.title
                                        )
                                    }
                            ),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }
        }
    }

    private suspend fun getCurrentApps(deviceId: String): Map<String, App> {
        val currentlyInstalled = Threads.backgroundOSInteraction.executeAndWait {
            appLogic.platformIntegration.getLocalApps(deviceId = deviceId).associateBy { app -> app.packageName }
        }

        val featureDummyApps = appLogic.platformIntegration.getFeatures().map {
            DummyApps.forFeature(
                id = it.id,
                title = it.title,
                deviceId = deviceId
            )
        }.associateBy { it.packageName }

        return currentlyInstalled + featureDummyApps
    }

    internal data class DeviceState(
        val id: String,
        val isCurrentUserChild: Boolean,
        val isDefaultUserChild: Boolean,
        val enableActivityLevelBlocking: Boolean,
        val isDeviceOwner: Boolean,
        val hasSyncConsent: Boolean,
        val isLocalMode: Boolean
    ) {
        val hasAnyChildUser = isCurrentUserChild || isDefaultUserChild
        val shouldAskForConsent = hasAnyChildUser && !isLocalMode && !hasSyncConsent
    }
}
