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
package io.timelimit.android.ui.setup.child

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.databinding.SetupRemoteChildFragmentBinding
import io.timelimit.android.extensions.setOnEnterListenr
import io.timelimit.android.ui.Theme
import io.timelimit.android.ui.manage.parent.key.ScanBarcode

class SetupRemoteChildFragment : Fragment() {
    companion object {
        private const val SHOW_DIALOG = "show dialog"
    }

    private val model: SetupRemoteChildViewModel by lazy {
        ViewModelProviders.of(this).get(SetupRemoteChildViewModel::class.java)
    }

    private val scanLoginCode = registerForActivityResult(ScanBarcode(forceBinaryEye = false)) { barcode ->
        barcode ?: return@registerForActivityResult

        model.trySetup(barcode)
    }

    private val showBarcodeScannerDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            showBarcodeScannerDialog.value = savedInstanceState.getBoolean(SHOW_DIALOG)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(SHOW_DIALOG, showBarcodeScannerDialog.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = SetupRemoteChildFragmentBinding.inflate(inflater, container, false)

        fun go() {
            model.trySetup(binding.editCode.text.toString())
        }

        binding.btnOk.setOnClickListener { go() }
        binding.editCode.setOnEnterListenr { go() }

        binding.scanCodeButton.setOnClickListener {
            try {
                scanLoginCode.launch(null)
            } catch (ex: ActivityNotFoundException) {
                showBarcodeScannerDialog.value = true
            }
        }

        model.status.observe(this, Observer {
            status ->

            when (status) {
                SetupRemoteChildStatus.Idle -> binding.flipper.displayedChild = 0
                SetupRemoteChildStatus.Working -> binding.flipper.displayedChild = 1
                SetupRemoteChildStatus.CodeInvalid -> {
                    Snackbar.make(container!!, R.string.setup_remote_child_code_invalid, Snackbar.LENGTH_SHORT).show()

                    model.confirmError()
                }
                SetupRemoteChildStatus.NetworkError -> {
                    Snackbar.make(container!!, R.string.error_network, Snackbar.LENGTH_SHORT).show()

                    model.confirmError()
                }
                null -> {/* nothing to do */}
            }.let {  }
        })

        binding.composeView.setContent {
            Theme {
                if (showBarcodeScannerDialog.value) MissingBarcodeScannerDialog(
                    onDismissRequest = { showBarcodeScannerDialog.value = false },
                    onOpenStoreEntry = {
                        try {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=${it.packageName}")

                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (ex: ActivityNotFoundException) {
                            Toast.makeText(
                                requireContext(),
                                R.string.error_general,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        return binding.root
    }
}
