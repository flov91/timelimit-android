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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.timelimit.android.ui.util.ImmutableList

@Composable
fun BlockedTimesHour(
    hourOfWeek: Int,
    data: BlockedTimesData,
    selectedMinuteOfWeek: Int?,
    onMinuteClick: ((minuteOfWeek: Int) -> Unit)?
) {
    val startMinuteOfWeek = hourOfWeek * 60

    val tileModes = remember (hourOfWeek, data) {
        val reader = data.ranges.readFrom(startMinuteOfWeek)

        (0 until 60).map {
            when (reader.countSetBits(1)) {
                0 -> Tile.Mode.Allowed
                1 -> Tile.Mode.Blocked
                else -> throw IllegalStateException()
            }
        }
    }

    Card(
        Modifier
            .background(Color.LightGray)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        elevation = 4.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            for (row in 0 until 6) {
                val tiles = (0 until 10).map { column ->
                    val minuteOfHour = row * 10 + column
                    val minuteOfWeek = startMinuteOfWeek + minuteOfHour

                    val mode =
                        if (minuteOfWeek == selectedMinuteOfWeek) Tile.Mode.Selected
                        else tileModes[minuteOfHour]

                    Tile.Regular(
                        text = minuteOfHour.toString().padStart(2, '0'),
                        mode = mode,
                        action = if (onMinuteClick != null) ({ onMinuteClick(minuteOfWeek) }) else null
                    )
                }.let { ImmutableList(it) }

                BlockedTimesRow(tiles)
            }
        }
    }
}