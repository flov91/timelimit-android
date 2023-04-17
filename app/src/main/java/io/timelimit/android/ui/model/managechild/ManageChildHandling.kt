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
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.AuthenticationModelApi
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.Title
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*

object ManageChildHandling {
    fun processState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        state: Flow<State.ManageChild>,
        updateState: ((State.ManageChild) -> State) -> Unit
    ): Flow<Screen> = state.splitConflated(
        Case.withKey<_, _, State.ManageChild, _>(
            withKey = { it.childId },
            producer = { childId, state2 ->
                val state3 = share(state2)
                val userLive = logic.database.user().getUserByIdFlow(childId)

                val hasUserLive = userLive.map { it?.type == UserType.Child }.distinctUntilChanged()
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
                        Case.simple<_, _, State.ManageChild.Sub> { processSubState(logic, activityCommand, authentication, share(it), baseBackStackLive, childId, foundUserLive, updateMethod(updateState)) },
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

    private fun processSubState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: SharedFlow<State.ManageChild.Sub>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        childId: String,
        userLive: Flow<User>,
        updateState: ((State.ManageChild.Sub) -> State) -> Unit
    ): Flow<Screen> {
        val subBackStackLive = combine(stateLive, parentBackStackLive, userLive) { state, baseBackStack, user ->
            baseBackStack + BackStackItem(
                Title.Plain(user.name)
            ) { updateState { state.previousMain } }
        }

        return stateLive.splitConflated(
            Case.simple<_, _, State.ManageChild.Apps> { processAppsState(it, subBackStackLive) },
            Case.simple<_, _, State.ManageChild.Advanced> { processAdvancedState(it, subBackStackLive) },
            Case.simple<_, _, State.ManageChild.Contacts> { processContactsState(it, subBackStackLive) },
            Case.simple<_, _, State.ManageChild.UsageHistory> { processUsageHistoryState(logic, childId, share(it), updateMethod(updateState), subBackStackLive) },
            Case.simple<_, _, State.ManageChild.Tasks> { processTasksState(it, subBackStackLive) },
            Case.simple<_, _, State.ManageChild.ManageCategory> { ManageCategoryHandling.processState(logic, activityCommand, authentication, it, subBackStackLive, updateMethod(updateState)) },
        )
    }

    private fun processAppsState(
        stateLive: Flow<State.ManageChild.Apps>,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> = stateLive.combine(parentBackStackLive) { state, backStack ->
        Screen.ManageChildAppsScreen(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_child_apps,
            backStack
        )
    }

    private fun processAdvancedState(
        stateLive: Flow<State.ManageChild.Advanced>,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> = stateLive.combine(parentBackStackLive) { state, backStack ->
        Screen.ManageChildAdvancedScreen(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_child_advanced,
            backStack
        )
    }

    private fun processContactsState(
        stateLive: Flow<State.ManageChild.Contacts>,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> = stateLive.combine(parentBackStackLive) { state, backStack ->
        Screen.ManageChildContactsScreen(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_child_contacts,
            backStack
        )
    }

    private fun processUsageHistoryState(
        logic: AppLogic,
        childId: String,
        stateLive: SharedFlow<State.ManageChild.UsageHistory>,
        updateState: ((State.ManageChild.UsageHistory) -> State) -> Unit,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> {
        val nestedLive = ManageChildUsageHistory.handle(
            logic = logic,
            childId = childId,
            stateLive = stateLive.map { it.state },
            updateState = { modifier ->
                updateState { it.copy(state = modifier(it.state)) }
            }
        )

        return combine(stateLive, nestedLive, parentBackStackLive) { state, nested, backStack ->
            Screen.ChildUsageHistory(
                state,
                state.toolbarIcons,
                state.toolbarOptions,
                nested,
                backStack
            )
        }
    }

    private fun processTasksState(
        stateLive: Flow<State.ManageChild.Tasks>,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> = stateLive.combine(parentBackStackLive) { state, backStack ->
        Screen.ManageChildUsageTasks(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_child_tasks,
            backStack
        )
    }
}