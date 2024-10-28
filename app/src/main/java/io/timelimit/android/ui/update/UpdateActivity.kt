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
package io.timelimit.android.ui.update

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.timelimit.android.R
import io.timelimit.android.databinding.UpdateActivityBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.ScreenScaffold
import io.timelimit.android.ui.Theme

class UpdateActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNightMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                if (isNightMode) android.graphics.Color.TRANSPARENT
                else resources.getColor(R.color.colorPrimaryDark)
            )
        )

        setContent {
            Theme {
                ScreenScaffold(
                    screen = null,
                    title = getString(R.string.app_name),
                    subtitle = null,
                    backStack = emptyList(),
                    snackbarHostState = null,
                    content = { padding ->
                        AndroidView(
                            factory = {
                                val binding = UpdateActivityBinding.inflate(LayoutInflater.from(it))

                                UpdateView.bind(
                                    view = binding.update,
                                    lifecycleOwner = this,
                                    fragmentManager = supportFragmentManager,
                                    appLogic = DefaultAppLogic.with(this)
                                )

                                binding.root
                            },
                            modifier = Modifier.fillMaxSize().padding(padding)
                        )
                    },
                    executeCommand = {},
                    showAuthenticationDialog = null
                )
            }
        }
    }
}