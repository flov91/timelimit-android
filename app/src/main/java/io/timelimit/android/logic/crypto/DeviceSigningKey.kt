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

import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database
import io.timelimit.android.sync.actions.UploadDevicePublicKeyAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

object DeviceSigningKey {
    fun getPublicAndPrivateKeySync(database: Database, uploadIfMissingAtServer: Boolean = false): ByteArray? = database.runInTransaction {
        if (database.config().getServerApiLevelSync() < 4) return@runInTransaction null

        val oldData = database.config().getSigningKeySync()

        if (oldData != null) {
            if (database.deviceKey().getSync(database.config().getOwnDeviceIdSync()!!) == null && uploadIfMissingAtServer) {
                ApplyActionUtil.addAppLogicActionToDatabaseSync(
                    UploadDevicePublicKeyAction(publicKey = Curve25519.getPublicKey(oldData)),
                    database
                )
            }

            return@runInTransaction oldData
        }

        val newData = Curve25519.generateKeyPair()

        database.config().setSigningKeySync(newData)
        ApplyActionUtil.addAppLogicActionToDatabaseSync(
            UploadDevicePublicKeyAction(publicKey = Curve25519.getPublicKey(newData)),
            database
        )

        return@runInTransaction newData
    }
}