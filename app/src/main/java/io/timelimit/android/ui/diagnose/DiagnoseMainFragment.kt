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
package io.timelimit.android.ui.diagnose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentDiagnoseMainBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionDialogFragment
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.execute

class DiagnoseMainFragment : Fragment(), FragmentWithCustomTitle {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentDiagnoseMainBinding.inflate(inflater, container, false)
        val logic = DefaultAppLogic.with(requireContext())
        val activity: ActivityViewModelHolder = activity as ActivityViewModelHolder
        val auth = activity.getActivityViewModel()

        binding.diagnoseClockButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.Clock)
        }

        binding.diagnoseConnectionButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.Connection)
        }

        binding.diagnoseSyncButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.Sync)
        }

        binding.diagnoseCryButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.Crypto)
        }

        binding.diagnoseBatteryButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.Battery)
        }

        binding.diagnoseFgaButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.ForegroundApp)
        }

        binding.diagnoseExfButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.ExperimentalFlags)
        }

        logic.backgroundTaskLogic.lastLoopException.observe(this, Observer { ex ->
            if (ex != null) {
                binding.diagnoseBgTaskLoopExButton.isEnabled = true
                binding.diagnoseBgTaskLoopExButton.setOnClickListener {
                    DiagnoseExceptionDialogFragment.newInstance(ex).show(parentFragmentManager)
                }
            } else {
                binding.diagnoseBgTaskLoopExButton.isEnabled = false
            }
        })

        binding.diagnoseExitReasonsButton.setOnClickListener {
            requireActivity().execute(UpdateStateCommand.Diagnose.ExitReasons)
        }

        AuthenticationFab.manageAuthenticationFab(
            fab = binding.fab,
            shouldHighlight = auth.shouldHighlightAuthenticationButton,
            authenticatedUser = auth.authenticatedUser,
            doesSupportAuth = liveDataFromNonNullValue(true),
            fragment = this
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.about_diagnose_title)} < ${getString(R.string.main_tab_overview)}")
}
