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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.CryptContainerPendingKeyRequest

@Dao
interface CryptContainerKeyRequestDao {
    @Query("SELECT * FROM crypt_container_pending_key_request WHERE crypt_container_id = :cryptContainerId")
    fun byCryptContainerId(cryptContainerId: Long): CryptContainerPendingKeyRequest?

    @Query("SELECT * FROM crypt_container_pending_key_request WHERE request_sequence_id = :requestId")
    fun byRequestId(requestId: Long): CryptContainerPendingKeyRequest?

    @Insert
    fun insert(item: CryptContainerPendingKeyRequest)

    @Delete
    fun delete(item: CryptContainerPendingKeyRequest)

    @Query("DELETE FROM crypt_container_pending_key_request")
    fun deleteAll()
}