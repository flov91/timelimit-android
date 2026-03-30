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
package io.timelimit.android.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import io.timelimit.android.BuildConfig
import io.timelimit.android.extensions.onContainerAvailable
import io.timelimit.android.ui.model.Screen

private const val LOG_TAG = "FragmentScreen"

@Composable
fun FragmentScreen(
    screen: Screen.FragmentScreen,
    fragmentManager: FragmentManager,
    fragmentIds: MutableSet<Int>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).also {
                val containerId = screen.fragment.containerId
                val fragment = fragmentManager.findFragmentById(containerId)

                it.id = containerId

                if (fragment == null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "connect $screen")
                    }

                    fragmentManager.beginTransaction()
                        .replace(
                            containerId,
                            screen.fragment.fragmentClass,
                            screen.fragment.arguments
                        )
                        .commitAllowingStateLoss()
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "already bound $screen")
                    }

                    fragmentManager.beginTransaction()
                        .attach(fragment)
                        .commitAllowingStateLoss()
                }

                fragmentManager.onContainerAvailable(it)
                fragmentIds.add(containerId)
            }
        }
    )

    DisposableEffect(true) {
        onDispose {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "detach $screen")
            }

            fragmentManager.findFragmentById(screen.fragment.containerId)?.also { fragment ->
                fragmentManager.beginTransaction()
                    .detach(fragment)
                    .commitAllowingStateLoss()
            }
        }
    }
}