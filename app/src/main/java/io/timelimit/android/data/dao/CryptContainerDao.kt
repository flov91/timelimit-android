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

import androidx.paging.DataSource
import androidx.room.*
import io.timelimit.android.data.model.CryptContainerData
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.data.model.CryptContainerMetadataProcessingStatusConverter

@Dao
@TypeConverters(CryptContainerMetadataProcessingStatusConverter::class)
interface CryptContainerDao {
    @Query("SELECT * FROM crypt_container_metadata WHERE crypt_container_id = :containerId")
    fun getCryptoMetadataSyncByContainerId(containerId: Long): CryptContainerMetadata?

    @Query("SELECT * FROM crypt_container_metadata JOIN crypt_container_data USING (crypt_container_id) WHERE category_id IS NULL AND device_id = :deviceId AND type = :type")
    fun getCryptoFullDataSyncByDeviceId(deviceId: String, type: Int): MetadataAndContent?

    @Query("SELECT * FROM crypt_container_metadata WHERE category_id IS NULL AND device_id IS NULL AND type = :type")
    fun getCryptoMetadataSyncByType(type: Int): CryptContainerMetadata?

    @Query("SELECT * FROM crypt_container_metadata WHERE category_id IS NULL AND device_id = :deviceId AND type = :type")
    fun getCryptoMetadataSyncByDeviceId(deviceId: String, type: Int): CryptContainerMetadata?

    @Query("SELECT * FROM crypt_container_metadata WHERE category_id = :categoryId AND device_id IS NULL AND type = :type")
    fun getCryptoMetadataSyncByCategoryId(categoryId: String, type: Int): CryptContainerMetadata?

    @Query("SELECT * FROM crypt_container_metadata WHERE status = :processingStatus")
    fun getMetadataByProcessingStatus(processingStatus: CryptContainerMetadata.ProcessingStatus): List<CryptContainerMetadata>

    @Insert
    fun insertMetadata(container: CryptContainerMetadata): Long

    @Update
    fun updateMetadata(container: List<CryptContainerMetadata>)

    @Update
    fun updateMetadata(container: CryptContainerMetadata)

    @Insert
    fun insertData(data: CryptContainerData)

    @Update
    fun updateData(data: List<CryptContainerData>)

    @Update
    fun updateData(data: CryptContainerData)

    @Query("SELECT * FROM crypt_container_data WHERE crypt_container_id = :containerId")
    fun getData(containerId: Long): CryptContainerData?

    @Query("UPDATE crypt_container_metadata SET server_version = ''")
    fun deleteAllServerVersionNumbers()

    @Query("SELECT * FROM crypt_container_metadata")
    fun getDiagnoseData(): DataSource.Factory<Int, CryptContainerMetadata>

    @Entity
    data class MetadataAndContent(
        @Embedded
        val metadata: CryptContainerMetadata,
        @ColumnInfo(name = "encrypted_data")
        val encryptedData: ByteArray
    )
}