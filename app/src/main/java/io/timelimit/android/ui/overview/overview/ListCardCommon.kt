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

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object ListCardCommon {
    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        androidx.compose.material.Card(
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }

    @Composable
    fun TextWithIcon(
        icon: ImageVector?,
        label: String,
        value: String,
        style: TextStyle = LocalTextStyle.current,
        tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
        multiline: Boolean = false
    ) {
        Row {
            if (icon != null) Icon(icon, label, tint = tint)
            else Spacer(Modifier.size(24.dp))

            Spacer(Modifier.width(8.dp))

            Text(
                value,
                modifier = Modifier.weight(1.0f),
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                style = style,
                color = tint
            )
        }
    }

    @Composable
    fun ActionButton(
        label: String,
        action: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Button(
                onClick = action
            ) {
                Text(label)
            }
        }
    }
}