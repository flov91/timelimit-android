package io.timelimit.android.ui.animation

import androidx.compose.animation.*

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

    val none = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None
    )
}