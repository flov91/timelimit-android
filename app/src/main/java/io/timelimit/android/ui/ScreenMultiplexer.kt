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
import io.timelimit.android.ui.diagnose.deviceowner.DeviceOwnerScreen
import io.timelimit.android.ui.manage.device.manage.user.ManageDeviceUserScreen
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.overview.overview.OverviewScreen
import io.timelimit.android.ui.setup.SetupDevicePermissionsScreen

@Composable
fun ScreenMultiplexer(
    screen: Screen?,
    fragmentManager: FragmentManager,
    fragmentIds: MutableSet<Int>,
    modifier: Modifier = Modifier
) {
    when (screen) {
        null -> {/* nothing to do */ }
        is Screen.FragmentScreen -> FragmentScreen(screen, fragmentManager, fragmentIds, modifier = modifier)
        is Screen.OverviewScreen -> OverviewScreen(screen.content, modifier = modifier)
        is Screen.ManageDeviceUserScreen -> ManageDeviceUserScreen(screen.items, screen.actions, screen.overlay, modifier)
        is Screen.DeviceOwnerScreen -> DeviceOwnerScreen(screen.content, modifier = modifier)
        is Screen.SetupDevicePermissionsScreen -> SetupDevicePermissionsScreen(screen.content, screen.next, modifier)
    }
}