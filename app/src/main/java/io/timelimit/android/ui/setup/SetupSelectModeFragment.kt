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
package io.timelimit.android.ui.setup


import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.databinding.FragmentSetupSelectModeBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.execute
import io.timelimit.android.ui.setup.parentmode.SetupParentmodeDialogFragment
import io.timelimit.android.ui.setup.privacy.PrivacyInfoDialogFragment

class SetupSelectModeFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SetupSelectModeFragment"
        private const val REQ_SETUP_CONNECTED_PARENT = 1
        private const val REQ_SETUP_CONNECTED_CHILD = 2
        private const val REQUEST_SETUP_PARENT_MODE = 3
    }

    private lateinit var binding: FragmentSetupSelectModeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSetupSelectModeBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLocalMode.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Setup.DevicePermissions)
        }

        binding.btnParentMode.setOnClickListener {
            PrivacyInfoDialogFragment().apply {
                setTargetFragment(this@SetupSelectModeFragment, REQ_SETUP_CONNECTED_PARENT)
            }.show(parentFragmentManager)
        }

        binding.btnNetworkChildMode.setOnClickListener {
            PrivacyInfoDialogFragment().apply {
                setTargetFragment(this@SetupSelectModeFragment, REQ_SETUP_CONNECTED_CHILD)
            }.show(parentFragmentManager)
        }

        binding.btnParentKeyMode.setOnClickListener {
            SetupParentmodeDialogFragment().apply {
                setTargetFragment(this@SetupSelectModeFragment, REQUEST_SETUP_PARENT_MODE)
            }.show(parentFragmentManager)
        }

        binding.btnUninstall.setOnClickListener {
            val context = requireContext().applicationContext
            val logic = DefaultAppLogic.with(requireContext())

            runAsync {
                try {
                    Threads.database.executeAndWait { SetupUnprovisionedCheck.checkSync(logic.database) }

                    logic.platformIntegration.disableDeviceAdmin()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${requireContext().packageName}")
                            )
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_UNINSTALL_PACKAGE,
                                Uri.parse("package:${requireContext().packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "reset failed", ex)
                    }

                    Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_SETUP_CONNECTED_CHILD) {
                requireActivity().execute(UpdateStateCommand.Setup.RemoteChild)
            } else if (requestCode == REQ_SETUP_CONNECTED_PARENT) {
                requireActivity().execute(UpdateStateCommand.Setup.ParentMode)
            }
        }
    }
}
