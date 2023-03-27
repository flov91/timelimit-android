/*
 * TimeLimit Copyright <C> 2019 - 2023 Jonas Lochmann
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.ConsentFlags
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.ServerApiLevelInfo
import io.timelimit.android.logic.ServerApiLevelLogic

data class DeviceState(
    val id: String,
    val isCurrentUserChild: Boolean,
    val isDefaultUserChild: Boolean,
    val enableActivityLevelBlocking: Boolean,
    val isDeviceOwner: Boolean,
    val hasSyncConsent: Boolean,
    val isLocalMode: Boolean,
    val serverApiLevel: ServerApiLevelInfo
) {
    companion object {
        fun getSync(database: Database): DeviceState? {
            val userAndDeviceData = database.derivedDataDao().getUserAndDeviceRelatedDataSync() ?: return null
            val deviceRelatedData = userAndDeviceData.deviceRelatedData
            val device = deviceRelatedData.deviceEntry
            val defaultUser = if (device.defaultUser.isNotEmpty()) database.user().getUserByIdSync(device.defaultUser) else null
            val serverApiLevel = ServerApiLevelLogic.getSync(database)

            return DeviceState(
                id = device.id,
                isCurrentUserChild = userAndDeviceData.userRelatedData?.user?.type == UserType.Child,
                isDefaultUserChild = defaultUser?.type == UserType.Child,
                enableActivityLevelBlocking = device.enableActivityLevelBlocking,
                isDeviceOwner = device.currentProtectionLevel == ProtectionLevel.DeviceOwner,
                hasSyncConsent = deviceRelatedData.consentFlags and ConsentFlags.APP_LIST_SYNC == ConsentFlags.APP_LIST_SYNC,
                isLocalMode = deviceRelatedData.isLocalMode,
                serverApiLevel = serverApiLevel
            )
        }

        fun getLive(appLogic: AppLogic): LiveData<DeviceState?>  = mergeLiveDataWaitForValues(
            appLogic.deviceEntryIfEnabled,
            appLogic.database.config().isConsentFlagSetAsync(ConsentFlags.APP_LIST_SYNC),
            appLogic.database.config().getDeviceAuthTokenAsync().map { it.isEmpty() },
            appLogic.deviceUserEntry,
            appLogic.deviceEntryIfEnabled.switchMap { deviceEntry ->
                val defaultUser = deviceEntry?.defaultUser

                if (defaultUser.isNullOrEmpty()) liveDataFromNullableValue(null)
                else appLogic.database.user().getUserByIdLive(defaultUser)
            },
            appLogic.serverApiLevelLogic.infoLive
        ).map { (deviceEntry, hasSyncConsent, isLocalMode, deviceUser, deviceDefaultUser, serverApiLevel) ->
            deviceEntry?.let { device ->
                DeviceState(
                    id = device.id,
                    isCurrentUserChild = deviceUser?.type == UserType.Child,
                    isDefaultUserChild = deviceDefaultUser?.type == UserType.Child,
                    enableActivityLevelBlocking = device.enableActivityLevelBlocking,
                    isDeviceOwner = device.currentProtectionLevel == ProtectionLevel.DeviceOwner,
                    hasSyncConsent = hasSyncConsent,
                    isLocalMode = isLocalMode,
                    serverApiLevel = serverApiLevel
                )
            }
        }.ignoreUnchanged()
    }

    val hasAnyChildUser = isCurrentUserChild || isDefaultUserChild
    val shouldAskForConsent = hasAnyChildUser && !isLocalMode && !hasSyncConsent
    val isConnectedMode = !isLocalMode
}