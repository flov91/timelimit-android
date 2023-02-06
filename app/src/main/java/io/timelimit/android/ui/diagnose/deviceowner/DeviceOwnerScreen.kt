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
package io.timelimit.android.ui.diagnose.deviceowner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.model.diagnose.DeviceOwnerHandling
import io.timelimit.android.ui.overview.overview.ListCardCommon
import io.timelimit.android.ui.overview.overview.ListCommon

@Composable
fun DeviceOwnerScreen(
    screen: DeviceOwnerHandling.OwnerScreen,
    modifier: Modifier = Modifier
) {
    when (screen) {
        is DeviceOwnerHandling.OwnerScreen.Error -> Box(
            modifier = modifier
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                stringResource(R.string.diagnose_dom_error),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        is DeviceOwnerHandling.OwnerScreen.Normal -> Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(
                    R.string.diagnose_dom_delegate_title
                ),
                style = MaterialTheme.typography.h5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )

            for (app in screen.apps) {
                key (app.packageName) {
                    ListCardCommon.Card(
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            app.title ?: app.packageName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.h6
                        )

                        for (scope in screen.scopes) {
                            val enabled = app.scopes.contains(scope)

                            val click: () -> Unit = {
                                screen.actions.updateScopeEnabled(app.packageName, scope, !enabled)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = click),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = enabled,
                                    onCheckedChange = {
                                        screen.actions.updateScopeEnabled(app.packageName, scope, it)
                                    }
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(scope.label)
                            }
                        }
                    }
                }
            }

            ListCommon.ActionListItem(
                icon = Icons.Default.Add,
                label = stringResource(R.string.diagnose_dom_delegate_add_app),
                action = screen.actions.showAppListDialog
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (screen.appListDialog != null) AddAppDialog(screen.appListDialog, screen.actions)
        }
    }
}

@Composable
fun AddAppDialog(
    screen: DeviceOwnerHandling.OwnerScreen.Normal.AppListDialog,
    actions: DeviceOwnerHandling.OwnerScreen.Normal.Actions
) {
    AlertDialog(
        onDismissRequest = actions.dismissAppListDialog,
        buttons = {
            TextButton(onClick = actions.dismissAppListDialog) {
                Text(stringResource(R.string.generic_cancel))
            }
        },
        title = { Text(stringResource(R.string.diagnose_dom_delegate_add_app)) },
        text = {
            LazyColumn {
                item {
                    TextField(
                        value = screen.filter,
                        onValueChange = actions.updateDialogSearch,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(screen.apps, key = { it.packageName }) { app ->
                    Text(
                        app.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                actions.addApp(app.packageName)
                            })
                            .padding(8.dp)
                    )
                }
            }
        }
    )
}