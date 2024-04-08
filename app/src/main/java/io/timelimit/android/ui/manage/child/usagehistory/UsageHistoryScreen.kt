/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child.usagehistory

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.ui.model.managechild.ManageChildUsageHistory
import io.timelimit.android.util.TimeTextUtil
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UsageHistoryScreen(
    content: ManageChildUsageHistory.Screen,
    modifier: Modifier = Modifier
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            TextField(
                value = content.currentCategory?.name ?: stringResource(R.string.usage_history_filter_all_categories),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                DropdownMenuItem(onClick = { content.selectCategory(null); dropdownExpanded = false }) {
                    Text(stringResource(R.string.usage_history_filter_all_categories))
                }

                for (category in content.categories) {
                    DropdownMenuItem(onClick = { content.selectCategory(category); dropdownExpanded = false }) {
                        Text(category.name)
                    }
                }
            }
        }

        Crossfade(content.items.isEmpty(), Modifier.weight(1f)) { isEmpty ->
            if (isEmpty) Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Text(
                    stringResource(R.string.usage_history_empty_title),
                    style = MaterialTheme.typography.h5,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(
                    R.string.usage_history_empty_text),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else {
                LazyColumn (
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items (content.items) { item ->
                        val dateStringPrefix = if (content.currentCategory == null) item.categoryTitle + " - " else ""

                        val timeAreaString = if (item.startMinuteOfDay == MinuteOfDay.MIN && item.endMinuteOfDay == MinuteOfDay.MAX)
                            null
                        else
                            stringResource(R.string.usage_history_time_area, MinuteOfDay.format(item.startMinuteOfDay), MinuteOfDay.format(item.endMinuteOfDay))

                        if (item.day != null) {
                            val dateObject = LocalDate.ofEpochDay(item.day)
                            val dateString = DateFormat.getDateFormat(LocalContext.current).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(dateObject.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L))

                            Text(
                                dateStringPrefix + dateString,
                                style = MaterialTheme.typography.h6
                            )

                            if (timeAreaString != null) Text(timeAreaString)

                            Text(TimeTextUtil.used(item.duration.toInt()))
                        } else if (item.lastUsage != null && item.maxSessionDuration != null && item.pauseDuration != null) {
                            val dateString = stringResource(
                                R.string.usage_history_item_session_duration_limit,
                                TimeTextUtil.time(item.maxSessionDuration.toInt()),
                                TimeTextUtil.time(item.pauseDuration.toInt())
                            )

                            Text(
                                dateStringPrefix + dateString,
                                style = MaterialTheme.typography.h6
                            )

                            if (timeAreaString != null) Text(timeAreaString)

                            Text(TimeTextUtil.used(item.duration.toInt()))

                            Text(stringResource(
                                R.string.usage_history_item_last_usage,
                                DateUtils.formatDateTime(LocalContext.current, item.lastUsage, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)
                            ))
                        } else {
                            Text("???")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeTextUtil.used(duration: Int) = used(duration, LocalContext.current)

@Composable
private fun TimeTextUtil.time(time: Int) = time(time, LocalContext.current)