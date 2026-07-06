/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.ThemeRes
import io.timelimit.android.ui.manage.child.category.CategoryItemLeftPadding
import io.timelimit.android.ui.model.intro.IntroHandling
import io.timelimit.android.ui.model.managechild.ManageChildCategoryList
import io.timelimit.android.ui.overview.overview.ListCommon
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.ui.view.IntroCard
import io.timelimit.android.util.TimeTextUtil

@Composable
fun ManageChildScreen(intro: IntroHandling.Screen, screen: ManageChildCategoryList.Screen, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var listPosition by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val categoryItem = remember { Any() }

    LaunchedEffect(intro is IntroHandling.Screen.Visible) {
        if (intro is IntroHandling.Screen.Visible) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .onGloballyPositioned { listPosition = it },
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (intro is IntroHandling.Screen.Visible) {
            item("intro") {
                IntroCard(
                    intro,
                    padding = false,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.manage_child_categories_intro_title),
                        style = ThemeRes.cardTitle
                    )

                    Text(
                        stringResource(R.string.manage_child_categories_intro_text)
                    )
                }
            }
        }

        screen.appListSyncConsent?.let { consent ->
            item("consent") {
                Card(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.consent_app_list_sync_dialog_title),
                            style = ThemeRes.cardTitle
                        )

                        Text(stringResource(R.string.consent_app_list_sync_card_text))

                        Button(
                            onClick = consent.open,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.generic_show_details))
                        }
                    }
                }
            }
        }

        if (screen.hasDeviceManipulation) {
            item("manipulation") {
                Card(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    backgroundColor = ThemeRes.orangeBackground,
                    contentColor = ThemeRes.orangeBackgroundContent
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row (
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // described by label
                            Icon(Icons.Filled.Warning, contentDescription = null)

                            Text(
                                stringResource(R.string.manage_child_manipulation_title),
                                modifier = Modifier.weight(1f),
                                style = ThemeRes.cardTitle
                            )
                        }

                        Text(stringResource(R.string.manage_child_manipulation_text))
                    }
                }
            }
        }

        items (screen.categories, key = { it.id }, contentType = { categoryItem }) {
            ManageChildScreenCategory(
                item = it,
                reportDrag = { coordinates, offset ->
                    listPosition?.let { listPosition ->
                        val finalPosition = listPosition.localPositionOf(coordinates) + offset

                        val finalItem = listState.layoutInfo.visibleItemsInfo.find {
                            it.offset <= finalPosition.y && finalPosition.y <= it.offset + it.size
                        }

                        if (finalItem?.contentType != categoryItem) return@let

                        val otherCategoryId = finalItem.key as String

                        it.moveTo(otherCategoryId)
                    }
                },
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        item("add") {
            ListCommon.ActionListItem(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.create_category_title),
                action = screen.addCategory,
                modifier = Modifier
                    .animateItem()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ManageChildScreenCategory(
    item: ManageChildCategoryList.CategoryItem,
    reportDrag: (LayoutCoordinates, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemPosition by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Card(
        modifier
            .padding(start = CategoryItemLeftPadding.calculate(item.categoryNestingLevel))
            .onGloballyPositioned { itemPosition = it }
            .clickable(
                onClick = item.open,
                onClickLabel = stringResource(R.string.manage_child_category_open)
            )
            .pointerInput(item.id, reportDrag) {
                var currentOffset = Offset(0f, 0f)

                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        currentOffset = it
                    },
                    onDrag = { _, dragAmount ->
                        currentOffset += dragAmount
                    },
                    onDragEnd = {
                        itemPosition?.let { reportDrag(it, currentOffset) }
                    }
                )
            }
    ) {
        Row (
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column (
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    item.name,
                    style = ThemeRes.cardTitle
                )

                if (item.usedForNotAssignedApps) {
                    Text(stringResource(R.string.manage_child_category_for_unassigned_apps))
                }

                if (item.usedTimeToday > 0) {
                    Text(TimeTextUtil.used(item.usedTimeToday.toInt(), LocalContext.current))
                }

                item.remainingTimeToday?.let { remaining ->
                    Text(TimeTextUtil.remaining(remaining.toInt(), LocalContext.current))
                } ?: run {
                    Text(stringResource(R.string.manage_child_category_no_time_limits))
                }

                val iconAndMessage = when (item.mode) {
                    ManageChildCategoryList.CategorySpecialMode.None -> null
                    is ManageChildCategoryList.CategorySpecialMode.TemporarilyBlocked -> item.mode.endTime.let { endTime ->
                        val message =
                            if (endTime == null) stringResource(R.string.overview_user_item_temporarily_blocked)
                            else stringResource(
                                R.string.overview_user_item_temporarily_blocked_until,
                                DateUtil.formatAbsoluteDate(LocalContext.current, endTime)
                            )

                        message to Icons.Outlined.Lock
                    }
                    is ManageChildCategoryList.CategorySpecialMode.TemporarilyAllowed -> stringResource(
                        R.string.overview_user_item_temporarily_disabled_until,
                        DateUtil.formatAbsoluteDate(LocalContext.current, item.mode.endTime)
                    ) to Icons.Filled.AlarmOff
                }

                iconAndMessage?.let { (message, icon) ->
                    Row (
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null) // only decorative
                        Text(message, Modifier.weight(1f))
                    }
                }
            }

            // has label in R.string.manage_child_category_modify
            // but can not provide it here
            Switch(
                checked = item.mode is ManageChildCategoryList.CategorySpecialMode.None,
                onCheckedChange = { item.modify() },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}