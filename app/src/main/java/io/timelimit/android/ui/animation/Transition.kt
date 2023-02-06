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
package io.timelimit.android.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

@OptIn(ExperimentalAnimationApi::class)
object Transition {
    val openScreen = ContentTransform(
        targetContentEnter = slideInHorizontally { it },
        initialContentExit = slideOutHorizontally() + fadeOut(targetAlpha = .5f)
    )

    val closeScreen = ContentTransform(
        targetContentEnter = slideInHorizontally { -it / 2 } + fadeIn(initialAlpha = .5f),
        initialContentExit = slideOutHorizontally { it },
        targetContentZIndex = -1f
    )

    val swap = run {
        val speed = 250

        fun <T> phase1() = tween<T>(speed, 0, FastOutLinearInEasing)
        fun <T> phase2() = tween<T>(speed, speed, LinearOutSlowInEasing)

        ContentTransform(
            initialContentExit = slideOutVertically(phase1()) { it / 2 } + fadeOut(phase1()),
            targetContentEnter = slideInVertically(phase2()) { it / 2 } + fadeIn(phase2())
        )
    }

    val none = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None
    )
}