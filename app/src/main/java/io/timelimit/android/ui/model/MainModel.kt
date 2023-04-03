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
package io.timelimit.android.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.model.diagnose.DeviceOwnerHandling
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import io.timelimit.android.ui.model.launch.LaunchHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import io.timelimit.android.ui.model.managechild.ManageChildHandling
import io.timelimit.android.ui.model.managedevice.ManageDeviceHandling
import io.timelimit.android.ui.model.setup.SetupLocalModePermissions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*

class MainModel(application: Application): AndroidViewModel(application) {
    companion object {
        private val viewIdPool = setOf(
            R.id.fragment_01,
            R.id.fragment_02,
            R.id.fragment_03,
            R.id.fragment_04,
            R.id.fragment_05,
            R.id.fragment_06,
            R.id.fragment_07,
            R.id.fragment_08,
            R.id.fragment_09,
            R.id.fragment_10,
            R.id.fragment_11,
            R.id.fragment_12,
            R.id.fragment_13,
            R.id.fragment_14,
            R.id.fragment_15,
            R.id.fragment_16
        )
    }

    val activityModel = ActivityViewModel(application)
    val logic = DefaultAppLogic.with(application)

    private val activityCommandInternal = Channel<ActivityCommand>()
    private val authenticationScreenClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val authenticationModelApi = object: AuthenticationModelApi {
        override val authenticatedParentOnly: Flow<AuthenticationModelApi.Parent?> =
            activityModel.authenticatedUser.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.Parent(pair.second, pair.first)
                else null
            }

        override val authenticatedParentOrCurrentChild: Flow<AuthenticationModelApi.ParentOrChild?> =
            activityModel.authenticatedUserOrChild.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.ParentOrChild(pair.second, pair.first)
                else null
            }

        override suspend fun doParentAuthentication(): AuthenticationModelApi.Parent? {
            authenticatedParentOnly.firstOrNull()?.let { return it }

            triggerAuthenticationScreen()

            authenticationScreenClosed.firstOrNull()

            return authenticatedParentOnly.firstOrNull()
        }

        override suspend fun doParentOrChildAuthentication(childId: String): AuthenticationModelApi.ParentOrChild? {
            authenticatedParentOrCurrentChild.firstOrNull()?.let {
                if (it.user.type == UserType.Parent || it.user.id == childId) return it
            }

            triggerAuthenticationScreen()

            authenticationScreenClosed.firstOrNull()

            return authenticatedParentOrCurrentChild.firstOrNull()
        }

        override fun triggerAuthenticationScreen() {
            activityCommandInternal.trySend(ActivityCommand.ShowAuthenticationScreen)
        }
    }

    val activityCommand: ReceiveChannel<ActivityCommand> = activityCommandInternal
    val state = MutableStateFlow(State.LaunchState as State)
    var fragmentIds = mutableSetOf<Int>()

    val screen: Flow<Screen> = state.splitConflated(
        Case.simple<_, _, State.LaunchState> { LaunchHandling.processLaunchState(state, logic) },
        Case.simple<_, _, State.Overview> { OverviewHandling.processState(logic, scope, activityCommandInternal, authenticationModelApi, state) },
        Case.simple<_, _, State.ManageChild> { state -> ManageChildHandling.processState(logic, state, updateMethod(::updateState)) },
        Case.simple<_, _, State.ManageDevice> { state -> ManageDeviceHandling.processState(logic, activityCommandInternal, authenticationModelApi, state, updateMethod(::updateState)) },
        Case.simple<_, _, State.DiagnoseScreen.DeviceOwner> { DeviceOwnerHandling.processState(logic, scope, authenticationModelApi, state) },
        Case.simple<_, _, State.Setup.DevicePermissions> { state -> SetupLocalModePermissions.handle(logic, activityCommandInternal, state, updateMethod(::updateState)) },
        Case.simple<_, _, FragmentState> { state ->
            state.transform {
                val containerId = it.containerId ?: run {
                    (viewIdPool - fragmentIds).firstOrNull()?.also { id ->
                        it.containerId = id
                    }
                }

                if (containerId != null) {
                    fragmentIds.add(containerId)

                    emit(Screen.FragmentScreen(it as State, it.toolbarIcons, it.toolbarOptions, it, containerId))
                }
            }
        }
    ).shareIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 1)

    fun execute(command: UpdateStateCommand) {
        command.applyTo(state)
    }

    fun reportAuthenticationScreenClosed() {
        authenticationScreenClosed.tryEmit(Unit)
    }

    private fun updateState(method: (State) -> State): Unit = state.update(method)
}