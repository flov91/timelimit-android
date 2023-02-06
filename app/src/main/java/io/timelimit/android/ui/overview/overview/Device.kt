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
package io.timelimit.android.ui.overview.overview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.main.OverviewHandling

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.DeviceItem(
    item: OverviewHandling.DeviceItem,
    executeCommand: (UpdateStateCommand) -> Unit
) {
    ListCardCommon.Card(
        Modifier
            .animateItemPlacement()
            .padding(horizontal = 8.dp)
            .clickable(
                onClick = {
                    executeCommand(UpdateStateCommand.Overview.ManageDevice(item.device.id))
                }
            )
    ) {
        ListCardCommon.TextWithIcon(
            icon = Icons.Default.Smartphone,
            label = stringResource(R.string.overview_device_item_name),
            value = item.device.name,
            style = MaterialTheme.typography.h6
        )

        if (item.userName != null) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.AccountCircle,
                label = stringResource(R.string.overview_device_item_user_name),
                value = item.userName
            )
        }

        if (item.device.isUserKeptSignedIn) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.LockOpen,
                label = stringResource(R.string.overview_device_item_password_disabled),
                value = stringResource(R.string.overview_device_item_password_disabled),
                multiline = true
            )
        }

        if (item.isConnected) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.Wifi,
                label = stringResource(R.string.overview_device_item_connected),
                value = stringResource(R.string.overview_device_item_connected),
                multiline = true
            )
        }

        if (item.device.currentAppVersion < BuildConfig.VERSION_CODE) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.Update,
                label = stringResource(R.string.overview_device_item_older_version),
                value = stringResource(R.string.overview_device_item_older_version),
                tint = MaterialTheme.colors.primary,
                multiline = true
            )
        }

        if (item.device.hasAnyManipulation) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.Warning,
                label = stringResource(R.string.overview_device_item_manipulation),
                value = stringResource(R.string.overview_device_item_manipulation),
                tint = MaterialTheme.colors.error,
                multiline = true
            )
        }

        if (item.isMissingRequiredPermission) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.Warning,
                label = stringResource(R.string.overview_device_item_missing_permission),
                value = stringResource(R.string.overview_device_item_missing_permission),
                tint = MaterialTheme.colors.error,
                multiline = true
            )
        }

        if (item.device.didReportUninstall) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.Warning,
                label = stringResource(R.string.overview_device_item_uninstall),
                value = stringResource(R.string.overview_device_item_uninstall),
                tint = MaterialTheme.colors.error,
                multiline = true
            )
        }

        if (item.isCurrentDevice) {
            ListCardCommon.TextWithIcon(
                icon = null,
                label = stringResource(R.string.manage_device_is_this_device),
                value = stringResource(R.string.manage_device_is_this_device),
                style = MaterialTheme.typography.subtitle1,
                multiline = true
            )
        }
    }
}