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
package io.timelimit.android.proto

import io.timelimit.android.crypto.CryptContainer
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.sync.actions.AppActivityItem
import io.timelimit.android.sync.actions.InstalledApp
import io.timelimit.proto.applist.InstalledAppActivityProto
import io.timelimit.proto.applist.InstalledAppProto
import io.timelimit.proto.applist.InstalledAppsDifferenceProto
import io.timelimit.proto.applist.SavedAppsDifferenceProto

fun InstalledAppProto.Recommendation.toDb(): AppRecommendation = when (this) {
    InstalledAppProto.Recommendation.NONE -> AppRecommendation.None
    InstalledAppProto.Recommendation.WHITELIST -> AppRecommendation.Whitelist
    InstalledAppProto.Recommendation.BLACKLIST -> AppRecommendation.Blacklist
}

fun AppRecommendation.toProto(): InstalledAppProto.Recommendation = when (this) {
    AppRecommendation.None -> InstalledAppProto.Recommendation.NONE
    AppRecommendation.Whitelist -> InstalledAppProto.Recommendation.WHITELIST
    AppRecommendation.Blacklist -> InstalledAppProto.Recommendation.BLACKLIST
}

fun InstalledAppProto.toInstalledApp(): InstalledApp = InstalledApp(
    packageName = this.package_name,
    title = this.title,
    recommendation = this.recommendation.toDb(),
    isLaunchable = this.is_launchable
)

fun App.toProto(): InstalledAppProto = InstalledAppProto(
    package_name = this.packageName,
    title = this.title,
    is_launchable = this.isLaunchable,
    recommendation = this.recommendation.toProto()
)

fun InstalledAppActivityProto.toAppActivityItem(): AppActivityItem = AppActivityItem(
    packageName = this.package_name,
    className = this.class_name,
    title = this.title
)

fun AppActivity.toProto(): InstalledAppActivityProto = InstalledAppActivityProto(
    package_name = this.appPackageName,
    class_name = this.activityClassName,
    title = this.title
)

fun SavedAppsDifferenceProto.Companion.build(header: CryptContainer.Header, diff: InstalledAppsDifferenceProto): SavedAppsDifferenceProto =
    SavedAppsDifferenceProto(
        base_generation = header.generation,
        base_counter = header.counter,
        apps = diff
    )

fun SavedAppsDifferenceProto.Companion.build(encryptedBaseData: ByteArray, diff: InstalledAppsDifferenceProto): SavedAppsDifferenceProto =
    build(CryptContainer.Header.read(encryptedBaseData), diff)