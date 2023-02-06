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
import androidx.lifecycle.viewModelScope
import io.timelimit.android.BuildConfig
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.model.launch.LaunchHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import kotlinx.coroutines.flow.*

class MainModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "MainModel"
    }

    private val logic = DefaultAppLogic.with(application)

    val state = MutableStateFlow(State.LaunchState as State)

    val screen: Flow<Screen> = flow {
        while (true) {
            when (state.value) {
                is State.LaunchState -> LaunchHandling.processLaunchState(state, logic)
                is State.Overview -> emitAll(OverviewHandling.processState(logic, viewModelScope, state))
                is FragmentState -> emitAll(state.transformWhile {
                    if (it is FragmentState && it !is State.Overview) {
                        emit(Screen.FragmentScreen(it, it.toolbarIcons, it.toolbarOptions, it))

                        true
                    } else false
                })
                else -> throw IllegalStateException()
            }
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
}