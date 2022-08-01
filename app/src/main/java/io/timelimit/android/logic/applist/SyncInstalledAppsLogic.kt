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
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseAppLogicActionDispatcher
import io.timelimit.android.util.SizeTextUtil
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

    private val deviceStateLive = DeviceState.getLive(appLogic)

    val shouldAskForConsent = deviceStateLive.map { it?.shouldAskForConsent ?: false }.ignoreUnchanged()

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
            } catch (ex: CryptoAppListSync.TooLargeException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "list is too large", ex)
                }

                val baseMsg = appLogic.context.getString(R.string.background_logic_toast_sync_apps)

                val msg = "$baseMsg (${SizeTextUtil.formatSize(ex.size.toLong())})"

                Toast.makeText(appLogic.context, msg, Toast.LENGTH_SHORT).show()

                // do not retry in this case
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
            val deviceState = Threads.database.executeAndWait { DeviceState.getSync(appLogic.database) } ?: return

            if (deviceState.isLocalMode) {
                // local mode -> sync always
            } else {
                // connected mode -> don't sync always
                if (!deviceState.hasSyncConsent) return@withLock
                if (!deviceState.hasAnyChildUser) return@withLock
            }

            val installed = InstalledAppsUtil.getInstalledAppsFromOs(appLogic, deviceState)

            val savedPlain = InstalledAppsUtil.getInstalledAppsFromPlainDatabaseAsync(appLogic.database, deviceState.id)

            val diffPlain = AppsDifferenceUtil.calculateAppsDifference(savedPlain, installed)
            val diffPlainActions = AppsDifferenceUtil.calculateAppsDifferenceActions(diffPlain, deviceState.id)

            if (deviceState.disableLegacySync) {
                if (diffPlainActions.isNotEmpty()) {
                    Threads.database.executeAndWait {
                        diffPlainActions.forEach {
                            LocalDatabaseAppLogicActionDispatcher.dispatchAppLogicActionSync(
                                it, appLogic.database.config().getOwnDeviceIdSync()!!, appLogic.database
                            )
                        }
                    }
                }
            } else {
                diffPlainActions.forEach { action ->
                    ApplyActionUtil.applyAppLogicAction(
                        action = action,
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }

            if (deviceState.isConnectedMode && deviceState.serverApiLevel.hasLevelOrIsOffline(4)) {
                CryptoAppListSync.sync(
                    deviceState = deviceState,
                    database = appLogic.database,
                    installed = installed,
                    syncUtil = appLogic.syncUtil,
                    disableLegacySync = deviceState.disableLegacySync
                )
            }
        }
    }
}
