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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.timelimit.android.data.model.*
import io.timelimit.android.integration.platform.NewPermissionStatusConverter
import io.timelimit.android.integration.platform.ProtectionLevelConverter
import io.timelimit.android.integration.platform.RuntimePermissionStatusConverter

@Dao
@TypeConverters(
        NetworkTimeAdapter::class,
        ProtectionLevelConverter::class,
        RuntimePermissionStatusConverter::class,
        NewPermissionStatusConverter::class
)
abstract class DeviceDao {
    @Query("SELECT * FROM device WHERE id = :deviceId")
    abstract fun getDeviceById(deviceId: String): LiveData<Device?>

    @Query("SELECT * FROM device WHERE id = :deviceId")
    abstract fun getDeviceByIdSync(deviceId: String): Device?

    @Query("SELECT * FROM device ORDER BY id")
    abstract fun getAllDevicesLive(): LiveData<List<Device>>

    @Query("SELECT id, name FROM device")
    abstract fun getDeviceNamesLive(): LiveData<List<DeviceName>>

    @Query("SELECT * FROM device")
    abstract fun getAllDevicesSync(): List<Device>

    @Insert
    abstract fun addDeviceSync(device: Device)

    @Query("UPDATE device SET current_user_id = :userId, is_user_kept_signed_in = 0 WHERE id = :deviceId")
    abstract fun updateDeviceUser(deviceId: String, userId: String)

    @Query("UPDATE device SET default_user = :defaultUserId, is_user_kept_signed_in = 0 WHERE id = :deviceId")
    abstract fun updateDeviceDefaultUser(deviceId: String, defaultUserId: String)

    @Query("SELECT id, apps_version FROM device")
    abstract fun getInstalledAppsVersionsSync(): List<DeviceWithAppVersion>

    @Query("DELETE FROM device WHERE id IN (:deviceIds)")
    abstract fun removeDevicesById(deviceIds: List<String>)

    @Update
    abstract fun updateDeviceEntry(device: Device)

    @Query("UPDATE device SET apps_version = :appsVersion WHERE id = :deviceId")
    abstract fun updateAppsVersion(deviceId: String, appsVersion: String)

    @Query("SELECT * FROM device WHERE current_user_id = :userId")
    abstract fun getDevicesByUserId(userId: String): LiveData<List<Device>>

    @Query("SELECT id FROM device WHERE current_user_id = :userId")
    abstract fun getDevicesIdByUserId(userId: String): LiveData<List<DeviceId>>

    @Query("UPDATE device SET apps_version = \"\"")
    abstract fun deleteAllInstalledAppsVersions()

    @Query("UPDATE device SET network_time = :mode WHERE id = :deviceId")
    abstract fun updateNetworkTimeVerification(deviceId: String, mode: NetworkTime)

    @Query("UPDATE device SET name = :name WHERE id = :deviceId")
    abstract fun updateDeviceName(deviceId: String, name: String): Int

    @Query("SELECT * FROM device LIMIT :pageSize OFFSET :offset")
    abstract fun getDevicePageSync(offset: Int, pageSize: Int): List<Device>

    @Query("UPDATE device SET current_user_id = \"\", is_user_kept_signed_in = 0 WHERE current_user_id = :userId")
    abstract fun unassignCurrentUserFromAllDevices(userId: String)

    @Query("UPDATE device SET is_user_kept_signed_in = :keepSignedIn WHERE id = :deviceId")
    abstract fun updateKeepSignedIn(deviceId: String, keepSignedIn: Boolean)

    @Query("SELECT COUNT(*) FROM device JOIN user ON (device.current_user_id = user.id) WHERE user.type = \"child\"")
    abstract fun countDevicesWithChildUser(): LiveData<Long>

    fun getDeviceDetailDataSync() = getDeviceDetailDataSyncInternal(
        CryptContainerMetadata.TYPE_APP_LIST_BASE,
        CryptContainerMetadata.TYPE_APP_LIST_DIFF
    )

    @Query("SELECT d.id AS device_id, c1.server_version AS app_base_version, c2.server_version AS app_diff_version FROM device d LEFT JOIN crypt_container_metadata c1 ON (c1.device_id = d.id AND c1.type = :baseType) LEFT JOIN crypt_container_metadata c2 ON (c2.device_id = d.id AND c2.type = :diffType)")
    protected abstract fun getDeviceDetailDataSyncInternal(baseType: Int, diffType: Int): List<DeviceDetailDataBase>
}

data class DeviceWithAppVersion(
        @ColumnInfo(name = "id")
        val deviceId: String,
        @ColumnInfo(name = "apps_version")
        val installedAppsVersions: String
)

data class DeviceDetailDataBase(
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "app_base_version")
    val appBaseVersion: String?,
    @ColumnInfo(name = "app_diff_version")
    val appDiffVersion: String?
)