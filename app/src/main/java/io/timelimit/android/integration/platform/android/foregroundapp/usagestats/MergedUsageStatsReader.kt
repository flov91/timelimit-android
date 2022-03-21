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
package io.timelimit.android.integration.platform.android.foregroundapp.usagestats

import java.lang.RuntimeException

class MergedUsageStatsReader(private val primary: UsageStatsReader, private val confirmation: UsageStatsReader): UsageStatsReader {
    override val timestamp = primary.timestamp
    override val eventType = primary.eventType
    override val instanceId = primary.instanceId
    override val packageName = primary.packageName
    override val className = primary.className

    override fun loadNextEvent(): Boolean {
        val didReadEvent = kotlin.run {
            val didReadPrimaryEvent = primary.loadNextEvent()
            val didReadConfirmationEvent = confirmation.loadNextEvent()

            if (didReadConfirmationEvent != didReadPrimaryEvent) {
                throw NotMatchingDataException(
                    if (didReadPrimaryEvent) "primary got next event but confirmation not"
                    else "confirmation got next event but primary not"
                )
            }

            didReadPrimaryEvent // == didReadNativeEvent
        }

        if (!didReadEvent) return false

        // check the consistency
        if (primary.eventType != confirmation.eventType) {
            throw NotMatchingDataException("got different eventTypes: ${primary.eventType} vs ${confirmation.eventType}")
        }

        if (primary.timestamp != confirmation.timestamp) {
            throw NotMatchingDataException("got different timestamps: ${primary.timestamp} vs ${confirmation.timestamp}")
        }

        return true
    }

    override fun free() {
        try {
            primary.free()
        } finally {
            confirmation.free()
        }
    }

    class NotMatchingDataException(detail: String): RuntimeException("not matching data: $detail")
}