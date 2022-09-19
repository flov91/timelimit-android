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
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.livedata.castDown

@RequiresApi(Build.VERSION_CODES.O)
class ModernNetworkStatusUtil(context: Context): NetworkStatusInterface {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val statusInternal = MutableLiveData<NetworkStatus>().apply { value = NetworkStatus.Offline }

    override val status = statusInternal.castDown()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object: NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)

                    statusInternal.value = NetworkStatus.Online
                }

                override fun onLost(network: Network) {
                    super.onLost(network)

                    statusInternal.value = NetworkStatus.Offline
                }
            },
            Threads.mainThreadHandler
        )
    }

    override fun forceRefresh() {/* do nothing */}
}