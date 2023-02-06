package io.timelimit.android.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

object Menu {
    data class Icon(val icon: ImageVector, val labelResource: Int, val action: UpdateStateCommand)
    data class Dropdown(val labelResource: Int, val action: UpdateStateCommand)
}