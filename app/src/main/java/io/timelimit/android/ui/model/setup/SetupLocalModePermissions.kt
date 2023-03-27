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
package io.timelimit.android.ui.model.setup

import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.manage.device.manage.permission.PermissionScreenContent
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

object SetupLocalModePermissions {
    fun handle(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        stateLive: Flow<State.Setup.DevicePermissions>,
        updateState: ((State.Setup.DevicePermissions) -> State) -> Unit
    ): Flow<Screen> {
        val deviceStatusLive = deviceStatus(logic.platformIntegration)

        return combine(stateLive, deviceStatusLive) { state, deviceStatus ->
            Screen.SetupDevicePermissionsScreen(
                state,
                PermissionScreenContent(
                    status = deviceStatus,
                    dialog = state.currentDialog?.let { dialog ->
                        PermissionScreenContent.Dialog(
                            permission = dialog,
                            launchSystemSettings = {
                                activityCommand.trySend(ActivityCommand.LaunchSystemSettings(dialog))

                                updateState { it.copy(currentDialog = null) }
                            },
                            close = { updateState { it.copy(currentDialog = null) } }
                        )
                    },
                    showDetails = { permission -> updateState { it.copy(currentDialog = permission) } }
                )
            ) { updateState { State.Setup.LocalMode(it) } }
        }
    }

    private fun deviceStatus(platformIntegration: PlatformIntegration): Flow<PermissionScreenContent.Status> = flow {
        while (true) {
            emit(
                PermissionScreenContent.Status(
                    notificationAccess = platformIntegration.getNotificationAccessPermissionStatus(),
                    protectionLevel = platformIntegration.getCurrentProtectionLevel(),
                    maxProtectionLevel = platformIntegration.maximumProtectionLevel,
                    usageStats = platformIntegration.getForegroundAppPermissionStatus(),
                    overlay = platformIntegration.getDrawOverOtherAppsPermissionStatus(true),
                    accessibility = platformIntegration.isAccessibilityServiceEnabled()
                )
            )

            delay(2000)
        }
    }.distinctUntilChanged()
}