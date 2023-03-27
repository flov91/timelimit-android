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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.timelimit.android.integration.platform.SystemPermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionScreenPermissionList(
    status: PermissionScreenContent.Status,
    showDetails: (SystemPermission) -> Unit
) {
    val permissions = listOf(
        SystemPermission.UsageStats,
        SystemPermission.DeviceAdmin,
        SystemPermission.Notification,
        SystemPermission.Overlay,
        SystemPermission.AccessibilityService
    )

    FlowRow (
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 3
    ) {
        for (permission in permissions) {
            val permissionIcon = PermissionVisualization.getIcon(permission)
            val permissionStatus = PermissionVisualization.getStatusColor(status, permission)
            val permissionLabel = PermissionVisualization.getLabel(LocalContext.current, false, permission)

            Box (
                Modifier.padding(vertical = 8.dp)
            ) {
                Card(
                    modifier = Modifier.clickable(
                        onClick = { showDetails(permission) },
                        onClickLabel = permissionLabel
                    ),
                    backgroundColor = when (permissionStatus) {
                        PermissionVisualization.Status.Neutral -> MaterialTheme.colors.surface
                        PermissionVisualization.Status.Good -> MaterialTheme.colors.primary
                    },
                    elevation = 8.dp
                ) {
                    Icon(
                        permissionIcon,
                        permissionLabel,
                        Modifier
                            .padding(16.dp)
                            .size(64.dp)
                    )
                }
            }
        }
    }
}