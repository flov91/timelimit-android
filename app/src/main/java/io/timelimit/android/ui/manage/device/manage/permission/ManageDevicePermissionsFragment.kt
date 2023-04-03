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
package io.timelimit.android.ui.manage.device.manage.permission

import android.content.Context
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus

object ManageDevicePermissionsFragment {
    fun getPreviewText(device: Device, context: Context): String {
        val permissionLabels = mutableListOf<String>()

        if (device.currentUsageStatsPermission == RuntimePermissionStatus.Granted) {
            permissionLabels.add(context.getString(R.string.manage_device_permissions_usagestats_title_short))
        }

        if (device.currentNotificationAccessPermission == NewPermissionStatus.Granted) {
            permissionLabels.add(context.getString(R.string.manage_device_permission_notification_access_title))
        }

        if (device.currentProtectionLevel != ProtectionLevel.None) {
            permissionLabels.add(context.getString(R.string.manage_device_permission_device_admin_title))
        }

        if (device.currentOverlayPermission == RuntimePermissionStatus.Granted) {
            permissionLabels.add(context.getString(R.string.manage_device_permissions_overlay_title))
        }

        if (device.accessibilityServiceEnabled) {
            permissionLabels.add(context.getString(R.string.manage_device_permission_accessibility_title))
        }

        return if (permissionLabels.isEmpty()) {
            context.getString(R.string.manage_device_permissions_summary_none)
        } else {
            permissionLabels.joinToString(", ")
        }
    }
}