/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.Navigation
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.databinding.FragmentSetupLocalModeBinding
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.mustread.MustReadFragment
import io.timelimit.android.ui.update.UpdateConsentCard
import io.timelimit.android.ui.view.NotifyPermissionCard
import io.timelimit.android.update.UpdateUtil

class SetupLocalModeFragment : Fragment() {
    companion object {
        private const val STATUS_NOTIFY_PERMISSION = "notify permission"
    }

    private val model: SetupLocalModeModel by viewModels()
    private var notifyPermission = MutableLiveData<NotifyPermissionCard.Status>()

    private val requestNotifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) notifyPermission.value = NotifyPermissionCard.Status.Granted
        else Toast.makeText(requireContext(), R.string.notify_permission_rejected_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            notifyPermission.value = savedInstanceState.getSerializable(STATUS_NOTIFY_PERMISSION, NotifyPermissionCard.Status::class.java)!!
        }

        notifyPermission.value = NotifyPermissionCard.updateStatus(notifyPermission.value ?: NotifyPermissionCard.Status.Unknown, requireContext())
    }

    override fun onResume() {
        super.onResume()

        notifyPermission.value = NotifyPermissionCard.updateStatus(notifyPermission.value ?: NotifyPermissionCard.Status.Unknown, requireContext())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATUS_NOTIFY_PERMISSION, notifyPermission.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentSetupLocalModeBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)

        binding.setPasswordView.allowNoPassword.value = true

        mergeLiveDataWaitForValues(binding.setPasswordView.passwordOk, model.status, notifyPermission)
            .observe(viewLifecycleOwner) { (passwordGood, modelStatus, notifyPermission) ->
                val isIdle = modelStatus == SetupLocalModeModel.Status.Idle

                binding.setPasswordView.isEnabled = isIdle

                binding.nextBtn.isEnabled = passwordGood && isIdle && NotifyPermissionCard.canProceed(notifyPermission)
            }

        model.status.observe(viewLifecycleOwner) {
            if (it == SetupLocalModeModel.Status.Done) {
                MustReadFragment.newInstance(R.string.must_read_child_manipulation).show(fragmentManager!!)

                navigation.popBackStack(R.id.overviewFragment, false)
            }
        }

        binding.nextBtn.setOnClickListener {
            model.trySetupWithPassword(
                    binding.setPasswordView.readPassword(),
                    SetupNetworkTimeVerification.readSelection(binding.networkTimeVerification),
                    enableUpdateChecks = binding.update.enableSwitch.isChecked
            )
        }

        SetupNetworkTimeVerification.prepareHelpButton(binding.networkTimeVerification, childFragmentManager)

        NotifyPermissionCard.bind(object: NotifyPermissionCard.Listener {
            override fun onGrantClicked() { requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            override fun onSkipClicked() { notifyPermission.value = NotifyPermissionCard.Status.SkipGrant }
        }, binding.notifyPermissionCard)

        notifyPermission.observe(viewLifecycleOwner) { NotifyPermissionCard.bind(it, binding.notifyPermissionCard) }

        UpdateConsentCard.bind(
                view = binding.update,
                lifecycleOwner = viewLifecycleOwner,
                database = DefaultAppLogic.with(context!!).database
        )

        return binding.root
    }
}

class SetupLocalModeModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "SetupLocalModeModel"
    }

    enum class Status {
        Idle, Running, Done
    }

    val status = MutableLiveData<Status>()

    init {
        status.value = Status.Idle
    }

    fun trySetupWithPassword(parentPassword: String, networkTimeVerification: NetworkTime, enableUpdateChecks: Boolean) {
        runAsync {
            if (status.value != Status.Idle) {
                throw IllegalStateException()
            }

            status.value = Status.Running

            try {
                DefaultAppLogic.with(getApplication()).appSetupLogic.setupForLocalUse(parentPassword, networkTimeVerification, getApplication())
                UpdateUtil.setEnableChecks(getApplication(), enableUpdateChecks)
                status.value = Status.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "setup failed", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                status.value = Status.Idle
            }
        }
    }
}
