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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.main.OverviewHandling
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.util.TimeTextUtil

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun OverviewScreen(
    screen: OverviewHandling.OverviewScreen,
    executeCommand: (UpdateStateCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn (
        contentPadding = PaddingValues(0.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        if (screen.intro.showSetupOption) {
            item (key = Pair("intro", "finish setup")) {
                ListCardCommon.Card(
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.overview_finish_setup_title),
                        style = MaterialTheme.typography.h6
                    )

                    Text(stringResource(R.string.overview_finish_setup_text))

                    ListCardCommon.ActionButton(
                        label = stringResource(R.string.generic_go),
                        action = {
                            executeCommand(UpdateStateCommand.Overview.SetupDevice)
                        }
                    )
                }
            }
        }

        if (screen.intro.showOutdatedServer) {
            item (key = Pair("intro", "outdated server")) {
                ListCardCommon.Card(
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.overview_server_outdated_title),
                        style = MaterialTheme.typography.h6
                    )

                    Text(stringResource(R.string.overview_server_outdated_text))
                }
            }
        }

        if (screen.intro.showServerMessage != null) {
            item (key = Pair("intro", "servermessage")) {
                ListCardCommon.Card(
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.overview_server_message),
                        style = MaterialTheme.typography.h6
                    )

                    Text(screen.intro.showServerMessage)
                }
            }
        }

        if (screen.intro.showIntro) {
            item (key = Pair("intro", "intro")) {
                val state = remember {
                    DismissState(
                        initialValue = DismissValue.Default,
                        confirmStateChange = {
                            screen.actions.hideIntro()

                            true
                        }
                    )
                }

                SwipeToDismiss(
                    state = state,
                    background = {},
                    modifier = Modifier.animateItemPlacement()
                ) {
                    ListCardCommon.Card(
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.overview_intro_title),
                            style = MaterialTheme.typography.h6
                        )

                        Text(stringResource(R.string.overview_intro_text))

                        Text(
                            stringResource(R.string.generic_swipe_to_dismiss),
                            style = MaterialTheme.typography.subtitle1
                        )
                    }
                }
            }
        }

        if (screen.taskToReview != null) {
            item (key = Pair("task", "review")) {
                ListCardCommon.Card(
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.task_review_title),
                        style = MaterialTheme.typography.h6
                    )

                    Text(
                        stringResource(R.string.task_review_text, screen.taskToReview.task.childName, screen.taskToReview.task.childTask.taskTitle)
                    )

                    Text(
                        stringResource(
                            R.string.task_review_category,
                            TimeTextUtil.time(screen.taskToReview.task.childTask.extraTimeDuration, LocalContext.current),
                            screen.taskToReview.task.categoryTitle
                        ),
                        style = MaterialTheme.typography.subtitle1
                    )

                    screen.taskToReview.task.childTask.lastGrantTimestamp.let { lastGrantTimestamp ->
                        if (lastGrantTimestamp != 0L) {
                            Text(
                                stringResource(
                                    R.string.task_review_last_grant,
                                    DateUtil.formatAbsoluteDate(LocalContext.current, lastGrantTimestamp)
                                ),
                                style = MaterialTheme.typography.subtitle1
                            )
                        }
                    }

                    Row {
                        TextButton(onClick = {
                            screen.actions.skipTaskReview(screen.taskToReview)
                        }) {
                            Text(stringResource(R.string.generic_skip))
                        }

                        Spacer(Modifier.weight(1.0f))

                        OutlinedButton(onClick = { screen.actions.reviewReject(screen.taskToReview) }) {
                            Text(stringResource(R.string.generic_no))
                        }

                        Spacer(Modifier.width(8.dp))

                        OutlinedButton(onClick = { screen.actions.reviewAccept(screen.taskToReview) }) {
                            Text(stringResource(R.string.generic_yes))
                        }
                    }

                    Text(
                        stringResource(R.string.purchase_required_info_local_mode_free),
                        style = MaterialTheme.typography.subtitle1
                    )
                }
            }
        }

        item (key = Pair("devices", "header")) { ListCommon.SectionHeader(stringResource(R.string.overview_header_devices), Modifier.animateItemPlacement()) }
        items(screen.devices.list, key = { Pair("device", it.device.id) }) {
            DeviceItem(it, executeCommand)
        }
        if (screen.devices.canAdd) {
            item (key = Pair("devices", "add")) {
                ListCommon.ActionListItem(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.add_device),
                    action = screen.actions.addDevice,
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
        if (screen.devices.canShowMore != null) {
            item (key = Pair("devices", "show more")) { ListCommon.ShowMoreItem(modifier = Modifier.animateItemPlacement()) {
                executeCommand(UpdateStateCommand.Overview.ShowMoreDevices(screen.devices.canShowMore))
            }}
        }

        item (key = Pair("header", "users")) { ListCommon.SectionHeader(stringResource(R.string.overview_header_users), Modifier.animateItemPlacement()) }
        items(screen.users.list, key = { Pair("user", it.id) }) { UserItem(it, screen.actions) }
        if (screen.users.canAdd) item (key = Pair("header", "user.create")) {
            ListCommon.ActionListItem(
                icon = Icons.Default.Add,
                label = stringResource(R.string.add_user_title),
                action = { executeCommand(UpdateStateCommand.Overview.AddUser) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        if (screen.users.canShowMore) item (key = Pair("header", "user.more")) {
            ListCommon.ShowMoreItem (modifier = Modifier.animateItemPlacement()) { executeCommand(UpdateStateCommand.Overview.ShowAllUsers) }
        }
    }
}
