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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.timelimit.android.R
import io.timelimit.android.ui.model.intro.IntroHandling
import io.timelimit.android.ui.model.managechild.ManageCategoryBlockedTimes
import io.timelimit.android.ui.util.ImmutableList
import io.timelimit.android.ui.view.IntroCard

@Composable
fun BlockedTimesScreen(
    content: ManageCategoryBlockedTimes.Screen,
    intro: IntroHandling.Screen,
    modifier: Modifier = Modifier
) {
    val copyOptions = when (content) {
        is ManageCategoryBlockedTimes.Screen.Editing -> (0..7).map { CopyOption.Copy { content.onCopyClicked(it) } }
        is ManageCategoryBlockedTimes.Screen.Copying -> (0..7).map {
            if (it == content.from) CopyOption.Save(content.onConfirmClicked)
            else CopyOption.Paste(content.to.contains(it)) { content.onToggleDay(it) }
        }
    }.let { ImmutableList(it) }

    val scrollState = rememberScrollState()

    if (content.onBackPressed != null) BackHandler(onBack = content.onBackPressed)

    LaunchedEffect(intro is IntroHandling.Screen.Visible) {
        if (intro is IntroHandling.Screen.Visible) scrollState.animateScrollTo(0)
    }

    Column(
        modifier.verticalScroll(scrollState)
    ) {
        IntroCard(intro) {
            Text(stringResource(R.string.blocked_time_areas_help_about))
            Text(stringResource(R.string.blocked_time_areas_help_about_data))
            Text(stringResource(R.string.blocked_time_areas_help_colors))
            Text(stringResource(R.string.blocked_time_areas_help_structure))
            Text(stringResource(R.string.blocked_time_areas_help_modify))
            Text(stringResource(R.string.blocked_time_areas_help_copy))
        }

        BlockedTimesView(
            data = content.blockedTimeAreas,
            expandedHourOfWeek = if (content is ManageCategoryBlockedTimes.Screen.Editing) content.expandedHourOfWeek else null,
            selectedMinuteOfWeek = if (content is ManageCategoryBlockedTimes.Screen.Editing) content.selectedMinuteOfWeek else null,
            copyOptions = copyOptions,
            onHourClicked = if (content is ManageCategoryBlockedTimes.Screen.Editing) content.onHourClick else null,
            onMinuteClicked = if (content is ManageCategoryBlockedTimes.Screen.Editing) content.onMinuteClick else null
        )
    }
}