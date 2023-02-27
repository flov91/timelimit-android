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
package io.timelimit.android.ui.model.managechild

import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.Title
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import kotlinx.coroutines.flow.*

object ManageChildHandling {
    fun processState(
        logic: AppLogic,
        state: Flow<State.ManageChild>,
        updateState: ((State.ManageChild) -> State) -> Unit
    ) = state.splitConflated(
        Case.simple<_, _, State.ManageChild.Main> { processMainState(logic, it, updateMethod(updateState)) },
        Case.simple<_, _, State.ManageChild.Apps> { processAppsState(logic, it, updateMethod(updateState)) }
    )

    private fun processMainState(
        logic: AppLogic,
        stateLive: Flow<State.ManageChild.Main>,
        updateState: ((State.ManageChild.Main) -> State) -> Unit
    ): Flow<Screen> {
        return stateLive.transformLatest { state ->
            val userLive = logic.database.user().getUserByIdFlow(state.childId)

            emitAll(userLive.transform {user ->
                if (user?.type != UserType.Child) updateState { state.previousOverview }
                else emit(Screen.ManageChildScreen(
                    state,
                    state.toolbarIcons,
                    state.toolbarOptions,
                    state,
                    R.id.fragment_manage_child,
                    user.name,
                    listOf(
                        BackStackItem(
                            Title.StringResource(R.string.main_tab_overview)
                        ) { updateState { state.previousOverview } }
                    )
                ))
            })
        }
    }

    private fun processAppsState(
        logic: AppLogic,
        stateLive: Flow<State.ManageChild.Apps>,
        updateState: ((State.ManageChild.Apps) -> State) -> Unit
    ): Flow<Screen> {
        return stateLive.transformLatest { state ->
            val userLive = logic.database.user().getUserByIdFlow(state.childId)

            emitAll(userLive.transform {user ->
                if (user?.type != UserType.Child) updateState { state.previousChild.previousOverview }
                else emit(Screen.ManageChildAppsScreen(
                    state,
                    state.toolbarIcons,
                    state.toolbarOptions,
                    state,
                    R.id.fragment_manage_child_apps,
                    listOf(
                        BackStackItem(
                            Title.StringResource(R.string.main_tab_overview)
                        ) { updateState { state.previousChild.previousOverview } },
                        BackStackItem(
                            Title.Plain(user.name)
                        ) { updateState { state.previousChild } }
                    )
                ))
            })
        }
    }
}