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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import io.timelimit.android.BuildConfig
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.model.launch.LaunchHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*

class MainModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "MainModel"
    }

    val activityModel = ActivityViewModel(application)

    private val logic = DefaultAppLogic.with(application)
    private val activityCommandInternal = Channel<ActivityCommand>()
    private val authenticationScreenClosed = MutableSharedFlow<Unit>()

    private val authenticationModelApi = object: AuthenticationModelApi {
        override val authenticatedParentOnly: Flow<AuthenticationModelApi.Parent?> =
            activityModel.authenticatedUser.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.Parent(pair.second, pair.first)
                else null
            }

        override val authenticatedParentOrSelfLimitAdding: Flow<AuthenticationModelApi.ParentOrChild?> =
            activityModel.authenticatedUserOrChild.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.ParentOrChild(pair.second, pair.first)
                else null
            }

        override suspend fun doParentAuthentication(): AuthenticationModelApi.Parent? {
            triggerAuthenticationScreen()

            authenticationScreenClosed.firstOrNull()

            return authenticatedParentOnly.firstOrNull()
        }

        override fun triggerAuthenticationScreen() {
            activityCommandInternal.trySend(ActivityCommand.ShowAuthenticationScreen)
        }
    }

    val activityCommand: ReceiveChannel<ActivityCommand> = activityCommandInternal
    val state = MutableStateFlow(State.LaunchState as State)

    val screen: Flow<Screen> = flow {
        while (true) {
            val scope = CoroutineScope(viewModelScope.coroutineContext + Job())

            when (state.value) {
                is State.LaunchState -> LaunchHandling.processLaunchState(state, logic)
                is State.Overview -> emitAll(OverviewHandling.processState(logic, scope, activityCommandInternal, authenticationModelApi, state))
                is FragmentState -> emitAll(state.transformWhile {
                    if (it is FragmentState && it !is State.Overview) {
                        emit(Screen.FragmentScreen(it, it.toolbarIcons, it.toolbarOptions, it))

                        true
                    } else false
                })
                else -> throw IllegalStateException()
            }

            scope.cancel()
        }
    }

    fun execute(command: UpdateStateCommand) {
        state.update { oldState ->
            command.transform(oldState) ?: oldState.also {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "execute($command) did not transform state")
                }
            }
        }
    }

    fun reportAuthenticationScreenClosed() {
        authenticationScreenClosed.tryEmit(Unit)
    }
}