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
    tableName = "crypt_container_key_result",
    primaryKeys = ["request_sequence_id", "device_id"],
    foreignKeys = [
        ForeignKey(
            entity = CryptContainerPendingKeyRequest::class,
            childColumns = ["request_sequence_id"],
            parentColumns = ["request_sequence_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Device::class,
            childColumns = ["device_id"],
            parentColumns = ["id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(CryptContainerKeyResultStatusConverter::class)
data class CryptContainerKeyResult (
    @ColumnInfo(name = "request_sequence_id", index = true)
    val requestSequenceId: Long,
    @ColumnInfo(name = "device_id", index = true)
    val deviceId: String,
    val status: Status
) {
    enum class Status {
        InvalidKey
    }
}

class CryptContainerKeyResultStatusConverter {
    @TypeConverter
    fun toStatus(input: Int): CryptContainerKeyResult.Status = when (input) {
        0 -> CryptContainerKeyResult.Status.InvalidKey
        else -> CryptContainerKeyResult.Status.InvalidKey
    }

    @TypeConverter
    fun toInt(input: CryptContainerKeyResult.Status): Int = when (input) {
        CryptContainerKeyResult.Status.InvalidKey -> 0
    }
}