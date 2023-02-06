package io.timelimit.android.ui.model

import androidx.fragment.app.Fragment

abstract class FragmentStateLegacy(
    previous: State?,
    @Transient override val fragmentClass: Class<out Fragment>,
    override var containerId: Int? = null
): State(previous), FragmentState, java.io.Serializable {
    override fun toString(): String = fragmentClass.name
}