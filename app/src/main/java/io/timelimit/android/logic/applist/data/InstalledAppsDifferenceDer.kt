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
import io.timelimit.android.encoding.readUtf8String
import io.timelimit.android.encoding.writeSequence
import io.timelimit.android.encoding.writeSequenceOf
import io.timelimit.android.encoding.writeUtf8String
import io.timelimit.proto.applist.InstalledAppsDifferenceProto

data class InstalledAppsDifferenceDer (
    val added: InstalledAppsDer = InstalledAppsDer(),
    val removedPackages: List<String> = emptyList(),
    val removedActivities: List<RemovedAppActivityDer> = emptyList()
) {
    companion object {
        fun derDecode(reader: DerReader): InstalledAppsDifferenceDer = reader.readSequence { reader ->
            val added = InstalledAppsDer.derDecode(reader)
            val removedPackages = reader.readSequenceOf { reader -> reader.readUtf8String() }
            val removedActivities = reader.readSequenceOf(RemovedAppActivityDer::derDecode)

            InstalledAppsDifferenceDer(
                added = added,
                removedPackages = removedPackages,
                removedActivities = removedActivities
            )
        }

        fun fromProto(proto: InstalledAppsDifferenceProto) = InstalledAppsDifferenceDer(
            added = proto.added?.let { InstalledAppsDer.fromProto(it) } ?: InstalledAppsDer(),
            removedPackages = proto.removed_packages,
            removedActivities = proto.removed_activities.map { RemovedAppActivityDer.fromProto(it) }
        )
    }

    fun derEncode(writer: DerWriter) = writer.writeSequence { writer ->
        added.derEncode(writer)
        writer.writeSequenceOf(removedPackages) { writer, item -> writer.writeUtf8String(item) }
        writer.writeSequenceOf(removedActivities) { writer, item -> item.derEncode(writer) }
    }

    fun encodedSize(): Long = LengthDerWriter().also { derEncode(it) }.length

    fun toProto() = InstalledAppsDifferenceProto(
        added = added.toProto(),
        removed_packages = removedPackages,
        removed_activities = removedActivities.map { it.toProto() }
    )
}