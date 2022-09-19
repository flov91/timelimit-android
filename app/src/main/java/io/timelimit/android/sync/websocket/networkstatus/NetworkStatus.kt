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
package io.timelimit.android.sync.websocket.networkstatus

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.lifecycle.LiveData

object NetworkStatusUtil {
    fun createWith(context: Context): NetworkStatusInterface =
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) ModernNetworkStatusUtil(context.applicationContext)
        else LegacyNetworkStatusUtil(context.applicationContext)
}

enum class NetworkStatus {
    Offline, Online
}

interface NetworkStatusInterface {
    fun forceRefresh()

    val status: LiveData<NetworkStatus>
}