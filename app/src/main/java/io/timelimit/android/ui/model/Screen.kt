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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import io.timelimit.android.R
import io.timelimit.android.ui.model.main.OverviewHandling

sealed class Screen(
    val state: State,
    val toolbarIcons: List<Menu.Icon> = emptyList(),
    val toolbarOptions: List<Menu.Dropdown> = emptyList()
) {
    open class FragmentScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        val fragment: FragmentState
    ): Screen(state, toolbarIcons, toolbarOptions)

    class OverviewScreen(
        state: State,
        val content: OverviewHandling.OverviewScreen
    ): Screen(
        state,
        listOf(Menu.Icon(
            Icons.Outlined.Info,
            R.string.main_tab_about,
            UpdateStateCommand.Overview.LaunchAbout
        )),
        listOf(Menu.Dropdown(
            R.string.main_tab_uninstall,
            UpdateStateCommand.Overview.Uninstall
        ))
    ), ScreenWithAuthenticationFab
}

interface ScreenWithAuthenticationFab