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

import androidx.compose.material.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import io.timelimit.android.R
import io.timelimit.android.ui.model.diagnose.DeviceOwnerHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import io.timelimit.android.ui.model.managedevice.ManageDeviceUser

sealed class Screen(
    val state: State,
    val toolbarIcons: List<Menu.Icon> = emptyList(),
    val toolbarOptions: List<Menu.Dropdown> = emptyList()
) {
    open class FragmentScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        val fragment: FragmentState,
        val containerId: Int
    ): Screen(state, toolbarIcons, toolbarOptions)

    class OverviewScreen(
        state: State,
        val content: OverviewHandling.OverviewScreen,
        override val snackbarHostState: SnackbarHostState
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
    ), ScreenWithAuthenticationFab, ScreenWithSnackbar

    class ManageChildScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        childName: String,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.Plain(childName)
    }

    class ManageChildAppsScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.child_apps_title)
    }

    class ManageChildAdvancedScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.manage_child_tab_other)
    }

    class ManageChildContactsScreen(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.contacts_title_long)
    }

    class ManageChildUsageHistory(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.usage_history_title)
    }

    class ManageChildUsageTasks(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.manage_child_tasks)
    }
    class ManageCategory(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        val categoryName: String,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.Plain(categoryName)
    }
    class ManageCategoryAdvanced(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.category_settings)
    }

    class ManageBlockedTimes(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.blocked_time_areas)
    }

    class ManageDevice(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        deviceName: String,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.Plain(deviceName)
    }

    class ManageDeviceUserScreen(
        state: State,
        override val backStack: List<BackStackItem>,
        override val snackbarHostState: SnackbarHostState,
        val items: List<ManageDeviceUser.UserItem>,
        val actions: ManageDeviceUser.Actions,
        val overlay: ManageDeviceUser.Overlay?
    ): Screen(state), ScreenWithBackStack, ScreenWithTitle, ScreenWithAuthenticationFab, ScreenWithSnackbar {
        override val title = Title.StringResource(R.string.manage_device_card_user_title)
    }

    class ManageDevicePermissions(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.manage_device_card_permission_title)
    }

    class ManageDeviceFeatures(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.manage_device_card_feature_title)
    }

    class ManageDeviceAdvances(
        state: State,
        toolbarIcons: List<Menu.Icon>,
        toolbarOptions: List<Menu.Dropdown>,
        fragment: FragmentState,
        containerId: Int,
        override val backStack: List<BackStackItem>
    ): FragmentScreen(state, toolbarIcons, toolbarOptions, fragment, containerId), ScreenWithBackStack, ScreenWithTitle {
        override val title = Title.StringResource(R.string.manage_device_card_manage_title)
    }

    class DeviceOwnerScreen(
        state: State,
        val content: DeviceOwnerHandling.OwnerScreen,
        override val snackbarHostState: SnackbarHostState
    ): Screen(state), ScreenWithAuthenticationFab, ScreenWithSnackbar, ScreenWithTitle {
        override val title = Title.StringResource(R.string.diagnose_dom_title)
    }
}

interface ScreenWithAuthenticationFab
interface ScreenWithSnackbar {
    val snackbarHostState: SnackbarHostState
}

interface ScreenWithTitle {
    val title: Title
}

interface ScreenWithBackStack {
    val backStack: List<BackStackItem>
}

data class BackStackItem(
    val title: Title,
    val action: () -> Unit
)

sealed class Title {
    data class Plain(val text: String): Title()
    data class StringResource(val id: Int): Title()
}