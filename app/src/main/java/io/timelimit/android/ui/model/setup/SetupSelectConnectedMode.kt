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

import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SetupSelectConnectedMode {
    fun handle(
        stateLive: Flow<State.Setup.SelectConnectedMode>,
        updateState: ((State.Setup.SelectConnectedMode) -> State) -> Unit
    ): Flow<Screen> {
        return stateLive.map { state ->
            Screen.SetupSelectConnectedModeScreen(
                state = state,
                mailLogin = { updateState { State.Setup.ParentMailAuthentication(it) } },
                codeLogin = { updateState { State.Setup.RemoteChild(it) } }
            )
        }
    }
}