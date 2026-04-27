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
import io.timelimit.android.encoding.readSequence
import io.timelimit.android.encoding.readUtf8String
import io.timelimit.android.encoding.writeSequence
import io.timelimit.android.encoding.writeUtf8String
import io.timelimit.android.sync.actions.AppActivityItem
import io.timelimit.proto.applist.InstalledAppActivityProto

data class InstalledAppActivityDer(
    val packageName: String,
    val className: String,
    val title: String
) {
    companion object {
        fun derDecode(reader: DerReader): InstalledAppActivityDer = reader.readSequence { reader ->
            val packageName = reader.readUtf8String()
            val className = reader.readUtf8String()
            val title = reader.readUtf8String()

            InstalledAppActivityDer(
                packageName = packageName,
                className = className,
                title = title
            )
        }

        fun fromProto(proto: InstalledAppActivityProto) = InstalledAppActivityDer(
            packageName = proto.package_name,
            className = proto.class_name,
            title = proto.title
        )
    }

    fun derEncode(writer: DerWriter) {
        writer.writeSequence { writer ->
            writer.writeUtf8String(packageName)
            writer.writeUtf8String(className)
            writer.writeUtf8String(title)
        }
    }

    fun toProto() = InstalledAppActivityProto(
        package_name = packageName,
        class_name = className,
        title = title
    )

    fun toAppActivityItem() = AppActivityItem(
        packageName = packageName,
        className = className,
        title = title
    )
}