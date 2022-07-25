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
package io.timelimit.android.logic.crypto

import okio.Buffer

data class KeyRequestSignedData (
    val deviceSequenceNumber: Long,
    val deviceId: String?,
    val categoryId: String?,
    val type: Int,
    val tempKey: ByteArray
) {
    init {
        if (tempKey.size != 32) {
            throw IllegalArgumentException()
        }
    }

    fun serialize(): ByteArray = Buffer().also { buffer ->
        fun writeBool(value: Boolean) = buffer.writeByte(when (value) {
            false -> 0
            true -> 1
        })

        fun writeString(value: String) = value.toByteArray(Charsets.UTF_8).also {
            buffer.writeInt(it.size)
            buffer.write(it)
        }

        fun writeOptionalString(value: String?) {
            if (value == null) {
                writeBool(false)
            } else {
                writeBool(true)
                writeString(value)
            }
        }

        writeString("KeyRequestSignedData")
        buffer.writeLong(deviceSequenceNumber)
        writeBool(deviceId != null)
        writeOptionalString(deviceId)
        writeOptionalString(categoryId)
        buffer.writeInt(type)
        buffer.write(tempKey) // fixed size => no length prefix required
    }.readByteArray()
}

data class KeyResponseSignedData (
    val request: KeyRequestSignedData,
    val senderDevicePublicKey: ByteArray,
    val tempKey: ByteArray,
    val encryptedKey: ByteArray
) {
    fun serialize(): ByteArray = Buffer().also { buffer ->
        fun writeByteArray(array: ByteArray) {
            buffer.writeInt(array.size)
            buffer.write(array)
        }

        fun writeString(value: String) = writeByteArray(value.toByteArray(Charsets.UTF_8))

        writeString("KeyResponseSignedData")
        writeByteArray(senderDevicePublicKey)
        writeByteArray(tempKey)
        writeByteArray(encryptedKey)
        buffer.write(request.serialize())
    }.readByteArray()
}