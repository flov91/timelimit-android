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
import io.timelimit.android.crypto.CryptContainer

@Entity(
    tableName = "crypt_container_metadata",
    foreignKeys = [
        ForeignKey(
            entity = Device::class,
            childColumns = ["device_id"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            childColumns = ["category_id"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(CryptContainerMetadataProcessingStatusConverter::class)
data class CryptContainerMetadata (
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "crypt_container_id")
    val cryptContainerId: Long,
    @ColumnInfo(name = "device_id", index = true)
    val deviceId: String?,
    @ColumnInfo(name = "category_id", index = true)
    val categoryId: String?,
    val type: Int,
    @ColumnInfo(name = "server_version")
    val serverVersion: String,
    @ColumnInfo(name = "current_generation")
    val currentGeneration: Long,
    @ColumnInfo(name = "current_generation_first_timestamp")
    val currentGenerationFirstTimestamp: Long,
    @ColumnInfo(name = "next_counter")
    val nextCounter: Long,
    @ColumnInfo(name = "current_generation_key")
    val currentGenerationKey: ByteArray?,
    val status: ProcessingStatus
) {
    companion object {
        private const val GENERATION_DURATION_LIMIT = 1000 * 60 * 60 * 24 * 7 // 7 days
        private const val GENERATION_COUNTER_LIMIT = 16L

        const val TYPE_APP_LIST_BASE = 1
        const val TYPE_APP_LIST_DIFF = 2

        fun buildFor(deviceId: String?, categoryId: String?, type: Int, params: CryptContainer.EncryptParameters) = CryptContainerMetadata(
            cryptContainerId = 0,
            deviceId = deviceId,
            categoryId = categoryId,
            type = type,
            serverVersion = "",
            currentGeneration = params.generation,
            currentGenerationFirstTimestamp = System.currentTimeMillis(),
            nextCounter = params.counter + 1,
            currentGenerationKey = params.key,
            status = ProcessingStatus.Finished
        )

        fun isTypeValid(type: Int) = type == TYPE_APP_LIST_BASE || type == TYPE_APP_LIST_DIFF
    }

    enum class ProcessingStatus {
        MissingKey,
        DowngradeDetected,
        Unprocessed,
        CryptoDamage,
        ContentDamage,
        Finished
    }

    fun needsNewGeneration(): Boolean {
        val now = System.currentTimeMillis()

        val timeWentBackwards = now < currentGenerationFirstTimestamp
        val timeLimitReached = now >= currentGenerationFirstTimestamp + GENERATION_DURATION_LIMIT
        val counterLimitReached = nextCounter >= GENERATION_COUNTER_LIMIT

        return timeWentBackwards or timeLimitReached or counterLimitReached
    }

    fun prepareEncryption(forceNewGeneration: Boolean): PrepareEncryptionResult {
        return if (needsNewGeneration() || forceNewGeneration || currentGenerationKey == null) {
            val newKey = CryptContainer.generateKey()
            val generation = currentGeneration + 1

            PrepareEncryptionResult(
                params = CryptContainer.EncryptParameters(
                    key = newKey,
                    generation = generation,
                    counter = 0
                ),
                newMetadata = copy(
                    currentGeneration = generation,
                    nextCounter = 1,
                    currentGenerationFirstTimestamp = System.currentTimeMillis(),
                    currentGenerationKey = newKey
                )
            )
        } else {
            PrepareEncryptionResult(
                params = CryptContainer.EncryptParameters(
                    key = currentGenerationKey,
                    generation = currentGeneration,
                    counter = nextCounter
                ),
                newMetadata = copy(
                    nextCounter = nextCounter + 1
                )
            )
        }
    }

    data class PrepareEncryptionResult (
        val params: CryptContainer.EncryptParameters,
        val newMetadata: CryptContainerMetadata
    )
}

class CryptContainerMetadataProcessingStatusConverter {
    @TypeConverter
    fun toStatus(input: Int): CryptContainerMetadata.ProcessingStatus = when (input) {
        0 -> CryptContainerMetadata.ProcessingStatus.MissingKey
        1 -> CryptContainerMetadata.ProcessingStatus.DowngradeDetected
        2 -> CryptContainerMetadata.ProcessingStatus.Unprocessed
        3 -> CryptContainerMetadata.ProcessingStatus.CryptoDamage
        4 -> CryptContainerMetadata.ProcessingStatus.ContentDamage
        5 -> CryptContainerMetadata.ProcessingStatus.Finished
        else -> CryptContainerMetadata.ProcessingStatus.CryptoDamage
    }

    @TypeConverter
    fun toInt(input: CryptContainerMetadata.ProcessingStatus): Int = when (input) {
        CryptContainerMetadata.ProcessingStatus.MissingKey -> 0
        CryptContainerMetadata.ProcessingStatus.DowngradeDetected -> 1
        CryptContainerMetadata.ProcessingStatus.Unprocessed -> 2
        CryptContainerMetadata.ProcessingStatus.CryptoDamage -> 3
        CryptContainerMetadata.ProcessingStatus.ContentDamage -> 4
        CryptContainerMetadata.ProcessingStatus.Finished -> 5
    }
}