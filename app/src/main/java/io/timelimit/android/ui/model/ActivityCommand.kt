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
package io.timelimit.android.ui.model

import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.ui.manage.child.category.specialmode.SpecialModeDialogMode

sealed class ActivityCommand {
    object ShowCanNotAddDevicesInLocalModeDialogFragment: ActivityCommand()
    object ShowAddDeviceFragment: ActivityCommand()
    object ShowAuthenticationScreen: ActivityCommand()
    object ShowMissingPremiumDialog: ActivityCommand()
    class LaunchSystemSettings(val permission: SystemPermission): ActivityCommand()
    class TriggerUninstall(val packageName: String, val errorHandler: () -> Unit): ActivityCommand()
    object RequestNotifyPermission: ActivityCommand()
    object ShowSyncConsentDialog: ActivityCommand()
    data class ShowCreateCategoryDialog(val childId: String): ActivityCommand()
    data class ShowCategorySpecialModeDialog(val childId: String, val categoryId: String, val mode: SpecialModeDialogMode): ActivityCommand()
}