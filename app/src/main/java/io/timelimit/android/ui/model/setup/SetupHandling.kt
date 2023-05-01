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

import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

object SetupHandling {
    fun handle(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        permissionsChanged: SharedFlow<Unit>,
        stateLive: Flow<State.Setup>,
        updateState: ((State.Setup) -> State) -> Unit
    ): Flow<Screen> = stateLive.splitConflated(
        Case.simple<_, _, State.Setup.SelectMode> { SetupSelectMode.handle(logic, activityCommand, scope, it, updateMethod(updateState)) },
        Case.simple<_, _, State.Setup.DevicePermissions> { SetupLocalModePermissions.handle(logic, scope, activityCommand, it, updateMethod(updateState)) },
        Case.simple<_, _, State.Setup.ConnectedPrivacy> { SetupConnectedModePrivacy.handle(logic, it, updateMethod(updateState)) },
        Case.simple<_, _, State.Setup.SelectConnectedMode> { SetupSelectConnectedMode.handle(it, updateMethod(updateState)) },
        Case.simple<_, _, State.Setup.ParentModeSetup> { SetupParentHandling.handle(logic, activityCommand, permissionsChanged, it, updateMethod(updateState)) }
    )
}