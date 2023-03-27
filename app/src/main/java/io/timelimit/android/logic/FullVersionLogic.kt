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

import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.or
import kotlinx.coroutines.flow.first

class FullVersionLogic(logic: AppLogic) {
    private val hasFullVersion = logic.database.config().getFullVersionUntilAsync().map { it != 0L }.ignoreUnchanged()
    val isLocalMode = logic.database.config().getDeviceAuthTokenAsync().map { it == "" }

    val shouldProvideFullVersionFunctions = hasFullVersion.or(isLocalMode)

    suspend fun shouldProvideFullVersionFunctions() = shouldProvideFullVersionFunctions.asFlow().first()
}
