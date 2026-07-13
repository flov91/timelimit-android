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
package io.timelimit.android.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.model.account.AccountDeletion
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import io.timelimit.android.ui.model.launch.LaunchHandling
import io.timelimit.android.ui.model.main.AboutHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import io.timelimit.android.ui.model.managechild.ManageChildHandling
import io.timelimit.android.ui.model.managedevice.ManageDeviceHandling
import io.timelimit.android.ui.model.setup.SetupHandling
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*

class MainModel(application: Application): AndroidViewModel(application) {
    val logic = DefaultAppLogic.with(application)
    val api = ApiModel(application)
    val state = MutableStateFlow(State.LaunchState as State)
    var fragmentIds = mutableSetOf<Int>()

    val screen: Flow<Screen> = state.splitConflated(
        Case.simple<_, _, State.LaunchState> { LaunchHandling.processLaunchState(state, logic) },
        Case.simple<_, _, State.Overview> { OverviewHandling.processState(logic, scope, api.activityCommand, api.authentication, state) },
        Case.simple<_, _, State.ManageChild> { state -> ManageChildHandling.processState(logic, api.activityCommand, api.authentication, state, updateMethod(::updateState)) },
        Case.simple<_, _, State.ManageDevice> { state -> ManageDeviceHandling.processState(logic, api.activityCommand, api.authentication, state, updateMethod(::updateState)) },
        Case.simple<_, _, State.Setup> { state -> SetupHandling.handle(logic, api.activityCommand, api.permissionsChanged, state, updateMethod(::updateState)) },
        Case.simple<_, _, State.DeleteAccount> { AccountDeletion.handle(logic, scope, share(it), updateMethod(::updateState)) },
        Case.simple<_, _, State.About> { state -> AboutHandling.handle(state, updateMethod(::updateState), fragmentIds) },
        Case.simple<_, _, FragmentState> { state ->
            state.transform {
                fragmentIds.add(it.containerId)

                emit(Screen.FragmentScreen(it as State, it.toolbarIcons, it.toolbarOptions, it))
            }
        }
    ).shareIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 1)

    fun execute(command: UpdateStateCommand) {
        command.applyTo(state)
    }

    private fun updateState(method: (State) -> State): Unit = state.update(method)
}