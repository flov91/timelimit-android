package io.timelimit.android.ui.model

import android.view.View
import androidx.fragment.app.Fragment

abstract class FragmentStateLegacy(
    previous: State?,
    @Transient override val fragmentClass: Class<out Fragment>,
    override val containerId: Int = View.generateViewId()
): State(previous), FragmentState, java.io.Serializable {
    override fun toString(): String = fragmentClass.name
}