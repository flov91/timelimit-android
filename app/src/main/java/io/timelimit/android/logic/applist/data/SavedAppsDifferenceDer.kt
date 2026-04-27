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
import io.timelimit.android.encoding.readLong
import io.timelimit.android.encoding.readSequence
import io.timelimit.android.encoding.writeInteger
import io.timelimit.android.encoding.writeSequence
import io.timelimit.proto.applist.SavedAppsDifferenceProto

data class SavedAppsDifferenceDer(
    val apps: InstalledAppsDifferenceDer,
    val baseGeneration: Long,
    val baseCounter: Long
) {
    companion object {
        fun derDecode(reader: DerReader): SavedAppsDifferenceDer = reader.readSequence { reader ->
            val apps = InstalledAppsDifferenceDer.derDecode(reader)
            val baseGeneration = reader.readLong()
            val baseCounter = reader.readLong()

            SavedAppsDifferenceDer(
                apps = apps,
                baseGeneration = baseGeneration,
                baseCounter = baseCounter
            )
        }

        fun fromProto(proto: SavedAppsDifferenceProto) = SavedAppsDifferenceDer(
            apps = proto.apps?.let { InstalledAppsDifferenceDer.fromProto(it) } ?: InstalledAppsDifferenceDer(),
            baseGeneration = proto.base_generation,
            baseCounter = proto.base_counter
        )
    }

    fun derEncode(writer: DerWriter) = writer.writeSequence { writer ->
        apps.derEncode(writer)
        writer.writeInteger(baseGeneration)
        writer.writeInteger(baseCounter)
    }
}