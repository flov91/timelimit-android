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
package io.timelimit.android.sync.network

import android.util.JsonWriter
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.DHHandshake
import io.timelimit.android.crypto.PasswordHashing
import org.json.JSONObject

data class ParentPassword (
        val parentPasswordHash: String,
        val parentPasswordSecondHash: String,
        val parentPasswordSecondSalt: String,
        val encrypted: Boolean
) {
    companion object {
        private const val HASH = "hash"
        private const val SECOND_HASH = "secondHash"
        private const val SECOND_SALT = "secondSalt"
        private const val ENCRYPTED = "encrypted"

        fun createSync(password: String, dhKey: ServerDhKey?): ParentPassword {
            val hash = PasswordHashing.hashSync(password)
            val secondSalt = PasswordHashing.generateSalt()
            val secondHash = PasswordHashing.hashSyncWithSalt(password, secondSalt)

            if (dhKey == null) {
                return ParentPassword(
                    parentPasswordHash = hash,
                    parentPasswordSecondSalt = secondSalt,
                    parentPasswordSecondHash = secondHash,
                    encrypted = false
                )
            } else {
                val handshake = DHHandshake.fromServerKey(dhKey)
                val secondHashEncrypted = handshake.encrypt(
                    cryptData = secondHash.toByteArray(Charsets.US_ASCII),
                    authData = "ParentPassword:$hash:$secondSalt".toByteArray(Charsets.US_ASCII)
                )

                return ParentPassword(
                    parentPasswordHash = hash,
                    parentPasswordSecondSalt = secondSalt,
                    parentPasswordSecondHash = secondHashEncrypted,
                    encrypted = true
                )
            }
        }

        suspend fun createCoroutine(password: String, dhKey: ServerDhKey?) = Threads.crypto.executeAndWait {
            createSync(password, dhKey)
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(HASH).value(parentPasswordHash)
        writer.name(SECOND_HASH).value(parentPasswordSecondHash)
        writer.name(SECOND_SALT).value(parentPasswordSecondSalt)

        if (encrypted) writer.name(ENCRYPTED).value(true)

        writer.endObject()
    }
}