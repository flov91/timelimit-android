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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.SystemPermission

@Composable
fun PermissionScreenDialog(
    dialog: PermissionScreenContent.Dialog,
    status: PermissionScreenContent.Status
) {
    val permissionIcon = PermissionVisualization.getIcon(dialog.permission)
    val permissionTitle = PermissionVisualization.getLabel(LocalContext.current, false, dialog.permission)
    val permissionDescription = PermissionVisualization.getDescription(LocalContext.current, dialog.permission)
    val permissionStatus = PermissionVisualization.getStatusText(LocalContext.current, dialog.permission, status)

    AlertDialog(
        onDismissRequest = dialog.close,
        title = {
            Row (
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(permissionIcon, permissionTitle)
                Text(permissionTitle)
            }
        },
        text = {
            Column (
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(permissionDescription, style = MaterialTheme.typography.body1)

                Text(permissionStatus, style = MaterialTheme.typography.body1)

                if (dialog.permission == SystemPermission.DeviceAdmin) {
                    val message =
                        if (status.maxProtectionLevel != ProtectionLevel.DeviceOwner) R.string.manage_device_permission_device_owner_unsupported
                        else if (status.protectionLevel != ProtectionLevel.DeviceOwner) R.string.manage_device_permission_device_owner_not_granted
                        else null

                    if (message != null) Text(stringResource(message), style = MaterialTheme.typography.body1)
                }

                Text(stringResource(R.string.manage_device_permission_link_only_info))
            }
        },
        dismissButton = {
            TextButton(onClick = dialog.close) {
                Text(stringResource(R.string.generic_cancel))
            }
        },
        confirmButton = {
            if (dialog.launchSystemSettings != null) {
                TextButton(onClick = dialog.launchSystemSettings) {
                    Text(stringResource(R.string.manage_device_permission_btn_modify))
                }
            }
        }
    )
}