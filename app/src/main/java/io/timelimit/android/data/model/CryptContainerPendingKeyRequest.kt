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
package io.timelimit.android.data.model

import androidx.room.*

@Entity(
    tableName = "crypt_container_pending_key_request",
    foreignKeys = [
        ForeignKey(
            entity = CryptContainerMetadata::class,
            childColumns = ["crypt_container_id"],
            parentColumns = ["crypt_container_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            unique = true,
            value = ["request_sequence_id"]
        )
    ]
)
data class CryptContainerPendingKeyRequest (
    @PrimaryKey
    @ColumnInfo(name = "crypt_container_id")
    val cryptContainerId: Long,
    @ColumnInfo(name = "request_time_crypt_container_generation")
    val requestTimeCryptContainerGeneration: Long,
    @ColumnInfo(name = "request_sequence_id")
    val requestSequenceId: Long,
    @ColumnInfo(name = "request_key")
    val requestKey: ByteArray
)