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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import io.timelimit.android.R
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.integration.platform.SystemPermission

object PermissionVisualization {
    fun getIcon(permission: SystemPermission): ImageVector = when (permission) {
        SystemPermission.UsageStats -> Icons.Default.BarChart
        SystemPermission.DeviceAdmin -> Icons.Default.Shield
        SystemPermission.Notification -> Icons.Default.Notifications
        SystemPermission.Overlay -> Icons.Default.Layers
        SystemPermission.AccessibilityService -> Icons.Default.AccessibilityNew
    }

    fun getLabel(context: Context, short: Boolean, permission: SystemPermission): String = context.getString(when (permission) {
        SystemPermission.UsageStats ->
            if (short) R.string.manage_device_permissions_usagestats_title_short
            else R.string.manage_device_permissions_usagestats_title
        SystemPermission.DeviceAdmin -> R.string.manage_device_permission_device_admin_title
        SystemPermission.Notification -> R.string.manage_device_permission_notification_access_title
        SystemPermission.Overlay -> R.string.manage_device_permissions_overlay_title
        SystemPermission.AccessibilityService -> R.string.manage_device_permission_accessibility_title
    })

    fun getDescription(context: Context, permission: SystemPermission): String = context.getString(when (permission) {
        SystemPermission.UsageStats -> R.string.manage_device_permissions_usagestats_text
        SystemPermission.DeviceAdmin -> R.string.manage_device_permission_device_admin_text
        SystemPermission.Notification -> R.string.manage_device_permission_notification_access_text
        SystemPermission.Overlay -> R.string.manage_device_permissions_overlay_text
        SystemPermission.AccessibilityService -> R.string.manage_device_permission_accessibility_text
    })

    fun getStatusColor(status: PermissionScreenContent.Status, permission: SystemPermission): Status = when (permission) {
        SystemPermission.UsageStats ->
            if (status.usageStats == RuntimePermissionStatus.Granted) Status.Good
            else Status.Neutral
        SystemPermission.DeviceAdmin ->
            if (status.protectionLevel != ProtectionLevel.None) Status.Good
            else Status.Neutral
        SystemPermission.Notification ->
            if (status.notificationAccess == NewPermissionStatus.Granted) Status.Good
            else Status.Neutral
        SystemPermission.Overlay ->
            if (status.overlay == RuntimePermissionStatus.Granted) Status.Good
            else Status.Neutral
        SystemPermission.AccessibilityService ->
            if (status.accessibility) Status.Good
            else Status.Neutral
    }

    fun getStatusText(context: Context, permission: SystemPermission, status: PermissionScreenContent.Status): String = when (permission) {
        SystemPermission.UsageStats ->
            context.getString(when (status.usageStats) {
                RuntimePermissionStatus.Granted -> R.string.manage_device_permission_status_granted
                RuntimePermissionStatus.NotGranted -> R.string.manage_device_permission_status_not_granted
                RuntimePermissionStatus.NotRequired -> R.string.manage_device_permission_status_not_required
            })
        SystemPermission.DeviceAdmin ->
            context.getString(when (status.protectionLevel) {
                ProtectionLevel.None -> R.string.manage_device_permission_device_admin_text_disabled
                ProtectionLevel.SimpleDeviceAdmin -> R.string.manage_device_permission_device_admin_text_simple
                ProtectionLevel.PasswordDeviceAdmin -> R.string.manage_device_permission_device_admin_text_password
                ProtectionLevel.DeviceOwner -> R.string.manage_device_permission_device_admin_text_owner
            })
        SystemPermission.Notification ->
            context.getString(when (status.notificationAccess) {
                NewPermissionStatus.Granted -> R.string.manage_device_permission_status_granted
                NewPermissionStatus.NotGranted -> R.string.manage_device_permission_status_not_granted
                NewPermissionStatus.NotSupported -> R.string.manage_device_permission_status_not_supported
            })
        SystemPermission.Overlay ->
            context.getString(when (status.overlay) {
                RuntimePermissionStatus.Granted -> R.string.manage_device_permission_status_granted
                RuntimePermissionStatus.NotGranted -> R.string.manage_device_permission_status_not_granted
                RuntimePermissionStatus.NotRequired -> R.string.manage_device_permission_status_not_required
            })
        SystemPermission.AccessibilityService ->
            context.getString(when (status.accessibility) {
                true -> R.string.manage_device_permission_status_granted
                false -> R.string.manage_device_permission_status_not_granted
            })
    }

    enum class Status {
        Neutral,
        Good,
    }
}