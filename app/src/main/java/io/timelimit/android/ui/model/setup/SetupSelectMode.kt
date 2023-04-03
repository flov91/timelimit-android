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

import androidx.compose.material.SnackbarHostState
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.setup.SetupUnprovisionedCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object SetupSelectMode {
    fun handle(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        scope: CoroutineScope,
        stateLive: Flow<State.Setup.SelectMode>,
        updateState: ((State.Setup.SelectMode) -> State) -> Unit
    ): Flow<Screen> {
        val snackbarHostState = SnackbarHostState()

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    action()
                } catch (ex: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        return stateLive.map { state ->
            Screen.SetupSelectModeScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                selectLocal = { updateState { State.Setup.DevicePermissions(it) } },
                selectConnected = { updateState { State.Setup.ConnectedPrivacy(it) } },
                selectUninstall = { launch {
                    Threads.database.executeAndWait { SetupUnprovisionedCheck.checkSync(logic.database) }

                    logic.platformIntegration.disableDeviceAdmin()

                    activityCommand.send(ActivityCommand.TriggerUninstall(
                        packageName = logic.context.packageName,
                        errorHandler = {
                            launch { throw RuntimeException() }
                        }
                    ))
                } }
            )
        }
    }
}