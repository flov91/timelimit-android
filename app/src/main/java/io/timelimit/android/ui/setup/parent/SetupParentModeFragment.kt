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
package io.timelimit.android.ui.setup.parent

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.observe
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.devicename.DeviceName
import io.timelimit.android.databinding.FragmentSetupParentModeBinding
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.StatusOfMailAddress
import io.timelimit.android.ui.authentication.AuthenticateByMailFragment
import io.timelimit.android.ui.authentication.AuthenticateByMailFragmentListener
import io.timelimit.android.ui.update.UpdateConsentCard
import io.timelimit.android.ui.view.NotifyPermissionCard

class SetupParentModeFragment : Fragment(), AuthenticateByMailFragmentListener {
    companion object {
        private const val STATUS_NOTIFY_PERMISSION = "notify permission"
    }

    private val model: SetupParentModeModel by lazy { ViewModelProviders.of(this).get(SetupParentModeModel::class.java) }
    private var notifyPermission = MutableLiveData<NotifyPermissionCard.Status>()

    private val requestNotifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) notifyPermission.value = NotifyPermissionCard.Status.Granted
        else Toast.makeText(requireContext(), R.string.notify_permission_rejected_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            notifyPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                savedInstanceState.getSerializable(STATUS_NOTIFY_PERMISSION, NotifyPermissionCard.Status::class.java)!!
            else
                savedInstanceState.getSerializable(STATUS_NOTIFY_PERMISSION)!! as NotifyPermissionCard.Status
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupParentModeBinding.inflate(layoutInflater, container, false)

        model.mailAuthToken.switchMap {
            mailAuthToken ->

            if (mailAuthToken == null) {
                liveDataFromNonNullValue(1)   // show login screen
            } else {
                // show form or loading indicator or error screen
                model.statusOfMailAddress.switchMap {
                    status ->

                    if (status == null) {
                        liveDataFromNonNullValue(2)    // loading screen
                    } else if (status.status == StatusOfMailAddress.MailAddressWithoutFamily && status.canCreateFamily == false) {
                        liveDataFromNonNullValue(3)    // signup disabled screen
                    } else {
                        model.isDoingSetup.map {
                            if (it!!) {
                                2   // loading screen
                            } else {
                                0   // the form
                            }
                        }
                    }
                }
            }
        }.observe(viewLifecycleOwner, Observer {
            binding.switcher.displayedChild = it!!
        })

        model.statusOfMailAddress.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                binding.isNewFamily = when (it.status) {
                    StatusOfMailAddress.MailAddressWithoutFamily -> true
                    StatusOfMailAddress.MailAddressWithFamily -> false
                }

                binding.showLimitedProInfo = !it.alwaysPro
                binding.mail = it.mail
            }
        })

        val isPasswordValid = model.statusOfMailAddress.switchMap {
            if (it == null) {
                liveDataFromNonNullValue(false)
            } else {
                when (it.status) {
                    StatusOfMailAddress.MailAddressWithFamily -> liveDataFromNonNullValue(true)
                    StatusOfMailAddress.MailAddressWithoutFamily -> binding.password.passwordOk
                }
            }
        }

        val isNotifyPermissionValid = notifyPermission.map { NotifyPermissionCard.canProceed(it) }

        val isPreNameValid = model.statusOfMailAddress.switchMap {
            if (it == null) {
                liveDataFromNonNullValue(false)
            } else {
                when (it.status) {
                    StatusOfMailAddress.MailAddressWithFamily -> liveDataFromNonNullValue(true)
                    StatusOfMailAddress.MailAddressWithoutFamily -> binding.prename.getTextLive().map { prename -> prename.isNotBlank() }
                }
            }
        }

        val isDeviceNameValid = binding.deviceName.getTextLive().map { it.isNotBlank() }

        val isInputValid = isPasswordValid.and(isNotifyPermissionValid).and(isPreNameValid).and(isDeviceNameValid)

        isInputValid.ignoreUnchanged().observe(viewLifecycleOwner, Observer {
            binding.enableOkButton = it!!
        })

        if (savedInstanceState == null) {
            val ctx = requireContext()

            runAsync {
                // provide an useful default value
                val deviceName = Threads.database.executeAndWait { DeviceName.getDeviceNameSync(ctx) }

                binding.deviceName.setText(deviceName)
            }
        }

        binding.ok.setOnClickListener {
            val status = model.statusOfMailAddress.value

            if (status == null) {
                throw IllegalStateException()
            }

            when (status.status) {
                StatusOfMailAddress.MailAddressWithoutFamily -> {
                    model.createFamily(
                            parentPassword = binding.password.readPassword(),
                            parentName = binding.prename.text.toString(),
                            deviceName = binding.deviceName.text.toString(),
                            enableBackgroundSync = binding.backgroundSyncCheckbox.isChecked,
                            enableUpdateChecks = binding.update.enableSwitch.isChecked
                    )
                }
                StatusOfMailAddress.MailAddressWithFamily -> {
                    model.addDeviceToFamily(
                            deviceName = binding.deviceName.text.toString(),
                            enableBackgroundSync = binding.backgroundSyncCheckbox.isChecked,
                            enableUpdateChecks = binding.update.enableSwitch.isChecked
                    )
                }
            }
        }

        model.isSetupDone.observe(this, Observer {
            if (it!!) {
                Navigation.findNavController(binding.root).popBackStack(R.id.overviewFragment, false)
            }
        })

        UpdateConsentCard.bind(
                view = binding.update,
                lifecycleOwner = viewLifecycleOwner,
                database = DefaultAppLogic.with(requireContext()).database
        )

        NotifyPermissionCard.bind(object: NotifyPermissionCard.Listener {
            override fun onGrantClicked() { requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            override fun onSkipClicked() { notifyPermission.value = NotifyPermissionCard.Status.SkipGrant }
        }, binding.notifyPermissionCard)

        notifyPermission.observe(viewLifecycleOwner) { NotifyPermissionCard.bind(it, binding.notifyPermissionCard) }

        return binding.root
    }

    override fun onLoginSucceeded(mailAuthToken: String) = model.setMailToken(mailAuthToken)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.mail_auth_container, AuthenticateByMailFragment())
                    .commit()
        }
    }
}
