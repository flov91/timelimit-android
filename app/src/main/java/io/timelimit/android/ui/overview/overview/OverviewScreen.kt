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
package io.timelimit.android.ui.overview.overview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.timelimit.android.ui.model.main.OverviewHandling

@Composable
fun OverviewScreen(
    screen: OverviewHandling.OverviewScreen,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn (
        contentPadding = object: PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = paddingValues.calculateLeftPadding(layoutDirection)
            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = paddingValues.calculateRightPadding(layoutDirection)
            override fun calculateTopPadding(): Dp = paddingValues.calculateTopPadding() + 8.dp
            override fun calculateBottomPadding(): Dp = paddingValues.calculateBottomPadding() + 8.dp
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        introItems(screen)
        deviceItems(screen)
        userItems(screen)
    }
}
