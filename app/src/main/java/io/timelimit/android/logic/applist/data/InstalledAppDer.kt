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

import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.encoding.DerReader
import io.timelimit.android.encoding.DerReaderValueOutOfBoundsException
import io.timelimit.android.encoding.DerWriter
import io.timelimit.android.encoding.readBoolean
import io.timelimit.android.encoding.readEnumerated
import io.timelimit.android.encoding.readSequence
import io.timelimit.android.encoding.readUtf8String
import io.timelimit.android.encoding.writeBoolean
import io.timelimit.android.encoding.writeEnumerated
import io.timelimit.android.encoding.writeSequence
import io.timelimit.android.encoding.writeUtf8String
import io.timelimit.android.sync.actions.InstalledApp
import io.timelimit.proto.applist.InstalledAppProto

data class InstalledAppDer(
    val packageName: String,
    val title: String,
    val isLaunchable: Boolean,
    val recommendation: AppRecommendation
) {
    companion object {
        fun derDecode(reader: DerReader): InstalledAppDer = reader.readSequence { reader ->
            val packageName = reader.readUtf8String()
            val title = reader.readUtf8String()
            val isLaunchable = reader.readBoolean()
            val recommendation = when (reader.readEnumerated()) {
                -1 -> AppRecommendation.Blacklist
                0 -> AppRecommendation.None
                1 -> AppRecommendation.Whitelist
                else -> throw DerReaderValueOutOfBoundsException()
            }

            InstalledAppDer(
                packageName = packageName,
                title = title,
                isLaunchable = isLaunchable,
                recommendation = recommendation
            )
        }

        fun fromProto(proto: InstalledAppProto) = InstalledAppDer(
            packageName = proto.package_name,
            title = proto.title,
            isLaunchable = proto.is_launchable,
            recommendation = when(proto.recommendation) {
                InstalledAppProto.Recommendation.BLACKLIST -> AppRecommendation.Blacklist
                InstalledAppProto.Recommendation.WHITELIST -> AppRecommendation.Whitelist
                else -> AppRecommendation.None
            }
        )
    }

    fun derEncode(writer: DerWriter) {
        writer.writeSequence { writer ->
            writer.writeUtf8String(packageName)
            writer.writeUtf8String(title)
            writer.writeBoolean(isLaunchable)
            writer.writeEnumerated(when(recommendation) {
                AppRecommendation.Blacklist -> -1
                AppRecommendation.None -> 0
                AppRecommendation.Whitelist -> 1
            })
        }
    }

    fun toProto() = InstalledAppProto(
        package_name = packageName,
        title = title,
        is_launchable = isLaunchable,
        recommendation = when (recommendation) {
            AppRecommendation.Blacklist -> InstalledAppProto.Recommendation.BLACKLIST
            AppRecommendation.None -> InstalledAppProto.Recommendation.NONE
            AppRecommendation.Whitelist -> InstalledAppProto.Recommendation.WHITELIST
        }
    )

    fun toInstalledApp() = InstalledApp(
        packageName = packageName,
        title = title,
        isLaunchable = isLaunchable,
        recommendation = recommendation
    )
}