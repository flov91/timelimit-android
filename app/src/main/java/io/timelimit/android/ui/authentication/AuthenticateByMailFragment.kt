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
package io.timelimit.android.ui.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class AuthenticateByMailFragment : Fragment() {
    companion object {
        private const val RECOVERY_USER_ID = "userId"

        fun newInstance(recoveryUserId: String) = AuthenticateByMailFragment().apply {
            arguments = Bundle().apply {
                putString(RECOVERY_USER_ID, recoveryUserId)
            }
        }
    }

    private val listener: AuthenticateByMailFragmentListener by lazy { parentFragment as AuthenticateByMailFragmentListener }
    private val model: AuthenticateByMailModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.recoveryUserIdLive.value = arguments?.getString(RECOVERY_USER_ID)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val token = model.getMailAuthToken()

                listener.onLoginSucceeded(token)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).also {
            it.setContent {
                val screenLive by model.screenLive.collectAsState(null)
                val screen = screenLive

                Scaffold(
                    snackbarHost = { SnackbarHost(model.snackbarHostState) }
                ) { paddingValues ->
                    if (screen != null) {
                        AuthenticateByMailScreen(
                            content = screen,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }
    }
}

interface AuthenticateByMailFragmentListener {
    fun onLoginSucceeded(mailAuthToken: String)
}
