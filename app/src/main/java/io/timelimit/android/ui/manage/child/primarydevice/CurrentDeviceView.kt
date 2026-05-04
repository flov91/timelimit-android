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
        val deviceAndUserRelatedDataLive = logic.database.derivedDataDao().getUserAndDeviceRelatedDataLive()
        val allDevicesLive = logic.database.device().getAllDevicesLive()

        mergeLiveData(deviceAndUserRelatedDataLive, allDevicesLive)
            .observe(lifecycleOwner) { (deviceAndUserRelatedData, allDevices) ->
                if (deviceAndUserRelatedData == null || allDevices == null) return@observe

                if (deviceAndUserRelatedData.deviceRelatedData.isLocalMode) {
                    view.status = PrimaryDeviceStatus.LocalMode
                } else if (deviceAndUserRelatedData.userRelatedData?.user?.id != childId) {
                    view.status = PrimaryDeviceStatus.OtherUserSelected
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
    ThisDeviceSelected,
    OtherUserSelected,
}
