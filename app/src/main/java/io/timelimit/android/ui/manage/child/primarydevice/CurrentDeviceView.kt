/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child.primarydevice

import androidx.lifecycle.LifecycleOwner
import io.timelimit.android.databinding.PrimaryDeviceViewBinding
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic

object CurrentDeviceView {
    fun bind(
            view: PrimaryDeviceViewBinding,
            childId: String,
            logic: AppLogic,
            lifecycleOwner: LifecycleOwner
    ) {
        val userEntryLive = logic.database.user().getUserByIdLive(childId)
        val deviceAndUserRelatedDataLive = logic.database.derivedDataDao().getUserAndDeviceRelatedDataLive()
        val allDevicesLive = logic.database.device().getAllDevicesLive()

        mergeLiveData(userEntryLive, deviceAndUserRelatedDataLive, allDevicesLive)
            .observe(lifecycleOwner) { (userEntry, deviceAndUserRelatedData, allDevices) ->
                if (userEntry == null || deviceAndUserRelatedData == null || allDevices == null) return@observe

                view.canAssignThisDevice = deviceAndUserRelatedData.userRelatedData?.user?.id == childId

                if (deviceAndUserRelatedData.deviceRelatedData.isLocalMode) {
                    view.status = PrimaryDeviceStatus.LocalMode
                } else {
                    val currentDeviceEntry = allDevices.find { device -> device.id == deviceAndUserRelatedData.userRelatedData?.user?.currentDevice }

                    if (currentDeviceEntry == null) {
                        view.status = PrimaryDeviceStatus.NoDeviceSelected
                    } else if (currentDeviceEntry.id == deviceAndUserRelatedData.deviceRelatedData.deviceEntry.id) {
                        view.status = PrimaryDeviceStatus.ThisDeviceSelected
                    } else {
                        view.status = PrimaryDeviceStatus.OtherDeviceSelected
                        view.primaryDeviceTitle = currentDeviceEntry.name
                    }
                }
            }
    }
}

enum class PrimaryDeviceStatus {
    LocalMode,
    NoDeviceSelected,
    OtherDeviceSelected,
    ThisDeviceSelected
}
