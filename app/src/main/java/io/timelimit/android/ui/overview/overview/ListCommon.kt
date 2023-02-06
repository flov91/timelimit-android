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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.timelimit.android.R

object ListCommon {
    @Composable
    fun SectionHeader(text: String, modifier: Modifier = Modifier) {
        Text(
            text,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.h5
        )
    }

    @Composable
    fun ActionListItem(
        icon: ImageVector,
        label: String,
        action: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = label,
                    onClick = action
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun ShowMoreItem(modifier: Modifier = Modifier, action: () -> Unit) {
        ActionListItem(
            icon = Icons.Default.ExpandMore,
            label = stringResource(R.string.generic_show_more),
            action = action,
            modifier = modifier
        )
    }
}