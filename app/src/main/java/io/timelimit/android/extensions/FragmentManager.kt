package io.timelimit.android.extensions

import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager

private val onContainerAvailable = FragmentManager::class.java.getDeclaredMethod(
    "onContainerAvailable",
    FragmentContainerView::class.java
).also { it.isAccessible = true }

fun FragmentManager.onContainerAvailable(view: FragmentContainerView) = onContainerAvailable.invoke(this, view)