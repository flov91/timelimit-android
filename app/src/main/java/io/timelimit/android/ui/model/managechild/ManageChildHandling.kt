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
import io.timelimit.android.extensions.whileTrue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.Title
import kotlinx.coroutines.flow.*

object ManageChildHandling {
    fun processState(
        logic: AppLogic,
        stateLive: MutableStateFlow<State>
    ) = flow {
        while (true) when (stateLive.value) {
            is State.ManageChild.Main -> emitAll(processMainState(logic, stateLive))
            is State.ManageChild.Apps -> emitAll(processAppsState(logic, stateLive))
            else -> break
        }
    }

    private fun processMainState(
        logic: AppLogic,
        stateLive: MutableStateFlow<State>
    ): Flow<Screen> {
        val hasMatchingStateLive = stateLive.map { it is State.ManageChild.Main }
        val matchingState = stateLive.filterIsInstance<State.ManageChild.Main>()

        val screenLive = matchingState.transformLatest { state ->
            val userLive = logic.database.user().getUserByIdFlow(state.childId)

            emitAll(userLive.transform {user ->
                if (user?.type != UserType.Child) stateLive.compareAndSet(state, state.previousOverview)
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
                        ) { stateLive.compareAndSet(state, state.previousOverview) }
                    )
                ))
            })
        }

        return hasMatchingStateLive.whileTrue { screenLive }
    }

    private fun processAppsState(
        logic: AppLogic,
        stateLive: MutableStateFlow<State>
    ): Flow<Screen> {
        val hasMatchingStateLive = stateLive.map { it is State.ManageChild.Apps }
        val matchingState = stateLive.filterIsInstance<State.ManageChild.Apps>()

        val screenLive = matchingState.transformLatest { state ->
            val userLive = logic.database.user().getUserByIdFlow(state.childId)

            emitAll(userLive.transform {user ->
                if (user?.type != UserType.Child) stateLive.compareAndSet(state, state.previousChild.previousOverview)
                else emit(Screen.ManageChildAppsScreen(
                    state,
                    state.toolbarIcons,
                    state.toolbarOptions,
                    state,
                    R.id.fragment_manage_child_apps,
                    listOf(
                        BackStackItem(
                            Title.StringResource(R.string.main_tab_overview)
                        ) { stateLive.compareAndSet(state, state.previousChild.previousOverview) },
                        BackStackItem(
                            Title.Plain(user.name)
                        ) { stateLive.compareAndSet(state, state.previousChild) }
                    )
                ))
            })
        }

        return hasMatchingStateLive.whileTrue { screenLive }
    }
}