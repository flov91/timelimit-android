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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.timelimit.android.R
import io.timelimit.android.ui.util.ImmutableList

@Composable
fun BlockedTimesRow(
    tiles: ImmutableList<out Tile>
) {
    Row (
        Modifier.background(Color.LightGray)
    ) {
        for (tile in tiles) {
            when (tile) {
                is Tile.Regular -> Text(
                    tile.text,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1.0f)
                        .background(tile.mode.color())
                        .then(
                            if (tile.action != null) Modifier.clickable(onClick = tile.action)
                            else Modifier
                        )
                        .padding(vertical = 4.dp)
                )
                is Tile.Empty -> Spacer(Modifier.weight(1.0f))
            }
        }
    }
}

sealed class Tile {
    data class Regular(val text: String, val mode: Mode, val action: (() -> Unit)?): Tile()
    object Empty: Tile()

    enum class Mode {
        Allowed,
        Blocked,
        Mixed,
        Selected
    }
}

@Composable
fun Tile.Mode.color(): Color = when (this) {
    Tile.Mode.Allowed -> R.color.blockedtimes_green
    Tile.Mode.Blocked -> R.color.blockedtimes_red
    Tile.Mode.Mixed -> R.color.blockedtimes_yellow
    Tile.Mode.Selected -> R.color.colorPrimary
}.let { colorRes ->
    Color(
        ContextCompat.getColor(LocalContext.current, colorRes)
    )
}