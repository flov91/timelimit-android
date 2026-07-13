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
package io.timelimit.android.ui.model.main

import io.timelimit.android.R
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.Title
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

object AboutHandling {
    fun handle(
        state: Flow<State.About>,
        updateState: ((State.About) -> State) -> Unit,
        fragmentIds: MutableSet<Int>
    ): Flow<Screen> = state.transform {
        fragmentIds.add(it.containerId)

        emit(Screen.FragmentScreen(
            it as State, it.toolbarIcons, it.toolbarOptions, it,
            listOf(
                BackStackItem(
                    Title.StringResource(R.string.main_tab_overview),
                    action = {
                        updateState { it.previousOverview }
                    }
                )
            )
        ))
    }
}