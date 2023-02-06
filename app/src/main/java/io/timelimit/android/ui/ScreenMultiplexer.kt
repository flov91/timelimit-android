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
package io.timelimit.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentManager
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.overview.overview.OverviewScreen

@Composable
fun ScreenMultiplexer(
    screen: Screen?,
    executeCommand: (UpdateStateCommand) -> Unit,
    fragmentManager: FragmentManager,
    fragmentIds: MutableSet<Int>,
    modifier: Modifier = Modifier
) {
    when (screen) {
        null -> {/* nothing to do */ }
        is Screen.OverviewScreen -> OverviewScreen(screen.content, modifier = modifier)
        is Screen.FragmentScreen -> FragmentScreen(screen, fragmentManager, fragmentIds, modifier = modifier)
    }
}