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

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.logic.crypto.decrypt.DecryptProcessor
import io.timelimit.android.sync.network.ServerKeyRequest
import io.timelimit.android.sync.network.ServerKeyResponse

object CryptoSyncLogic {
    suspend fun postPullHook(
        database: Database,
        keyRequests: List<ServerKeyRequest>,
        keyResponses: List<ServerKeyResponse>
    ): Result {
        return Threads.database.executeAndWait {
            val privateKeyAndPublicKey = DeviceSigningKey.getPublicAndPrivateKeySync(database, uploadIfMissingAtServer = true)
                ?: return@executeAndWait Result(didSendReplies = false)

            val keyRequestResult = KeyRequestProcessor.process(
                keyRequests = keyRequests,
                database = database,
                privateKeyAndPublicKey = privateKeyAndPublicKey
            )

            val keyResponseResult = KeyResponseProcessor.process(
                keyResponses = keyResponses,
                database = database,
                privateKeyAndPublicKey = privateKeyAndPublicKey
            )

            DecryptProcessor.handleEncryptedApps(database)

            Result(
                didSendReplies = keyRequestResult.didSendReplies or keyResponseResult.gotNewKeys
            )
        }
    }

    data class Result(val didSendReplies: Boolean)
}