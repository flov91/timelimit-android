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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Build
import android.util.SparseArray
import androidx.core.util.size
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus

@TargetApi(Build.VERSION_CODES.Q)
class InstanceIdForegroundAppHelper(context: Context): UsageStatsForegroundAppHelper(context) {
    companion object {
        const val START_QUERY_INTERVAL = 1000 * 60 * 60 * 24 * 3 // 3 days
        private const val TOLERANCE = 3000L
    }

    private var lastQueryTime = 0L
    private var lastEventTimestamp = 0L
    private val apps = SparseArray<ForegroundApp>()
    private val nativeEvent = UsageEvents.Event()

    override suspend fun getForegroundApps(
        queryInterval: Long,
        experimentalFlags: Long
    ): Set<ForegroundApp> {
        if (Build.VERSION.SDK_INT > 32) {
            throw InstanceIdException.UntestedSystemVersionException()
        }

        if (getPermissionStatus() != RuntimePermissionStatus.Granted) {
            throw SecurityException()
        }

        val result = backgroundThread.executeAndWait {
            val now = System.currentTimeMillis()

            val didTimeWentBackwards = lastQueryTime > now
            val didNeverQuery = lastQueryTime == 0L
            val shouldDoFullQuery = didTimeWentBackwards || didNeverQuery

            if (shouldDoFullQuery) {
                apps.clear()
            }

            val minQueryStartTime = (now - START_QUERY_INTERVAL).coerceAtLeast(1)
            val queryStartTimeByLastEvent = lastEventTimestamp - TOLERANCE

            val queryStartTime = if (shouldDoFullQuery) {
                minQueryStartTime
            } else {
                queryStartTimeByLastEvent
                    .coerceAtLeast(minQueryStartTime)
                    .coerceAtMost(now - TOLERANCE)
            }

            val queryEndTime = now + TOLERANCE

            usageStatsManager.queryEvents(queryStartTime, queryEndTime)?.let { nativeEvents ->
                val events = TlUsageEvents.fromUsageEvents(nativeEvents)

                try {
                    var isFirstEvent = true

                    while (true) {
                        // loop condition with additional checks
                        val didReadEvent = kotlin.run {
                            val didReadNativeEvent = nativeEvents.getNextEvent(nativeEvent)
                            val didReadTlEvent = events.readNextItem()

                            if (didReadNativeEvent != didReadTlEvent) {
                                throw InstanceIdException.NotMatchingData(
                                    if (didReadTlEvent) "events got next event but nativeEvents not"
                                    else "nativeEvents got next event but events not"
                                )
                            }

                            didReadTlEvent // == didReadNativeEvent
                        }

                        if (!didReadEvent) break

                        // check the consistency
                        kotlin.run {
                            if (events.eventType != nativeEvent.eventType) {
                                throw InstanceIdException.NotMatchingData("got different eventTypes: ${events.eventType} vs ${nativeEvent.eventType}")
                            }

                            if (events.timestamp != nativeEvent.timeStamp) {
                                throw InstanceIdException.NotMatchingData("got different timestamps: ${events.timestamp} vs ${nativeEvent.timeStamp}")
                            }

                            if (events.timestamp < lastEventTimestamp && !isFirstEvent) {
                                throw InstanceIdException.EventsNotSortedByTimestamp()
                            }
                        }

                        // process the event
                        if (events.eventType == TlUsageEvents.DEVICE_STARTUP) {
                            apps.clear()
                        } else if (events.eventType == TlUsageEvents.MOVE_TO_FOREGROUND) {
                            val app = ForegroundApp(events.packageName, events.className)

                            apps.put(events.instanceId, app)
                        } else if (
                            events.eventType == TlUsageEvents.MOVE_TO_BACKGROUND ||
                            events.eventType == TlUsageEvents.ACTIVITY_STOPPED
                        ) {
                            apps.remove(events.instanceId)
                        }

                        // save values for the next iteration and the next query
                        isFirstEvent = false
                        lastEventTimestamp = events.timestamp
                    }
                } finally {
                    events.free()

                    // the nativeEvents have no free function; but they release their data when everything was read
                    while (nativeEvents.getNextEvent(nativeEvent)) {/* consume all values */}
                }
            }

            lastQueryTime = now

            val appsSet = mutableSetOf<ForegroundApp>()

            for (index in 0 until apps.size) {
                appsSet.add(apps.valueAt(index))
            }

            appsSet
        }

        return result
    }

    sealed class InstanceIdException(message: String): RuntimeException(message) {
        class UntestedSystemVersionException: InstanceIdException("untested system version")
        class NotMatchingData(detail: String): InstanceIdException("not matching data: $detail")
        class EventsNotSortedByTimestamp: InstanceIdException("events not sorted by timestamp")
    }
}