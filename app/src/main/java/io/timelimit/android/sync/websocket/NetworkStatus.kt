/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.sync.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads

object NetworkStatusUtil {
    class NetworkStatusLiveData(private val context: Context) : MutableLiveData<NetworkStatus>() {
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        init {
            value = NetworkStatus.Offline
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                Threads.mainThreadHandler.post {
                    val networkInfo = connectivityManager.activeNetworkInfo
                    if (networkInfo == null) {
                        postValue(NetworkStatus.Offline)
                    } else if (networkInfo.detailedState == NetworkInfo.DetailedState.CONNECTED) {
                        postValue(NetworkStatus.Online)
                    } else {
                        postValue(NetworkStatus.Offline)
                    }
                }
            }
        }

        override fun onActive() {
            super.onActive()
            if (BuildConfig.hasServer) {
                context.applicationContext.registerReceiver(receiver,
                        IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            }
        }

        override fun onInactive() {
            super.onInactive()
            context.applicationContext.unregisterReceiver(receiver)
        }
    }

    private var networkStatusLiveData : NetworkStatusLiveData? = null

    fun getSystemNetworkStatusLive(context: Context): LiveData<NetworkStatus> {
        if (networkStatusLiveData == null) {
            networkStatusLiveData = NetworkStatusLiveData(context)
        }
        return networkStatusLiveData!!
    }
}

enum class NetworkStatus {
    Offline, Online
}
