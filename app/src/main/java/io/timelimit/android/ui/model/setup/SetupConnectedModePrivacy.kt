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
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

object SetupConnectedModePrivacy {
    fun handle(
        logic: AppLogic,
        stateLive: Flow<State.Setup.ConnectedPrivacy>,
        updateState: ((State.Setup.ConnectedPrivacy) -> State) -> Unit
    ): Flow<Screen> {
        val domainLive = logic.database.config().getCustomServerUrlFlow().map { it.ifEmpty { null } }

        return combine(stateLive, domainLive) { state, domain ->
            Screen.SetupConnectModePrivacyScreen(
                state = state,
                customServerDomain = domain,
                accept = { updateState { State.Setup.SelectConnectedMode(it) } }
            )
        }
    }
}