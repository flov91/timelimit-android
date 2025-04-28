/*
 * TimeLimit Copyright <C> 2019 - 2025 Jonas Lochmann
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.ui.model.main.OverviewHandling

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.deviceItems(screen: OverviewHandling.OverviewScreen) {
    item (key = Pair("devices", "header")) {
        ListCommon.SectionHeader(stringResource(R.string.overview_header_devices), Modifier.animateItem())
    }

    items(screen.devices.list, key = { Pair("device", it.device.id) }) {
        DeviceItem(it, screen.actions.openDevice)
    }

    if (screen.devices.canAdd) {
        item (key = Pair("devices", "add")) {
            ListCommon.ActionListItem(
                icon = Icons.Default.Add,
                label = stringResource(R.string.add_device),
                action = screen.actions.addDevice,
                modifier = Modifier.animateItem()
            )
        }
    }

    if (screen.devices.canShowMore != null) {
        item (key = Pair("devices", "more")) {
            ListCommon.ShowMoreItem(
                modifier = Modifier.animateItem(),
                action = { screen.actions.showMoreDevices(screen.devices.canShowMore) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.DeviceItem(
    item: OverviewHandling.DeviceItem,
    openAction: (OverviewHandling.DeviceItem) -> Unit
) {
    ListCardCommon.Card(
        Modifier
            .animateItem()
            .padding(horizontal = 8.dp)
            .clickable(onClick = { openAction(item) })
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