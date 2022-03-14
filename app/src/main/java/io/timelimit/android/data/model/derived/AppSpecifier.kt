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
package io.timelimit.android.data.model.derived

import kotlin.text.StringBuilder

data class AppSpecifier(val packageName: String, val activityName: String?, val deviceId: String?) {
    companion object {
        fun decode(input: String): AppSpecifier {
            val activityIndex = input.indexOf(':')
            val deviceIndex = input.indexOf('@', startIndex = activityIndex)
            val packageNameEndIndex = if (activityIndex != -1) activityIndex else deviceIndex

            val packageName = if (packageNameEndIndex == -1) input else input.substring(0, packageNameEndIndex)
            val activityName = if (activityIndex == -1) null else {
                input.substring(activityIndex + 1, if (deviceIndex == -1) input.length else deviceIndex)
            }
            val deviceId = if (deviceIndex == -1) null else input.substring(deviceIndex + 1)

            return AppSpecifier(
                packageName = packageName,
                activityName = activityName,
                deviceId = deviceId
            )
        }
    }

    init {
        if (packageName.indexOf(':') != -1 || packageName.indexOf(':') != -1) {
            throw InvalidValueException()
        }

        if (activityName != null && activityName?.indexOf('@') != -1) {
            throw InvalidValueException()
        }
    }

    fun encode(): String = StringBuilder().let { builder ->
        builder.append(packageName)

        if (activityName != null) {
            builder.append(':').append(activityName)
        }

        if (deviceId != null) {
            builder.append('@').append(deviceId)
        }

        builder.trimToSize()
        builder.toString()
    }

    class InvalidValueException: RuntimeException()
}