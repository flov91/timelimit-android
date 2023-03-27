/*
 * TimeLimit Copyright <C> 2019 - 2023 Jonas Lochmann
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
package io.timelimit.android.logic

import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.livedata.liveDataFromNonNullValue

class ServerApiLevelLogic(logic: AppLogic) {
    companion object {
        fun getSync(database: Database): ServerApiLevelInfo = if (database.config().getDeviceAuthTokenSync().isEmpty())
            ServerApiLevelInfo.Offline
        else
            ServerApiLevelInfo.Online(serverLevel = database.config().getServerApiLevelSync())

        private suspend fun getCoroutine(database: Database): ServerApiLevelInfo = Threads.database.executeAndWait {
            getSync(database)
        }
    }

    private val database = logic.database

    val infoLive = database.config().getDeviceAuthTokenAsync().switchMap { authToken ->
        if (authToken.isEmpty())
            liveDataFromNonNullValue(ServerApiLevelInfo.Offline)
        else
            database.config().getServerApiLevelLive().map { apiLevel ->
                ServerApiLevelInfo.Online(serverLevel = apiLevel)
            }
    }

    suspend fun getCoroutine() = getCoroutine(database)
}

sealed class ServerApiLevelInfo {
    abstract fun hasLevelOrIsOffline(level: Int): Boolean

    data class Online(val serverLevel: Int): ServerApiLevelInfo() {
        override fun hasLevelOrIsOffline(level: Int) = serverLevel >= level
    }

    object Offline: ServerApiLevelInfo() {
        override fun hasLevelOrIsOffline(level: Int) = true
    }
}