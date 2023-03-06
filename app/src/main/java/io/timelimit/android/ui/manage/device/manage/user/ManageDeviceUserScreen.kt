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
package io.timelimit.android.ui.manage.device.manage.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import io.timelimit.android.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.data.model.UserType
import io.timelimit.android.ui.model.managedevice.ManageDeviceUser
import io.timelimit.android.util.TimeTextUtil

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ManageDeviceUserScreen(
    items: List<ManageDeviceUser.UserItem>,
    actions: ManageDeviceUser.Actions,
    overlay: ManageDeviceUser.Overlay?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Card(
                onClick = { actions.select(item) },
                modifier = Modifier
                    .animateItemPlacement()
                    .fillMaxWidth(),
                backgroundColor = when (item.selected) {
                    true -> MaterialTheme.colors.secondary
                    false -> MaterialTheme.colors.surface
                }
            ) {
                val buttonColors =
                    if (item.selected) ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colors.onSecondary.copy(alpha = .8f),
                        disabledContentColor = MaterialTheme.colors.onSecondary
                    )
                    else ButtonDefaults.textButtonColors()

                Column(
                    Modifier.padding(8.dp)
                ) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.h5
                    )

                    if (item.defaultUser is ManageDeviceUser.UserItem.DefaultUser.Yes) {
                        Text(stringResource(R.string.manage_device_user_is_default_user))

                        if (item.defaultUser.timeout > 0) {
                            Text(stringResource(
                                R.string.manage_device_user_default_user_timeout,
                                if (item.defaultUser.timeout < 1000 * 60) TimeTextUtil.seconds(item.defaultUser.timeout / 1000, LocalContext.current)
                                else TimeTextUtil.time(item.defaultUser.timeout, LocalContext.current)
                            ))
                        }

                        TextButton(onClick = actions.disableDefaultUser, colors = buttonColors) {
                            Text(stringResource(R.string.manage_device_user_disable_default_user))
                        }

                        TextButton(onClick = actions.configureAutoSwitching, colors = buttonColors) {
                            Text(stringResource(
                                if (item.defaultUser.timeout == 0) R.string.manage_device_default_user_timeout_btn_enable
                                else R.string.manage_device_default_user_timeout_btn_change
                            ))
                        }
                    } else if (item.selected) {
                        TextButton(onClick = { actions.makeDefaultUser(item) }, colors = buttonColors) {
                            Text(stringResource(R.string.manage_device_user_make_default_user))
                        }
                    } else {
                        Text(when (item.type) {
                            UserType.Child -> stringResource(R.string.add_user_type_child)
                            UserType.Parent -> stringResource(R.string.add_user_type_parent)
                        })
                    }
                }
            }
        }
    }

    when (overlay) {
        is ManageDeviceUser.Overlay.EnableDefaultUser -> AlertDialog(
            title = { Text(stringResource(R.string.manage_device_default_user_title)) },
            text = {
                Column (
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.manage_device_default_user_info))
                    Text(stringResource(R.string.manage_device_default_user_confirm, overlay.userTitle))
                    Text(stringResource(R.string.purchase_required_info_local_mode_free))
                }
            },
            confirmButton = {
                TextButton(onClick = overlay.confirm) {
                    Text(stringResource(R.string.generic_set))
                }
            },
            dismissButton = {
                TextButton(onClick = overlay.cancel) {
                    Text(stringResource(R.string.generic_cancel))
                }
            },
            onDismissRequest = overlay.cancel
        )
        is ManageDeviceUser.Overlay.ConfigureTimeout -> AlertDialog(
            title = { Text(stringResource(R.string.manage_device_default_user_timeout_dialog_title)) },
            text = {
                val options = listOf(
                    0,
                    1000 * 5,
                    1000 * 60,
                    1000 * 60 * 5,
                    1000 * 60 * 15,
                    1000 * 60 * 30,
                    1000 * 60 * 60
                )

                Column {
                    for (option in options) {
                        val onClick = { overlay.confirm(option) }

                        val label =
                            if (option == 0) stringResource(R.string.manage_device_default_user_timeout_dialog_disable)
                            else if (option < 1000 * 60) TimeTextUtil.seconds(option / 1000, LocalContext.current)
                            else TimeTextUtil.time(option, LocalContext.current)

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = label, onClick = onClick),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == overlay.currentValue, onClick = onClick)
                            Text(label)
                        }
                    }

                    Text(stringResource(R.string.purchase_required_info_local_mode_free))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = overlay.cancel) {
                    Text(stringResource(R.string.generic_cancel))
                }
            },
            onDismissRequest = overlay.cancel
        )
        null -> Unit
    }
}