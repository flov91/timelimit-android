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
package io.timelimit.android.ui.manage.category.blocked_times

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.timelimit.android.ui.util.ImmutableList

@Composable
fun BlockedTimesDay(
    day: Int,
    data: BlockedTimesData,
    expandedHourOfWeek: Int?,
    selectedMinuteOfWeek: Int?,
    copyOption: CopyOption?,
    onHourClicked: ((hourOfWeek: Int) -> Unit)?,
    onMinuteClicked: ((minuteOfWeek: Int) -> Unit)?
) {
    val dayStartHourOfWeek = day * 24
    val dayStartMinuteOfWeek = dayStartHourOfWeek * 60

    val dayName = LocalContext.current.resources.getStringArray(R.array.days_of_week_array)[day]

    Column {
        Box {
            Text(
                dayName,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            when (copyOption) {
                is CopyOption.Copy -> IconButton(
                    onClick = copyOption.action,
                    Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.blocked_time_areas_copy_to_other_days))
                }
                is CopyOption.Paste -> IconButton(
                    onClick = copyOption.toggle,
                    Modifier.align(Alignment.CenterEnd)
                ) {
                    if (copyOption.highlight) Icon(
                        Icons.Default.ContentPaste,
                        stringResource(R.string.blocked_time_areas_copy_to_yes),
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colors.primary, CircleShape)
                            .padding(all = 8.dp)
                    )
                    else Icon(Icons.Default.ContentPasteOff, stringResource(R.string.blocked_time_areas_copy_to_no))
                }
                is CopyOption.Save -> IconButton(
                    onClick = copyOption.action,
                    Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Save,
                        stringResource(R.string.generic_save),
                        tint = MaterialTheme.colors.onSecondary,
                        modifier = Modifier
                            .background(MaterialTheme.colors.secondary, CircleShape)
                            .padding(all = 8.dp)
                    )
                }
                null -> {/* nothing to do */}
            }
        }

        for (row in 0 until 3) {
            key(row) {
                val rowStartHour = row * 8
                val emptyHourTiles = (0 until 8).map { Tile.Empty }

                val hourTiles = remember(day, data) {
                    val reader = data.ranges.readFrom(dayStartMinuteOfWeek + rowStartHour * 60)

                    (0 until 8).map { column ->
                        val hour = rowStartHour + column

                        val mode = reader.countSetBits(60).let {
                            if (it in 1..59) Tile.Mode.Mixed
                            else if (it == 0) Tile.Mode.Allowed
                            else if (it == 60) Tile.Mode.Blocked
                            else throw IllegalStateException()
                        }

                        Tile.Regular(
                            text = hour.toString().padStart(2, '0'),
                            mode = mode,
                            action = null
                        )
                    }
                }

                val realHourTiles = remember (day, data, selectedMinuteOfWeek, onHourClicked, hourTiles) {
                    hourTiles.mapIndexed { column, tile ->
                        val hour = rowStartHour + column
                        val hourStartOffset = dayStartMinuteOfWeek + hour * 60

                        val mode =
                            if ((hourStartOffset until hourStartOffset + 60).contains(selectedMinuteOfWeek)) Tile.Mode.Selected
                            else tile.mode

                        tile.copy(mode = mode, action = if (onHourClicked != null) ({ onHourClicked(dayStartHourOfWeek + hour) }) else null)
                    }.let { ImmutableList(it) }
                }

                val expandedHourIndex =
                    if (expandedHourOfWeek == null) null
                    else (expandedHourOfWeek - rowStartHour - dayStartHourOfWeek).let {
                        if (it < 0) null
                        else if (it >= emptyHourTiles.size) null
                        else it
                    }

                var lastExpandedHourIndex by remember { mutableStateOf<Int?>(null) }
                val expandAnimation = remember { MutableTransitionState(false) }
                val currentLastExpandedHourIndex = lastExpandedHourIndex

                LaunchedEffect(expandedHourIndex) {
                    if (expandedHourIndex != null) lastExpandedHourIndex = expandedHourIndex

                    expandAnimation.targetState = expandedHourIndex != null
                }

                if (expandedHourIndex == null) BlockedTimesRow(realHourTiles)
                else realHourTiles.content.subList(0, expandedHourIndex).let {
                    BlockedTimesRow(
                        ImmutableList(it +
                                realHourTiles[expandedHourIndex].copy(mode = Tile.Mode.Selected) +
                                emptyHourTiles.subList(expandedHourIndex + 1, emptyHourTiles.size))
                    )
                }

                if (currentLastExpandedHourIndex != null) AnimatedVisibility(visibleState = expandAnimation) {
                    Column {
                        BlockedTimesHour(
                            hourOfWeek = dayStartHourOfWeek + rowStartHour + currentLastExpandedHourIndex,
                            data = data,
                            selectedMinuteOfWeek = selectedMinuteOfWeek,
                            onMinuteClick = onMinuteClicked
                        )

                        realHourTiles.content.subList(currentLastExpandedHourIndex + 1, realHourTiles.size).let {
                            if (it.isNotEmpty()) BlockedTimesRow(
                                ImmutableList(emptyHourTiles.subList(0, currentLastExpandedHourIndex + 1) + it)
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class CopyOption {
    class Copy(val action: () -> Unit): CopyOption()
    class Paste(val highlight: Boolean, val toggle: () -> Unit): CopyOption()
    class Save(val action: () -> Unit): CopyOption()
}