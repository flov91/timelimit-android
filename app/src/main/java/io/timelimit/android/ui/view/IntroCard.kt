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
package io.timelimit.android.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.model.intro.IntroHandling

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun IntroCard(
    intro: IntroHandling.Screen,
    content: @Composable () -> Unit
) {
    val currentIntro = rememberUpdatedState(intro)

    fun dismiss() = currentIntro.value.let {
        if (it is IntroHandling.Screen.Visible) {
            it.hide()
            true
        } else false
    }

    val state = remember { DismissState(DismissValue.Default) { dismiss() } }

    LaunchedEffect(intro is IntroHandling.Screen.Visible) {
        if (intro is IntroHandling.Screen.Visible) state.snapTo(DismissValue.Default)
    }

    AnimatedVisibility(
        visible = intro is IntroHandling.Screen.Visible,
        enter = fadeIn() + expandIn(initialSize = { IntSize(it.width, 0) }),
        exit = shrinkOut(targetSize = { IntSize(it.width, 0) }) + fadeOut(),
    ) {
        SwipeToDismiss(state = state, background = {/* empty */}) {
            Card (
                Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()

                    Text(
                        stringResource(R.string.generic_swipe_to_dismiss),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
            }
        }
    }
}