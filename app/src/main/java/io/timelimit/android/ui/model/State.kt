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

import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Phone
import androidx.fragment.app.Fragment
import io.timelimit.android.R
import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.ui.contacts.ContactsFragment
import io.timelimit.android.ui.diagnose.*
import io.timelimit.android.ui.diagnose.exitreason.DiagnoseExitReasonFragment
import io.timelimit.android.ui.fragment.*
import io.timelimit.android.ui.manage.category.ManageCategoryFragment
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.child.ManageChildFragment
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.device.manage.ManageDeviceFragment
import io.timelimit.android.ui.manage.device.manage.ManageDeviceFragmentArgs
import io.timelimit.android.ui.manage.device.manage.advanced.ManageDeviceAdvancedFragment
import io.timelimit.android.ui.manage.device.manage.advanced.ManageDeviceAdvancedFragmentArgs
import io.timelimit.android.ui.manage.device.manage.feature.ManageDeviceFeaturesFragment
import io.timelimit.android.ui.manage.device.manage.feature.ManageDeviceFeaturesFragmentArgs
import io.timelimit.android.ui.manage.parent.ManageParentFragment
import io.timelimit.android.ui.manage.parent.ManageParentFragmentArgs
import io.timelimit.android.ui.manage.parent.link.LinkParentMailFragment
import io.timelimit.android.ui.manage.parent.link.LinkParentMailFragmentArgs
import io.timelimit.android.ui.manage.parent.password.change.ChangeParentPasswordFragment
import io.timelimit.android.ui.manage.parent.password.change.ChangeParentPasswordFragmentArgs
import io.timelimit.android.ui.manage.parent.password.restore.RestoreParentPasswordFragment
import io.timelimit.android.ui.manage.parent.password.restore.RestoreParentPasswordFragmentArgs
import io.timelimit.android.ui.manage.parent.u2fkey.ManageParentU2FKeyFragment
import io.timelimit.android.ui.manage.parent.u2fkey.ManageParentU2FKeyFragmentArgs
import io.timelimit.android.ui.model.account.AccountDeletion
import io.timelimit.android.ui.model.diagnose.DeviceOwnerHandling
import io.timelimit.android.ui.model.main.OverviewHandling
import io.timelimit.android.ui.overview.uninstall.UninstallFragment
import io.timelimit.android.ui.parentmode.ParentModeFragment
import io.timelimit.android.ui.payment.PurchaseFragment
import io.timelimit.android.ui.payment.StayAwesomeFragment
import io.timelimit.android.ui.setup.*
import io.timelimit.android.ui.setup.child.SetupRemoteChildFragment
import io.timelimit.android.ui.setup.device.SetupDeviceFragment
import io.timelimit.android.ui.setup.parent.SetupParentModeFragment
import io.timelimit.android.ui.user.create.AddUserFragment
import java.io.Serializable

sealed class State (val previous: State?): Serializable {
    fun hasPrevious(other: State): Boolean = this.previous == other || this.previous?.hasPrevious(other) ?: false
    fun find(predicate: (State) -> Boolean): State? =
        if (predicate(this)) this
        else previous?.find(predicate)
    fun first(): State = previous?.first() ?: this
    object LaunchState: State(previous = null)
    data class Overview(
        val state: OverviewHandling.OverviewState = OverviewHandling.OverviewState.empty
    ): State(previous = null)
    class About(previous: Overview): FragmentStateLegacy(previous = previous, fragmentClass = AboutFragmentWrapped::class.java)
    class AddUser(previous: Overview): FragmentStateLegacy(previous = previous, fragmentClass = AddUserFragment::class.java)
    sealed class ManageChild(
        previous: State,
        fragmentClass: Class<out Fragment>,
        val childId: String,
        val previousOverview: Overview
    ): FragmentStateLegacy(previous, fragmentClass) {
        class Main(
            previousOverview: Overview,
            childId: String
        ): ManageChild(
            previous = previousOverview,
            fragmentClass = ManageChildFragment::class.java,
            childId = childId,
            previousOverview = previousOverview
        ) {
            override val arguments get() = ManageChildFragmentArgs(childId = childId, fromRedirect = false).toBundle()

            override val toolbarIcons: List<Menu.Icon> get() = listOf(
                Menu.Icon(
                    Icons.Default.DirectionsBike,
                    R.string.manage_child_tasks,
                    UpdateStateCommand.ManageChild.Tasks
                ),
                Menu.Icon(
                    Icons.Default.Phone,
                    R.string.contacts_title_long,
                    UpdateStateCommand.ManageChild.Contacts
                )
            )

            override val toolbarOptions: List<Menu.Dropdown> get() = listOf(
                Menu.Dropdown(R.string.child_apps_title, UpdateStateCommand.ManageChild.Apps),
                Menu.Dropdown(R.string.usage_history_title, UpdateStateCommand.ManageChild.UsageHistory),
                Menu.Dropdown(R.string.manage_child_tab_other, UpdateStateCommand.ManageChild.Advanced)
            )
        }

        sealed class Sub(
            previous: State,
            val previousMain: Main,
            fragmentClass: Class<out Fragment>
        ): ManageChild(previous, fragmentClass, previousMain.childId, previousMain.previousOverview)

        class Apps(val previousChild: Main): Sub(previousChild, previousChild, ChildAppsFragmentWrapper::class.java) {
            override val arguments: Bundle get() = ChildAppsFragmentWrapperArgs(previousChild.childId).toBundle()
        }
        class Advanced(val previousChild: Main): Sub(previousChild, previousChild, ChildAdvancedFragmentWrapper::class.java) {
            override val arguments: Bundle get() = ChildAdvancedFragmentWrapperArgs(previousChild.childId).toBundle()
        }
        class Contacts(val previousChild: Main): Sub(previousChild, previousChild, ContactsFragment::class.java)
        class UsageHistory(val previousChild: Main): Sub(previousChild, previousChild, ChildUsageHistoryFragmentWrapper::class.java) {
            override val arguments: Bundle get() = ChildUsageHistoryFragmentWrapperArgs(previousChild.childId).toBundle()
        }
        class Tasks(val previousChild: Main): Sub(previousChild, previousChild, ChildTasksFragmentWrapper::class.java) {
            override val arguments: Bundle get() = ChildTasksFragmentWrapperArgs(previousChild.childId).toBundle()
        }

        sealed class ManageCategory(
            previous: State,
            val previousChild: ManageChild.Main,
            val categoryId: String,
            fragmentClass: Class<out Fragment>
        ): Sub(previous, previousChild, fragmentClass) {
            class Main(
                previousChild: ManageChild.Main,
                categoryId: String
            ): ManageCategory(previousChild, previousChild, categoryId, ManageCategoryFragment::class.java) {
                override val arguments: Bundle get() = ManageCategoryFragmentArgs(
                    childId = previousChild.childId,
                    categoryId = categoryId
                ).toBundle()

                override val toolbarOptions: List<Menu.Dropdown> get() = listOf(
                    Menu.Dropdown(R.string.blocked_time_areas, UpdateStateCommand.ManageChild.BlockedTimes),
                    Menu.Dropdown(R.string.category_settings, UpdateStateCommand.ManageChild.CategoryAdvanced)
                )
            }

            sealed class Sub(
                previous: State,
                val previousCategory: Main,
                fragmentClass: Class<out Fragment>
            ): ManageCategory(previous, previousCategory.previousChild, previousCategory.categoryId, fragmentClass)

            class BlockedTimes(
                previousCategory: Main
            ): Sub(previousCategory, previousCategory, BlockedTimeAreasFragmentWrapper::class.java) {
                override val arguments: Bundle get() = BlockedTimeAreasFragmentWrapperArgs(
                    childId = previousCategory.previousChild.childId,
                    categoryId = previousCategory.categoryId
                ).toBundle()
            }

            class Advanced(
                previousCategory: Main
            ): Sub(previousCategory, previousCategory, CategoryAdvancedFragmentWrapper::class.java) {
                override val arguments: Bundle get() = CategoryAdvancedFragmentWrapperArgs(
                    childId = previousCategory.previousChild.childId,
                    categoryId = previousCategory.categoryId
                ).toBundle()
            }
        }
    }
    sealed class ManageParent(previous: State, fragmentClass: Class<out Fragment>): FragmentStateLegacy(previous = previous, fragmentClass = fragmentClass) {
        class Main(
            previous: Overview,
            val parentId: String
        ): ManageParent(previous = previous, fragmentClass = ManageParentFragment::class.java) {
            override val arguments get() = ManageParentFragmentArgs(parentId).toBundle()
        }

        class ChangePassword(val previousParent: Main): ManageParent(previousParent, ChangeParentPasswordFragment::class.java) {
            override val arguments: Bundle get() = ChangeParentPasswordFragmentArgs(previousParent.parentId).toBundle()
        }
        class RestorePassword(val previousParent: Main): ManageParent(previousParent, RestoreParentPasswordFragment::class.java) {
            override val arguments: Bundle get() = RestoreParentPasswordFragmentArgs(previousParent.parentId).toBundle()
        }
        class LinkMail(val previousParent: Main): ManageParent(previousParent, LinkParentMailFragment::class.java) {
            override val arguments: Bundle get() = LinkParentMailFragmentArgs(previousParent.parentId).toBundle()
        }
        class U2F(val previousParent: Main): ManageParent(previousParent, ManageParentU2FKeyFragment::class.java) {
            override val arguments: Bundle get() = ManageParentU2FKeyFragmentArgs(previousParent.parentId).toBundle()
        }
    }
    sealed class ManageDevice(
        previous: State,
        val previousOverview: Overview,
        val deviceId: String,
        fragmentClass: Class<out Fragment>
    ): FragmentStateLegacy(previous, fragmentClass) {
        class Main(
            previousOverview: Overview,
            deviceId: String
        ): ManageDevice(previousOverview, previousOverview, deviceId, ManageDeviceFragment::class.java) {
            override val arguments: Bundle get() = ManageDeviceFragmentArgs(deviceId).toBundle()
        }

        sealed class Sub(
            val previousManageDeviceMain: Main,
            fragmentClass: Class<out Fragment>
        ): ManageDevice(
            previousManageDeviceMain,
            previousManageDeviceMain.previousOverview,
            previousManageDeviceMain.deviceId,
            fragmentClass
        )

        data class User(
            val previousMain: Main,
            val overlay: Overlay? = null
        ): Sub(previousMain, Fragment::class.java) {
            sealed class Overlay: Serializable {
                data class EnableDefaultUserDialog(val userId: String): Overlay()
                object AdjustDefaultUserTimeout: Overlay()
            }
        }
        data class Permissions(val previousMain: Main, val currentDialog: SystemPermission? = null): Sub(previousMain, Fragment::class.java)
        class Features(previousMain: Main): Sub(previousMain, ManageDeviceFeaturesFragment::class.java) {
            override val arguments: Bundle get() = ManageDeviceFeaturesFragmentArgs(deviceId).toBundle()
        }
        class Advanced(previousMain: Main): Sub(previousMain, ManageDeviceAdvancedFragment::class.java) {
            override val arguments: Bundle get() = ManageDeviceAdvancedFragmentArgs(deviceId).toBundle()
        }
    }
    class SetupDevice(val previousOverview: Overview): FragmentStateLegacy(previous = previousOverview, fragmentClass = SetupDeviceFragment::class.java)
    data class DeleteAccount(
        val previousOverview: Overview,
        val content: AccountDeletion.MyState = AccountDeletion.MyState.Preparing()
    ): State(previousOverview)
    class Uninstall(previous: Overview): FragmentStateLegacy(previous = previous, fragmentClass = UninstallFragment::class.java)
    object DiagnoseScreen {
        class Main(previous: About): FragmentStateLegacy(previous, DiagnoseMainFragment::class.java)
        class Battery(previous: Main): FragmentStateLegacy(previous, DiagnoseBatteryFragment::class.java)
        class Clock(previous: Main): FragmentStateLegacy(previous, DiagnoseClockFragment::class.java)
        class Connection(previous: Main): FragmentStateLegacy(previous, DiagnoseConnectionFragment::class.java)
        class ExperimentalFlags(previous: Main): FragmentStateLegacy(previous, DiagnoseExperimentalFlagFragment::class.java)
        class ExitReasons(previous: Main): FragmentStateLegacy(previous, DiagnoseExitReasonFragment::class.java)
        class Crypto(previous: Main): FragmentStateLegacy(previous, DiagnoseCryptoFragment::class.java)
        class ForegroundApp(previous: Main): FragmentStateLegacy(previous, DiagnoseForegroundAppFragment::class.java)
        class Sync(previous: Main): FragmentStateLegacy(previous, DiagnoseSyncFragment::class.java)
        data class DeviceOwner(val previousMain: Main, val details: DeviceOwnerHandling.OwnerState = DeviceOwnerHandling.OwnerState()): State(previousMain)
    }
    sealed class Setup(previous: State): State(previous) {
        class SetupTerms: FragmentStateLegacy(previous = null, fragmentClass = SetupTermsFragment::class.java)
        class SetupHelpInfo(previous: SetupTerms): FragmentStateLegacy(previous = previous, fragmentClass = SetupHelpInfoFragment::class.java)
        class SelectMode(previous: SetupHelpInfo): Setup(previous)
        data class DevicePermissions(
            val previousSelectMode: SelectMode,
            val currentDialog: Dialog? = null
        ): Setup(previous = previousSelectMode) {
            sealed class Dialog

            data class SystemPermissionDialog(val permission: SystemPermission): Dialog()
            object ParentKeyDialog: Dialog()
        }
        class LocalMode(previous: DevicePermissions): FragmentStateLegacy(previous = previous, fragmentClass = SetupLocalModeFragment::class.java)
        class ConnectedPrivacy(previousSelectMode: SelectMode): Setup(previousSelectMode)
        class SelectConnectedMode(previousConnectedPrivacy: ConnectedPrivacy): Setup(previousConnectedPrivacy)
        class RemoteChild(previous: SelectConnectedMode): FragmentStateLegacy(previous = previous, fragmentClass = SetupRemoteChildFragment::class.java)
        class ParentMode(previous: SelectConnectedMode): FragmentStateLegacy(previous = previous, fragmentClass = SetupParentModeFragment::class.java)
    }
    class ParentMode: FragmentStateLegacy(previous = null, fragmentClass = ParentModeFragment::class.java)
    object Purchase {
        class Purchase(previous: About): FragmentStateLegacy(previous, PurchaseFragment::class.java)
        class StayAwesome(previous: About): FragmentStateLegacy(previous, StayAwesomeFragment::class.java)
    }
}