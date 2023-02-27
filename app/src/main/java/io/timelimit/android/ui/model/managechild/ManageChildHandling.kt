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
import io.timelimit.android.data.model.User
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
    ): Flow<Screen> = state.splitConflated(
        Case.withKey<_, _, State.ManageChild, _>(
            withKey = { it.childId },
            producer = { childId, state2 ->
                val state3 = share(state2)
                val userLive = logic.database.user().getUserByIdFlow(childId)

                val hasUserLive = userLive.map { it != null }.distinctUntilChanged()
                val foundUserLive = userLive.filterNotNull()

                val baseBackStackLive = state3.map { state ->
                    listOf(
                        BackStackItem(
                            Title.StringResource(R.string.main_tab_overview)
                        ) { updateState { state.previousOverview } }
                    )
                }

                hasUserLive.transformLatest { hasUser ->
                    if (hasUser) emitAll(state3.splitConflated(
                        Case.simple<_, _, State.ManageChild.Main> { processMainState(it, baseBackStackLive, foundUserLive) },
                        Case.simple<_, _, State.ManageChild.Apps> { processAppsState(share(it), baseBackStackLive, foundUserLive, updateMethod(updateState)) }
                    ))
                    else updateState { it.previousOverview }
                }
            }
        )
    )

    private fun processMainState(
        stateLive: Flow<State.ManageChild.Main>,
        baseBackStackLive: Flow<List<BackStackItem>>,
        userLive: Flow<User>
    ): Flow<Screen> = combine(stateLive, baseBackStackLive, userLive) { state, backStack, user ->
        Screen.ManageChildScreen(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_child,
            user.name,
            backStack
        )
    }

    private fun processAppsState(
        stateLive: SharedFlow<State.ManageChild.Apps>,
        baseBackStackLive: Flow<List<BackStackItem>>,
        userLive: Flow<User>,
        updateState: ((State.ManageChild.Apps) -> State) -> Unit
    ): Flow<Screen> {
        val subBackStackLive = combine(stateLive, baseBackStackLive, userLive) { state, baseBackStack, user ->
            baseBackStack + BackStackItem(
                Title.Plain(user.name)
            ) { updateState { state.previousChild } }
        }

        return stateLive.combine(subBackStackLive) { state, backStack ->
            Screen.ManageChildAppsScreen(
                state,
                state.toolbarIcons,
                state.toolbarOptions,
                state,
                R.id.fragment_manage_child_apps,
                backStack
            )
        }
    }
}