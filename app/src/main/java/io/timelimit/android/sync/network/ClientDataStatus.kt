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
package io.timelimit.android.sync.network

import android.util.JsonWriter
import io.timelimit.android.data.Database

data class ClientDataStatus(
        val deviceListVersion: String,
        val installedAppsVersionsByDeviceId: Map<String, String>,
        val deviceDetailData: Map<String, DeviceDataStatus>,
        val categories: Map<String, CategoryDataStatus>,
        val userListVersion: String,
        val lastKeyRequestServerSequence: Long?,
        val lastKeyResponseServerSequence: Long?,
        val dhKeyVersion: String?,
        val u2fVersion: String?
) {
    companion object {
        private const val DEVICES = "devices"
        private const val APPS = "apps"
        private const val CATEGORIES = "categories"
        private const val USERS = "users"
        private const val CLIENT_LEVEL = "clientLevel"
        private const val DEVICES_DETAIL = "devicesDetail"
        private const val LAST_KEY_REQUEST_SEQUENCE = "kri"
        private const val LAST_KEY_RESPONSE_SEQUENCE = "kr"
        private const val DH = "dh"
        private const val U2F = "u2f"
        const val CLIENT_LEVEL_VALUE = 6

        val empty = ClientDataStatus(
            deviceListVersion = "",
            installedAppsVersionsByDeviceId = emptyMap(),
            deviceDetailData = emptyMap(),
            categories = emptyMap(),
            userListVersion = "",
            lastKeyRequestServerSequence = null,
            lastKeyResponseServerSequence = null,
            dhKeyVersion = null,
            u2fVersion = null
        )

        fun getClientDataStatusSync(database: Database): ClientDataStatus {
            return database.runInUnobservedTransaction {
                ClientDataStatus(
                    deviceListVersion = database.config().getDeviceListVersionSync(),
                    installedAppsVersionsByDeviceId = database.device()
                        .getInstalledAppsVersionsSync()
                        .associateBy { it.deviceId }
                        .mapValues { it.value.installedAppsVersions },
                    deviceDetailData = if (database.config().getServerApiLevelSync() >= 4)
                        database.device().getDeviceDetailDataSync()
                            .associateBy { it.deviceId }
                            .mapValues {
                                val item = it.value

                                DeviceDataStatus(
                                    appsBaseVersion = item.appBaseVersion,
                                    appsDiffVersion = item.appDiffVersion
                                )
                            }
                    else emptyMap(),
                    categories = database.category().getCategoriesWithVersionNumbersSybc()
                        .associateBy { it.categoryId }
                        .mapValues {
                            val item = it.value

                            CategoryDataStatus(
                                baseVersion = item.baseVersion,
                                assignedAppsVersion = item.assignedAppsVersion,
                                timeLimitRulesVersion = item.timeLimitRulesVersion,
                                usedTimeItemsVersion = item.usedTimeItemsVersion,
                                taskListVersion = item.taskListVersion
                            )
                        },
                    userListVersion = database.config().getUserListVersionSync(),
                    lastKeyRequestServerSequence = database.config().getLastServerKeyRequestSequenceSync(),
                    lastKeyResponseServerSequence = database.config().getLastServerKeyResponseSequenceSync(),
                    dhKeyVersion = database.config().getLastDhKeySync()?.version,
                    u2fVersion = database.config().getU2fVersionSync()
                )
            }
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(CLIENT_LEVEL).value(CLIENT_LEVEL_VALUE)
        writer.name(DEVICES).value(deviceListVersion)
        writer.name(USERS).value(userListVersion)

        writer.name(APPS)
        writer.beginObject()
        installedAppsVersionsByDeviceId.entries.forEach {
            writer.name(it.key).value(it.value)
        }
        writer.endObject()

        if (deviceDetailData.isNotEmpty()) {
            writer.name(DEVICES_DETAIL)
            writer.beginObject()
            deviceDetailData.entries.forEach {
                writer.name(it.key)
                it.value.serialize(writer)
            }
            writer.endObject()
        }

        writer.name(CATEGORIES)
        writer.beginObject()
        categories.entries.forEach {
            writer.name(it.key)
            it.value.serialize(writer)
        }
        writer.endObject()

        lastKeyRequestServerSequence?.let { writer.name(LAST_KEY_REQUEST_SEQUENCE).value(it) }
        lastKeyResponseServerSequence?.let { writer.name(LAST_KEY_RESPONSE_SEQUENCE).value(it) }
        dhKeyVersion?.let { writer.name(DH).value(it) }
        u2fVersion?.let { writer.name(U2F).value(it) }

        writer.endObject()
    }
}

data class CategoryDataStatus(
        val baseVersion: String,
        val assignedAppsVersion: String,
        val timeLimitRulesVersion: String,
        val usedTimeItemsVersion: String,
        val taskListVersion: String
) {
    companion object {
        private const val BASE_VERSION = "base"
        private const val ASSIGNED_APPS_VERSION = "apps"
        private const val TIME_LIMIT_RULES_VERSION = "rules"
        private const val USED_TIME_ITEMS_VERSION = "usedTime"
        private const val TASK_LIST_VERSION = "tasks"
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(BASE_VERSION).value(baseVersion)
        writer.name(ASSIGNED_APPS_VERSION).value(assignedAppsVersion)
        writer.name(TIME_LIMIT_RULES_VERSION).value(timeLimitRulesVersion)
        writer.name(USED_TIME_ITEMS_VERSION).value(usedTimeItemsVersion)

        if (taskListVersion.isNotEmpty()) writer.name(TASK_LIST_VERSION).value(taskListVersion)

        writer.endObject()
    }
}

data class DeviceDataStatus (
    val appsBaseVersion: String?,
    val appsDiffVersion: String?
) {
    companion object {
        private const val APPS_BASE_VERSION = "appsB"
        private const val APPS_DIFF_VERSION = "appsD"
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        appsBaseVersion?.let { writer.name(APPS_BASE_VERSION).value(it) }
        appsDiffVersion?.let { writer.name(APPS_DIFF_VERSION).value(it) }

        writer.endObject()
    }
}