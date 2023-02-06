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
package io.timelimit.android.ui.manage.device.manage

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.crypto.HexString
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.FragmentManageDeviceBinding
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RealTime
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.device.manage.feature.ManageDeviceFeaturesFragment
import io.timelimit.android.ui.manage.device.manage.permission.ManageDevicePermissionsFragment
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.execute

class ManageDeviceFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFragmentArgs by lazy { ManageDeviceFragmentArgs.fromBundle(requireArguments()) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentManageDeviceBinding.inflate(inflater, container, false)
        val userEntries = logic.database.user().getAllUsersLive()

        DeviceUninstall.bind(
                view = binding.uninstall,
                deviceEntry = deviceEntry,
                lifecycleOwner = this
        )

        ManageDeviceManipulation.bindView(
                binding = binding.manageManipulation,
                deviceEntry = deviceEntry,
                lifecycleOwner = this,
                activityViewModel = auth,
                status = ViewModelProviders.of(this).get(ManageDeviceManipulationStatusModel::class.java).data
        )

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        binding.handlers = object: ManageDeviceFragmentHandlers {
            override fun showUserScreen() {
                requireActivity().execute(UpdateStateCommand.ManageDevice.User(args.deviceId))
            }

            override fun showPermissionsScreen() {
                requireActivity().execute(UpdateStateCommand.ManageDevice.Permissions(args.deviceId))
            }

            override fun showFeaturesScreen() {
                requireActivity().execute(UpdateStateCommand.ManageDevice.Features(args.deviceId))
            }

            override fun showManageScreen() {
                requireActivity().execute(UpdateStateCommand.ManageDevice.Advanced(args.deviceId))
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                requireActivity().execute(UpdateStateCommand.ManageDevice.Leave)
            } else {
                val now = RealTime.newInstance()
                logic.realTimeLogic.getRealTime(now)

                binding.modelString = device.model
                binding.addedAtString = getString(R.string.manage_device_added_at, DateUtils.getRelativeTimeSpanString(
                        device.addedAt,
                        now.timeInMillis,
                        DateUtils.HOUR_IN_MILLIS

                ))
                binding.didAppDowngrade = device.currentAppVersion < device.highestAppVersion
                binding.permissionCardText = ManageDevicePermissionsFragment.getPreviewText(device, requireContext())
                binding.featureCardText = ManageDeviceFeaturesFragment.getPreviewText(device, requireContext())
            }
        })

        val isThisDevice = logic.deviceId.map { ownDeviceId -> ownDeviceId == args.deviceId }.ignoreUnchanged()

        isThisDevice.observe(this, Observer {
            binding.isThisDevice = it
        })

        val signingKey = isThisDevice.switchMap { isLocalDevice ->
            if (isLocalDevice) {
                logic.fullVersion.isLocalMode.switchMap { isLocalMode ->
                    if (isLocalMode) liveDataFromNullableValue(null)
                    else
                        logic.database.config().getSigningKeyAsync().map {
                            if (it != null) Curve25519.getPublicKey(it)
                            else null
                        }
                }
            } else {
                logic.database.deviceKey().getLive(args.deviceId).map {
                    it?.publicKey
                }
            }
        }.map {
            if (it == null) null
            else HexString.toHex(it)
        }

        ManageDeviceIntroduction.bind(
                view = binding.introduction,
                database = logic.database,
                lifecycleOwner = this
        )

        val userEntry = deviceEntry.switchMap {
            device ->

            userEntries.map { users ->
                users.find { user -> user.id == device?.currentUserId }
            }
        }

        UsageStatsAccessRequiredAndMissing.bind(
                view = binding.usageStatsAccessMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        ActivityLaunchPermissionRequiredAndMissing.bind(
                view = binding.activityLaunchPermissionMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        userEntry.observe(this, Observer {
            binding.userCardText = it?.name ?: getString(R.string.manage_device_current_user_none)
        })

        signingKey.observe(viewLifecycleOwner) { binding.devicePublicKey = it }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = deviceEntry.map { "${it?.name} < ${getString(R.string.main_tab_overview)}" }
}

interface ManageDeviceFragmentHandlers {
    fun showUserScreen()
    fun showPermissionsScreen()
    fun showFeaturesScreen()
    fun showManageScreen()
    fun showAuthenticationScreen()
}
