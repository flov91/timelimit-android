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

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.integration.platform.SystemPermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionGoals(status: PermissionScreenContent.Status) {
    PermissionGoal(
        stringResource(R.string.manage_device_permission_goal_limit_title),
        status.usageStats != RuntimePermissionStatus.NotGranted &&
                (!status.isQOrLater || status.overlay == RuntimePermissionStatus.Granted || status.accessibility),
    ) {
        if (status.usageStats != RuntimePermissionStatus.NotRequired) FlowRow {
            Text(stringResource(R.string.manage_device_permission_goal_needs))
            PermissionIcon(SystemPermission.UsageStats, status.usageStats == RuntimePermissionStatus.Granted)
        }

        if (status.isQOrLater) FlowRow {
            Text(stringResource(R.string.manage_device_permission_goal_needs))
            PermissionIcon(SystemPermission.Overlay, status.overlay == RuntimePermissionStatus.Granted)
            Text(stringResource(R.string.manage_device_permission_goal_or))
            PermissionIcon(SystemPermission.AccessibilityService, status.accessibility)
        }

        if (status.usageStats == RuntimePermissionStatus.NotRequired && !status.isQOrLater)
            Text(stringResource(R.string.manage_device_permission_goal_reached_by_old_android))
    }

    PermissionGoal(
        stringResource(R.string.manage_device_permission_goal_floating_window),
        status.accessibility
    ) {
        FlowRow {
            Text(stringResource(R.string.manage_device_permission_goal_eventually_needs))
            PermissionIcon(SystemPermission.AccessibilityService, status.accessibility)
        }
    }

    PermissionGoal(
        stringResource(R.string.manage_device_permission_goal_background_audio),
        status.notificationAccess == NewPermissionStatus.Granted
    ) {
        FlowRow {
            Text(stringResource(R.string.manage_device_permission_goal_needs))
            PermissionIcon(
                SystemPermission.Notification,
                status.notificationAccess == NewPermissionStatus.Granted
            )
        }
    }

    PermissionGoal(
        stringResource(R.string.manage_device_permission_goal_manipulation_protection),
        status.protectionLevel == ProtectionLevel.DeviceOwner
    ) {
        Text(stringResource(
            if (status.maxProtectionLevel == null)
                R.string.manage_device_permission_goal_manipulation_protection_check_remotely
            else if (status.maxProtectionLevel == ProtectionLevel.DeviceOwner)
                R.string.manage_device_permission_goal_needs_device_owner
            else
                R.string.manage_device_permission_goal_manipulation_protection_unavailable
        ))
    }
}

@Composable
fun PermissionGoal(
    title: String,
    checked: Boolean,
    content: @Composable () -> Unit
) {
    Card (
        elevation = 4.dp
    ) {
        Row (
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column (
                Modifier
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.h6)

                content()
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (checked) Icon(
                Icons.Default.CheckBox,
                stringResource(R.string.manage_device_permission_goal_reached),
                tint = MaterialTheme.colors.primary
            ) else Icon(
                Icons.Default.CheckBoxOutlineBlank,
                stringResource(R.string.manage_device_permission_goal_missed)
            )

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun PermissionIcon(permission: SystemPermission, checked: Boolean) {
    val icon = PermissionVisualization.getIcon(permission)
    val label = PermissionVisualization.getLabel(LocalContext.current, false, permission)

    val tint =
        if (checked) MaterialTheme.colors.primary
        else LocalContentColor.current

    Icon(
        icon,
        label,
        tint = tint
    )
}