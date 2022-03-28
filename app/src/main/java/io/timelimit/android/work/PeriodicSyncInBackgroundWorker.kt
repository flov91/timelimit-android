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
package io.timelimit.android.work

import android.content.Context
import android.util.Log
import androidx.work.*
import io.timelimit.android.BuildConfig
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.SyncingDisabledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PeriodicSyncInBackgroundWorker(private val context: Context, workerParameters: WorkerParameters): CoroutineWorker(context, workerParameters) {
    companion object {
        private const val LOG_TAG = "PeriodicBackgroundSync"
        private const val UNIQUE_WORK_NAME = "PeriodicSyncInBackgroundWorker"

        fun enable(context: Context) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "enable()")
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PeriodicSyncInBackgroundWorker>(1, TimeUnit.HOURS)
                            .setConstraints(
                                    Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .setRequiresBatteryNotLow(true)
                                            .build()
                            )
                            .build()
            )
        }

        fun disable(context: Context) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "disable()")
            }

            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "do sync now")
        }

        return withContext (Dispatchers.Main) {
            try {
                DefaultAppLogic.with(context).syncUtil.doSync()

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "sync done")
                }

                Result.success()
            } catch (ex: SyncingDisabledException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "failed because syncing is disabled")
                }

                Result.failure()
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "background sync failed", ex)
                }

                Result.retry()
            }
        }
    }
}