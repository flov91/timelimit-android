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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.timelimit.android.ui.util.ImmutableList

@Composable
fun BlockedTimesView(
    data: BlockedTimesData,
    expandedHourOfWeek: Int?,
    selectedMinuteOfWeek: Int?,
    copyOptions: ImmutableList<out CopyOption?>,
    onHourClicked: ((hourOfWeek: Int) -> Unit)?,
    onMinuteClicked: ((minuteOfWeek: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column (modifier) {
        for (day in 0 until 7) {
            BlockedTimesDay(
                day = day,
                data = data,
                expandedHourOfWeek = expandedHourOfWeek,
                selectedMinuteOfWeek = selectedMinuteOfWeek,
                copyOption = if (copyOptions.size > day) copyOptions[day] else null,
                onHourClicked = onHourClicked,
                onMinuteClicked = onMinuteClicked
            )
        }
    }
}