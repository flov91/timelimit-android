/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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
package io.timelimit.android.logic.applist.data

import io.timelimit.android.encoding.DerReader
import io.timelimit.android.encoding.DerWriter
import io.timelimit.android.encoding.LengthDerWriter
import io.timelimit.android.encoding.readSequence
import io.timelimit.android.encoding.readSequenceOf
import io.timelimit.android.encoding.writeSequence
import io.timelimit.android.encoding.writeSequenceOf
import io.timelimit.proto.applist.InstalledAppsProto

data class InstalledAppsDer(
    val apps: List<InstalledAppDer> = emptyList(),
    val activities: List<InstalledAppActivityDer> = emptyList()
) {
    companion object {
        fun derDecode(reader: DerReader): InstalledAppsDer = reader.readSequence { reader ->
            val apps = reader.readSequenceOf(InstalledAppDer::derDecode)
            val activities = reader.readSequenceOf(InstalledAppActivityDer::derDecode)

            InstalledAppsDer(
                apps=apps,
                activities=activities
            )
        }

        fun fromProto(proto: InstalledAppsProto) = InstalledAppsDer(
            apps = proto.apps.map { InstalledAppDer.fromProto(it) },
            activities = proto.activities.map { InstalledAppActivityDer.fromProto(it) }
        )
    }

    fun derEncode(writer: DerWriter) {
        writer.writeSequence { writer ->
            writer.writeSequenceOf(apps) { writer, item ->
                item.derEncode(writer)
            }

            writer.writeSequenceOf(activities) { writer, item ->
                item.derEncode(writer)
            }
        }
    }

    fun encodedSize(): Long = LengthDerWriter().also { derEncode(it) }.length

    fun toProto() = InstalledAppsProto(
        apps = apps.map { it.toProto() },
        activities = activities.map { it.toProto() }
    )
}